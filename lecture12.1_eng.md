# Upload file in chunks with client-streaming gRPC - Golang
Welcome back, everyone. In this lecture we will learn how to use 
client-streaming RPC to upload image file to the server in multiple chunks. 
In this first part of the lecture, we will implement with Golang.

## Define client-streaming RPC in proto file
OK, let's start! First, we will define the RPC in the `laptop_service.proto`
file. We need an `UploadImageRequest` message. The idea is to divide the image 
file into multiple chunks and send them one by one to the server in each 
request message. I use a `oneof` field here because the first request will only 
contain the metadata or some basic information of the image and the following 
request will contain the image data chunks. The `ImageInfo` will have 2 fields:
the laptop ID and the image type, such as ".jpg" or ".png".

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

Then we define an `UploadImageResponse` message, which will be returned to the
client once the server has received all chunks of the image. It will contain 
the ID of the image, generated by the server, and the total size of the 
uploaded image in bytes. OK, now we define the `UploadImage` RPC in the 
`LaptopService`. It takes a stream of `UploadImageRequest` as input and return
1 single `UploadImageResponse`.

```protobuf
message UploadImageResponse {
  string id = 1;
  string size = 2;
}
```

Alright, now let's run `make gen` to generate codes.

```shell
make gen
```

The code is successfully generated, and if we comment line
`pb.UnimplementedLaptopServiceServer` in `laptop_server.go`

```go
type LaptopServer struct {
    Store LaptopStore
    pb.UnimplementedLaptopServiceServer
}
```

we see an error here `pb.RegisterLaptopServiceServer(grpcServer, laptopServer)`
in `laptop_client_test.go` because the laptop server 
hasn't implemented the `UploadImage` method that is required by the 
`LaptopServiceServer` interface.

## Implement the server
So let's open the `laptop_Server.go` file and add `UploadImage()` function to 
the `LaptopServer` struct. We can easily find this function signature inside 
the generated `laptop_service_grpc.pb.go` file. Just need to copy and paste it 
here. Let's return `nil` for now.

```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
	return nil
}
```

And you can see that the error is gone. We will come back to that function
later.

### Implement the image store
Now we need to implement a new image store first. The `ImageStore` interface
has 1 function to save a laptop image. It will take 3 input parameters: the
laptop ID, the image type, and the image data. And it will return the ID of
the saved image, or an error.

```go
type ImageStore interface {
    // Save saves a new laptop image to the store
    Save(laptopID string, imageType string, imageData bytes.Buffer) (string, error)
}
```

Next we will implement a `DiskImageStore` that will save image file to the
disk and store its information in memory. Similar to the laptop store, we
need a mutex to handle concurrency. Then we need the path of the folder to 
save laptop images and finally a map with the key is image ID, and the value
is some information of the image.

```go
type DiskImageStore struct {
    mutex       sync.RWMutex
    imageFolder string
    images      map[string]*ImageInfo
}
```

The `ImageInfo` will contain 3 fields: the ID of the laptop, the type of the
image (or its file extension), and the path to the image file on disk.

```go
type ImageInfo struct {
    LaptopID string
    Type     string
    Path     string
}
```

OK, let's write a function to create a new DiskImageStore. It has only 1 input,
which is the image folder and inside we just need to initialize the map.

```go
func NewDiskImageStore(imageFolder string) *DiskImageStore {
    return &DiskImageStore{
        imageFolder: imageFolder,
        images:      make(map[string]*ImageInfo),
    }
}
```

Now we have to implement the `Save` function which is required by the 
`ImageStore` interface. First we have to generate a new random UUID for the 
image. If error is not nil, just wrap and return it. Else, we make the path
to store the image by joining the image folder, image ID, and image type. Then
we call `os.Create` to create the file. If there's an error, just wrap and
return it. Otherwise, we write the image data to the created file. Wrap and
return an error if it occurs. If the file is written successfully, we need to 
save its information to the in-memory map. So we have to acquire the write
lock of the store. We save the image information to the map with key is the ID
of the image. And the value contains the laptop ID, the image type, and the 
path to the image file. Finally, we return the image ID and no error. That's 
it, we're done with the image store.

```go
func (store *DiskImageStore) Save(
    laptopID string,
    imageType string,
    imageData bytes.Buffer,
) (string, error) {
    imageID, err := uuid.NewRandom()
    if err != nil {
        return "", fmt.Errorf("cannot generate image id: %w", err)
    }

    imagePath := fmt.Sprintf("%s/%s%s", store.imageFolder, imageID, imageType)

    file, err := os.Create(imagePath)
    if err != nil {
        return "", fmt.Errorf("cannot create image file: %w", err)
    }

    _, err = imageData.WriteTo(file)
    if err != nil {
        return "", fmt.Errorf("cannot write image to file: %w", err)
    }

    store.mutex.Lock()
    defer store.mutex.Unlock()
    
    store.images[imageID.String()] = &ImageInfo{
        LaptopID: laptopID,
        Type: imageType,
        Path: imagePath,
    }

    return imageID.String(), nil
}
```

Now let's go back to the server.

### Implement the UploadImage RPC
We need to add the new image store to the `LaptopServer` struct, so I will 
change this store field to `laptopStore` and add the `imageStore` as the second
parameter of this `NewLaptopServer` function.

```go
type LaptopServer struct {
    laptopStore LaptopStore
    imageStore ImageStore
    pb.UnimplementedLaptopServiceServer
}

// NewLaptopServer returns a new LaptopServer
func NewLaptopServer(laptopStore LaptopStore, imageStore ImageStore) *LaptopServer {
    return &LaptopServer{
        laptopStore: laptopStore,
        imageStore: imageStore,
    }
}
```

Alright, now some new errors show up because of this change. Let's open the
`laptop_client_test.go` file. First, the public `Store` field is replaced by
the `laptopStore`. So let's extract this new in-memory laptop store to a 
separate variable and replace `Find` call with it. Now we no longer need the 
`laptopServer` object so let's remove it from the `startTestLaptopServer`
function and add `imageStore` as its input parameter. Now we can pass the 2
stores into the `NewLaptopServer()` function. At the end, we only need to 
return the server address. Then in this `TestClientCreateLaptop` test, we 
just pass `nil` as the image store because the test doesn't do anything with 
it.

```go
func TestClientCreateLaptop(t *testing.T) {
    // ...
    
    laptopStore := service.NewInMemoryLaptopStore()
    serverAddress := startTestLaptopServer(t, laptopStore, nil)
    // ...

    other, err := laptopStore.Find(res.Id)
}
```
```go
func startTestLaptopServer(t *testing.T, laptopStore service.LaptopStore, imageStore service.ImageStore) string {
    laptopServer := service.NewLaptopServer(laptopStore, imageStore)
    
    // ...
    
    return listener.Addr().String()
}
```

Similar for the search laptop test. No more errors in this file.

```go
func TestClientSearchLaptop(t *testing.T) {
    // ...
    
    laptopStore := service.NewInMemoryLaptopStore()
    expectedIDs := make(map[string]bool)
    
    for i := 0; i < 6; i++ {
        // ...
    
        err := laptopStore.Save(laptop)
        require.NoError(t, err)
    }
    
    serverAddress := startTestLaptopServer(t, laptopStore, nil)
    // ...
}
```

We do the same for the `laptop_server_test.go` file.

```go
func TestServerCreateLaptop(t *testing.T) {
    // ...
	
    server := service.NewLaptopServer(tc.store, nil)
    res, err := server.CreateLaptop(context.Background(), req)
	// ...
}
```

Finally, in the `laptop_server.go` file we just change the call from `Store` to
`laptopStore` and all errors are gone.

```go
func (server *LaptopServer) CreateLaptop(
	ctx context.Context,
	req *pb.CreateLaptopRequest,
) (*pb.CreateLaptopResponse, error) {
    // ...
	
    // save the laptop to store
    err := server.laptopStore.Save(laptop)
    // ...
}
```

To be sure, I will run unit tests of this package.

```shell
cd service
go test
```

They passed. So we're good. Now in this `main.go` file of the server we also 
need to pass 2 stores into the NewLaptopServer function. One is the laptop
store. And the other is the image store. I will create a new "img" folder to
save the uploaded images.

```go
func main() {
    // ...
	
    laptopStore := service.NewInMemoryLaptopStore()
    imageStore := service.NewDiskImageStore("img")
    
    laptopServer := service.NewLaptopServer(laptopStore, imageStore)
    // ...
}
```

OK, looks like everything is good now. Let's implement the `UploadImage` 
function on the server. First we call `stream.Recv()` to receive the first
request which contains the information of the image. If there's an error, we
write a log and return the status code `Unknown` to the client.

```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
    req, err := stream.Recv()
    if err != nil {
        log.Print("cannot receive image info", err)
        return status.Errorf(codes.Unknown, "cannot receive image info")
    }
    
    return nil
}
```

Actually this looks a bit long and duplicate, so I will define a `logError()` 
function to log the error before returning it. It only prints log if the error
is not `nil`. And always returns the error to the caller.

```go
func logError(err error) error {
    if err != nil {
        log.Print(err)
    }
    return err
}
```

Now with this function, we can simplify the error handling block like this.

```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
    // ...
	
    if err != nil {
        return logError(status.Errorf(codes.Unknown, "cannot receive image info"))
    }
    
    // ...
}
```

If there's no error, we can get the laptop ID from the request. As well as the 
image type. Let's write a log here saying that we have received the 
upload-image request with this laptop ID and image type.

```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
    // ...
	
    laptopID := req.GetInfo().GetLaptopId()
    imageType := req.GetInfo().GetImageType()
    log.Printf("receive an upload-image request for laptop %s with image type %s", laptopID, imageType)
    // ...
}
```

Next we have to make sure that the laptop ID exists. So we call 
`server.laptopStore.Find()` to find the laptop by ID. If we get an error, just 
log and return it with the `Internal` status code. Else if the laptop is `nil`,
which means it not found we log and return an error status code 
`InvalidArgument`. Or you might use code `NotFound` if you want.

```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
    // ...	
    
    laptop, err := server.laptopStore.Find(laptopID)
    if err != nil {
        return logError(status.Errorf(codes.Internal, "cannot find laptop: %v", err))
    }
    if laptop == nil {
        return logError(status.Errorf(codes.InvalidArgument, "laptop %s doesn't exists", laptopID))
    }
    // ...
}
```

Now if everything goes well and the laptop is found, we can start receiving the
image chunks data. So let's create a new byte buffer to store them. And also a 
variable to keep track of the total image size. Since we're going to receive 
many requests from the stream, I will use a `for` loop here. And inside it, 
let's write a log saying we're waiting for chunk data. Similar as before, we 
call `stream.Recv()` to get the request. But this time, we first check if the
error is `EOF` or not. If it is this means that no more data will be sent, and 
we can safely break the loop. Else if the error is still not nil we return it
with `Unknown` status code to the client. Otherwise, if there's no error, we
can get the chunk data from the request. And we get its size using the `len()`
function. We add this size to the total image size.

```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
    // ...

    imageData := bytes.Buffer{}
    imageSize := 0
    
    for {
        log.Print("waiting to receive more data")
    
        req, err := stream.Recv()
        if err == io.EOF {
            log.Print("no more data")
            break
        }
        if err != nil {
            return logError(status.Errorf(codes.Unknown, "cannot receive data: %v", err))
        }
    
        chunk := req.GetChunkData()
        size := len(chunk)
    }

    // ... 
}
```

Let's say we don't want the client to send too large image, so we will check if
image size is greater than the maximum size. I will define a constant for the
max image size of 1 megabyte.

```go
// maximum 1 megabyte
const maxImageSize = 1 << 20
```

Now if this happens, we can return an error with `InvalidArgument` status code
and a message saying the image is too large. Else we can append the chunk to 
the image data with the `Write()` function. Also log and return `Internal` 
status code if an error occurs.

```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
	// ...
	
    for {
        // ...
    
        imageSize += size
        if imageSize > maxImageSize {
            return logError(status.Errorf(codes.InvalidArgument, "image is too large %d > %d", imageSize, maxImageSize))
        }
        
        _, err = imageData.Write(chunk)
        if err != nil {
            return logError(status.Errorf(codes.Internal, "cannot write chunk data: %v", err))
        }
    }
    // ...
}
```

After the `for` loop we have collected all data of the image in the buffer. 
Now we can call `imageStore.Save` to save the image data to the store and get
back the image ID. If there's an error, we log and return it with `Internal` 
status code. If the image is saved successfully, we create a response object 
with the image ID and image size. Then we call `stream.SendAndClose()` to send
the response to client. Return any error that occurs with `Unknown` status 
code. And finally we can write a log saying that the image is successfully 
saved with this ID and size. Then we're done with the server.

```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
	// ...
	
    imageID, err := server.imageStore.Save(laptopID, imageType, imageData)
    if err != nil {
        return logError(status.Errorf(codes.Internal, "cannot save image to the store: %v", err))
    }
    
    res := &pb.UploadImageResponse{
        Id: imageID,
        Size: uint32(imageSize),
    }
    
    err = stream.SendAndClose(res)
    if err != nil {
        return logError(status.Errorf(codes.Unknown, "cannot send response: %v", err))
    }
    
    log.Printf("saved image with id: %s, size: %d", imageID, imageSize)
    // ...
}
```

Now let's implement the client.

## Implement the client
First I will refactor the code a bit. Let's make laptop as a parameter of 
this `createLaptop` function.

`cmd/client/main.go`
```go
func createLaptop(laptopClient pb.LaptopServiceClient, laptop *pb.Laptop) {
    laptop.Id = ""
    req := &pb.CreateLaptopRequest{
        Laptop: laptop,
    }
    
    // ...
}
```

And send a sample laptop to it from outside, like in this `for` loop.

`cmd/client/main.go`
```go
func main() {
	// ...
    for i := 0; i < 10; i++ {
        createLaptop(laptopClient, sample.NewLaptop())
    }
    
    // ...
}
```

Then I'm going to create a separate function for the test search laptop RPC
that we wrote in the last lecture. Let's copy this block of codes

```go
for i := 0; i < 10; i++ {
    createLaptop(laptopClient, sample.NewLaptop())
}

filter := &pb.Filter{
    MaxPriceUsd: 3000,
    MinCpuCores: 4,
    MinCpuGhz:   2.5,
    MinRam:      &pb.Memory{Value: 8, Unit: pb.Memory_GIGABYTE},
}

searchLaptop(laptopClient, filter)
```

And paste it to the function.

```go
func testSearchLaptop(laptopClient pb.LaptopServiceClient) {
    for i := 0; i < 10; i++ {
        createLaptop(laptopClient, sample.NewLaptop())
    }
    
    filter := &pb.Filter{
        MaxPriceUsd: 3000,
        MinCpuCores: 4,
        MinCpuGhz:   2.5,
        MinRam:      &pb.Memory{Value: 8, Unit: pb.Memory_GIGABYTE},
    }
    
    searchLaptop(laptopClient, filter)
}
```

Let's add another function for test create laptop RPC as well.

```go
func testCreateLaptop(laptopClient pb.LaptopServiceClient) {
    createLaptop(laptopClient, sample.NewLaptop())
}
```

OK, now we will write a new function to test the upload image RPC and call it
from the `main` function.

```go
func testUploadImage(laptopClient pb.LaptopServiceClient) {

}
func main() {
    // ...
    
    laptopClient := pb.NewLaptopServiceClient(conn)
    testUploadImage(laptopClient)
}
```

In this `testUploadImage()` function we first generate a random laptop and 
call `createLaptop()` to create it on the server. Then we will write a new
`uploadImage()` function to upload an image of this laptop to the server.

```go
func testUploadImage(laptopClient pb.LaptopServiceClient) {
    laptop := sample.NewLaptop()
    createLaptop(laptopClient, laptop)
    uploadImage(laptopClient, laptop.GetId(), "tmp/laptop.jpg")
}
```

That function will have 3 input parameters: the laptop client, the laptop ID
and the path to the laptop image. First we call `os.Open()` to open the image
file. If there's an error, we write a fatal log. Else we use defer to close
the file afterward. Then we create a context with timeout of 5 seconds, and we 
call `laptopClient.UploadImage()` with that context. It will return a stream 
object and an error. If error is not `nil`, we write a fatal log. Otherwise, we
create the first request to send some image information to the server which
includes the laptop ID and the image type, or the extension of the image file.

```go
func uploadImage(laptopClient pb.LaptopServiceClient, laptopID string, imagePath string) {
    file, err := os.Open(imagePath)
    if err != nil {
        log.Fatal("cannot open image file: ", err)
    }
    defer file.Close()
    
    ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
    defer cancel()
    
    stream, err := laptopClient.UploadImage(ctx)
    if err != nil {
        log.Fatal("cannot upload image", err)
    }
    
    req := &pb.UploadImageRequest{
        Data: &pb.UploadImageRequest_Info{
            Info: &pb.ImageInfo{
                LaptopId:  laptopID,
                ImageType: filepath.Ext(imagePath),
            },
        },
    }
}
```

OK, now we call `stream.Send()` to send the first request to the server. If
we get an error, write a fatal log. Else we will create a buffer reader to 
read the content of the image file in chunks. Let's say each chunk will be 1
kilobyte or 1024 bytes. Now we will read the image data chunks in this `for`
loop. Just call `reader.Read()` to read the data to the buffer. It will return
the number of bytes read and an error. If the error is EOF, then it's the end
of the file. We simply break the loop. Else if error is not nil, we write a
fatal log. Otherwise, we create a new request with the chunk data. Make sure
that the chunk only contains the first `n` bytes of the buffer. Then we call 
`stream.Send()` to send it to the server. Again, write a fatal log here if an
error occurs.

```go
func uploadImage(laptopClient pb.LaptopServiceClient, laptopID string, imagePath string) {
    // ...
	
    err = stream.Send(req)
    if err != nil {
        log.Fatal("cannot send image info: ", err)
    }
    
    reader := bufio.NewReader(file)
    buffer := make([]byte, 1024)
    
    for {
        n, err := reader.Read(buffer)
        if err == io.EOF {
            break
        }
        if err != nil {
            log.Fatal("cannot read chunk to buffer: ", err)
        }
    
        req := &pb.UploadImageRequest{
            Data: &pb.UploadImageRequest_ChunkData{
                ChunkData: buffer[:n],
            },
        }
        
        err = stream.Send(req)
        if err != nil {
            log.Fatal("cannot send chunk to server: ", err)
        }
    }
}
```

Finally, after the `for` loop we call `stream.CloseAndRecv()` to receive a 
response from the server. If there's an error, write a fatal log. Else, we 
write a log saying that the image is successfully uploaded with this ID and 
size. And that's it.

```go
func uploadImage(laptopClient pb.LaptopServiceClient, laptopID string, imagePath string) {
    res, err := stream.CloseAndRecv()
    if err != nil {
        log.Fatal("cannot receive response: ", err)
    }
    
    log.Printf("image uploaded with id: %s, size: %d", res.GetId(), res.GetSize())
}
```

The client is done.

Now let's run the server.

```shell
make server
```

And run the client.
 
```shell
make client
```

There's an error: cannot open image file `laptop.jpg`. It's because I forgot 
to put the file to the `tmp` folder. So let's do that. I have a laptop image 
file in the `Download` folder. I will drag it into the `tmp` folder. OK, the
file is ready. Now let's rerun the client. We got another error:

```shell
2021/04/09 19:05:00 cannot send chunk to server: EOF
```

This error message is not very useful since it doesn't tell us exactly why. So
let's look at the client code. We know that the message comes from this log.

```go
func uploadImage(laptopClient pb.LaptopServiceClient, laptopID string, imagePath string) {
    // ...	

    for {
        // ...
        
        err = stream.Send(req)
        if err != nil {
            log.Fatal("cannot send chunk to server: ", err)
        }
    }

    // ...
}
```

But this error is EOF because when an error occurs, the server will close the
stream, and thus the client cannot send more data to it. To get the real error
that contains the gRPC status code we must call `stream.RecvMsg()` with a `nil`
parameter. Now we can print out this error as well.

```go
func uploadImage(laptopClient pb.LaptopServiceClient, laptopID string, imagePath string) {
    // ...	

    for {
        // ...
        
        err = stream.Send(req)
        if err != nil {
            err2 := stream.RecvMsg(nil)
            log.Fatal("cannot send chunk to server: ", err, err2)
        }
    }

    // ...
}
```

And rerun the client to see what happens.

```shell
2021/04/09 19:14:24 cannot send chunk to server: EOF rpc error: code = InvalidArgument desc = laptop  doesn't exists
```

Now we can see that the real error is `InvalidArgument, laptop doesn't exists`. 
And it is because the laptop ID is empty. OK, now let's simplify this error
log a bit and add it to this log as well.

```go
func uploadImage(laptopClient pb.LaptopServiceClient, laptopID string, imagePath string) {
    // ...
    
    err = stream.Send(req)
    if err != nil {
        log.Fatal("cannot send image info: ", err, stream.RecvMsg(nil))
    }
    
    // ...
    for {
        // ...
        
        err = stream.Send(req)
        if err != nil {
        err2 := stream.RecvMsg(nil)
            log.Fatal("cannot send chunk to server: ", err, err2)
        }
    }
        
    // ...
}
```

The laptop ID is empty because it is set in the `createLaptop()` function. So
let's remove this line.

```go
func createLaptop(laptopClient pb.LaptopServiceClient, laptop *pb.Laptop) {
    laptop.Id = "" // remove this line
    req := &pb.CreateLaptopRequest{
        Laptop: laptop,
    }
    
    // ...
}
```

And rerun the client. This time it works.

```shell
2021/04/09 19:26:13 image uploaded with id: f3b2a396-2765-475f-9d28-f5e8d9614093, size: 87635
```

The image is uploaded successfully. On the server side, we see a bunch of 
logs: waiting to receive more data. It doesn't look very nice, so let's write
one more line of log here, saying that we have received a new chunk data with
this size.

`service/laptop_server.go`
```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
    // ...
    
    for {
        // ...
        
        chunk := req.GetChunkData()
        size := len(chunk)
    
        log.Printf("received a chunk with size: %d", size)
        
        imageSize += size
        if imageSize > maxImageSize {
            return logError(status.Errorf(codes.InvalidArgument, "image is too large %d > %d", imageSize, maxImageSize))
        }
        
        // ...
    }
    
    // ...
}
```

Alright, now if we open the `img` folder, we can see the laptop image is saved 
there. Excellent! OK, now let's see what happens if timeout occurs. Suppose
that somehow the server is writing the data very slowly. Here I sleep 1 second 
before writing the chunk to the buffer.

```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
    // ...
    
    for {
        // ...
        
        imageSize += size
        if imageSize > maxImageSize {
            return logError(status.Errorf(codes.InvalidArgument, "image is too large %d > %d", imageSize, maxImageSize))
        }

        // write slowly
        time.Sleep(time.Second)
        
        // ...
    }
    
    // ...
}
```

OK, let's try it. Run the server and run the client. After 5 seconds, we see
an error log on the server.

```shell
2021/04/09 19:39:52 rpc error: code = Unknown desc = cannot receive data: rpc error: code = DeadlineExceeded desc = context deadline exceeded
```

However, the status code is `Unknown` and it also contains other 
`DeadlineExceeded` error, which is not very nice. So let's fix this by 
checking the context error before calling receive on the stream. I will 
extract this context error checking block from the `CreateLaptop` RPC, and make 
it a separate function. Let's use switch case here to make it more concise and
easier to read. In case the context error is `Cancelled`, we log it and return 
the error. In case `DeadlineExceeded`, we do the same. And for default case,
just return `nil`.

```go
func (server *LaptopServer) CreateLaptop(
    ctx context.Context,
    req *pb.CreateLaptopRequest,
) (*pb.CreateLaptopResponse, error) {
	// ...
	
    // some heavy processing
    // time.Sleep(6 * time.Second)
    
    if err := contextError(ctx); err != nil {
        return nil, err
    }
    
    // ...
}

func contextError(ctx context.Context) error {
    switch ctx.Err() {
    case context.Canceled:
        return logError(status.Error(codes.Canceled, "request is cancelled"))
    case context.DeadlineExceeded:
        return logError(status.Error(codes.DeadlineExceeded, "deadline is exceeded"))
    default:
        return nil
    }
}
```

OK now go back to our `for` loop. Here we call the `contextError()` function 
with the stream context. If error is not `nil`, we return it immediately.

```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
    // ...
    
    for {
        // check context error
        if err := contextError(stream.Context()); err != nil {
            return err
        }
        
        // ...
    }
    
    // ...
}
```

Alright, let's run the server and run the client. Now on the server side, we 
see a better error log with status code `DeadlineExceeded`.

```shell
2021/04/09 20:13:37 rpc error: code = DeadlineExceeded desc = deadline is exceeded
```

Perfect! Let's try another case where the upload image is larger than the 
maximum allowed size. I will change this constant to 1 kilobyte instead of 1
megabyte.

```go
// maximum 1 kilobyte
const maxImageSize = 1 << 10
```

Then rerun the server and the client. This time we got `InvalidArgument`: image
is too large. On the server side, it only receives 2 data chunks before the 
same error log is printed. So it works! I'm gonna undo this change to make it
1 megabyte as before. And also comment out this `time.Sleep` statement.

```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
    // ...
    
    for {
        // ...
        
        imageSize += size
        if imageSize > maxImageSize {
            return logError(status.Errorf(codes.InvalidArgument, "image is too large %d > %d", imageSize, maxImageSize))
        }

        // write slowly
        // time.Sleep(time.Second)
        
        // ...
    }
    
    // ...
}
```

OK, now let's learn how to write test for this client-streaming RPC.

## Write unit test
Function `TestClientUploadImage`. For this test, I'm gonna use tmp as the 
image folder. The first thing we need to do is to create a new in-memory laptop
store. And create a new disk image store with `tmp` image folder. We generate 
a sample laptop and save it to the laptop store.

```go
func TestClientUploadImage(t *testing.T) {
    t.Parallel()
    
    testImageFolder := "../tmp"
    
    laptopStore := service.NewInMemoryLaptopStore()
    imageStore := service.NewDiskImageStore(testImageFolder)
    
    laptop := sample.NewLaptop()
    err := laptopStore.Save(laptop)
    require.NoError(t, err)
}
```

Then we start the test server. And make a new client. The image we're gonna
upload is the `laptop.jpg` file inside the `tmp` folder. So let's open the 
file, check that there's no error and `defer` closing the file. Then we call
`laptopClient.UploadImage` to get the stream. Now we get the image type from 
the file extension.

```go
func TestClientUploadImage(t *testing.T) {
    // ...
    
    serverAddress := startTestLaptopServer(t, laptopStore, imageStore)
    laptopClient := newTestLaptopClient(t, serverAddress)
    
    imagePath := fmt.Sprintf("%s/laptop.jpg", testImageFolder)
    file, err := os.Open(imagePath)
    require.NoError(t, err)
    defer file.Close()
    
    stream, err := laptopClient.UploadImage(context.Background())
    require.NoError(t, err)
    
    imageType := filepath.Ext(imagePath)
}
```

Actually the rest of the test is very similar to what we've done in the 
client `main.go` file. So I'm just gonna do a copy and paste to save time. 
OK, this `laptopID` should be changed to `laptop.GetId()` and this image type 
should be just `imageType`. We replace error checking block with 
`require.NoError()`. The same for error in the loop. We also want to keep 
track of the total image size so let's define a `size` variable here. And add 
`n` to the size here. Replace remaining error checking block in the loop 
with `require.NoError()` and the same for last one.

```go
func TestClientUploadImage(t *testing.T) {
    // ...
    
    imageType := filepath.Ext(imagePath)
    req := &pb.UploadImageRequest{
        Data: &pb.UploadImageRequest_Info{
            Info: &pb.ImageInfo{
                LaptopId: laptop.GetId(),
                ImageType: imageType,
            },
        },
    }
    
    err = stream.Send(req)
    require.NoError(t, err)
    
    reader := bufio.NewReader(file)
    buffer := make([]byte, 1024)
    size := 0
    
    for {
        n, err := reader.Read(buffer)
        if err == io.EOF {
            break
        }
        require.NoError(t, err)
        size += n
    
        req := &pb.UploadImageRequest{
            Data: &pb.UploadImageRequest_ChunkData{
                ChunkData: buffer[:n],
            },
        }
    
        err = stream.Send(req)
        require.NoError(t, err)
    }
    
    res, err := stream.CloseAndRecv()
    require.NoError(t, err)
}
```

Now we check that the returned ID should not be a zero-value, and the value of
the returned image size should equal to `size`. We also want to check that the 
image is saved to the correct folder on the server. It should be inside the
test image folder. With file name is the image ID and file extension is the 
image type. We can use `require.FileExists()` function to check that. And 
finally we need to remove the file at the end of the test.

```go
func TestClientUploadImage(t *testing.T) {
    // ...
	
    res, err := stream.CloseAndRecv()
    require.NoError(t, err)
    require.NotZero(t, res.GetId())
    require.EqualValues(t, size, res.GetSize())
    
    savedImagePath := fmt.Sprintf("%s/%s%s", testImageFolder, res.GetId(), imageType)
    require.FileExists(t, savedImagePath)
    require.NoError(t, os.Remove(savedImagePath))
}
```

Alright, let's run it. It passed! Let's run the whole test sets.

```shell
make test
```

Excellent! All tests passed!

And that's it for today's lecture about client-streaming RPC. In the next 
lecture, we will learn how to implement it in Java. Thank you for reading and
I will see you later.