# Upload file in chunks with client-streaming gRPC - Java
Hello and welcome back to the gRPC course. In this lecture, we will learn how
to implement client-streaming RPC with Java. We're gonna implement an API that
allows clients to upload a laptop image file in multiple chunks.

## Define client-streaming RPC in proto file
OK let's start! This is the pcbook-java project that we've been working on. 
The first thing we need to do is to define the new upload image RPC. As we've
already done that in the previous lecture with Golang, I will just open the 
pcbook golang project, and copy-paste the content of the `laptop_service.proto`
file. Here we have the `UploadImageRequest` message. It has a `oneof` data 
field which can either be image info, or a chunk of image data.

```protobuf
message UploadImageRequest {
  oneof data {
    ImageInfo info = 1;
    bytes chunk_data = 2;
  }
}

message ImageInfo {
  string laptop_id = 1;
  string image_type = 2;
}
```

The `ImageInfo` contains the laptop ID and image type such as `.jpg` or `.png`.
The `chunk_data` is a sequence of bytes. The idea is that we will divide the 
image into multiple chunks of 1 kilobyte and send them to the server 
sequentially via the stream.
Then the server send back 1 single response, which contains the ID of the 
uploaded image and the total size of that image.

```protobuf
message UploadImageResponse {
  string id = 1;
  uint32 size = 2;
}
```

So the `UploadImage` RPC will take a stream of `UploadImageRequest` as input 
and return a `UploadImageResponse`.

```protobuf
service LaptopService {
  rpc CreateLaptop(CreateLaptopRequest) returns (CreateLaptopResponse) {};
  rpc SearchLaptop(SearchLaptopRequest) returns (stream SearchLaptopResponse) {};
  rpc UploadImage(stream UploadImageRequest) returns (UploadImageResponse) {};
}
```

Alright, let's build the project to generate Java codes. The build is 
successful.

## Implement the image store
Now before we implement the RPC we will need to add a new store
to save the uploaded image. I will create a new `ImageStore` interface in
`service` folder. It has 1 function: `Save`, which takes the `laptopID`, the 
`imageType` and the `imageData` as input and returns the `imageID`, or throws
out an `IOException`.

```java
package com.gitlab.techschool.pcbook.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public interface ImageStore {
    String Save(String laptopID, String imageType, ByteArrayOutputStream imageData) throws IOException;
}
```

Let's say we want to store the image on disk and its metadata on memory. So
I will create a new `DiskImageStore` class to implement this interface. In 
this class, we need a field to tell us where to store the images. We also need
a concurrent map to store the metadata of the images. The key of the map is
the image ID, and its value is the metadata.

```java
package com.gitlab.techschool.pcbook.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentMap;

public class DiskImageStore implements ImageStore {
    private String imageFolder;
    private ConcurrentMap<String, ImageMetadata> data;

    @Override
    public String Save(String laptopID, String imageType, ByteArrayOutputStream imageData) throws IOException {
        return null;
    }
}
```

I will create a new class for the `ImageMetadata`. In this class we will store
the laptop ID, the type of the image, and the path to the image on disk. Let's
write a constructor to initialize the object. And also create some getter 
functions for each of the fields.

```java
package com.gitlab.techschool.pcbook.service;

public class ImageMetadata {
    private String laptopID;
    private String type;
    private String path;

    public ImageMetadata(String laptopID, String type, String path) {
        this.laptopID = laptopID;
        this.type = type;
        this.path = path;
    }
    
    public String getLaptopID() {
        return laptopID;
    }
    
    public String getType() {
        return type;
    }
    
    public String getPath() {
        return path;
    }
}
```

OK, now go back to our `DiskImageStore`. First we create a new constructor that
takes only the `imageFolder` as input. We initialize the data map with a new
`ConcurrentHashMap`. Then in the `Save` function, we generate a random UUID
that will be used as the image ID. We make the path to store the image by 
joining the `imageFolder`, `imageID` and `imageType` together. Then we create 
a new `FileOutputStream` with the image path. We call `imageData.writeTo()` to
write the image data to that file output stream. And close the output stream. 
Once the file is successfully written to disk we create a new metadata object
and put it to the data map with the `imageID` key. Finally, we return the 
`imageID`. And we're done with the `DiskImageStore`.

```java
public class DiskImageStore implements ImageStore {
    // ...

    @Override
    public String Save(String laptopID, String imageType, ByteArrayOutputStream imageData) throws IOException {
        String imageID = UUID.randomUUID().toString();
        String imagePath = String.format("%s/%s%s", imageFolder, imageID, imageType);

        FileOutputStream fileOutputStream = new FileOutputStream(imagePath);
        imageData.writeTo(fileOutputStream);
        fileOutputStream.close();
        
        ImageMetadata metadata = new ImageMetadata(laptopID, imageType, imagePath);
        data.put(imageID, metadata);
        
        return imageID;
    }
}
```

## Implement the UploadImage RPC
Now let's implement the `UploadImage` RPC in the `LaptopService` class. First I 
will change `store` field to `laptopStore`. Then we will add a new field for 
the `imageStore`. Also add it to this constructor.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...

    private LaptopStore laptopStore;
    private ImageStore imageStore;

    public LaptopService(LaptopStore laptopStore, ImageStore imageStore) {
        this.laptopStore = laptopStore;
        this.imageStore = imageStore;
    }

    @Override
    public void createLaptop(CreateLaptopRequest request, StreamObserver<CreateLaptopResponse> responseObserver) {
        // ...
        try {
            laptopStore.Save(other);
        } catch (AlreadyExistsException e) {
            // ...
        } catch (Exception e) {
            // ...
        }

        // ...
    }

    @Override
    public void searchLaptop(SearchLaptopRequest request, StreamObserver<SearchLaptopResponse> responseObserver) {
        // ...

        laptopStore.Search(Context.current(), filter, new LaptopStream() {
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

OK, now we need to override the `uploadImage()` method. As you can see, this
method has a `responseObserver` parameter that will be used to send the 
response to the client, just like the way it works in the `searchLaptop` RPC. 
How about the stream of requests? This is very different from server-streaming 
RPC, because it's not an input parameter, but the return value of this 
function instead. Here we can see that the `uploadImage` function must return
a `StreamObserver` of `UploadImageRequest` and this `StreamObserver` is just an 
interface with 3 functions: `onNext`, `onError` and `onCompleted`. What we need
to do is to return an implementation of this interface. So let's do that.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...

    @Override
    public StreamObserver<UploadImageRequest> uploadImage(StreamObserver<UploadImageResponse> responseObserver) {
        return new StreamObserver<UploadImageRequest>() {
            private String laptopID;
            private String imageType;
            private ByteArrayOutputStream imageData;

            @Override
            public void onNext(UploadImageRequest value) {
                if (request.getDataCase() == UploadImageRequest.DataCase.INFO) {
                    ImageInfo info = request.getInfo();
                }
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

First we define 3 fields: `laptopID`, `imageType` and `imageData`. Now in the 
`onNext()` function, we check the data case. If it is image info, we write a
simple log saying that we have received the image info. Then we get the 
`laptopID` and `imageType` from that info. We also initialize the `imageData` 
as a new `ByteArrayOutputStream` and `return`. Else, it must be a new data 
chunk. So we get the chunk from the request. Write a log here saying that
we've received a chunk with this size. Then we check if the `imageData` is 
`null` or not. If it is `null`, it means that the client hasn't sent the image
info, so we just send an error with `INVALID_ARGUMENT` status and `return` 
immediately. Otherwise, we just call `chunkData().writeTo()` function to add 
this chunk to the image data. If we catch an exception just send an `INTERNAL`
error to the client and `return`. That's it for the `onNext()` function.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...

    @Override
    public StreamObserver<UploadImageRequest> uploadImage(StreamObserver<UploadImageResponse> responseObserver) {
        return new StreamObserver<UploadImageRequest>() {
            private String laptopID;
            private String imageType;
            private ByteArrayOutputStream imageData;

            @Override
            public void onNext(UploadImageRequest request) {
                if (request.getDataCase() == UploadImageRequest.DataCase.INFO) {
                    ImageInfo info = request.getInfo();
                    logger.info("receive image info:\n" + info);

                    laptopID = info.getLaptopId();
                    imageType = info.getImageType();
                    imageData = new ByteArrayOutputStream();

                    return;
                }

                ByteString chunkData = request.getChunkData();
                logger.info("receive image chunk with size: " + chunkData.size());

                if (imageData == null) {
                    logger.info("image info wasn't sent before");
                    responseObserver.onError(
                            Status.INVALID_ARGUMENT
                                    .withDescription("image info wasn't sent beefore")
                                    .asRuntimeException()
                    );
                    return;
                }

                try {
                    chunkData.writeTo(imageData);
                } catch (IOException e) {
                    responseObserver.onError(
                            Status.INTERNAL
                                    .withDescription("cannot write chunk data: " + e.getMessage())
                                    .asRuntimeException()
                    );
                    return;
                }
            }

            // ...
        };
    }
}
```

The `onError()` function is called whenever an error occurs while the server 
is receiving stream data. So here we just need to write a warning log.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...

    @Override
    public StreamObserver<UploadImageRequest> uploadImage(StreamObserver<UploadImageResponse> responseObserver) {
        return new StreamObserver<UploadImageRequest>() {
            // ...
            
            @Override
            public void onError(Throwable t) {
                logger.warning(t.getMessage());
            }
            
            // ...
        };
    }
}
```

OK, now let's implement the `onCompleted()` function. When this function is 
called, it means that the server has received all image chunk data. So we just
call `imageStore.Save()` to save the image data to the store. Surround this
call with a try-catch. If an error is caught, we call 
`responseObserver.onError()` to send it to the client. We save the output
`imageID` to a variable and also get the total image size. Then we build a 
new `UploadImageResponse` object with the `imageID` and `imageSize`. We call 
`responseObserver.onNext()` to send the response to the client, and finally
call `responseObserver.onCompleted()` to finish it.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...

    @Override
    public StreamObserver<UploadImageRequest> uploadImage(StreamObserver<UploadImageResponse> responseObserver) {
        return new StreamObserver<UploadImageRequest>() {
            // ...

            @Override
            public void onCompleted() {
                String imageID = "";
                int imageSize = imageData.size();
                try {
                    imageID = imageStore.Save(laptopID, imageType, imageData);
                } catch (IOException e) {
                    responseObserver.onError(
                            Status.INTERNAL
                                    .withDescription("cannot save image to the store: " + e.getMessage())
                                    .asRuntimeException()
                    );
                }

                UploadImageResponse response = UploadImageResponse.newBuilder()
                        .setId(imageID)
                        .setSize(imageSize)
                        .build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        };
    }
}
```

OK the `uploadImage` RPC is ready.

## Update the server
Now we need to update the `LaptopServer` a bit. First change `store` in 
`LaptopServer` constructor to `laptopStore`. Add a new `imageStore` to this
constructor then pass it into this `LaptopService`. Do the same for another 
constructor. In the `main` function we also change the `store` variable to 
`laptopStore` and create a new `DiskImageStore` with the image folder is 
`"img"`. Then pass it into the new `LaptopServer` constructor. Create a 
folder for images `"img"` in project. So we're all set.

```java
public class LaptopServer {
    // ...

    public LaptopServer(int port, LaptopStore laptopStore, ImageStore imageStore) {
        this(ServerBuilder.forPort(port), port, laptopStore, imageStore);
    }

    public LaptopServer(ServerBuilder serverBuilder, int port, LaptopStore laptopStore, ImageStore imageStore) {
        // ...
        LaptopService laptopService = new LaptopService(laptopStore, imageStore);
        // ...
    }

    // ...
    
    public static void main(String[] args) throws InterruptedException, IOException {
        InMemoryLaptopStore laptopStore = new InMemoryLaptopStore();
        DiskImageStore imageStore = new DiskImageStore("img");

        LaptopServer server = new LaptopServer(8080, laptopStore, imageStore);
        // ...
    }
}

public class LaptopServerTest {
    // ...

    @Before
    public void setUp() throws Exception {
        // ...

        laptopStore = new InMemoryLaptopStore();
        ImageStore imageStore = new DiskImageStore("img");
        server = new LaptopServer(serverBuilder, 0, laptopStore, imageStore);
        // ...
    }
    
    // ...
}
```

Let's run the server. Now let's try to call this server using the Golang client
that we wrote in the previous lecture. The laptop image is successfully 
uploaded. We can see it in the `img` folder. So it works!

## Implement the client
Now we will implement the Java client. We cannot use the `blockingStub` to 
call the client-streaming RPC. Instead, we will need an asynchronous stub. So
let's define it here. And initialize it inside this `LaptopClient` constructor
by calling `LaptopServiceGrpc.newStub()`. Alright, now define a `uploadImage()`
function with 2 input parameters: a laptop ID, and an image path.

```java
public class LaptopClient {
    // ...
    private final LaptopServiceGrpc.LaptopServiceStub asyncStub;

    public LaptopClient(String host, int port) {
        // ...
        asyncStub = LaptopServiceGrpc.newStub(channel);
    }

    // ...
    
    public void uploadImage(String laptopID, String imagePath) {
        
    }
}
```

In the `main` function I'm gonna comment out block of codes to test create and
search laptop that we wrote in the previous lectures. And add new codes to 
test upload image here. First we generate a new random laptop. We call 
`client.createLaptop()` to create this laptop on the server. Then we call 
`client.uploadImage()` with the laptop ID and a `laptop.jpg` file inside the
`tmp` folder. Let's create that `tmp` folder. And copy the `laptop.jpg` from
the golang project to that folder. Alright, it's here.

```java
public class LaptopClient {
    // ...
    
    public static void main(String[] args) throws InterruptedException {
        // ...

        try {
/*            for (int i = 0; i < 10; i++) {
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

            client.searchLaptop(filter);*/

            // Test upload laptop image
            Laptop laptop = generator.NewLaptop();
            client.createLaptop(laptop);
            client.uploadImage(laptop.getId(), "tmp/laptop.jpg");
        } finally {
            client.shutdown();
        }
    }
}
```

Now in the `uploadImage()` function we call `asyncStub.withDeadlineAfter` 5 
seconds then `uploadImage()`. We create a new `StreamObserver` of 
`UploadImageResponse` here. The output of this call will be another 
`StreamObserver` of `UploadImageRequest`. In the `onNext()` function we just
write a simple log saying we've received this response from the server. In 
the `onError()` function we write a SEVERE log: upload failed. Note that the
stub is asynchronous, which means that the send request part and the receive 
response part are run asynchronously because of this, we need to use a 
`CountDownLatch()` to wait until the whole process is completed. Here we just
use a count of 1 because we only need to wait for the response thread. OK, now
if an error occurs, we will call `countDown()` inside the `onError()` 
function. Similarly, in the `onCompleted()` function we also write a log and
call `finishLatch.countDown()`. At the end of the `uploadImage()` function we 
call `finishLatch.await()` to wait for the response thread to finish. Here
we only wait for at most 1 minute, which is more than enough because above we
set the deadline of the call to be 5 seconds. Next we will create a new 
`FileInputStream` to read the image file. If we catch an exception, just write
a SEVERE log and return. Else we get the image type from the image file 
extension. We build a new image info with the laptop ID and image type. We 
create a new `UploadImageRequest` with the image info. And call 
`requestObserver.onNext()` to send the request to the server. Surround this 
with a try-catch. If there's an exception we write a SEVERE log, call 
`requestObserver.onError()` to report it to the server and return. Finally, we
call `requestObserver.onCompleted()`. Inside the try-catch block after we've 
sent the image info, we will start sending the image data in chunks. Each chunk
will be 1 kilobyte, so we create a new byte buffer with the size of 1024. We 
use a `while` loop here to read and send data multiple times. I will need to 
pull this `fileInputStream` variable out. Then here we can call 
`fileInputStream.read()` to read more data into the buffer. It will return the
number if bytes read. Assign it to `n`. If `n` is less than or equal to 0, then 
it's the end of file. We can safely break the loop. Now we check if the latch
has already finished because of some unexpected error, then we don't need to
send more data, so just return. Otherwise, we make a new request with the
chunk data. Here we just copy the first `n` bytes from the buffer. Similar as 
before, we call `requestObserver.onNext()` to send the request to the server.
And write a log saying that the chunk with this size was sent. That's it! 
We're done with the client.

```java
public class LaptopClient {
    // ...
    
    public void uploadImage(String laptopID, String imagePath) throws InterruptedException {
        final CountDownLatch finishLatch = new CountDownLatch(1);

        StreamObserver<UploadImageRequest> requestObserver = asyncStub.withDeadlineAfter(5, TimeUnit.SECONDS)
                .uploadImage(new StreamObserver<UploadImageResponse>() {
                    @Override
                    public void onNext(UploadImageResponse response) {
                        logger.info("receive response:\n" + response);
                    }

                    @Override
                    public void onError(Throwable t) {
                        logger.log(Level.SEVERE, "upload failed: " + t);
                        finishLatch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        logger.info("image uploaded");
                        finishLatch.countDown();
                    }
                });

        FileInputStream fileInputStream;
        try {
            fileInputStream = new FileInputStream(imagePath);
        } catch (FileNotFoundException e) {
            logger.log(Level.SEVERE, "cannot read image file: " + e.getMessage());
            return;
        }

        String imageType = imagePath.substring(imagePath.lastIndexOf("."));
        ImageInfo info = ImageInfo.newBuilder().setLaptopId(laptopID).setImageType(imageType).build();
        UploadImageRequest request = UploadImageRequest.newBuilder().setInfo(info).build();

        try {
            requestObserver.onNext(request);
            logger.info("sent image info:\n" + info);

            byte[] buffer = new byte[1024];
            while (true) {
                int n = fileInputStream.read(buffer);
                if (n <= 0) {
                    break;
                }

                if (finishLatch.getCount() == 0) {
                    return;
                }

                request = UploadImageRequest.newBuilder()
                        .setChunkData(ByteString.copyFrom(buffer, 0, n))
                        .build();
                requestObserver.onNext(request);
                logger.info("sent image chunk with size: " + n);
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

Now let's run the server and run the client. The image is successfully 
uploaded. And we got this response with image ID and image size. The logs on
server side look good. And we can see the laptop image inside the `img` 
folder. Now let's say, we want to put a constraint on the maximum size of the 
image. For example only allow upload images with size of at most 1 kilobyte:
`private static final int maxImageSize = 1 << 10`. Then in the `onNext()` 
function, before writing the chunk to the image data, we compute the current 
size of the image. If it is greater than the maximum allowed size, then we 
write a log "image is too large". We report the error to the client with 
`INVALID_ARGUMENT` status and return right away.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...

    @Override
    public StreamObserver<UploadImageRequest> uploadImage(StreamObserver<UploadImageResponse> responseObserver) {
        return new StreamObserver<UploadImageRequest>() {
            private static final int maxImageSize = 1 << 10; // 1 kilobyte
            // ...

            @Override
            public void onNext(UploadImageRequest request) {
                // ...

                int size = imageData.size() + chunkData.size();
                if (size > maxImageSize) {
                    logger.info("image is too large: " + size);
                    responseObserver.onError(
                            Status.INVALID_ARGUMENT
                                    .withDescription("image is too large: " + size)
                                    .asRuntimeException()
                    );
                    return;
                }

                // ...
            }

            // ...
        };
    }
}
```

OK let's try it. Run the server. Then run the client. As you can see, some 
chunks are sent to the server, and we got an `INVALID_ARGUMENT` error: image is
too large. So it works. Note that the send part and receive part are parallel,
So it's totally possible that the client will send more than 2 chunks before 
it receives the error from the server and stops sending more. As a result, we
may see a warning on server side:  `WARNING: Stream Error`. Because the server 
has already closed the stream when it sent the error to the client. Let's 
change `maxImageSize` to 1 megabyte.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...

    @Override
    public StreamObserver<UploadImageRequest> uploadImage(StreamObserver<UploadImageResponse> responseObserver) {
        return new StreamObserver<UploadImageRequest>() {
            private static final int maxImageSize = 1 << 20; // 1 megabyte
            // ...
        };
    }
}
```

OK, the last thing before we finish. When we receive the image info, we need 
to check that the laptop ID exists in the store. To do so, we just call
`laptopStore.Find(laptopID)`. If the laptop is not found, we simply call 
`responseObserver.onError()` with Status `NOT_FOUND`. On the client side, we
can comment out this command `client.createLaptop(laptop);` so that the laptop
is not created on the server.

```java
public class LaptopClient {
    public static void main(String[] args) throws InterruptedException {
        // ...
        
        try {
            // ...
            // Test upload laptop image
            Laptop laptop = generator.NewLaptop();
            // client.createLaptop(laptop);
            client.uploadImage(laptop.getId(), "tmp/laptop.jpg");
        } finally {
            client.shutdown();
        }
    }
    // ...
}
```

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...
    
    @Override
    public StreamObserver<UploadImageRequest> uploadImage(StreamObserver<UploadImageResponse> responseObserver) {
        return new StreamObserver<UploadImageRequest>() {
            private static final int maxImageSize = 1 << 20; // 1 megabyte
            // ...

            @Override
            public void onNext(UploadImageRequest request) {
                if (request.getDataCase() == UploadImageRequest.DataCase.INFO) {
                    // ...

                    // check laptop exists
                    Laptop found = laptopStore.Find(laptopID);
                    if (found == null) {
                        responseObserver.onError(
                                Status.NOT_FOUND
                                        .withDescription("laptop ID doesn't exists")
                                        .asRuntimeException()
                        );
                    }

                    return;
                }

                // ...
            }

            // ...
        };
    }
}
```

Alright, now let's run the server and run the client. We got not found error.

```shell
SEVERE: upload failed: io.grpc.StatusRuntimeException: NOT_FOUND: laptop ID doesn't exists
```

So it's working as expected. And that's it for today lecture about 
client-streaming RPC. In the next lecture, we will learn how to implement the
last type of gRPC, which is bidirectional streaming. I hope the course is 
useful for you so far. Thank you for reading, and see you later!