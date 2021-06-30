# Implement server-streaming gRPC API - Java
Hello and welcome back to the gRPC course. In this lecture we will learn how
to implement the server-streaming RPC in Java. This RPC will allow us to 
search for laptops with some filtering conditions. The result will be returned
to the client as a stream of laptops.

## Add server-streaming RPC definition to Protobuf
OK, let's start! I will open the IntelliJ IDEA project that we're working on
in previous lectures. As we have already defined the search laptop RPC in the
last lecture, I will just go to the `pcbook` golang project, and copy 2 proto 
files to our java project. The first one is the `filter_message.proto` file.
And the second one is the `laptop_service.proto` file. Alright, let's review
them a bit. This filter message allows us to define some configurations of the 
laptop that we want to search for. In the `laptop_service.proto` file, we have 
the `SearchLaptopRequest` that contains only the filter and the 
`SearchLaptopResponse` that contains only the laptop object. We define the 
server-streaming RPC SearchLaptop which takes the `SearchLaptopRequest` as 
input and returns a stream of `SearchLaptopResponse`.

Now let's build the project to generate Java codes. The build is successful. 
Let's look at the generated code. As you can see in this 
`LaptopServiceImplBase` class there is a new `searchLaptop` function that we 
will have to implement on the server side. Then in the 
`LaptopServiceBlockingStub` class there is also a `searchLaptop` function that
we can use on the client side to call the server. It will return an iterator 
of `SearchLaptopResponse` object.

```java
/**
 */
public static abstract class LaptopServiceImplBase implements io.grpc.BindableService {
    // ...
    /**
     */
    public void searchLaptop(com.github.techschool.pcbook.pb.SearchLaptopRequest request,
                             io.grpc.stub.StreamObserver<com.github.techschool.pcbook.pb.SearchLaptopResponse> responseObserver) {
        io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSearchLaptopMethod(), responseObserver);
    }
    
    // ...
}

/**
 */
public static final class LaptopServiceBlockingStub extends io.grpc.stub.AbstractBlockingStub<LaptopServiceBlockingStub> {
    // ...
    /**
     */
    public java.util.Iterator<com.github.techschool.pcbook.pb.SearchLaptopResponse> searchLaptop(
            com.github.techschool.pcbook.pb.SearchLaptopRequest request) {
        return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
                getChannel(), getSearchLaptopMethod(), getCallOptions(), request);
    }
}
```

Alright, now we will implement the server side first. Let's start with the 
`LaptopStore` interface. We will define a new `Search` function which takes the
filter as input and sends back the qualified laptops via a `LaptopStream` 
parameter. This stream is defined as a functional interface with only 1 
method: `Send`. So you can use it as a lambda if you want. I'm gonna move this
interface to a separate file.

`com/gitlab/techschool/pcbook/service/LaptopStore.java`
```java
package com.gitlab.techschool.pcbook.service;

import com.github.techschool.pcbook.pb.Filter;
import com.github.techschool.pcbook.pb.Laptop;

public interface LaptopStore {
    void Save(Laptop laptop) throws Exception; // consider using a separate db model
    Laptop Find(String id);
    void Search(Filter filter, LaptopStream stream);
}
```
`com/gitlab/techschool/pcbook/service/LaptopStream.java`
```java
package com.gitlab.techschool.pcbook.service;

import com.github.techschool.pcbook.pb.Laptop;

public interface LaptopStream {
    void Send(Laptop laptop);
}
```

## Add search function to the in-memory store
OK, now we have to implement the `Search` function for the 
`InMemoryLaptopStore`. In IntelliJ IDEA just press  
Option + Enter (on macOS) or Alt + Enter (on Win and Linux) on class name and 
choose `Implement methods`.

![Implement-search-method](images/lecture11.2/implement_class.png)

**Picture 1** - Implement Search method.

In this `Search` function we use a `for` loop to iterate through all elements
of the data map. The value of the `entry` variable is a laptop. We check if 
this laptop is qulified to the filter or not. If it is, we just call 
`stream.Send()` to send a deep-copy of the laptop to the caller.

```java
public class InMemoryLaptopStore implements LaptopStore {
    // ...
    @Override
    public void Search(Filter filter, LaptopStream stream) {
        for (Map.Entry<String, Laptop> entry: data.entrySet()) {
            Laptop laptop = entry.getValue();
            if (isQualified(filter, laptop)) {
                stream.Send(laptop.toBuilder().build());
            }
        }
    }
}
```

OK, let's implement the `isQualified()` function. First if the price of the 
laptop is greater that the max price of the filter return `false`. If the 
number of cores of the laptop is smaller than the number required by the 
filter, return `false`. If the min frequency of the laptop CPU is less than
that of the filter return `false`. And finally if the RAM of the laptop is less
than the filter return `false`.

```java
public class InMemoryLaptopStore implements LaptopStore {   
    // ...
    private boolean isQualified(Filter filter, Laptop laptop) {
        if (laptop.getPriceUsd() > filter.getMaxPriceUsd()) {
            return false;
        }
        
        if (laptop.getCpu().getNumberCores() < filter.getMinCpuCores()) {
            return false;
        }
        
        if (laptop.getCpu().getMinGhz() < filter.getMinCpuGhz()) {
            return false;
        }
        
        if (toBit(laptop.getRam()) < toBit(filter.getMinRam())) {
            return false;
        }
        
        return true;
    }
}
```

Here we must write a `toBit()` function to convert the memory size to the 
smallest unit: `BIT`. In this function, we first get the value of the memory.
Then we use switch-case statement on the memory unit. If it is `BIT`, just 
return the value. If it is `BYTE`, return value shift-left 3. This is because 
1 byte equals 8 bits and 8 equals 2 to the power of 3. If the unit is 
`KILOBYTE`, return value shift-left 13. Because 1 `KILOBYTE` equals 1024 (or 
2^10) `BYTE`, and thus it equals 2^13 BIT. Similarly, if it is `MEGABYTE`, 
return value shift-left 23. If it is `GIGABYTE`, return value shift-left 33 and
for `TERABYTE` return value shift-left 43. For default case just return 0.

```java
public class InMemoryLaptopStore implements LaptopStore {
    // ...
    private long toBit(Memory memory) {
        long value = memory.getValue();

        switch (memory.getUnit()) {
            case BIT:
                return value;
            case BYTE:
                return value << 3; // 1 BYTE = 8 BIT = 2^3 BIT
            case KILOBYTE:
                return value << 13; // 1 KILOBYTE = 1024 BYTE = 2^10 BYTE = 2^13 BIT
            case MEGABYTE:
                return value << 23;
            case GIGABYTE:
                return value << 33;
            case TERABYTE:
                return value << 43;
            default:
                return 0;
        }
    }
}
```

OK, now the store is ready.

## Implement the server
Let's open the `LaptopService` file. We have to override the `searchLaptop()` 
function. First we get the filter from the request. Write a log here saying 
that we have received a search laptop request with this filter. Then we call 
`store.Search()` function with the filter, and provide an implementation of 
the `LaptopStream` interface. When a laptop is found, we will get it from the
`Send` function. So we write a log here saying that a laptop with this ID is 
found. Then we build a response with the laptop and call 
`responseObserver.onNext()` function to send this response to client. In the 
end when the search is completed, we call `responseObserver.onCompleted` to 
tell the client that there won't be any more responses. Let's write a simple 
log here as well.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...
    @Override
    public void searchLaptop(SearchLaptopRequest request, StreamObserver<SearchLaptopResponse> responseObserver) {
        Filter filter = request.getFilter();
        logger.info("got a search-laptop request with filters: \n" + filter);

        store.Search(filter, new LaptopStream() {
            @Override
            public void Send(Laptop laptop) {
                logger.info("found laptop with ID: " + laptop.getId());
                SearchLaptopResponse response = SearchLaptopResponse.newBuilder().setLaptop(laptop).build();
                responseObserver.onNext(response);
            }
        });

        responseObserver.onCompleted();
        logger.info("search laptop completed");
    }
}
```

## Implement the client
OK, the server is ready, now let's implement the client. Here in 
`LaptopClient.java` `main` function I will use a `for` loop to create 10 random
laptops. Then let's create a filter with maximum price of 3000, minimum CPU 
cores of 4, minimum CPU frequency of 2.5, and minimum RAM of 8 gigabytes. Now
we have the filter, we can pass it in the `searchLaptop()` function.

```java
public class LaptopClient {
    public static void main(String[] args) throws InterruptedException {
        // ...

        try {
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
        } finally {
            client.shutdown();
        }
    }
}
```

In this function, let's write a log saying the search is started. We create a
new `SearchLaptopRequest` with the filter. Then we use the `blockingStub` to
call the `searchLaptop()` RPC on the server, passing in the created request. It
will return an iterator of `SearchLaptopResponse` objects. All we have to do 
is to run through that iterator. And for each response object, we just print 
out a simple log containing the laptop ID. And finally we write the log saying
the search is completed. And that's it for the client.

```java
public class LaptopClient {
    // ...
    
    private void searchLaptop(Filter filter) {
        logger.info("search started");
        
        SearchLaptopRequest request = SearchLaptopRequest.newBuilder().setFilter(filter).build();
        Iterator<SearchLaptopResponse> responseIterator = blockingStub.searchLaptop(request);
        
        while (responseIterator.hasNext()) {
            SearchLaptopResponse response = responseIterator.next();
            Laptop laptop = response.getLaptop();
            logger.info("- found: " + laptop.getId());
        }

        logger.info("search completed");
    }
}
```

Let's start the gRPC server. Then run the client. It works! Some laptops are 
found and shown on the client side.

```shell
INFO: search started
INFO: - found: 55c098c9-0c55-4193-8f2f-5d0f336b2c36
INFO: - found: b0c2878c-f6a0-454d-bedb-802c79c38fb2
INFO: - found: fbc31c73-c925-4759-8e89-f7c175db73c6
INFO: - found: 309fc3f3-ca10-4760-a14b-aa2ac4ddaf91
INFO: search completed
```

And the same on the server side.

```shell
INFO: found laptop with ID: 55c098c9-0c55-4193-8f2f-5d0f336b2c36
INFO: found laptop with ID: b0c2878c-f6a0-454d-bedb-802c79c38fb2
INFO: found laptop with ID: fbc31c73-c925-4759-8e89-f7c175db73c6
INFO: found laptop with ID: 309fc3f3-ca10-4760-a14b-aa2ac4ddaf91
INFO: search laptop completed
```

OK, now I will show you how to handle timeout or deadline. Suppose that in the
search function of the store, each iteration through a laptop in the data map
takes 1 second. And on the client side, it sets the request deadline to be
after 5 seconds. I will wrap this block inside a `try-catch` and print out a log 
if error is caught.

```java
public class InMemoryLaptopStore implements LaptopStore {
    // ...
    
    @Override
    public void Search(Filter filter, LaptopStream stream) {
        for (Map.Entry<String, Laptop> entry: data.entrySet()) {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Laptop laptop = entry.getValue();
            if (isQualified(filter, laptop)) {
                stream.Send(laptop.toBuilder().build());
            }
        }
    }
    
    // ...
}
public class LaptopClient {
    // ...

    private void searchLaptop(Filter filter) {
        // ...

        try {
            Iterator<SearchLaptopResponse> responseIterator = blockingStub
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .searchLaptop(request);

            while (responseIterator.hasNext()) {
                SearchLaptopResponse response = responseIterator.next();
                Laptop laptop = response.getLaptop();
                logger.info("- found: " + laptop.getId());
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "request failed: " + e.getMessage());
            return;
        }

        // ...
    }
    
    // ...
}

```

Alright, let's run the server. Then run the client. After 5 seconds, we got
a DEADLINE_EXCEEDED error.

```shell
SEVERE: request failed: DEADLINE_EXCEEDED: deadline exceeded after 4.997785771s. [remote_addr=0.0.0.0/[0:0:0:0:0:0:0:1]:8080]
```

However, on the server side, it keeps searching for more laptops. You can run
the client one more time so that you can see it more clearly. Ok, we've got 
the error on client side. How about the server? As you can see, it's still
searching, and several more laptops are found. But they're useless because the 
client already cancelled the request. To fix this, we have to check the status
of the request context, just like what we did in the unary RPC in the previous
lecture. So we must pass the context into the search function of the store. I
will add it to `LaptopStore` interface first.

```java
public interface LaptopStore {
    void Save(Laptop laptop) throws Exception; // consider using a separate db model
    Laptop Find(String id);
    void Search(Context ctx, Filter filter, LaptopStream stream);
}
```

Then update the `InMemoryLaptopStore` accordingly. We will check if the 
context is already cancelled or not. If it is, we will return immediately. I 
will write a log here so that we can see it better.

```java
public class InMemoryLaptopStore implements LaptopStore {
    private static final Logger logger = Logger.getLogger(LaptopClient.class.getName());

    // ...
    @Override
    public void Search(Context ctx, Filter filter, LaptopStream stream) {
        for (Map.Entry<String, Laptop> entry : data.entrySet()) {
            if (ctx.isCancelled()) {
                logger.info("context is cancelled");
                return;
            }
            // ...
        }
    }
}
```

Alright now in the `LaptopService` we must pass in the current context of the
request. Then it's done.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    public void searchLaptop(SearchLaptopRequest request, StreamObserver<SearchLaptopResponse> responseObserver) {
        // ...
        
        @Override
        store.Search(Context.current(), filter, new LaptopStream() {
            @Override
            public void Send(Laptop laptop) {
                logger.info("found laptop with ID: " + laptop.getId());
                SearchLaptopResponse response = SearchLaptopResponse.newBuilder().setLaptop(laptop).build();
                responseObserver.onNext(response);
            }
        });

        // ...
    }
}
```

Now let's run the server and the client. When the deadline is exceeded we can 
see on the server side it prints out the log "context is cancelled"

```shell
INFO: context is cancelled
INFO: search laptop completed
```

and stops searching immediately. Excellent!

And that's it for today's lecture about server-streaming RPC. The unit-testing
is very similar to unary RPC, so you can try to write it on your own as an 
exercise.

In the next lecture, we will learn how to implement the 3rd type of gRPC which
is client-streaming. Until then, happy coding and I will see you later.