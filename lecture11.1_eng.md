# Implement server-streaming gRPC API - Golang
Hi everyone, today we will learn how to implement a server-streaming RPC in Go.
First we will define a new RPC in the proto file to search for laptops with
some specific requirements. Then we will implement the server, the client and
write unit test for that RPC.

## Add server-streaming RPC definition to Protobuf
Alright let's start! I will open the `pcbook` golang project that we've been 
working on. Our RPC will allow us to search for laptops that satisfy some 
configuration requirements. So I will create a `filter_message.proto` file. 
This message will define what kind of laptop we're looking for. Such as the 
maximum price that we're willing to pay for the laptop, the minimum number of 
cores that the laptop CPU should have, the minimum frequency of the CPU and the
minimum size of the RAM.

```protobuf
syntax = "proto3";

package techschool_pcbook;

option go_package = ".;pb";
option java_package = "com.github.techschool.pcbook.pb";
option java_multiple_files = true;

import "memory_message.proto";

message Filter {
  double max_price_usd = 1;
  uint32 min_cpu_cores = 2;
  double min_cpu_ghz = 3;
  Memory min_ram = 4;
}
```

OK now we will define the new server-streaming RPC in the 
`laptop_service.proto` file. First we define the `SearchLaptopRequest` that 
contains only 1 Filter field. Then a `SearchLaptopResponse` that contains only
1 Laptop Field.

`proto/laptop_service.proto`
```protobuf
// ...
import "filter_message.proto";

//...

message SearchLaptopRequest { Filter filter = 1; }

message SearchLaptopResponse { Laptop laptop = 1; }
```

The server-streaming RPC is defined in a similar way to the unary RPC. Start 
with the `rpc` keyword, then the RPC name is `SearchLaptop`, the input is 
`SearchLaptopRequest` and output is a stream of `SearchLaptopResponse`. That's
it. Pretty straight-forward.

```protobuf
// ...

service LaptopService {
  rpc CreateLaptop(CreateLaptopRequest) returns (CreateLaptopResponse) {};
  rpc SearchLaptop(SearchLaptopRequest) returns (stream SearchLaptopResponse) {};
}
```

OK, let's generate the code. In the `laptop_service.pb.go` file some new codes
have been added. We have the `SearchLaptopRequest` struct, the 
`SearchLaptopResponse` struct 

```go
type SearchLaptopRequest struct {
    state         protoimpl.MessageState
    sizeCache     protoimpl.SizeCache
    unknownFields protoimpl.UnknownFields
    
    Filter *Filter `protobuf:"bytes,1,opt,name=filter,proto3" json:"filter,omitempty"`
}

// ...

type SearchLaptopResponse struct {
    state         protoimpl.MessageState
    sizeCache     protoimpl.SizeCache
    unknownFields protoimpl.UnknownFields
    
    Laptop *Laptop `protobuf:"bytes,1,opt,name=laptop,proto3" json:"laptop,omitempty"`
}
```

Then the `LaptopServiceClient` interface with a new `SearchLaptop` function.

```go
type LaptopServiceClient interface {
    CreateLaptop(ctx context.Context, in *CreateLaptopRequest, opts ...grpc.CallOption) (*CreateLaptopResponse, error)
    SearchLaptop(ctx context.Context, in *SearchLaptopRequest, opts ...grpc.CallOption) (LaptopService_SearchLaptopClient, error)
}
```

Similarly we also have a new `SearchLaptop` function inside the 
`LaptopServiceServer` interface.

```go
type LaptopServiceServer interface {
    CreateLaptop(context.Context, *CreateLaptopRequest) (*CreateLaptopResponse, error)
    SearchLaptop(*SearchLaptopRequest, LaptopService_SearchLaptopServer) error
    mustEmbedUnimplementedLaptopServiceServer()
}
```

## Add search function to the in-memory store
We will implement the server side first. Let's add a `Search()` function to
the `LaptopStore` interface. It takes a filter as input, and also a callback
function to report wherever a laptop is found. It will return an error.

```go
// ...

type LaptopStore interface {
    // ...
    // Search searches for laptops with filter, returns one by one via the found function
    Search(filter *pb.Filter, found func(laptop *pb.Laptop) error) error
}

// ...
```

Now let's implement this function for the `InMemoryLaptopStore`. Since we're
reading data, we have to acquire a read lock. Remember to unlock it 
afterward.

```go
// ...

// Search searches for laptops with filter, returns one by one via the found function
func (store *InMemoryLaptopStore) Search(
    filter *pb.Filter,
    found func(laptop *pb.Laptop) error,
) error {
    store.mutex.RLock()
    defer store.mutex.RUnlock()
}
```

We iterate through all laptops in the store and check which one is 
qualified to the filter. The `isQualified()` function takes a filter and a 
laptop as input and returns `true` if the laptop satisfies the filter. If the 
laptop price is greater than the maximum price in the filter, returns `false`.
If the number cores of the laptop CPU is less than the minimum cores in the
filter, return `false`. If the minimum frequency of the laptop CPU is less than
that of the filter, return `false`. Now we have to compare the RAM. Since there
are different types of memory units to compare them, we have to write a 
function to convert the memory to the smallest unit: `BIT`. If the size of the 
laptop RAM is smaller than that of the filter, return `false`, else return 
`true`.

```go
// ...
func isQualified(filter *pb.Filter, laptop *pb.Laptop) bool {
    if laptop.GetPriceUsd() > filter.GetMaxPriceUsd() {
        return false
    }
    
    if laptop.GetCpu().GetNumberCores() < filter.GetMinCpuCores() {
        return false
    }
    
    if laptop.GetCpu().GetMinGhz() < filter.GetMinCpuGhz() {
        return false
    }
    
    if toBit(laptop.GetRam()) < toBit(filter.GetMinRam()) {
        return false
    }
    
    return true
}
```

Now let's implement the `toBit()` function. First we get the memory value. 
Then we do a switch-case on the memory unit. If it is `BIT`, we simply return 
the value. If it is `BYTE` we have to multiply the value by 8 because `1 BYTE = 
8 BITS`. And because 8 = 2^3, we can use a bit-operator shift-left 3 here to
avoid multiplication. If it is `KILOBYTE` we have to multiply the value by 1024
and 8 because `1 KILOBYTE = 1024 BYTEs`. And because 1024 * 8 = 2^13 we can use
a simple shift-left 13 here. Similarly, if it is `MEGABYTE`, we return value
shift-left 23. For `GIGABYTE`, value shift-left 33 and finally for `TERABYTE`, 
value shift-left 43. For the default case, just return 0.

```go
// ...

func toBit(memory *pb.Memory) uint64 {
    value := memory.GetValue()

    switch memory.GetUnit() {
        case pb.Memory_BIT:
            return value
        case pb.Memory_BYTE:
            return value << 3 // 8 = 2^3
        case pb.Memory_KILOBYTE:
            return value << 13 // 1024 * 8 = 2^10 * 2^3 = 2^13
        case pb.Memory_MEGABYTE:
            return value << 23
        case pb.Memory_GIGABYTE:
            return value << 33
        case pb.Memory_TERABYTE:
            return value << 43
        default:
            return 0
    }
}
```

Now let's go back to our `SearchLaptop()` function. When the laptop is 
qualified, we have to deep-copy it before calling the callback function. 
Since deep-copy is used in many places I will write a separate function for it.
Just copy and paste the code block to this `deepCopy` function from `Find` 
function.

```go
func deepCopy(laptop *pb.Laptop) (*pb.Laptop, error) {
    // deep copy
    other := &pb.Laptop{}
    err := copier.Copy(other, laptop)
    if err != nil {
        return nil, fmt.Errorf("cannot copy laptop data: %w", err)
    }
    
    return other, nil
}
```

Then in this `Find()` function, we simply return `deepCopy(laptop)` and the 
`Save()` function can also be simplified like this.

```go
func (store *InMemoryLaptopStore) Find(id string) (*pb.Laptop, error) {
    store.mutex.RLock()
    defer store.mutex.RUnlock()
    
    laptop := store.data[id]
    if laptop == nil {
        return nil, nil
    }
    
    return deepCopy(laptop)
}

// Save saves the laptop to the store
func (store *InMemoryLaptopStore) Save(laptop *pb.Laptop) error {
    store.mutex.Lock()
    defer store.mutex.Unlock()
    
    if store.data[laptop.Id] != nil {
        return ErrAlreadyExists
    }
    
    // deep copy
    other, err := deepCopy(laptop)
    if err != nil {
        return err
    }
    
    store.data[other.Id] = other
    return nil
}
```

In the `Search()` function, we deep copy the qualified laptop, and call 
`found()` to send it to the caller. If there's an error, returns it. Otherwise,
return `nil` at the end of the function. OK, the store is done.

```go
// Search searches for laptops with filter, returns one by one via the found function
func (store *InMemoryLaptopStore) Search(
    filter *pb.Filter,
    found func(laptop *pb.Laptop) error,
) error {
    // ...

    for _, laptop := range store.data {
        if isQualified(filter, laptop) {
            other, err := deepCopy(laptop)
            if err != nil {
                return err
            }
    
            err = found(other)
            if err != nil {
                return err
            }
        }
    }
    
    return nil
}
```

## Implement the server
Now let's implement the server. We will have to implement the `SearchLaptop`
function of the `LaptopServiceServer` interface. So I will copy this function
and paste it in the `laptop_server.go` file. It has 2 arguments: the input 
request and the output stream response. The first thing we do is to get the
filter from the request. Then we write a log saying a search-laptop request
is received with this filter, and we call `server.Store.Search`, pass in the 
filter and a callback function. If an error occurs, we return it with the 
`Internal status code` else we return `nil`. Now in the callback function, when
we found a laptop, we create a new response object with that laptop and send it
to the client by calling `stream.Send()`. If an error occurs, just return it. 
Else we write a simple log saying we have sent the laptop with this ID then
return nil. And we're done with the server.

```go
// SearchLaptop is a server-streaming RPC to search for laptops
func (server *LaptopServer) SearchLaptop(
    req *pb.SearchLaptopRequest,
    stream pb.LaptopService_SearchLaptopServer,
) error {
    filter := req.GetFilter()
    log.Printf("receive a search-laptop request with filter: %v", filter)
    
    err := server.Store.Search(
        filter,
        func (laptop *pb.Laptop) error {
            res := &pb.SearchLaptopResponse{Laptop: laptop}
    
            err := stream.Send(res)
            if err != nil {
                return err
            }
    
            log.Printf("sent laptop with id: %s", laptop.GetId())
            return nil
        },
    )
    
    if err != nil {
        return status.Errorf(codes.Internal, "unexpected error: %v", err)
    }
    
    return nil
}
```

## Implement the client
Now let's implement the client. First I will make a separate function to create
a random laptop. Let's copy code block from `main` function of 
`cmd/client/main.go` file and paste it in the `createLaptop()` function. Now 
in the `main` function we will use a `for` loop to create 10 random laptops.

`cmd/client/main.go`
```go
func createLaptop(laptopClient pb.LaptopServiceClient)  {
    laptop := sample.NewLaptop()
    laptop.Id = ""
    req := &pb.CreateLaptopRequest{
        Laptop: laptop,
    }
    
    // set timeout
    ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
    defer cancel()
    
    res, err := laptopClient.CreateLaptop(ctx, req)
    if err != nil {
        st, ok := status.FromError(err)
        if ok && st.Code() == codes.AlreadyExists {
            // not a big deal
            log.Print("laptop already exists")
        } else {
            log.Fatal("cannot create laptop: ", err)
        }
        return
    }
    
    log.Printf("created laptop with id: %s", res.Id)
}
func main() {
    // ...
    
    laptopClient := pb.NewLaptopServiceClient(conn)
    for i := 0; i < 10; i++ {
        createLaptop(laptopClient)
    }
}
```

Then we will create a new search filter. I want to search for laptops with
maximum price of 3000 and at least 4 CPU cores, minimum frequency of 2.5 and
at least 8 gigabytes of RAM. Now we call `searchLaptop` with the client and
the filter.

```go
func main() {
    // ...
    filter := &pb.Filter{
        MaxPriceUsd: 3000,
        MinCpuCores: 4,
        MinCpuGhz:   2.5,
        MinRam:      &pb.Memory{Value: 8, Unit: pb.Memory_GIGABYTE},
    }

    searchLaptop(laptopClient, filter)
}
```

Let's write this function. First we write a log here to show the filter values. 
Then we create a context with timeout of 5 seconds. We make a 
`SearchLaptopRequest` object with the filter. Then we call 
`laptopClient.SearchLaptop()` to get the stream. If there's an error, write a
fatal log. Else we use a `for` loop to receive multiple responses from the 
stream. If it returns an end-of-file (EOF) error, this means it's the end of 
the stream. So we just return. Otherwise, if error is not `nil`, we write a 
fatal log. If everything goes well, we can get the laptop from the stream. I 
will print out only a few properties of the laptop so that it's easier to read:
the laptop ID, the brand, the name, the number of CPU cores, the min frequency
of the CPU, the RAM and finally the price.

`cmd/client/main.go`
```go
func searchLaptop(laptopClient pb.LaptopServiceClient, filter *pb.Filter) {
	log.Print("search filter: ", filter)

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	req := &pb.SearchLaptopRequest{Filter: filter}
	stream, err := laptopClient.SearchLaptop(ctx, req)
	if err != nil {
		log.Fatal("cannot search laptop: ", err)
	}

	for {
		res, err := stream.Recv()
		if err == io.EOF {
			return
		}
		if err != nil {
			log.Fatal("cannot receive response: ", err)
		}

		laptop := res.GetLaptop()
		log.Print("- found: ", laptop.GetId())
		log.Print(" + brand: ", laptop.GetBrand())
		log.Print(" + name: ", laptop.GetName())
		log.Print(" + cpu cores: ", laptop.GetCpu().GetNumberCores())
		log.Print(" + cpu min ghz: ", laptop.GetCpu().GetMinGhz())
		log.Print(" + ram: ", laptop.GetRam().GetValue(), laptop.GetRam().GetUnit())
		log.Print(" + price: ", laptop.GetPriceUsd(), "usd")
	}
}
```

OK, let's run the server and run the client.

```shell
2021/04/02 19:30:12 cannot create laptop: rpc error: code = DeadlineExceeded desc = context deadline exceeded
exit status 1
```

There's a deadline exceeded error when creating the laptops. This is because
in the previous lecture, we're doing a sleep for 6 seconds on the server side.
So let's comment this out.

`service/laptop_server.go`
```go
func (server *LaptopServer) CreateLaptop(
    ctx context.Context,
    req *pb.CreateLaptopRequest,
) (*pb.CreateLaptopResponse, error) {
    // ...
	
    // some heavy processing
    // time.Sleep(6 * time.Second) 
    
    //...
}
```

And restart the server. Then re-run the client. This time we've created 10 
laptops, and found 3 laptops that matches the filter.

```shell
2021/04/02 19:35:35 created laptop with id: 0b3b3be6-1341-4a8b-820e-b48cc63e7a4f
2021/04/02 19:35:35 created laptop with id: 2d4d9bae-16ca-4e96-9588-0a8ebcfb12d8
2021/04/02 19:35:35 created laptop with id: 724e7fe8-5a70-4b70-97c1-8435fbfa01f9
2021/04/02 19:35:35 created laptop with id: c6d58719-6153-4db2-af54-664d0083bcfb
2021/04/02 19:35:35 created laptop with id: 11ea4983-ab3a-492f-829a-4237839226b0
2021/04/02 19:35:35 created laptop with id: fc2a5fef-298e-49b5-a974-fc09695c15ff
2021/04/02 19:35:35 created laptop with id: 74f7643b-5c80-4dee-aa65-7e3a5c4ebd37
2021/04/02 19:35:35 created laptop with id: 2b052e26-2758-4382-bcfd-aa783f5d6831
2021/04/02 19:35:35 created laptop with id: f86d0fbc-50f2-4f23-93af-34d5353226eb
2021/04/02 19:35:35 created laptop with id: a4498ed8-eccc-4907-b8e3-68ab0d7ebc70
2021/04/02 19:35:35 search filter: max_price_usd:3000 min_cpu_cores:4 min_cpu_ghz:2.5 min_ram:{value:8 unit:GIGABYTE}
2021/04/02 19:35:35 - found: 724e7fe8-5a70-4b70-97c1-8435fbfa01f9
2021/04/02 19:35:35  + brand: Dell
2021/04/02 19:35:35  + name: Alienware
2021/04/02 19:35:35  + cpu cores: 6
2021/04/02 19:35:35  + cpu min ghz: 3.1019312916606565
2021/04/02 19:35:35  + ram: 22 GIGABYTE
2021/04/02 19:35:35  + price: 2242.9127272631004usd
2021/04/02 19:35:35 - found: 74f7643b-5c80-4dee-aa65-7e3a5c4ebd37
2021/04/02 19:35:35  + brand: Apple
2021/04/02 19:35:35  + name: Macbook Air
2021/04/02 19:35:35  + cpu cores: 5
2021/04/02 19:35:35  + cpu min ghz: 3.4362480622854794
2021/04/02 19:35:35  + ram: 10 GIGABYTE
2021/04/02 19:35:35  + price: 2761.2363802318578usd
2021/04/02 19:35:35 - found: 0b3b3be6-1341-4a8b-820e-b48cc63e7a4f
2021/04/02 19:35:35  + brand: Apple
2021/04/02 19:35:35  + name: Macbook Air
2021/04/02 19:35:35  + cpu cores: 8
2021/04/02 19:35:35  + cpu min ghz: 2.6970044221301923
2021/04/02 19:35:35  + ram: 13 GIGABYTE
2021/04/02 19:35:35  + price: 2086.775426243169usd
```

Let's run it one more time to create 10 more laptops. This time we found 7 
matched laptops. Let's look at the server logs. Here it receives a 
search-laptop request and sent 3 laptops to the client. Perfect! 

```shell
2021/04/02 19:35:35 receive a search-laptop request with filter: max_price_usd:3000  min_cpu_cores:4  min_cpu_ghz:2.5  min_ram:{value:8  unit:GIGABYTE}
2021/04/02 19:35:35 sent laptop with id: 724e7fe8-5a70-4b70-97c1-8435fbfa01f9
2021/04/02 19:35:35 sent laptop with id: 74f7643b-5c80-4dee-aa65-7e3a5c4ebd37
2021/04/02 19:35:35 sent laptop with id: 0b3b3be6-1341-4a8b-820e-b48cc63e7a4f
```

Now let's simulate the timeout case. In the `Search` function of the laptop
store, let's say it runs very slowly, each iteration takes 1 second. We write
a log here so that we can track the progress. 

```go
func (store *InMemoryLaptopStore) Search(
    filter *pb.Filter,
    found func(laptop *pb.Laptop) error,
) error {
    // ...
    for _, laptop := range store.data {
        // heavy processing
		time.Sleep(time.Second)
		log.Print("checking laptop id: ", laptop.GetId())
        if isQualified(filter, laptop) {
            other, err := deepCopy(laptop)
            if err != nil {
                return err
            }
    
            err = found(other)
            if err != nil {
                return err
            }
        }
    }
    
    return nil
}
```

OK, let's restart the server. Then run the client. After a few seconds, it 
gets a deadline exceeded error. Let's run it one more time so that the server
will have more records to scan through. OK, the deadline error is thrown. 
However, on the server side, you can see that it's still checking more 
records. This is useless because the client has already cancelled the request.
So let's fix it. In this `for` loop before checking if a laptop is qualified
or not, we will have to check the context status. To do that, we have to add 
the request context as a parameter of the `Search` function.

```go
type LaptopStore interface {
    // ...
    // Search searches for laptops with filter, returns one by one via the found function
    Search(ctx context.Context, filter *pb.Filter, found func(laptop *pb.Laptop) error) error
}

// ...

func (store *InMemoryLaptopStore) Search(
    ctx context.Context,
    filter *pb.Filter,
    found func(laptop *pb.Laptop) error,
) error {
    // ...
}
```

Alright, now we check if the context error is `Cancelled` or 
`DeadlineExceeded`. If it is, we write a log and return an error saying the
context is cancelled.

```go
func (store *InMemoryLaptopStore) Search(
    filter *pb.Filter,
    found func(laptop *pb.Laptop) error,
) error {
    // ...
    for _, laptop := range store.data {
    // heavy processing
    time.Sleep(time.Second)
    log.Print("checking laptop id: ", laptop.GetId())
    
    if ctx.Err() == context.Canceled || ctx.Err() == context.DeadlineExceeded {
        log.Print("context is cancelled")
        return errors.New("context is cancelled")
    }

    // ...
}
```

In the laptop server, we have to get the context from the stream and pass it 
in the `Search` function. And that's it.

```go
func (server *LaptopServer) SearchLaptop(
    req *pb.SearchLaptopRequest,
    stream pb.LaptopService_SearchLaptopServer,
) error {
    // ...
    
    err := server.Store.Search(
        stream.Context(),
        filter,
        func (laptop *pb.Laptop) error {
            // ...
        }
    )
    
    // ...
}
```

Now let's restart the server. Then run the client. One more time. OK. This 
time on the server side we can see a log "context is cancelled".

```shell
2021/04/02 21:06:54 context is cancelled
```

And it stops processing other records. So it works as expected.

## Write unit test
Now I will show you know to write unit tests for the server-streaming RPC. 
There are 2 ways to do this. The first way is to mock this stream interface, 
provide an implementation of the `Send` function to catch the responses, but 
we also need to add some empty implementation of the functions in the 
`grpc.ServerStreaming` interface. There are about 6 of them, so it's too much. 
Thus, I will use the 2nd way, which is to use the client to call the RPC on 
the test server. I will copy this setup block

```go
    t.Parallel()
    
    laptopServer, serverAddress := startTestLaptopServer(t)
    laptopClient := newTestLaptopClient(t, serverAddress)
```

and paste it into `TestClientSearchLaptop` to make a new unit test.

```go
func TestClientSearchLaptop(t *testing.T) {
	t.Parallel()

	laptopServer, serverAddress := startTestLaptopServer(t)
	laptopClient := newTestLaptopClient(t, serverAddress)
}
```

First I will create a search filter. Let's say max price is 2000, the min cpu
cores is 4, the min cpu frequency is 2.2, and the min RAM is 8 gigabytes.

```go
func TestClientSearchLaptop(t *testing.T) {
    // ...
    
    filter := &pb.Filter{
        MaxPriceUsd: 2000,
        MinCpuCores: 4,
        MinCpuGhz:   2.2,
        MinRam:      &pb.Memory{Value: 8, Unit: pb.Memory_GIGABYTE},
    }

    // ...
}
```

Next I will create a new in-memory laptop store to insert some laptops for 
searching. This expectedIDs map will contain all laptop IDs that we expect to 
be found by the server. OK, now we will use a `for` loop to create 6 laptops.
The first case will be an unmatched laptop with a too high price. The second
case is also unmatched because it has only 2 cores. The third case doesn't 
match because the min frequency is too low. The forth case doesn't match since
it has only 4 gigs RAM. The fifth case is gonna be a matched laptop. The price
is 1999. It has 4 cores, with minimum frequency of 2.5, max frequency of 4.5
and 16 gigabytes of RAM. We add the ID of this laptop to the expectedIDs map.
The last case is also matched. So I will just duplicate the previous one, and
change the configurations a bit.

```go
func TestClientSearchLaptop(t *testing.T) {
    // ...
    
    store := service.NewInMemoryLaptopStore()
    expectedIDs := make(map[string]bool)
    
    for i := 0; i < 6; i++ {
        laptop := sample.NewLaptop()
    
        switch i {
        case 0:
            laptop.PriceUsd = 2500
        case 1:
            laptop.Cpu.NumberCores = 2
        case 2:
            laptop.Cpu.MinGhz = 2.0
        case 3:
            laptop.Ram = &pb.Memory{Value: 4096, Unit: pb.Memory_GIGABYTE}
        case 4:
            laptop.PriceUsd = 1999
            laptop.Cpu.NumberCores = 4
            laptop.Cpu.MinGhz = 2.5
            laptop.Cpu.MinGhz = 4.5
            laptop.Ram = &pb.Memory{Value: 16, Unit: pb.Memory_GIGABYTE}
            expectedIDs[laptop.Id] = true
        case 5:
            laptop.PriceUsd = 2000
            laptop.Cpu.NumberCores = 6
            laptop.Cpu.MinGhz = 2.8
            laptop.Cpu.MinGhz = 5.0
            laptop.Ram = &pb.Memory{Value: 64, Unit: pb.Memory_GIGABYTE}
            expectedIDs[laptop.Id] = true
        }
    }

    // ...
}
```

Alright, now we call `Store.Save` to save the laptop to the store. Require 
there's no error. 

```go
func TestClientSearchLaptop(t *testing.T) {
    // ...
    
    for i := 0; i < 6; i++ {
    	// ...

        err := store.Save(laptop)
        require.NoError(t, err)
    }

    // ...
}
```

Next we have to add this store to the test laptop server. I will add one more 
`store` parameter to this function.

```go
func TestClientSearchLaptop(t *testing.T) {
    // ...

    laptopServer, serverAddress := startTestLaptopServer(t, store)
    laptopClient := newTestLaptopClient(t, serverAddress)
}

func startTestLaptopServer(t *testing.T, store service.LaptopStore) (*service.LaptopServer, string) {
    laptopServer := service.NewLaptopServer(store)
    // ...
}	
```

Then update the create-laptop test to pass in a new in-memory laptop store.

```go
func TestClientCreateLaptop(t *testing.T) {
    // ...
    	
    laptopServer, serverAddress := startTestLaptopServer(t, service.NewInMemoryLaptopStore())
    
    // ...
}
```

OK, back to our search-laptop test. Here we're not gonna use the `laptopServer`
object, so I will remove it.

```go
func TestClientSearchLaptop(t *testing.T) {
    // ...

    _, serverAddress := startTestLaptopServer(t, store)
    laptopClient := newTestLaptopClient(t, serverAddress)
}
```

Now we create a new `SearchLaptopRequest` with the filter. Then we call 
`laptopClient.SearchLaptop` with the created request. We require no errors to
be returned. Next I will use this variable to keep track of the number of 
laptops found. Then use a `for` loop to receive multiple responses. If we got
an end-of-file error, then break. Else we check that there's no error. And the
laptop ID should be in the expectedIDs map. Then we increase the number of 
laptops found. Finally, we require that number to equal to the size of the 
expectedIDs.

```go
func TestClientSearchLaptop(t *testing.T) {
    // ...

    req := &pb.SearchLaptopRequest{Filter: filter}
    stream, err := laptopClient.SearchLaptop(context.Background(), req)
    require.NoError(t, err)
    
    found := 0
    for {
        res, err := stream.Recv()
        if err == io.EOF {
            break
        }
    
        require.NoError(t, err)
        require.Contains(t, expectedIDs, res.GetLaptop().GetId())
        
        found += 1
    }
    
    require.Equal(t, len(expectedIDs), found)
}
```

OK, now let's run this unit test. It passed.

```shell
--- PASS: TestClientSearchLaptop (6.00s)
```

But it took 6 seconds to run. That's because we forget to comment out the 
`time.Sleep` in the search function. So let's comment it out.

```go
func (store *InMemoryLaptopStore) Search(
    filter *pb.Filter,
    found func(laptop *pb.Laptop) error,
) error {
    // ...
    for _, laptop := range store.data {
        // heavy processing
        // time.Sleep(time.Second)
        // log.Print("checking laptop id: ", laptop.GetId())
        
    	// ...
    }
    
    return nil
}
```

And re-run the test. It's much faster now. Let's run the whole package test.

```shell
go test -cover
```

All passed and the coverage is 75.8%. Not bad!

That's all for today's lecture. We have learned how to implement and test a
server-streaming RPC in Go. In the next lecture we will learn how to do that
in Java. Thanks for your time and I will see you later.
