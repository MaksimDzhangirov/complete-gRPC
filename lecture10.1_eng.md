# Implement unary gRPC API - Golang
There are 4 types of gRPC. In this lecture, we will learn how to implement 
the simplest one: unary gRPC. We will use Golang in this lecture, and Java in
the next one. Here's the plan. First we will define a proto service that 
contains a unary gRPC to create a laptop. Then we will implement the server to
handle the request and save the laptop to an in-memory storage. We will 
implement the client to call the RPC, and write unit tests for the interaction 
between client and server. And finally we will learn how to handle errors, 
return suitable gRPC status codes, and how to work with gRPC deadline.
Alright, let's start! I will open the `pcbook` project with Visual Studio Code.

## Define a proto service and an unary RPC
First step, we will create a new `laptop_service.proto` file. The syntax, 
package and options will be the same as other files, so I just copy and paste 
them here. We're gonna need the `Laptop` message, so let's import it. Next 
define the `CreateLaptopRequest`. It contains only 1 field: the laptop. Then 
the `CreateLaptopResponse`. It also has only 1 field: the ID of the laptop. Now
the important part. We will define a `LaptopService` with the keyword 
"service". Then inside it, we define a unary RPC. Start with the keyword 
"rpc". Then the name of the RPC is "CreateLaptop". It takes the 
`CreateLaptopRequest` as input and returns the `CreateLaptopResponse`. End it 
with a pair of curly brackets and a semicolon. And that's it! Pretty easy and 
straight-forward!

```protobuf
syntax = "proto3";

package techschool_pcbook;

option go_package = ".;pb";
option java_package = "com.github.techschool.pcbook.pb";
option java_multiple_files = true;

import "laptop_message.proto";

message CreateLaptopRequest {
  Laptop laptop = 1;
}

message CreateLaptopResponse {
  string id = 1;
}

service LaptopService {
  rpc CreateLaptop(CreateLaptopRequest) returns (CreateLaptopResponse) {};
}
```

## Generate codes for the unary RPC
Now let's open the terminal and run `make gen` to generate the codes. The 
`laptop_service.pb.go` is generated. Let's take a closer look! This is the 
`CreateLaptopRequest` struct.

```go
type CreateLaptopRequest struct {
    state         protoimpl.MessageState
    sizeCache     protoimpl.SizeCache
    unknownFields protoimpl.UnknownFields
    
    Laptop *Laptop `protobuf:"bytes,1,opt,name=laptop,proto3" json:"laptop,omitempty"`
}
func (x *CreateLaptopRequest) GetLaptop() *Laptop {
    if x != nil {
        return x.Laptop
    }
    return nil
}
```

It has a function to get the input laptop. This is the `CreateLaptopResponse`
struct.

```go
type CreateLaptopResponse struct {
    state         protoimpl.MessageState
    sizeCache     protoimpl.SizeCache
    unknownFields protoimpl.UnknownFields
    
    Id string `protobuf:"bytes,1,opt,name=id,proto3" json:"id,omitempty"`
}
func (x *CreateLaptopResponse) GetId() string {
    if x != nil {
        return x.Id
    }
    return ""
}
```

It has a function to get the output ID of the laptop. This is the 
`LaptopServiceClient` interface in `laptop_service_grpc.pb.go`. It has a 
function `CreateLaptop`. Just like how we defined it in the proto file.

```go
type LaptopServiceClient interface {
    CreateLaptop(ctx context.Context, in *CreateLaptopRequest, opts ...grpc.CallOption) (*CreateLaptopResponse, error)
}
```

Why is it an interface? Because it will allow us to implement our own custom
client. For example, a mock client that can be used for unit testing.
This `laptopServiceClient` struct with a small letter "l" is the real 
implementation of the interface. You can see its implementation right here.

```go
type laptopServiceClient struct {
    cc grpc.ClientConnInterface
}
func (c *laptopServiceClient) CreateLaptop(ctx context.Context, in *CreateLaptopRequest, opts ...grpc.CallOption) (*CreateLaptopResponse, error) {
    out := new(CreateLaptopResponse)
    err := c.cc.Invoke(ctx, "/techschool_pcbook.LaptopService/CreateLaptop", in, out, opts...)
    if err != nil {
        return nil, err
    }
    return out, nil
}
```

Next this is the `LaptopServiceServer`. It is also an interface with no
implementation. Basically, we must write our own implementation of the server.

```go
type LaptopServiceServer interface {
    CreateLaptop(context.Context, *CreateLaptopRequest) (*CreateLaptopResponse, error)
    mustEmbedUnimplementedLaptopServiceServer()
}
```

But it must have the `CreateLaptop` function as defined in this interface.
Here's the function to register the laptop service on a specific gRPC server
so that it can receive and handle requests from client.

```go
func RegisterLaptopServiceServer(s grpc.ServiceRegistrar, srv LaptopServiceServer) {
    s.RegisterService(&LaptopService_ServiceDesc, srv)
}
```

## Implement the server's unary RPC handler
OK now let's implement the `LaptopServiceServer`! I will create a new "service"
folder, and create a `laptop_server.go` inside it. I will declare a 
`LaptopServer` struct. Write some comments for it. Then a `NewLaptopServer`
function to return a new instance.

```go
package service

// LaptopServer is the server that provides laptop services
type LaptopServer struct {
	pb.UnimplementedLaptopServiceServer
}

// NewLaptopServer returns a new LaptopServer
func NewLaptopServer() *LaptopServer {
    return &LaptopServer{}
}
```

Now we need to implement the `CreateLaptop` function which is required by the
`LaptopServiceServer` interface. It takes a context and a `CreateLaptopRequest`
as input and returns a `CreateLaptopResponse` with an error. Write some
comments here.

```go
// CreateLaptop is a unary RPC to create a new laptop
func (server *LaptopServer) CreateLaptop(ctx context.Context, req *pb.CreateLaptopRequest) (*pb.CreateLaptopResponse, error) {

}
```

This line looks too long, so I'm gonna break it down to make it easier to read.
First we call `GetLaptop` function to get the laptop object from request. Then
we write a simple log saying that we have received a create-laptop request with
this ID.

```go
func (server *LaptopServer) CreateLaptop(
    ctx context.Context,
    req *pb.CreateLaptopRequest,
) (*pb.CreateLaptopResponse, error) {
    laptop := req.GetLaptop()
    log.Printf("receive a create-laptop request with id: %s", laptop.Id)
}
```

If the client has already generated the laptop ID, we must check if it is a 
valid UUID or not. To do that we will use Google UUID package. Search for 
`golang google uuid` on the browser, open this github page 
`https://github.com/google/uuid`, copy this `go get` command

```shell
go get github.com/google/uuid
```

and run it in the terminal to install the package. OK, now we can use 
`uuid.Parse()` function to parse the laptop ID. If it returns an error then it
means the provided ID is invalid. We should return a `nil` response to the 
client together with an error status code. For that, we can use the `status` 
and `codes` subpackages of the `grpc` package. In this case, we will use the 
`InvalidArgument` code. Because the laptop ID is provided by the client. We 
send the code with a message and the original error. If client hasn't sent 
the laptop ID, we will generate it on the server with this command: 
`uuid.NewRandom()`. If an error occurs, we return it with the `codes.Internal`,
meaning an internal server error. Else, if everything goes well, we just set 
the `laptop.Id` to the generated random UUID. There's a type mismatch, so we 
have to convert the UUID to string here.

```go
func (server *LaptopServer) CreateLaptop(
	ctx context.Context,
	req *pb.CreateLaptopRequest,
) (*pb.CreateLaptopResponse, error) {
	// ...

    if len(laptop.Id) > 0 {
        // check if it's a valid UUID
        _, err := uuid.Parse(laptop.Id)
        if err != nil {
            return nil, status.Errorf(codes.InvalidArgument, "laptop ID is not a valid UUID: %v", err)
        }
    } else {
        id, err := uuid.NewRandom()
        if err != nil {
            return nil, status.Errorf(codes.Internal, "cannot generate a new laptop ID: %v", err)
        }
        laptop.Id = id.String()
    }
}
```

## Implement in-memory storage to save laptops
Alright. Normally after this, we should save the laptop to the database. But
this is a course about gRPC, so I just want to focus on it. Therefore, to make
it simple, I will just use an in-memory storage. It will also be very useful
for unit testing later. Let's add a `LaptopStore` inside the `LaptopServer`
struct.

```go
type LaptopServer struct {
    Store LaptopStore
    pb.UnimplementedLaptopServiceServer
}
```

Then create a new `laptop_store.go` file inside the `service` folder. As we 
might have different types of store I will define `LaptopStore` as an interface.
It has a `Save` function to save a laptop to the store. Then we will write an
`InMemoryLaptopStore` to implement that interface. Later if we want to save
laptop to the database, we can always implement another `DBLaptopStore` to 
do so. OK, back to our `InMemoryLaptopStore`. We will use a map to store the 
data, where the key is the laptop ID, and the value is the laptop object. 
There will be multiple concurrent requests to save laptops, so we will need 
a read-write mutex to handle the concurrency.

```go
// LaptopStore is an interface to store laptop
type LaptopStore interface {
    // Save saves the laptop to the store
    Save(laptop *pb.Laptop) error
}

// InMemoryLaptopStore stores laptop in memory
type InMemoryLaptopStore struct {
    mutex sync.RWMutex
    data  map[string]*pb.Laptop
}
```

Now let's declare a function to return a new `InMemoryLaptopStore` and 
initialize the data map inside it.

```go
// NewInMemoryLaptopStore returns a new InMemoryLaptopStore
func NewInMemoryLaptopStore() *InMemoryLaptopStore {
    return &InMemoryLaptopStore{
        data: make(map[string]*pb.Laptop),
    }
}
```

Then implement the `Save` laptop function as required by the interface. First
we need to acquire a write lock before adding new objects. Remember to defer
the unlock command so that we won't forget. Next check if the laptop ID already
exists in the map or not. If it does, just return an error to the caller.

```go
// Save saves the laptop to the store
func (store *InMemoryLaptopStore) Save(laptop *pb.Laptop) error {
    store.mutex.Lock()
    defer store.mutex.Unlock()
    
    if store.data[laptop.Id] != nil {
        return ErrAlreadyExists
    }
}
```

I will define an exported error variable, so that it can be used from outside
this `service` package.

```go
// ErrAlreadyExists is returned when a record with the same ID already exists in the store
var ErrAlreadyExists = errors.New("record already exists")
```

Now if the laptop doesn't exist, we can go ahead to save it to the store. To
be safe, I will do a deep-copy of the laptop object. Let's open the browser
and search for `golang copier`. Go to `https://github.com/jinzhu/copier`, copy
this package path and run `go get` in the terminal to install it.

```shell
go get github.com/jinzhu/copier
```

Now create a new laptop object, call it "other", then call `copier.Copy` to
deep-copy the "laptop" object to "other" object. If an error occurs, just 
wrap the error and return it. Otherwise, save the "other" object to the store
data map.

```go
func (store *InMemoryLaptopStore) Save(laptop *pb.Laptop) error {
    // ...
    
    // deep copy
    other := &pb.Laptop{}
    err := copier.Copy(other, laptop)
    if err != nil {
        return fmt.Errorf("cannot copy laptop data: %w", err)
    }
    
    store.data[other.Id] = other
    return nil
}
```

Alright, let's go back to our laptop server. We can call `server.Store.Save` 
to save the input laptop to the store. If there's an error, return 
`codes.Internal` with the error to the client. We can make it clearer to the 
client to handle by checking if the error is because the record already exists
or not. Simply call `errors.Is()` function. If it's `true`, just return 
`AlreadyExists` instead of `Internal`.

```go
func (server *LaptopServer) CreateLaptop(
    ctx context.Context,
    req *pb.CreateLaptopRequest,
) (*pb.CreateLaptopResponse, error) {
    // ...
    
    // save the laptop to store
    err := server.Store.Save(laptop)
    if err != nil {
        code := codes.Internal
        if errors.Is(err, ErrAlreadyExists) {
            code = codes.AlreadyExists
        }
        return nil, status.Errorf(code, "cannot save laptop to the store: %v", err)
    }
}
```

Finally, if no errors occur, we can write a log here saying that the laptop 
is successfully saved with this ID. We create a new response object with the 
laptop ID then return it to the caller. And that's it for the server.

```go
func (server *LaptopServer) CreateLaptop(
    ctx context.Context,
    req *pb.CreateLaptopRequest,
) (*pb.CreateLaptopResponse, error) {
    // ...
    
    log.Printf("saved laptop with id: %s", laptop.Id)
    
    res := &pb.CreateLaptopResponse{
        Id: laptop.Id,
    }
    return res, nil
}
```

## Test the unary RPC handler
Now I'm gonna show you how to test it. Let's create a `laptop_server_test.go`
file in `service` folder. The package name is `service_test`. Create a 
function `TestServerCreateLaptop()`, make it run in parallel.

```go
package service_test

import "testing"

func TestServerCreateLaptop(t *testing.T) {
    t.Parallel()

}
```

I want to test many different cases, so let's use table-driven tests. First I 
will declare all test cases. A test case will have a name, an input laptop
object, a laptop store, and an expected status code. The 1st case is a
successful call with laptop ID generated by the client. So the laptop will
be a `sample.NewLaptop()`, store is just a new `InMemoryLaptopStore`, and the
expected code is `OK`. The 2nd case is also a successful call, but with no 
laptop ID. I expect the server to generate a random ID for us. Let's create
a `laptopNoID` object by generating a sample laptop and set its ID to empty
string. The 3rd case is a failed call because of an invalid UUID. Let's
create a `laptopInvalidID` object, by generating a sample laptop, and set
its ID to "invalid-uuid". This case, we expect the status code to be 
`InvalidArgument`.

```go
func TestServerCreateLaptop(t *testing.T) {
    // ...
    laptopNoID := sample.NewLaptop()
    laptopNoID.Id = ""
    
    laptopInvalidID := sample.NewLaptop()
    laptopInvalidID.Id = "invalid-uuid"
}
```

The last case is a failed call because of duplicate ID. We will have to
create a laptop and save it to the store first, then call `CreateLaptop`
function with that laptop's ID. We expect to see an `AlreadyExists` status code
in this case. OK let's create a `laptopDuplicateID` as a sample laptop. Make
a new `InMemoryLaptopStore`. Save the laptop to the store and check that there's
no error returned.

```go
func TestServerCreateLaptop(t *testing.T) {
    // ...
	
    laptopDuplicateID := sample.NewLaptop()
    storeDuplicateID := service.NewInMemoryLaptopStore()
    err := storeDuplicateID.Save(laptopDuplicateID)
    require.Nil(t, err)
}
```

Alright, the test cases are ready.

```go
func TestServerCreateLaptop(t *testing.T) {
    // ...
	
    testCases := []struct{
        name string
        laptop *pb.Laptop
        store service.LaptopStore
        code codes.Code
    } {
        {
            name: "success_with_id",
            laptop: sample.NewLaptop(),
            store: service.NewInMemoryLaptopStore(),
            code: codes.OK,
        },
        {
            name: "success_no_id",
            laptop: laptopNoID,
            store: service.NewInMemoryLaptopStore(),
            code: codes.OK,
        },
        {
            name: "failure_invalid_id",
            laptop: laptopInvalidID,
            store: service.NewInMemoryLaptopStore(),
            code: codes.InvalidArgument,
        },
        {
            name: "failure_duplicate_id",
            laptop: laptopDuplicateID,
            store: storeDuplicateID,
            code: codes.AlreadyExists,
        },
    }
}
```

Now we iterate though them with this simple `for` loop. Save the current
test case to a local variable. This is very important to avoid concurrency
issues, because we want to create multiple parallel subtests. To create
a subtest, we call `t.Run()` and use `tc.name` for the name of the subtest.
Call `t.Parallel()` to make it run in parallel with other tests. Then we 
create a new `CreateLaptopRequest` with the input is `tc.loptop`. We create
a new `LaptopServer` with the in memory store. But wait looks like I forgot to 
take in the laptop store in this `NewLaptopServer` function. So let's add it.

```go
// NewLaptopServer returns a new LaptopServer
func NewLaptopServer(store LaptopStore) *LaptopServer {
    return &LaptopServer{
        Store: store,
    }
}
```

Now we pass the `tc.store` to this function to create a new `LaptopServer`. 
Then just call `server.CreateLaptop()` function with a background context
and the request object. The successful case or when `tc.code` is `OK`. In this
case, we should check there's no error. The response should be not `nil`. The
returned ID should be not empty. And if the input laptop already has ID,
then the returned ID should equal to it. The failure case, when `tc.code` is
not `OK`. We check there should be an error. And the response should be `nil`.
Now we want to check the status code. Let's call `status.FromError` to get the
status object. Check that `ok` should be `true`. And `st.Code()` should equal 
to `tc.code`. Then it's done.

```go
func TestServerCreateLaptop(t *testing.T) {
	// ...
    
	for i := range testCases {
        tc := testCases[i]
    
        t.Run(tc.name, func (t *testing.T) {
            t.Parallel()
            
            req := &pb.CreateLaptopRequest{
                Laptop: tc.laptop,
            }
            
            server := service.NewLaptopServer(tc.store)
            res, err := server.CreateLaptop(context.Background(), req)
            if tc.code == codes.OK {
                require.NoError(t, err)
                require.NotNil(t, res)
                require.NotEmpty(t, res.Id)
                if len(tc.laptop.Id) > 0 {
                    require.Equal(t, tc.laptop.Id, res.Id)
                }
            } else {
                require.Error(t, err)
                require.Nil(t, res)
                st, ok := status.FromError(err)
                require.True(t, ok)
                require.Equal(t, tc.code, st.Code())
            }
        })
    }
}
```

Let's run the tests. Yee! It passed. Also run the whole package test. And 
check the code coverage.

```shell
go test -cover
```

93.5% is a very high coverage. However, the tests that we've written didn't
use any kind of network call yet. They're basically just a direct call on 
server side.

## Test the unary RPC with the real connection
Now I will show you how to test the RPC request from the client side. 
Let's create `laptop_client_test.go` file in `service` folder. The package 
name is still `service_test`. But the function name is now 
`TestClientCreateLaptop`. I will make it run in parallel as well.

```go
package service_test

import "testing"

func TestClientCreateLaptop(t *testing.T) {
    t.Parallel()
	
}
```

First we need to start the gRPC server, I'm gonna write a separate function 
for this. It will take the `testing.T` as an argument. And return the 
`LaptopServer` object together with the network address string of the server.
In this function, we create a new laptop server with an in-memory laptop store.

```go
func startTestLaptopServer(t *testing.T) (*service.LaptopServer, string) {
    laptopServer := service.NewLaptopServer(service.NewInMemoryLaptopStore())
}
```

Create the gRPC server by calling `grpc.NewServer()` function. Then register 
the laptop service server on that gRPC server. We create a new listener that
will listen to tcp connection. The number 0 here means that we want it to be 
assigned any random available port. Now we just call `grpc.Server.Serve` to
start listening to the request. This is a blocking call, so we have to run it
in a separate go-routine. Since we want to send requests to this server after
that. OK, now just return the laptop server and the address string of the 
listener.

```go
func startTestLaptopServer(t *testing.T) (*service.LaptopServer, string) {
    // ...
    
    grpcServer := grpc.NewServer()
    pb.RegisterLaptopServiceServer(grpcServer, laptopServer)
    
    listener, err := net.Listen("tcp", ":0") // random available port
    require.NoError(t, err)
    
    go grpcServer.Serve(listener)

    return laptopServer, listener.Addr().String()
}
```

In the test, we call this function to get the server and its address. Then we
will create another function to return a new laptop-client for test.

```go
func TestClientCreateLaptop(t *testing.T) {
    // ...
    
    laptopServer, serverAddress := startTestLaptopServer(t)
    laptopClient := newTestLaptopClient(t, serverAddress)
}
```

This function will take the `testing.T` object, and the server address as its
agruments and return a `pb.LaptopServiceClient`. First we will dial the server
address with `grpc.Dial()`. Since this is just for testing, we will use an
insecure connection. Check that there is no error and return a new laptop
service client with the created connection.

```go
func newTestLaptopClient(t *testing.T, serverAddress string) pb.LaptopServiceClient {
    conn, err := grpc.Dial(serverAddress, grpc.WithInsecure())
    require.NoError(t, err)
    return pb.NewLaptopServiceClient(conn)
}
```

Next we create a new sample laptop. Save its ID to a variable to compare later.
Create a new request object with the laptop. This time we use the `laptopClient`
object to call `CreateLaptop()` function. Then we check that no error is 
returned and response should be not `nil`. The returned ID should match the
expected ID we saved before.

```go
func TestClientCreateLaptop(t *testing.T) {
    // ...
    
    laptop := sample.NewLaptop()
    expectedID := laptop.Id
    req := &pb.CreateLaptopRequest{
        Laptop: laptop,
    }
    
    res, err := laptopClient.CreateLaptop(context.Background(), req)
    require.NoError(t, err)
    require.NotNil(t, res)
    require.Equal(t, expectedID, res.Id)

}
```

Now we want to make sure that the laptop is really stored on the server. To
do that we need to add 1 more function to laptop store. It's the `Find()` 
function to search for a laptop by its ID. It takes a string ID as an input
and returns a laptop object with an error.

```go
type LaptopStore interface {
    // Save saves the laptop to the store
    Save(laptop *pb.Laptop) error
    // Find finds a laptop by ID
    Find(id string) (*pb.Laptop, error)
}
```

In this function we first call `mutex.RLock()` to acquire a read lock. 
Remember to release it with a `defer` command. Now we can find the laptop
from the `store.data` map by its id. If it's not found just return `nil`. Else,
we should deep-copy it to another object by using `copier.Copy()` as we did
before. Returns an error if it occurs. Otherwise, if everything is good, just 
return the copied object.

```go
func (store *InMemoryLaptopStore) Find(id string) (*pb.Laptop, error) {
    store.mutex.RLock()
    defer store.mutex.RUnlock()
    
    laptop := store.data[id]
    if laptop == nil {
        return nil, nil
    }
    
    // deep copy
    other := &pb.Laptop{}
    err := copier.Copy(other, laptop)
    if err != nil {
        return nil, fmt.Errorf("cannot copy laptop data: %w", err)
    }
    
    return other, nil
}
```

Now go back to our client test. We call `laptopServer.Store.Find()` to find
laptop by ID. Check there's no error. And the laptop should be found, so it
should be not `nil`. 

```go
func TestClientCreateLaptop(t *testing.T) {
    // ...
	
    // check that the laptop is saved to the store
    other, err := laptopServer.Store.Find(res.Id)
    require.NoError(t, err)
    require.NotNil(t, other)
}
```

Finally, we want to check that the saved laptop is the same as the one we
sent.

```go
func TestClientCreateLaptop(t *testing.T) {
    // ...
    
    // check that saved laptop is the same as the one we send
    requireSameLaptop(t, laptop, other)
}
```

I will write a separate function to check this. It will have 3 inputs: the 
`testing.T` object and 2 laptop objects. Now if we just use `require.Equal` 
function for there 2 objects, and run the test. It will fail.

```go
func requireSameLaptop(t *testing.T, laptop1 *pb.Laptop, laptop2 *pb.Laptop) {
    require.Equal(t, laptop1, laptop2)
}
```

It's because in the `Laptop` struct there are some special fields that are 
used internally by gRPC to serialize the objects. Therefore, to compare 2 
laptops properly, we must ignore those special fields. One easy way is just 
serializing the objects to JSON and compare the 2 output JSON strings as I'm 
doing here.

```go
func requireSameLaptop(t *testing.T, laptop1 *pb.Laptop, laptop2 *pb.Laptop) {
    json1, err := serializer.ProtobufToJSON(laptop1)
    require.NoError(t, err)
    
    json2, err := serializer.ProtobufToJSON(laptop2)
    require.NoError(t, err)
    
    require.Equal(t, json1, json2)
}
```

Now if we run the test again, it passed! Excellent!

## Write the main server and client
Next we will implement the real server and client. First I will delete this 
unused `main.go` file. Then create a new `"cmd"` folder. In this folder, let's 
create 1 folder for the server and 1 folder for the client. The server will 
have its own `main.go` file. Let's paste a simple hello world program here 
for now.

```go
package main

import "fmt"

func main() {
    fmt.Println("Hello world")
}
```

Similar for the client.

Now I will open Makefile and change this `"run"` command into 2 commands: the
`"server"` command to run the server main file and the `"client"` command to
run the client main file.

```makefile
server:
    go run cmd/server/main.go
client:
    go run cmd/client/main.go
```

Let's change the content of the hello message a bit to make them different.
Let's say "hello world from server" and "hello world from client".

`cmd/server/main.go`
```go
package main

import "fmt"

func main() {
    fmt.Println("Hello world from server")
}
```

`cmd/server/client.go`
```go
package main

import "fmt"

func main() {
    fmt.Println("Hello world from client")
}
```

Alright, let's try it! Run `make server`, then `make client` in terminal. 
Perfect!

Now let's implement the real server. We need a port for the server, so I use
the `flag.Int` to get it from command line arguments. Parse the flag and print
a simple log.

```go
package main

import (
    "flag"
    "log"
)

func main() {
    port := flag.Int("port", 0, "the server port")
    flag.Parse()
    log.Printf("start server on port %d", *port)
}
```

Similar to what we wrote in the unit test, we create a new laptop server 
object with an in-memory store. Then create a new gRPC server. Register the 
laptop server with the gRPC server. Then create an address string with the port
we get before. We will listen for tcp connections on this server address. Call 
`grpcServer.Serve()` start the server. If any error occurs, just write a fatal
log and exit. That's the server code.

```go
func main() {
    // ...
    
    laptopServer := service.NewLaptopServer(service.NewInMemoryLaptopStore())
    grpcServer := grpc.NewServer()
    pb.RegisterLaptopServiceServer(grpcServer, laptopServer)
    
    address := fmt.Sprintf("0.0.0.0:%d", *port)
    listener, err := net.Listen("tcp", address)
    if err != nil {
        log.Fatal("cannot start server: ", err)
    }
    
    err = grpcServer.Serve(listener)
    if err != nil {
        log.Fatal("cannot start server: ", err)
    }
}
```

Now we have to update the make file to send the `port` argument to the server
program. I will use port 8080.

```makefile
server:
    go run cmd/server/main.go -port 8080
```

Let's test it in terminal:

```shell
make server
```

The server is started on port 8080.

Now the client. First we will get the server address from the command line 
arguments. And write a simple log saying we're dialing this server address.
We call `grpc.Dial()` function with the input address, and just create an 
insecure connection for now. If an error occurs, write a fatal log and exit. 
Else create a new laptop client object with connection. Then generate a new 
laptop, make a new request object and just call `laptopClient.Createlaptop()` 
function with the request and a background context. Similar to what we wrote 
in the unit test, if an error occurs, we convert it into a status object. So
that we can check the returned status code. If code is `Already Exists` then 
it's not a big deal. Just write a normal log. Else write a fatal log. In any
error cases we have to return here. If everything is good, we simply write a
log saying the laptop is created with this ID. OK, let's run it in the 
terminal.

```go
func main() {
    serverAddress := flag.String("address", "", "the server address")
    flag.Parse()
    log.Printf("dial server %s", *serverAddress)
    
    conn, err := grpc.Dial(*serverAddress, grpc.WithInsecure())
    if err != nil {
        log.Fatal("cannot dial server: ", err)
    }
    
    laptopClient := pb.NewLaptopServiceClient(conn)
    
    laptop := sample.NewLaptop()
    req := &pb.CreateLaptopRequest{
        Laptop: laptop,
    }
    
    res, err := laptopClient.CreateLaptop(context.Background(), req)
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
```

The server is already running. Now open a new tab and run `make client`. Oops, 
it failed to dial server because the address is missing. I forgot to update 
the `Makefile`. So let's open the file and add the address argument to the 
`client` command.

```makefile
client:
	go run cmd/client/main.go -address 0.0.0.0:8080
```

Now let's go back to the terminal and run `make client` again. This time the
laptop is successfully created! On the server we see 2 logs:

```shell
2021/03/31 19:34:47 receive a create-laptop request with id: a6a4e0ba-d26b-4a9e-a71e-dab31f6e2d70
2021/03/31 19:34:47 saved laptop with id: a6a4e0ba-d26b-4a9e-a71e-dab31f6e2d70
```

1 saying it has received a request with this ID, and the other saying the 
laptop is saved with the same ID. Now what if the client doesn't send ID? In
the client main file I will set `laptop.Id` to an empty string.

`cmd/client/main.go`
```go
func main() {
    // ...	
    
    laptop := sample.NewLaptop()
    laptop.Id = ""
    req := &pb.CreateLaptopRequest{
        Laptop: laptop,
    }
    
    // ...
}
```

And re-run the client. A laptop is still created with some ID. On the server
side, we also see 2 logs.

```shell
2021/03/31 19:39:41 receive a create-laptop request with id: 
2021/03/31 19:39:41 saved laptop with id: e332aea5-5ad0-4096-b859-025fb8012d41
```

But this time, the ID in the 1st log is empty. It means the server has 
generated a new ID for the laptop. Let's try to send an ID that already exists
to see what happends. I will copy this ID `a6a4e0ba-d26b-4a9e-a71e-dab31f6e2d70`
from the first server log and paste it in the client code.

`cmd/client/main.go`
```go
func main() {
    // ...	
    
    laptop := sample.NewLaptop()
    laptop.Id = "a6a4e0ba-d26b-4a9e-a71e-dab31f6e2d70"
    req := &pb.CreateLaptopRequest{
        Laptop: laptop,
    }
    
    // ...
}
```

Re-run the client. This time it says that the laptop already exists.

```shell
2021/03/31 19:44:57 laptop already exists
```

On the server side, there's only 1 log for receiving the request.

```shell
2021/03/31 19:44:57 receive a create-laptop request with id: a6a4e0ba-d26b-4a9e-a71e-dab31f6e2d70
```

Let's try one more test with an invalid UUID. I will change this `laptop.Id` in 
client to `"invalid"` and run the client again.

`cmd/client/main.go`
```go
func main() {
    // ...	
    
    laptop := sample.NewLaptop()
    laptop.Id = "invalid"
    req := &pb.CreateLaptopRequest{
        Laptop: laptop,
    }
    
    // ...
}
```

It's a fatal error message with the status code `InvalidArgument`. Excellent!

```shell
2021/03/31 19:49:00 cannot create laptop: rpc error: code = InvalidArgument desc = laptop ID is not a valid UUID: invalid UUID length: 7
exit status 1
```

Next I'm gonna show you how to set timeout for the request. In Go, we will
use context to do this. On the client side, instead of using 
`context.Background()` I will call `context.WithTimeout()` and pass in a 
background context together with the duration of time, let's say 5 seconds. 
The function returns a context and a `cancel` object. The context is used in 
the `CreateLaptop` function, and we defer the `cancel()` call before exiting
the `main` function.

`cmd/client/main.go`
```go
func main() {
    // ...
	
    // set timeout
    ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
    defer cancel()
    
    res, err := laptopClient.CreateLaptop(ctx, req)
    // ...
}
```
`service/laptop_server.go`
```go
func (server *LaptopServer) CreateLaptop(
    ctx context.Context,
    req *pb.CreateLaptopRequest,
) (*pb.CreateLaptopResponse, error) {
    // ...

    // some heavy processing
    time.Sleep(6 * time.Second)

    // save the laptop to store
}
```

On the server side, let's pretend that we have to do some heavy processing 
here, and it takes about 6 seconds. Now lets restart the server and run the 
client. After 5 seconds, on the client side, we get an error with 
`DeadlineExceeded` code.

```shell
2021/03/31 20:04:41 cannot create laptop: rpc error: code = DeadlineExceeded desc = context deadline exceeded
exit status 1
```

However, on the server side, the laptop is still getting created and saved.

```shell
2021/03/31 20:04:42 saved laptop with id: b6acadec-3f62-4712-b1ba-3bd450f5f2b3
```

It might not be the behaviour that we want. If the request is canceled before
the call to save laptop to the store we want the server to stop saving the
laptop as well. To do that we have to check the context error on the server.
If there's a `DeadlineExceeded` error, we will print a log here and return a
status error code `DeadlineExceeded` to the client.

`service/laptop_server.go`
```go
func (server *LaptopServer) CreateLaptop(
    ctx context.Context,
    req *pb.CreateLaptopRequest,
) (*pb.CreateLaptopResponse, error) {
    // ...

    // some heavy processing
    time.Sleep(6 * time.Second)

    if ctx.Err() == context.DeadlineExceeded {
        log.Print("deadline is exceeded")
        return nil, status.Error(codes.DeadlineExceeded, "deadline is exceeded")
    }
    // save the laptop to store
}
```

OK, let's restart the server and re-run the client. The client still gets this
`DeadlineExceeded` error.

```shell
2021/03/31 20:17:25 cannot create laptop: rpc error: code = DeadlineExceeded desc = context deadline exceeded
exit status 1
```

But this time the server also has a log saying deadline is exceeded.

```shell
2021/03/31 20:17:26 deadline is exceeded
```

And it doesn't save the laptop to the store anymore. Now I wonder what happens
if we cancel the request by interrupting the client. Let's run the client and
after 1 second, press `Ctrl+C` to stop it. On the server side we still see that
laptop is saved. That is also not what we want, because the client already
canceled the request. To fix this we will go back to the server and add one 
more check before saving the laptop. If the context error is `context.Canceled`,
then we just log it and return an error with status code `Canceled` to the
client.

`service/laptop_server.go`
```go
func (server *LaptopServer) CreateLaptop(
    ctx context.Context,
    req *pb.CreateLaptopRequest,
) (*pb.CreateLaptopResponse, error) {
    // ...
    
    // some heavy processing
    time.Sleep(6 * time.Second)

    if ctx.Err() == context.Canceled {
        log.Print("request is canceled")
        return nil, status.Error(codes.Canceled, "deadline is canceled")
    }
    
    if ctx.Err() == context.DeadlineExceeded {
        log.Print("deadline is exceeded")
        return nil, status.Error(codes.DeadlineExceeded, "deadline is exceeded")
    }
    // save the laptop to store
}
```

Now if we restart the server and re-run the client, interrupt it with `Ctrl+C`
then this time on the server we will see a log saying that the context is 
cancelled.

```shell
2021/03/31 20:26:30 request is canceled
```

And no laptop is saved to the store. Exactly what we wanted! And that's the end
of this coding session. We have learnt a lot about how to implement and test
a unary gRPC request with Go. In the next lecture, we will learn how to do the 
same thing with Java. Until then, happy coding! And I will see you later.