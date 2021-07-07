# Implement bidirectional-streaming gRPC - Java
Hi everyone! In this lecture, we're going to implement bidirectional streaming
RPC with Java. We will implement the same rate-laptop API that we have build 
with Golang in the previous lecture. It allows client to rate multiple laptops
with a score, and receive back the average rated score for each of them. We
will also learn how to write unit tests for this new streaming RPC.

## Define bidi-streaming gRPC protobuf
OK, let's start! As we have already defined the rate laptop RPC in the last 
golang lecture I will just go to the `pcbook` golang project and copy the 
content of the `laptop_service.proto` file. Here we have the new 
`RateLaptopRequest`. It contains 2 fields: the `laptopID` of type `string`, and
a `score` of type `double`.

```protobuf
message RateLaptopRequest {
  string laptop_id = 1;
  double score = 2;
}
```

Then we have the `RateLaptopResponse` which has 3 fields: the laptop ID, the
number of times that laptop is rated, and the average rated scores.

```protobuf
message RateLaptopResponse {
  string laptop_id = 1;
  uint32 rated_count = 2;
  double average_score = 3;
}
```

The `RateLaptop` RPC is a bidirectional streaming RPC, so it takes a stream of
requests, and returns a stream of responses. Pretty straight-forward, right?
Now let's click this build button to generate Java codes from this protobuf
definition. OK, the build is successful.

## Implement the rating store
Now let's start with the new `Rating` class. This class will contain the rating
metrics of a given laptop. So we have an integer count to store the number of 
times the laptop is rated and a double sum to store the sum of all rated 
scores. I will generate a constructor with these 2 fields and also generate 
2 getter functions for them.

```java
package com.gitlab.techschool.pcbook.service;

public class Rating {
    private int count;
    private double sum;

    public Rating(int count, double sum) {
        this.count = count;
        this.sum = sum;
    }
    
    public int getCount() {
        return count;
    }
    
    public double getSum() {
        return sum;
    }
}
```

Alright, now we write 1 more function to add 2 rating objects together. This
function will be useful later to update the laptop rating in the store. It's
very simple, we just return a new `Rating` object where both `count` and `sum`
are computed by adding the corresponding fields of the 2 input objects 
together.

```java
public class Rating {
    // ...
    
    public static Rating add(Rating r1, Rating r2) {
        return new Rating(r1.count + r2.count, r1.sum + r2.sum);
    }
}
```

Now let's define the new `RatingStore` interface. It will have only 1 
function: `Add`, that has 2 input parameters: the `laptopID`, and the `score`. 
And it returns the updated rating of the laptop.

```java
package com.gitlab.techschool.pcbook.service;

public interface RatingStore {
    Rating Add(String laptopID, double score);
}
```

Let's create an `InMemoryRatingStore` to implement this interface. Similar to
the `InMemoryLaptopStore`, we have a `ConcurrentMap` to store the rating data,
where the key is `laptopID`, and the value is its `Rating`. We initialize the
map inside this constructor. Now in the `Add` function, we have to update the 
laptop rating atomically because there might be many requests to rate the same
laptop at the same time. To do that, we use the `merge()` function of the 
`ConcurrentMap`. Basically, this function takes a `laptopID` key, a `Rating`
value to be used if the key is not associated with any value before, which 
should be `Rating(1, score)` in our case. And a remapping function to update
the value of an existing key. In our case, we want to add 1 to the rating 
count, and score to the rating sum. So we just use the `Rating:add` function 
here. This `merge` function is amazing and very convenient.

```java
package com.gitlab.techschool.pcbook.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryRatingStore implements RatingStore {
    private ConcurrentMap<String, Rating> data;

    public InMemoryRatingStore() {
        data = new ConcurrentHashMap<>();
    }

    @Override
    public Rating Add(String laptopID, double score) {
        return data.merge(laptopID, new Rating(1, score), Rating::add);
    }
}
```

But I want to make sure that it works correctly. So let's write a unit test, 
where we concurrently call `ratingStore.Add` from multiple threads. First we
create a new in-memory rating store. Then we create a list of callable tasks,
which will return a rating. We generate a random laptop ID and let's say all
tasks will call `ratingStore.Add` with the same score of 5. I'm gonna add 10
tasks to the list, so let's use a `for` loop here. And inside we call 
`task.add` with a lambda that has no input argument. And it will return 
`ratingStore.Add()` with the `laptopID` and `score`. We use a set integer here 
to keep track of what rated-count value has been recorded by the store after 
each call. Then we call `Executors.newWorkStealingPool()` to create a new 
worker pool, then call `.invokeAll()` and pass in the task list, turn it into
a stream and iterate through the elements using `forEach`. Each element will 
be a `future` object, so we call `future.get()` to get the output rating of 
each call. If we catch an exception here, just throw an 
`IllegalStateException()`. Else, we expect that the sum of rated score should 
be equal to the number of rated times multiplied by the score. And we record 
that this count has appeared in 1 of the function calls. What we expect to see 
is, there should be exactly `n` different rated count, and the value of them 
should be from 1 to `n` (or 10 in this case). OK, let's run this test.

```java
package com.gitlab.techschool.pcbook.service;

import org.junit.Test;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

public class InMemoryRatingStoreTest {

    @Test
    public void add() throws InterruptedException {
        InMemoryRatingStore ratingStore = new InMemoryRatingStore();

        List<Callable<Rating>> tasks = new LinkedList<>();
        String laptopID = UUID.randomUUID().toString();
        double score = 5;

        int n = 10;
        for (int i = 0; i < n; i++) {
            tasks.add(() -> ratingStore.Add(laptopID, score));
        }

        Set<Integer> ratedCount = new HashSet<>();
        Executors.newWorkStealingPool()
                .invokeAll(tasks)
                .stream()
                .forEach(future -> {
                    try {
                        Rating rating = future.get();
                        assertEquals(rating.getSum(), rating.getCount() * score, 1e-9);
                        ratedCount.add(rating.getCount());
                    } catch (Exception e) {
                        throw new IllegalStateException();
                    }
                });

        assertEquals(n, ratedCount.size());
        for (int cnt = 1; cnt <= n; cnt++) {
            assertTrue(ratedCount.contains(cnt));
        }
    }
}
```

It passed. So the `ratingStore.Add` function is working perfectly for 
concurrent calls.

## Implement the bidi-streaming gRPC server
Now let's implement the server. First we need to add a new rating store to the
`LaptopService` class and update this constructor with the new field.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...
    
    private RatingStore ratingStore;

    public LaptopService(LaptopStore laptopStore, ImageStore imageStore, RatingStore ratingStore) {
        // ...
        this.ratingStore = ratingStore;
    }
}
```

Because of this change we need to update the constructors of the `LaptopServer`
class as well.

```java
public class LaptopServer {
    // ...

    public LaptopServer(int port, LaptopStore laptopStore, ImageStore imageStore, RatingStore ratingStore) {
        this(ServerBuilder.forPort(port), port, laptopStore, imageStore, ratingStore);
    }

    public LaptopServer(ServerBuilder serverBuilder, int port, LaptopStore laptopStore, ImageStore imageStore, RatingStore ratingStore) {
        this.port = port;
        LaptopService laptopService = new LaptopService(laptopStore, imageStore, ratingStore);
        server = serverBuilder.addService(laptopService).build();
    }
}
```

And in the `main` function, we have to create a new `InMemoryRatingStore` 
object, and pass it in the constructor to create the server.

```java
public class LaptopServer {
    // ...
    public static void main(String[] args) throws InterruptedException, IOException {
        // ...
        InMemoryRatingStore ratingStore = new InMemoryRatingStore();

        LaptopServer server = new LaptopServer(8080, laptopStore, imageStore, ratingStore);
    }
}

public class LaptopServerTest {
    // ...

    @Before
    public void setUp() throws Exception {
        //...
        
        RatingStore ratingStore = new InMemoryRatingStore();
        server = new LaptopServer(serverBuilder, 0, laptopStore, imageStore, ratingStore);
        
        // ...
    }
}
```

Alright, let's go back to the `LaptopService` to implement the rate laptop
API. Similar to the upload image API that we wrote in the last lecture, we 
have to override the new `rateLaptop` function. It has a response observer as
input and must return an implementation of the 
`StreamObserver<RateLaptopRequest>` interface.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...
    
    @Override
    public StreamObserver<RateLaptopRequest> rateLaptop(StreamObserver<RateLaptopResponse> responseObserver) {
        return new StreamObserver<RateLaptopRequest>() {
            @Override
            public void onNext(RateLaptopRequest request) {
                
            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onCompleted() {

            }
        };
    }
}
```

Now in the `onNext` function we get the laptop ID and the score from the 
request. Write a log here saying that we have received a rate-laptop request. 
Then we find the laptop by ID from the store. If it is not found we call 
`responseObserver.onError`, with status code `NOT_FOUND` and return immediately. 
Else we call `ratingStore.Add()` to add a new rated score of the laptop to the 
rating, and get back the updated rating. Then we build a new response, with 
the input laptop ID, rated count taken from the `rating` object, and average 
score computed by dividing the rated sum to count. We call 
`responseObserver.onNext()` to send this response to the client.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...
    
    @Override
    public StreamObserver<RateLaptopRequest> rateLaptop(StreamObserver<RateLaptopResponse> responseObserver) {
        return new StreamObserver<RateLaptopRequest>() {
            @Override
            public void onNext(RateLaptopRequest request) {
                String laptopId = request.getLaptopId();
                double score = request.getScore();

                logger.info("received rate-laptop request: id = " + laptopId + ", score = " + score);

                Laptop found = laptopStore.Find(laptopId);
                if (found == null) {
                    responseObserver.onError(
                            Status.NOT_FOUND
                                    .withDescription("laptop ID doesn't exists")
                                    .asRuntimeException()
                    );
                    return;
                }

                Rating rating = ratingStore.Add(laptopId, score);
                RateLaptopResponse response = RateLaptopResponse.newBuilder()
                        .setLaptopId(laptopId)
                        .setRatedCount(rating.getCount())
                        .setAverageScore(rating.getSum() / rating.getCount())
                        .build();

                responseObserver.onNext(response);
            }
            
            // ...
        };
    }
}
```

In the `onError` function we simply write a warning log. And finally in the
`onCompleted` function we just call `responseObserver.onCompleted()`.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...
    
    @Override
    public StreamObserver<RateLaptopRequest> rateLaptop(StreamObserver<RateLaptopResponse> responseObserver) {
        return new StreamObserver<RateLaptopRequest>() {
            // ...

            @Override
            public void onError(Throwable t) {
                logger.warning(t.getMessage());
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
}
```

OK, let's try it. The laptop server has started. Now we will connect the 
golang client that we wrote in the previous lecture to this java server. 3 
laptops are created, enter "y" to rate them, as you can see,

```shell
INFO: received rate-laptop request: id = f8c2b615-97a3-4878-bc98-27ffd8e5476b, score = 8.0
INFO: received rate-laptop request: id = 734d852c-cfb5-4b8a-9e87-000565602f4c, score = 10.0
INFO: received rate-laptop request: id = 7473c005-be2b-4dcb-9445-040f7f964b27, score = 7.0
```

the laptops are rated with scores of 8, 10 and 7. Perfect! Let's enter "y" 
again to rate them 1 more time.

```shell
INFO: received rate-laptop request: id = f8c2b615-97a3-4878-bc98-27ffd8e5476b, score = 1.0
INFO: received rate-laptop request: id = 734d852c-cfb5-4b8a-9e87-000565602f4c, score = 4.0
INFO: received rate-laptop request: id = 7473c005-be2b-4dcb-9445-040f7f964b27, score = 6.0
```

Now they're rated with new scores of 1, 4 and 6. And the rating has been 
updated with rated count of 2 and average scores of 4.5, 7 and 6.5 which are
all correct.

```shell
2021/04/14 19:41:02 received response: laptop_id:"f8c2b615-97a3-4878-bc98-27ffd8e5476b" rated_count:2 average_score:4.5
2021/04/14 19:41:02 received response: laptop_id:"734d852c-cfb5-4b8a-9e87-000565602f4c" rated_count:2 average_score:7
2021/04/14 19:41:02 received response: laptop_id:"7473c005-be2b-4dcb-9445-040f7f964b27" rated_count:2 average_score:6.5
```

Awesome! So the Java server is working very well.

## Implement the bidi-streaming gRPC client
Let's implement the Java client. I will define a `rateLaptop` function that
has 2 input parameters: an array of laptop IDs and an array of scores. Similar
to the upload image client, we need a count down latch to wait for the 
response stream to finish. Then we call `asyncStub.withDeadlineAfter` 5 
seconds, `.rateLaptop()` and pass in an implementation of the 
`StreamObserver<RateLaptopResponse>` interface.

```java
public class LaptopClient {
    // ...
    
    public void rateLaptop(String[] laptopIDs, double[] scores) {
        CountDownLatch finishLatch = new CountDownLatch(1);
        StreamObserver<RateLaptopRequest> requestObserver = asyncStub.withDeadlineAfter(5, TimeUnit.SECONDS)
                .rateLaptop(new StreamObserver<RateLaptopResponse>() {
                    @Override
                    public void onNext(RateLaptopResponse value) {
                        
                    }

                    @Override
                    public void onError(Throwable t) {

                    }

                    @Override
                    public void onCompleted() {

                    }
                });
    }
}
```

In the `onNext` function, we only write a log saying that we have received a
response with this laptop ID, this rate count and this average score. In the
`onError` function we write a severe log with this error message `"rate laptop
failed: " + t.getMessage()` and call `finishLatch.countDown()`. In the 
`onCompleted` function we write an info log and also call 
`finishLatch.countDown()`.

```java
public class LaptopClient {
    // ...

    public void rateLaptop(String[] laptopIDs, double[] scores) {
        CountDownLatch finishLatch = new CountDownLatch(1);
        StreamObserver<RateLaptopRequest> requestObserver = asyncStub.withDeadlineAfter(5, TimeUnit.SECONDS)
                .rateLaptop(new StreamObserver<RateLaptopResponse>() {
                    @Override
                    public void onNext(RateLaptopResponse response) {
                        logger.info("laptop rated: id = " + response.getLaptopId() +
                                ", count = " + response.getRatedCount() +
                                ", average = " + response.getAverageScore());
                    }

                    @Override
                    public void onError(Throwable t) {
                        logger.log(Level.SEVERE, "rate laptop failed: " + t.getMessage());
                        finishLatch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        logger.info("rate laptop completed");
                        finishLatch.countDown();
                    }
                });
    }
}
```

Now we need to start sending a stream of requests. Let's iterate through the 
list of the laptop IDs, build a new request with the laptop ID and score. And 
call `requestObserver.onNext()` to send the `request` to the server. Then write 
an info log saying the request is sent. Let's surround this `for` loop with
try-catch. If an exception is caught, we write a severe log, call 
`requestObserver.onError()` and return. Finally, we call 
`requestObserver.onCompleted()` to tell the server that we won't send any more 
requests to the stream. And we call `finishLatch.await()` to wait for the 
response stream. OK, the `rateLaptop` function is ready.

```java
public class LaptopClient {
    // ...

    public void rateLaptop(String[] laptopIDs, double[] scores) throws InterruptedException {
        // ...

        int n = laptopIDs.length;
        try {
            for (int i = 0; i < n; i++) {
                RateLaptopRequest request = RateLaptopRequest.newBuilder()
                        .setLaptopId(laptopIDs[i])
                        .setScore(scores[i])
                        .build();
                requestObserver.onNext(request);
                logger.info("sent rate-laptop request: id = " + request.getLaptopId() + ", score = " + request.getScore());
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "unexpected error: " + e.getMessage());
            requestObserver.onError(e);
            return;
        }
        
        requestObserver.onCompleted();
        if (!finishLatch.await(1, TimeUnit.MINUTES)) {
            logger.warning("request cannot finish within 1 minute");
        }
    }
}
```

Now before calling this function, let's refactor the codes a bit. I will 
create a separate function to test the search laptop API. Copy and paste the 
codes in the `main` function here. Let's also add a function to test the create
laptop API. And a function to test the upload image API as well.

```java
public class LaptopClient {
    // ...
    
    public static void testCreateLaptop(LaptopClient client, Generator generator) {
        Laptop laptop = generator.NewLaptop();
        client.createLaptop(laptop);
    }

    public static void testSearchLaptop(LaptopClient client, Generator generator) {
        for (int i = 0; i < 10; i++) {
            Laptop laptop = generator.NewLaptop();
            client.createLaptop(laptop);
        }

        Memory minRam = Memory.newBuilder()
                .setValue(8)
                .setUnit(Memory.Unit.GIGABYTE)
                .build();

        Filter filter = Filter.newBuilder()
                .setMaxPriceUsd(3000)
                .setMinCpuCores(4)
                .setMinCpuGhz(2.5)
                .setMinRam(minRam)
                .build();

        client.searchLaptop(filter);
    }

    public static void testUploadImage(LaptopClient client, Generator generator) throws InterruptedException {
        // Test upload laptop image
        Laptop laptop = generator.NewLaptop();
        client.createLaptop(laptop);
        client.uploadImage(laptop.getId(), "tmp/laptop.jpg");
    }
    
    // ...
}
```

Alright, now let's implement a new function to test the rate laptop API and 
call it from the `main` function. Let's say we want to rate 3 laptops multiple
times. So here I declare an array to store the laptop IDs. Use a `for` loop to
generate random laptops, save the ID to the array, and call `createLaptop` API
to create it on the server. After that, we need a scanner to get input from
standard input. Use a `while` loop here and inside ask if the user wants to
do another round of rating or not. Scan the next line, if the answer is no, 
we break the loop.

```java
public class LaptopClient {
    // ...
    
    public static void testRateLaptop(LaptopClient client, Generator generator) {
        int n = 3;
        String[] laptopIDs = new String[n];

        for (int i = 0; i < n; i++) {
            Laptop laptop = generator.NewLaptop();
            laptopIDs[i] = laptop.getId();
            client.createLaptop(laptop);
        }

        Scanner scanner = new Scanner(System.in);
        while (true) {
            logger.info("rate laptop (y/n)?");
            String answer = scanner.nextLine();
            if (answer.toLowerCase().trim().equals("n")) {
                break;
            }
        }
    }
    
    // ...
}
```

Else, we will generate a new array of scores.

```java
public class LaptopClient {
    // ...

    public static void testRateLaptop(LaptopClient client, Generator generator) {
        // ...
        
        while (true) {
            // ...
            
            double[] scores = new double[n];
            for (int i = 0; i < n; i++) {
                
            }
        }
    }
    
    // ...
}
```

I will add a new function to the `Generator` class to simply returns a random
laptop score, which is an integer between 1 and 10.

```java
public class Generator {
    // ...
    
    public double NewLaptopScore() {
        return randomInt(1, 10);
    }
    
    // ...    
}
```

Then call that function from here. Finally, we call `client.RateLaptop` with
the laptopIDs and scores array. And we're done.

```java
public class LaptopClient {
    // ...

    public static void testRateLaptop(LaptopClient client, Generator generator) throws InterruptedException {
        // ...
        
        while (true) {
            // ...
                        
            for (int i = 0; i < n; i++) {
                scores[i] = generator.NewLaptopScore();
            }
            client.rateLaptop(laptopIDs, scores);
        }
    }
    
    // ...
}
```

## Run the bidi-streaming gRPC server and client
Let's run the server, then run the client. 3 laptops are created. Enter "y" to
rate them. Looks good! The requests are sent and the responses are received.

```shell
INFO: sent rate-laptop request: id = 01eab4b0-c77d-45ec-948d-8cf9a034e2e1, score = 8.0
INFO: sent rate-laptop request: id = 653e64c9-ee0b-4883-a2eb-c906311c0569, score = 9.0
INFO: sent rate-laptop request: id = f02073f0-776d-4b50-9be4-1b0c900dfa9a, score = 8.0
INFO: laptop rated: id = 01eab4b0-c77d-45ec-948d-8cf9a034e2e1, count = 1, average = 8.0
INFO: laptop rated: id = 653e64c9-ee0b-4883-a2eb-c906311c0569, count = 1, average = 9.0
INFO: laptop rated: id = f02073f0-776d-4b50-9be4-1b0c900dfa9a, count = 1, average = 8.0
```

Let's rate them 1 more time. Here we can see new scores are sent, and the 
returned average scores are updated.

```shell
INFO: sent rate-laptop request: id = 01eab4b0-c77d-45ec-948d-8cf9a034e2e1, score = 7.0
INFO: sent rate-laptop request: id = 653e64c9-ee0b-4883-a2eb-c906311c0569, score = 3.0
INFO: sent rate-laptop request: id = f02073f0-776d-4b50-9be4-1b0c900dfa9a, score = 10.0
INFO: laptop rated: id = 01eab4b0-c77d-45ec-948d-8cf9a034e2e1, count = 2, average = 7.5
INFO: laptop rated: id = 653e64c9-ee0b-4883-a2eb-c906311c0569, count = 2, average = 6.0
INFO: laptop rated: id = f02073f0-776d-4b50-9be4-1b0c900dfa9a, count = 2, average = 9.0
```

Excellent!

## Test bidi-streaming gRPC
Now before we finish, I will show you how to test the bidirectional streaming
RPC. You can base on that `LaptopServerTest` to write test for the client 
streaming RPC. They're very similar. First we need to add a new rating store
to this class. Initialize it inside the `setUp` function. Pass it into the 
constructor to create a new `LaptopServer`.

```java
public class LaptopServerTest {
    // ...
    
    private RatingStore ratingStore;
    
    // ...

    @Before
    public void setUp() throws Exception {
        // ...
        ratingStore = new InMemoryRatingStore();

        server = new LaptopServer(serverBuilder, 0, laptopStore, imageStore, ratingStore);
        // ...
    }
```

Then at the end of file we add a new test for the `rateLaptop` API. Let's 
create a new generator. Generate a new random laptop. And save it to the 
laptop store. To be simple, I will just rate 1 single laptop multiple times.
Here we have to create a new stub from the channel. Remember that it's an 
async stub, not the blocking stub as in the unary RPC.

```java
public class LaptopServerTest {
    // ...
    
    @Test
    public void rateLaptop() throws Exception {
        Generator generator = new Generator();
        Laptop laptop = generator.NewLaptop();
        laptopStore.Save(laptop);
        
        LaptopServiceGrpc.LaptopServiceStub stub = LaptopServiceGrpc.newStub(channel);
    }
}
```

Then we need to define a new class that implements the 
`StreamObserver<RateLaptopResponse>` interface. In this class, we will keep 
track of 3 things: the list of responses, an error if it occurs, and a 
boolean to tell whether it is completed normally or not. Since this class is
only used for unit tests, I will make all of these fields public. We also need
to initialize the responses list inside the constructor. Then in the `onNext()`
function we add the received response to the list. In the `onError()` function
we save the throwable `t` to the error field. And in the `onCompleted` 
function we just set the completed field to `true`.

```java
public class LaptopServerTest {
    // ...
    
    private class RateLaptopResponseStreamObserver implements StreamObserver<RateLaptopResponse> {
        public List<RateLaptopResponse> responses;
        public Throwable err;
        public boolean completed;

        public RateLaptopResponseStreamObserver() {
            responses = new LinkedList<>();
        }

        @Override
        public void onNext(RateLaptopResponse response) {
            responses.add(response);
        }

        @Override
        public void onError(Throwable t) {
            err = t;
        }

        @Override
        public void onCompleted() {
            completed = true;
        }
    }
}
```

Alright, let's go back to our test. We create a new 
`RateLaptopResponseStreamObserver`, and call the `stub.ratelaptop()` function
with that `responseObserver`. Now we will send 3 requests with scores of 8, 7.5
and 10. Then the expected average scores after each request will be 8, 7.75 and
8.5. We use a `for` loop to send the requests sequentially. Inside the loop we
build a new request with the same laptop ID and the score from the `scores` 
array. And call `requestObserver.onNext()` to send it to the server. At the 
end, we must call `requestObserver.onCompleted()`. Then we assert that the 
error is `null`. The response stream observer is completed. The size of 
responses should be equal to the number of requests. And finally, when we run 
through the responses list, the laptop ID should match the input laptop ID. 
The rated count should be equal to the response index + 1, and the average 
score should be equal to the expected value. 

```java
public class LaptopServerTest {
    // ...
    
    @Test
    public void rateLaptop() throws Exception {
        // ...
        
        RateLaptopResponseStreamObserver responseObserver = new RateLaptopResponseStreamObserver();
        StreamObserver<RateLaptopRequest> requestObserver = stub.rateLaptop(responseObserver);

        double[] scores = {8, 7.5, 10};
        double[] averages = {8, 7.75, 8.5};
        int n = scores.length;

        for (int i = 0; i < n; i++) {
            RateLaptopRequest request = RateLaptopRequest.newBuilder()
                    .setLaptopId(laptop.getId())
                    .setScore(scores[i])
                    .build();
            requestObserver.onNext(request);
        }

        requestObserver.onCompleted();
        assertNull(responseObserver.err);
        assertTrue(responseObserver.completed);
        assertEquals(n, responseObserver.responses.size());

        int idx = 0;
        for (RateLaptopResponse response : responseObserver.responses) {
            assertEquals(laptop.getId(), response.getLaptopId());
            assertEquals(idx + 1, response.getRatedCount());
            assertEquals(averages[idx], response.getAverageScore(), 1e-9);
            idx++;
        }
    }
}
```

OK, let's run this unit test. Cool! It passed! Let's run the whole 
`LaptopServerTest`. All tests passed! And that wraps up all lectures about 
implementing 4 types of gRPC. I hope you find them interesting and useful. 
Thanks for reading, and see you later.
