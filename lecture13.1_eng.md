# Implement bidirectional-streaming gRPC - Golang
Hi everyone. Today we're gonna learn how to implement the last type of gRPC,
that is bidirectional-streaming, or bidi-streaming. This streaming allows 
client and server to send multiple requests and multiple responses to each 
other in parallel. We will write an API for client to rate a stream of laptops
with score from 1 to 10 and server will respond with a stream of average 
scores for each of the laptops. Alright, let's start!

## Define bidi-streaming gRPC protobuf
The first thing we need to do is to define a new bidi-streaming RPC in the 
`laptop_service.proto` file. We define the `RateLaptopRequest` with 2 fields: 
the laptop ID and the score.

```protobuf
message RateLaptopRequest {
  string laptop_id = 1;
  double score = 2;
}

message RateLaptopResponse {
  string laptop_id = 1;
  uint32 rated_count = 2;
  double average_score = 3;
}
```

Then the `RateLaptopResponse` with 3 fields: the laptop ID, the number of time
this laptop was rated, and the average rated score. Now we define the 
`RateLaptop` RPC with input is a stream of `RateLaptopRequest` and output is a
stream of `RateLaptopResponse`.

```protobuf
service LaptopService {
  rpc CreateLaptop(CreateLaptopRequest) returns (CreateLaptopResponse) {};
  rpc SearchLaptop(SearchLaptopRequest) returns (stream SearchLaptopResponse) {};
  rpc UploadImage(stream UploadImageRequest) returns (UploadImageResponse) {};
  rpc RateLaptop(stream RateLaptopRequest) returns (stream RateLaptopResponse) {};
}
```

Alright, now let's run 

```shell
make gen
```

to generate the codes. After the codes are generated, comment out line 
`pb.UnimplementedLaptopServiceServer` in `service/laptop_server.go` file and 
we can see an error inside the server `main.go` file. This is because the 
`LaptopServiceServer` interface now requires 1 more method: `RateLaptop`. We 
can find the signature of this method inside the `laptop_service_grpc.pb.go` 
file. So let's copy it and paste in the `laptop_server.go` file. Just return 
`nil` for now and update this `LaptopService_RateLaptopServer` input parameter 
to `pb.LaptopService_RateLaptopServer`. OK, uncomment line you commented 
before, and the error is gone.

`service/laptop_server.go`
```go
// RateLaptop is a bidirectional-streaming RPC that allows client to rate a
// stream of laptops with a score, and returns a stream of average score for
// each of them
func (server *LaptopServer) RateLaptop(stream pb.LaptopService_RateLaptopServer) error {
    return nil
}
```

We will come back to implement this method later.

## Implement the rating store
Now we need to create a new rating store to save the laptop ratings. I will 
define a `RatingStore` interface. It has 1 function `Add` that takes a laptop 
ID and a score as input and returns the updated laptop rating or an error. The 
rating will consist of 2 fields: one is count, which is the number of times the
laptop is rated, and the other is the sum of all rated scores.

```go
package service

// RatingStore is an interface to store laptop ratings
type RatingStore interface {
    // Add adds a new laptop score to the store and returns its rating
    Add(laptopID string, score float64) (*Rating, error)
}

// Rating contains the rating information of a laptop
type Rating struct {
    Count uint32
    Sum float64
}
```

Then we will write an in-memory rating store that implements the interface.
Similar to the in-memory laptop store, here we will need a mutex to handle
concurrent access, and we have a `rating` map with key is the laptop ID, and 
value is the rating object. Then we define a function to create a new in-memory
rating store. In this function we just need to initialize the `rating` map.

```go
// InMemoryRatingStore stores laptop ratings in memory
type InMemoryRatingStore struct {
    mutex sync.RWMutex
    rating map[string]*Rating
}
// NewInMemoryRatingStore returns a new InMemoryRatingStore
func NewInMemoryRatingStore() *InMemoryRatingStore {
    return &InMemoryRatingStore{
        rating: make(map[string]*Rating),
    }
}
```

OK, now let's implement the `Add` function. As we're going to change the 
internal data of the store, we have to acquire a lock here. Then we get the 
rating of the laptop ID from the map. If the rating is not found, then we just
create a new object with `Сount` is 1 and `Sum` is the input score. Else we 
increase the rating `Count` by 1 and add the score to the `Sum`. Finally, we 
put the updated rating back to the map and return it to the caller. Then we're 
done with the score.

```go
// Add adds a new laptop score to the store and returns its rating
func (store *InMemoryRatingStore) Add(laptopID string, score float64) (*Rating, error) {
    store.mutex.Lock()
    defer store.mutex.Unlock()
    
    rating := store.rating[laptopID]
    if rating == nil {
        rating = &Rating{
            Count: 1,
            Sum: score,
        }
    } else {
        rating.Count++
        rating.Sum += score
    }
    
    store.rating[laptopID] = rating
    return rating, nil
}
```

Now let's go back to implement the server.

## Implement the bidi-streaming gRPC server
We add a new rating store to the `LaptopServer` struct and also add it to 
`NewLaptopServer` function.

`service/laptop_server.go`
```go
// LaptopServer is the server that provides laptop services
type LaptopServer struct {
    // ...
    ratingStore RatingStore
    // ...
}

// NewLaptopServer returns a new LaptopServer
func NewLaptopServer(laptopStore LaptopStore, imageStore ImageStore, ratingStore RatingStore) *LaptopServer {
    return &LaptopServer{
        laptopStore: laptopStore,
        imageStore:  imageStore,
        ratingStore: ratingStore,
    }
}
```

Because of this change, some errors will show up. So let's fix them. In the 
`laptop_client_test.go` file, we add a new rating store parameter to the 
`startTestLaptopServer` function and send it in the call to create a new 
laptop server.

```go
func startTestLaptopServer(t *testing.T, laptopStore service.LaptopStore, imageStore service.ImageStore, ratingStore service.RatingStore) string {
    laptopServer := service.NewLaptopServer(laptopStore, imageStore, ratingStore)
    
    // ...
}
```

Because this rating store is not needed in all current unit tests we can 
simplify set it to `nil` here.

```go
func TestClientUploadImage(t *testing.T) {
    // ...
	
    serverAddress := startTestLaptopServer(t, laptopStore, imageStore, nil)
    
    // ...
}
```

here 

```go
func TestClientSearchLaptop(t *testing.T) {
    // ...
	
    serverAddress := startTestLaptopServer(t, laptopStore, nil, nil)
    
    // ...
}
```

and here.

```go
func TestClientCreateLaptop(t *testing.T) {
    // ...
    
    serverAddress := startTestLaptopServer(t, laptopStore, nil, nil)
    
    // ...
}
```

In the `laptop_server_test.go` file, we can also pass `nil` to this function.
And all errors in the test files are gone.

```go
func TestServerCreateLaptop(t *testing.T) {
    // ...
    
    server := service.NewLaptopServer(tc.store, nil, nil)
    
    // ...
}
```

Now there's only 1 error left in the server `main.go` file. Here we have to 
create a new in-memory rating store, and pass it into the create new laptop
server function. Then we're done.

```go
func main() {
    // ...
    ratingStore := service.NewInMemoryRatingStore()
    laptopServer := service.NewLaptopServer(laptopStore, imageStore, ratingStore)
    
    // ...
}
```

Now let's come back to implement the `RateLaptop` function. Since we will 
receive multiple requests from the stream we must use a `for` loop here. 
Similar to what we did on the client-streaming RPC, before doing anything, 
let's check the context error to see if it's already canceled or deadline 
exceeded or not. Then we call `stream.Recv()` to get a request from the stream.
If error is end of file, then there's no more data, we simply break the loop.
Else if error is not `nil` we log it and return the error with status code
`Unknown` to the client. Otherwise, we can get the laptop ID and the score from
the request. Let's write a log here saying that we have received a request 
with this laptop ID and score.

```go
func (server *LaptopServer) RateLaptop(stream pb.LaptopService_RateLaptopServer) error {
    for {
        err := contextError(stream.Context())
        if err != nil {
            return err
        }
    
        req, err := stream.Recv()
        if err == io.EOF {
            log.Print("no more data")
            break
        }
        if err != nil {
            return logError(status.Errorf(codes.Unknown, "cannot receive stream request: %v", err))
        }
    
        laptopID := req.GetLaptopId()
        score := req.GetScore()
    
        log.Printf("received a rate-laptop request: id = %s, score = %.2f", laptopID, score)
    }
    
    return nil
}
```

Now we should check if this laptop ID really exists or not by using the 
`laptopStore.Find()` function. If an error occurs, we return it with the 
status code `Internal`. If the laptop is not found, we return the status code
`NotFound` to the client.

```go
func (server *LaptopServer) RateLaptop(stream pb.LaptopService_RateLaptopServer) error {
    for {
        // ...
    
        found, err := server.laptopStore.Find(laptopID)
        if err != nil {
            return logError(status.Errorf(codes.Internal, "cannot find laptop: %v", err))
        }
        if found == nil {
            return logError(status.Errorf(codes.NotFound, "laptopID %s is not found", laptopID))
        }
    }
    
    return nil
}
```

If everything goes well, we call `ratingStore.Add` to add the new laptop score
to the store and get back the updated rating object. If there's an error, we
return `Internal` status code. Else, we create a `RateLaptopResponse` with
`laptopID` is the input laptop ID. Rated count taken from the rating object, 
and average score is computed using the `Sum` and `Count` of the rating. We 
call `stream.Send()` to send the response to the client. If error is not `nil`,
we log it and return status code `Unknown`. And that's it! We're done with the
server.

```go
func (server *LaptopServer) RateLaptop(stream pb.LaptopService_RateLaptopServer) error {
	for {
        // ...
		
        rating, err := server.ratingStore.Add(laptopID, score)
        if err != nil {
            return logError(status.Errorf(codes.Internal, "cannot and rating to the scores: %v", err))
        }
        
        res := &pb.RateLaptopResponse{
            LaptopId: laptopID,
            RatedCount: rating.Count,
            AverageScore: rating.Sum / float64(rating.Count),
        }
        
        err = stream.Send(res)
        if err != nil {
            return logError(status.Errorf(codes.Unknown, "cannot send stream response: %v", err))
        }
    }

    return nil
}
```

## Implement the bidi-streaming gRPC client
Now before moving to the client, I will add a new function to generate a 
random laptop score in the `sample` package. To be simple, let's say it's gonna
be a random integer between 1 and 10.

`sample/generator.go`
```go
// RandomLaptopScore returns a random laptop score
func RandomLaptopScore() float64 {
    return float64(randomInt(1, 10))
}
```

Alright, now let's implement the client. First we define a `rateLaptop` 
function with 3 input parameters: a laptop client, a list of laptop IDs and 
their corresponding scores. In this function, we create a new context with 
timeout after 5 seconds. Then we call `laptopClient.RateLaptop()` with the 
created context. The output is a stream or an error. If error is not `nil`, we
just return it.

```go
func rateLaptop(laptopClient pb.LaptopServiceClient, laptopIDs []string, scores []float64) error {
    ctx, cancel := context.WithTimeout(context.Background(), 5 * time.Second)
    defer cancel()
    
    stream, err := laptopClient.RateLaptop(ctx)
    if err != nil {
        return fmt.Errorf("cannot rate laptop: %v", err)
    }
}
```

Else, we will have to make a channel to wait for the responses from the server.
Note that the requests and responses are sent concurrently, so here we have to 
start a new go routine to receive the responses. And the `waitResponse` channel
will receive an error when it occurs, or a `nil` if all responses are received
successfully. In the go routine, we use a `for` loop, and call `stream.Recv()`
to get a response from server. If error is EOF, it means there's no more 
responses, so we send `nil` to the `waitResponse` channel and return. Else if 
error is not `nil`, we send the error to the `waitResponse` channel and return
as well. If no errors occur, we just write a simple log.

```go
func rateLaptop(laptopClient pb.LaptopServiceClient, laptopIDs []string, scores []float64) error {
    // ...

    waitResponse := make(chan error)
    // go routine to receive responses
    go func() {
        for {
            res, err := stream.Recv()
            if err == io.EOF {
                log.Print("no more responses")
                waitResponse <- nil
                return
            }
            if err != nil {
                waitResponse <- fmt.Errorf("cannot receive stream response: %v", err)
                return
            }
            
            log.Print("received response: ", res)
        }
    }()
}
```

OK, after this go routine, we can start sending requests to the server. Let's
iterate through the list of the laptops and create a new request for each of 
them with the input laptop ID, and the corresponding input scores. Then call 
`stream.Send()` to send the request to the server. If we get an error, just 
return it. Note that here we call `stream.RecvMsg()` to get the real error, 
just like what we did in the previous lecture with client-streaming RPC. If no
error occurs, we write a log saying the request is sent.

```go
func rateLaptop(laptopClient pb.LaptopServiceClient, laptopIDs []string, scores []float64) error {
    // ...
	
    // send requests
    for i, laptopID := range laptopIDs {
        req := &pb.RateLaptopRequest{
            LaptopId: laptopID,
            Score:    scores[i],
        }
    
        err := stream.Send(req)
        if err != nil {
            return fmt.Errorf("cannot send stream request: %v - %v", err, stream.RecvMsg(nil))
        }
        
        log.Print("sent request: ", req)
    }
}
```

Now one important thing that we must do after sending all request. Is to call
`stream.CloseSend()` to tell the server that we won't send any more data, and 
finally read from the `waitResponse` channel, and return the received error. 
The `rateLaptop()` function is completed.

```go
func rateLaptop(laptopClient pb.LaptopServiceClient, laptopIDs []string, scores []float64) error {
    // ...
	
    err = stream.CloseSend()
    if err != nil {
        return fmt.Errorf("cannot close send: %v", err)
    }
    
    err = <-waitResponse
    return err
}
```

Now we will write a `testRatelaptop()` function to call it. Let's say we want
to rate 3 laptops. So we declare a slice to keep the laptop IDs, and use a 
`for` loop to generate a random laptop, save its ID to the slice and call 
`createLaptop()` function to create it on the server.

```go
func testRateLaptop(laptopClient pb.LaptopServiceClient) {
    n := 3
    laptopIDs := make([]string, n)
    
    for i := 0; i < n; i++ {
        laptop := sample.NewLaptop()
        laptopIDs[i] = laptop.GetId()
        createLaptop(laptopClient, laptop)
    }
}
```

Then we also make a slice to keep the scores. Now I want to rate these 3 
laptops in multiple rounds, so I will use a `for` loop here and ask if we want
to do another round of rating or not. If the answer is no, we break the loop 
else we generate a new set of scores for the laptops and call `rateLaptop` 
function to rate them with the generated scores. If an error occurs, we write
a `Fatal` log. And that's all.

```go
func testRateLaptop(laptopClient pb.LaptopServiceClient) {
    // ...
    
    scores := make([]float64, n)
    for {
        fmt.Print("rate laptop (y/n)?")
        var answer string
        fmt.Scan(&answer)
    
        if strings.ToLower(answer) != "y" {
            break
        }
    
        for i := 0; i < n; i++ {
            scores[i] = sample.RandomLaptopScore()
        }
    
        err := rateLaptop(laptopClient, laptopIDs, scores)
        if err != nil {
            log.Fatal(err)
        }
    }
}
```

Now in the `main` function just call `testRateLaptop` and we're all set.

`cmd/client/main.go`
```go
func main() {
    // ...
    testRateLaptop(laptopClient)
}
```

## Run the bidi-streaming gRPC server and client
Let's run the server then run the client. 3 laptops are created.

```shell
2021/04/13 19:48:58 created laptop with id: 7087f361-bd46-4d08-87b2-66e92dad6b0d
2021/04/13 19:48:58 created laptop with id: c209924a-ac73-48f1-88e7-91f7f62bd0b6
2021/04/13 19:48:58 created laptop with id: f2ceae64-1938-4a16-b7de-654cbbaa7fb8
rate laptop (y/n)?
```

Rate laptop? Yes.

```shell
2021/04/13 20:09:54 sent request: laptop_id:"03572d1f-c64e-4e71-ba6f-7985bfa9dd9c" score:10
2021/04/13 20:09:54 sent request: laptop_id:"ad198e92-ef54-48f7-a36d-3a2c1435c820" score:7
2021/04/13 20:09:54 sent request: laptop_id:"788a4976-172f-43ee-b50d-13e2563fbff7" score:10
2021/04/13 20:09:54 received response: laptop_id:"03572d1f-c64e-4e71-ba6f-7985bfa9dd9c" rated_count:1 average_score:10
2021/04/13 20:09:54 received response: laptop_id:"ad198e92-ef54-48f7-a36d-3a2c1435c820" rated_count:1 average_score:7
2021/04/13 20:09:54 received response: laptop_id:"788a4976-172f-43ee-b50d-13e2563fbff7" rated_count:1 average_score:10
2021/04/13 20:09:54 no more responses
rate laptop (y/n)?
```

As you can see, we sent 3 requests with scores of 10, 7 and 10 and received 3 
responses with rated count of 1 and average scores of 10, 7 and 10. Good!
Now let's do another rating round.

```shell
2021/04/13 20:16:38 sent request: laptop_id:"03572d1f-c64e-4e71-ba6f-7985bfa9dd9c" score:4
2021/04/13 20:16:38 sent request: laptop_id:"ad198e92-ef54-48f7-a36d-3a2c1435c820" score:6
2021/04/13 20:16:38 sent request: laptop_id:"788a4976-172f-43ee-b50d-13e2563fbff7" score:2
2021/04/13 20:16:38 received response: laptop_id:"03572d1f-c64e-4e71-ba6f-7985bfa9dd9c" rated_count:2 average_score:7
2021/04/13 20:16:38 received response: laptop_id:"ad198e92-ef54-48f7-a36d-3a2c1435c820" rated_count:2 average_score:6.5
2021/04/13 20:16:38 received response: laptop_id:"788a4976-172f-43ee-b50d-13e2563fbff7" rated_count:2 average_score:6
2021/04/13 20:16:38 no more responses
```

This time the scores we sent are 4, 6 and 2. And the responses has rated count
of 2 and the average scores have been updated to 7, 6.5 and 6 which are all
correct. You can do the math. We can try another round.

```shell
2021/04/13 20:19:15 sent request: laptop_id:"03572d1f-c64e-4e71-ba6f-7985bfa9dd9c" score:2
2021/04/13 20:19:15 sent request: laptop_id:"ad198e92-ef54-48f7-a36d-3a2c1435c820" score:5
2021/04/13 20:19:15 sent request: laptop_id:"788a4976-172f-43ee-b50d-13e2563fbff7" score:8
2021/04/13 20:19:15 received response: laptop_id:"03572d1f-c64e-4e71-ba6f-7985bfa9dd9c" rated_count:3 average_score:5.333333333333333
2021/04/13 20:19:15 received response: laptop_id:"ad198e92-ef54-48f7-a36d-3a2c1435c820" rated_count:3 average_score:6
2021/04/13 20:19:15 received response: laptop_id:"788a4976-172f-43ee-b50d-13e2563fbff7" rated_count:3 average_score:6.666666666666667
2021/04/13 20:19:15 no more responses
```

And it's working as expected. Awesome!

## Test bidi-streaming gRPC
Now I'm gonna show you how to test this bidirectional streaming RPC. Let's go
to `laptop_client_test.go` file. The test setup will be very similar to that
of the upload image. So I just copy and paste it. Change the test name to
`TestClientRateLaptop`, remove `testImageFolder`, change `imageStore` to 
`ratingStore` and pass it into the `startTestLaptopServer` function instead of
the `imageStore`.

```go
func TestClientRateLaptop(t *testing.T) {
    t.Parallel()
    
    laptopStore := service.NewInMemoryLaptopStore()
    ratingStore := service.NewInMemoryRatingStore()
    
    laptop := sample.NewLaptop()
    err := laptopStore.Save(laptop)
    require.NoError(t, err)
    
    serverAddress := startTestLaptopServer(t, laptopStore, nil, ratingStore)
    laptopClient := newTestLaptopClient(t, serverAddress)
}
```

Alright, now we call `laptopClient.RateLaptop()` with a background context to
get the stream. Require no error. For simplicity, we just rate 1 single 
laptop, but we will rate it 3 times with a score of 8, 7.5 and 10 
respectively. So the expected average score after each time will be 8, 7.75 and
8.5. We define `n` as the number of rated times and use a `for` loop to send
multiple requests. Each time we will create a new request with the same laptop
ID and a new score. We call `stream.Send()` to send the request to the server 
and require no errors to be returned.

```go
func TestClientRateLaptop(t *testing.T) {
    // ...

    stream, err := laptopClient.RateLaptop(context.Background())
    require.NoError(t, err)
    
    scores := []float64{8, 7.5, 10}
    averages := []float64{8, 7.75, 8.5}
    
    n := len(scores)
    for i := 0; i < n; i++ {
        req := &pb.RateLaptopRequest{
            LaptopId: laptop.GetId(),
            Score: scores[i],
        }
        
        err := stream.Send(req)
        require.NoError(t, err)
    }
}
```

To be simple, I will not create a separate go routine to receive the responses.
Here I will use a `for` loop to receive them, and use an `idx` variable to 
count how many responses we have received. Inside the loop, we call 
`stream.Recv()` to receive a new response. If error is EOF, then it's the end 
of the stream, we just require that the number of responses we received must 
be equal to `n`, which is the number of requests we sent, and we return 
immediately. Else there should be no error. The response laptop ID should be 
equal to the input laptop ID. The rated count should be equal to `idx + 1`, 
and the average score should be equal to the expected value.

```go
func TestClientRateLaptop(t *testing.T) {
    // ...
    
    for idx := 0; ; idx++ {
        res, err := stream.Recv()
        if err == io.EOF {
            require.Equal(t, n, idx)
            return
        }
    
        require.NoError(t, err)
        require.Equal(t, laptop.GetId(), res.GetLaptopId())
        require.Equal(t, uint32(idx+1), res.GetRatedCount())
        require.Equal(t, averages[idx], res.GetAverageScore())
    }
}
```

Now let's run the test. It's timeout after 30 seconds. That's because I forget 
to close send the stream. So let's call it here.

```go
func TestClientRateLaptop(t *testing.T) {
    // ...
	
	err = stream.CloseSend()
	require.NoError(t, err)
    
    for idx := 0; ; idx++ {
        // ...
    }
}
```

Require no error. And rerun the test. Now it passed. Let's run the whole
package tests. All passed. Excellent! And that wraps up today's lecture about
implementing bidirectional streaming RPC in Go. In the next lecture, we will
learn how to do that in Java. Thank you for reading. Happy coding and I will
see you in the next lecture.