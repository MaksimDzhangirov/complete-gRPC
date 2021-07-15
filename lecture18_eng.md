# Generate RESTful service and swagger documentation with gRPC gateway
Hi, welcome back! It's been a long journey with gRPC, and we've learned so much
about amazing features of it. However, we all know that gRPC is not a tool for
everything. There are cases where we still want to provide a traditional 
RESTful JSON API. The reasons can range from maintaining 
backwards-compatibility, to supporting programming languages or clients not 
well supported by gRPC. But coding another API for REST is quite time consuming
and tedious. So is there any way to code just once, but can provide APIs in
both gRPC and REST at the same time? The answer is yes.

## Install and config gRPC gateway
One way to achieve that is to use gRPC gateway. gRPC gateway is a plugin of 
the protocol buffer compiler. It reads the protobuf service definitions and 
generates a proxy server, which translates a RESTful HTTP call into gRPC 
request. All we need to do is a small amount of configuration in the service. 
And that's what we will learn in this lecture. This is the [github page](https://github.com/grpc-ecosystem/grpc-gateway) 
of gRPC gateway. I'm gonna use its latest version, which is version 2. You can
read its documentation in [this page]((https://grpc-ecosystem.github.io/grpc-gateway/)).
Before start coding, we have to install some tools. First, the 
`protoc-gen-grpc-gateway`. We copy this github link, and run

```shell
go get -u github.com/grpc-ecosystem/grpc-gateway/v2/protoc-gen-grpc-gateway
```

in the terminal. One cool thing about gRPC gateway is, it also generate 
swagger files for us to create API documentation. So let's install this 
`protoc-gen-openapiv2` tool as well.

```shell
go get -u github.com/grpc-ecosystem/grpc-gateway/v2/protoc-gen-openapiv2
```

Alright, now if we look at the `go/bin` folder, we can see the 
`protoc-gen-openapiv2` and `protoc-gen-grpc-gateway` executable binary. The
next thing we need to do is to add a `google.api.http` annotation to the proto
files. There are a lot of thing we can config. You can check out this 
[a_bit_of_everything.proto](https://github.com/grpc-ecosystem/grpc-gateway/blob/master/examples/internal/proto/examplepb/a_bit_of_everything.proto)
file to read more about them. For now, I will show you the most basic 
configuration. To have the google api http annotation, we have to copy the
third party protobuf files to our project. These can be found by manually 
cloning and copying the relevant files from the [googleapis repository](https://github.com/googleapis/googleapis).
Inside it, there's a `google` folder. The files you will need are:

```shell
google/api/annotations.proto
google/api/field_behaviour.proto
google/api/http.proto
google/api/httpbody.proto
```

Let's copy it to our `pcbook` golang project. With this in place, we can now
add annotation to our service proto.

## Add annotation to proto files
Let's start with the `auth_service.proto`. First we need to import 
`google/api/annotations.proto`. Then inside the Login RPC, we add this option
`google.api.http`. We declare the route with method POST, and the path is
`v1/auth/login`. Since this is a POST request, it should have a `body` so we 
add body star here.

```protobuf
// ...

import "google/api/annotations.proto";

// ...

service AuthService {
  rpc Login(LoginRequest) returns (LoginResponse) {
    option (google.api.http) = {
      post : "/v1/auth/login"
      body : "*"
    };
  };
}
```

Alright, now let's do similar thing for the `laptop_service.proto` file. First
import google api annotation, then add the `google.api.http` option to the 
`CreateLaptop` RPC. It's also a POST request, but the path should be different.
I'm gonna use `/v1/laptop/create`. Next, the search laptop RPC will be a GET
request, and the path is `v1/laptop/search`. Similarly, the upload image RPC
will be POST `/v1/laptop/upload_image` and finally the rate laptop RPC will
be POST `/v1/laptop/rate`.

```protobuf
// ...

import "google/api/annotations.proto";

// ...

service LaptopService {
  rpc CreateLaptop(CreateLaptopRequest) returns (CreateLaptopResponse) {
    option (google.api.http) = {
      post : "/v1/laptop/create"
      body : "*"
    };
  };
  rpc SearchLaptop(SearchLaptopRequest) returns (stream SearchLaptopResponse) {
    option (google.api.http) = {
      get  : "/v1/laptop/search"
    };
  };
  rpc UploadImage(stream UploadImageRequest) returns (UploadImageResponse) {
    option (google.api.http) = {
      post : "/v1/laptop/search"
      body : "*"
    };
  };
  rpc RateLaptop(stream RateLaptopRequest) returns (stream RateLaptopResponse) {
    option (google.api.http) = {
      post : "/v1/laptop/rate"
      body : "*"
    };
  };
}
```

## Generate gRPC gateway and swagger files
OK, the proto files are updated, now we have to update our `make gen` command
to instruct `protoc` to generate grpc gateway and swagger files for us. We use
this `grpc-gateway_out` option to tell `protoc` to generate codes to the `pb`
folder which is the same folder that our gRPC codes will be generated to. Then
we use the `openapiv2_out` option to tell `protoc` to generate swagger files
to the `openapiv2` folder.

```makefile
gen:
	protoc --proto_path=proto --go_out=pb --go-grpc_out=pb --grpc-gateway_out ./pb --openapiv2_out ./openapiv2  proto/*.proto
# ...
```

Let's create that `openapiv2` folder at the root of our `pcbook` project. OK,
now let's open the terminal and run

```shell
make gen
```

to generate the codes.

As you can see in `pb` folder, 2 new files has been generated: first the 
`auth_service.pb.gw.go` file for the authentication service. It has a function
to handle the POST login request. Note that this 
`RegisterAuthServiceHandlerServer` function is used for in-process translation
from REST to gRPC. It means that we don't need to run a separate gRPC server
to serve requests from REST server via network call. Unfortunately, at the 
moment in-process translation only supports unary RPC. For streaming RPC,
we must use `RegisterAuthServiceHandlerFromEndpoint` function which will 
translate the incoming RESTful requests into gRPC format, and call the 
corresponding RPC on the specified endpoint. The content of the 
`laptop_service.pb.gw.go` file is similar, you can check it out by youself if
you want. Now, in the `openapiv2` folder, there are many generated swagger 
files. But we only care about the `auth_service.swagger.json` and the 
`laptop_service.swagger.json` files. These files are very useful for us to 
create API documentation. We can easily do that by going to `swagger.io`, 
`Login`, then click `Create New` and choose `Import and Document API`, click
`Browse` and select the `auth_service.swagger.json` file and click `Upload`.
Enter the name and version for our API. Looks like it doesn't allow space in
the name so let's change name to `pcbook-auth-service` and click 
`Import OpenAPI`. Voila, we have a beautiful API documentation for our auth 
service. Inspect the login route, the request body is a JSON with username and
password. The successful response will have status code 200 and a JSON body 
with the access token or the error response will contain these fields in the
body.

```json
{
  "error": "string",
  "code": 0,
  "message": "string",
  "details": [
    {
      "type_url": "string",
      "value": "string"
    }
  ]
}
```

It looks great! So let's upload the swagger file of the laptop service as 
well. Select `laptop_service.swagger.json` file, click `Upload File`. Then
change the name to `pcbook-laptop-service`, update version to 1.0 and click 
`Import OpenAPI`. Cool, we have the documentation with 4 routes: create, rate,
search laptop and upload image. This create laptop API is a `POST` request with
a very long JSON body. Then the rate laptop API is also a `POST`, but the request
body is a stream input. The search laptops uses method `GET`. As you can see, 
all filtering conditions are presented here as query parameters. Alright, we
will come back to this later.

## Implementing the REST server
Now let's get back to the code to implement the REST server. First I will 
refactor the existing code of the gRPC server a bit. Let's define a function
to run gRPC server. Then move block of code to that function. The function
will need several input parameters: first the auth service server, the laptop
service server, the JWT manager, whether we want to enable TLS or not, and 
finally a `net.Listener` object. Note this `enableTLS` variable is not a 
pointer anymore and we don't need to write a fatal log, but we can just wrap
the error and return it. Finally, we just return `grpcServer.Serve(listener)`.
And the `runGRPCServer` function is done.

`cmd/server/main.go`
```go
func runGRPCServer(
    authServer pb.AuthServiceServer,
    laptopServer pb.LaptopServiceServer,
    jwtManager *service.JWTManager,
    enableTLS bool,
    listener net.Listener,
) error {
    interceptor := service.NewAuthInterceptor(jwtManager, accessibleRoles())
    serverOptions := []grpc.ServerOption{
        grpc.UnaryInterceptor(interceptor.Unary()),
        grpc.StreamInterceptor(interceptor.Stream()),
    }
    
    if enableTLS {
        tlsCredentials, err := loadTLSCredentials()
        if err != nil {
            return fmt.Errorf("cannot load TLS credentials: %w", err)
        }
    
        serverOptions = append(serverOptions, grpc.Creds(tlsCredentials))
    }
    
    grpcServer := grpc.NewServer(serverOptions...)
    
    pb.RegisterAuthServiceServer(grpcServer, authServer)
    pb.RegisterLaptopServiceServer(grpcServer, laptopServer)
    reflection.Register(grpcServer)
    
    return grpcServer.Serve(listener)
}
```

In the `main` function we just call `runGRPCServer` and pass in all required
arguments, and check the returned error. If it is not `nil`, we write a fatal
log.

```go
func main() {
    // ...
    
    laptopStore := service.NewInMemoryLaptopStore()
    imageStore := service.NewDiskImageStore("img")
    ratingStore := service.NewInMemoryRatingStore()
    laptopServer := service.NewLaptopServer(laptopStore, imageStore, ratingStore)
    
    address := fmt.Sprintf("0.0.0.0:%d", *port)
    listener, err := net.Listen("tcp", address)
    if err != nil {
        log.Fatal("cannot start server: ", err)
    }
    
    err = runGRPCServer(authServer, laptopServer, jwtManager, *enableTLS, listener)
    if err != nil {
        log.Fatal("cannot start server: ", err)
    }
}
```

Now the REST server is gonna need similar input arguments, so I will just 
duplicate `runGRPCServer` function signature and change the function name to
`runRESTServer`. First we call `runtime.NewServerMux()` to create a new HTTP 
request multiplexer. You should check to make sure that the correct package is
imported. It should be `grpc-gateway/v2/runtime` package. OK, then we create
a new context with cancel, call `defer cancel` to avoid leaking memory. Now 
let's start with the in-process translation from REST to gRPC, we call 
`pb.RegisterAuthServiceHandlerServer()` function, pass in the context, the 
multiplexer, and the auth service server object. If error is not `nil`, return 
it. Let's do the same thing to register the laptop service server. Then let's
write a log here. Saying we're starting a REST server at this address with 
this TLS option.

```go
log.Printf("start REST server on port %d, TLS = %t", listener.Addr().String, enableTLS)
```

I'm gonna add this log to the `runGRPCServer()` as well, and change the 
message from REST to GRPC.

```go
func runGRPCServer(
    authServer pb.AuthServiceServer,
    laptopServer pb.LaptopServiceServer,
    jwtManager *service.JWTManager,
    enableTLS bool,
    listener net.Listener,
) error {
    // ...
    
    log.Printf("start GRPC server on port %s, TLS = %t", listener.Addr().String(), enableTLS)
	return grpcServer.Serve(listener)
}
```

OK, now we check if TLS is enable, then to start the REST server, we call 
`http.ServeTLS` with the `listener` and multiplexer. We also need to pass in the
path to server's certificate and private key file. So I will go to the 
`loadTLSCredentials` to get them. Let's define a constant for the server's 
certificate file. Then another constant for the server's private key file, and 
a constant for the client CA's certificate file, too to be consistent.

```go
const (
    serverCertFile   = "cert/server-cert.pem"
    serverKeyFile    = "cert/server-key.pem"
    clientCACertFile = "cert/ca-cert.pem"
)

func loadTLSCredentials() (credentials.TransportCredentials, error) {
    // Load certificate of the CA who signed server's certificate
    pemClientCA, err := ioutil.ReadFile(clientCACertFile)
    // ...
    
    // Load server's certificate and private key
    serverCert, err := tls.LoadX509KeyPair(serverCertFile, serverKeyFile)
    if err != nil {
        return nil, err
    }
    
    // ...
}
```

OK, get back to our `runRESTServer` function. We can now pass the server 
certificate and private key file to this function. If TLS is not enabled, we
simply call `http.Serve()` with the `listener` and multiplexer. And that's it!

```go
func runRESTServer(
    authServer pb.AuthServiceServer,
    laptopServer pb.LaptopServiceServer,
    jwtManager *service.JWTManager,
    enableTLS bool,
    listener net.Listener,
) error {
    mux := runtime.NewServeMux()
    ctx, cancel := context.WithCancel(context.Background())
    defer cancel()
    
    // in-process handler
    err := pb.RegisterAuthServiceHandlerServer(ctx, mux, authServer)
    if err != nil {
        return err
    }
    
    err = pb.RegisterLaptopServiceHandlerServer(ctx, mux, laptopServer)
    if err != nil {
        return err
    }
    
    log.Printf("start REST server on port %s, TLS = %t", listener.Addr().String(), enableTLS)
    if enableTLS {
        return http.ServeTLS(listener, mux, serverCertFile, serverKeyFile)
    }
    return http.Serve(listener, mux)
}
```

The REST server is ready.

## Test REST server using Postman
Now in the `main` function let's add 1 more flag to get the server type from 
command line argument. It can either be `grpc` or `rest` and default value is 
`grpc`. Then we check if the server type is `grpc` then we call the 
`runGRPCServer()` function else we call the `runRESTServer()` function.

```go
func main() {
    // ...
    serverType := flag.String("type", "grpc", "type of server (grpc/rest")
    
    // ...

    if *serverType == "grpc" {
        err = runGRPCServer(authServer, laptopServer, jwtManager, *enableTLS, listener)
    } else {
        err = runRESTServer(authServer, laptopServer, jwtManager, *enableTLS, listener)
    }
    
    // ...
}
```

Alright, to test this server we should add 1 more command to the `Makefile`. 
This command will start the REST server. So let's call it `make rest`. And I 
will use a different port for it. Let's say `8081`.

```makefile
rest:
	go run cmd/server/main.go -port 8081 -type rest
```

OK, now let's run 

```shell
make rest
```

in the terminal.

As you can see, the REST server is started on port 8081. Now let's open our
swagger page of the authentication service and copy login path. I will use
Postman to test the API. Click `+` button to create a new request, change 
the method to `POST`, paste in the login path, and set the root URL to 
`http://localhost:8081/v1/auth/login`. This is a JSON request. So in the `body` 
tab, we choose `raw`, then type `JSON`. Let's go back to swagger page and copy
example body string.

```json
{
    "username": "string",
    "password": "string"
}
```

Paste it in the form. We can get the `username` and `password` from the code.
I'm gonna use the `admin1` user, password `secret`. Then click "Send".

```json
{
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MjA5MTE3NDMsInVzZXJuYW1lIjoiYWRtaW4xIiwicm9sZSI6ImFkbWluIn0.LQfZIHHgXemWP4oP4Eg2PBbAdA3qjmnP-iI05Vqk2ig"
}
```

Yee, we've got `200 OK` status code, and the access token in the response body. 
So it works! If we change the username to `admin2`, for example, and click 
`Send` again, we will get `400 Not Found` status code, and an error: "incorrect
username/password" in the body. 

```json
{
    "code": 5,
    "message": "incorrect username/password",
    "details": []
}
```

Now keep in mind that only the REST server is running since we're using 
in-process translation. And because of this, it only works for unary request.
Let's see what happen if we try to call a streaming request. I'm gonna open
the swagger page of the laptop service. And copy the search laptop request 
path `/v1/laptop/search`. To remind, this is originally a server streaming RPC.
So let's paste it to Postman. OK, now the filtering parameters. First the 
max price 5000, number of CPU cores: 2 cores, min CPU frequency 2.0 GHz, min
RAM value 3, min RAM unit: GIGABYTE. Now click "Send".

```
filter.max_price_usd    5000
filter.min_cpu_cores    2
filter.min_cpu_ghz      2.0
filter.min_ram_value    3
filter.min_ram_unit     GIGABYTE
```

This time we've got 501 Not Implemented, and the error message saying 
streaming calls are not yet supported in the in-process transport.

```json
{
    "code": 12,
    "message": "streaming calls are not yet supported in the in-process transport",
    "details": []
}
```

If we open the generated codes, there's a link to an issue on the grpc-gateway
repository to request for streaming support in the in-process transport. 
However, typically we just use 1 request-response mechanism in a normal 
RESTful call, so it makes sense to just convert unary RPC to REST. But if we
really want to convert streaming RPCs, we can also do that by using gRPC 
gateway as a proxy. The `RegisterAuthServiceHandlerFromEndpoint` is used for
this purpose. So let's update our REST server code a little bit. I will 
comment out this in-process call.

```go
func runRESTServer(
    authServer pb.AuthServiceServer,
    laptopServer pb.LaptopServiceServer,
    jwtManager *service.JWTManager,
    enableTLS bool,
    listener net.Listener,
) error {
    // ...
	
    // in-process handler
    // err := pb.RegisterAuthServiceHandlerServer(ctx, mux, authServer)
	
    // ...
}
```

And replace it with `RegisterAuthServiceHandlerFromEndpoint()`. We need to 
pass in an endpoint of the gRPC server so let's define it as an input 
parameter of `runRESTServer()` function. Since this is a network call, we also
have to provide a `dialOptions` object. Let's declare this gRPC dial options
at the beginning of `runRESTServer()` function. To be simple, in this tutorial,
I'm just gonna use `grpc.WithInsecure()`. OK, we have to do the same thing to 
register laptop service handler.

```go
func runRESTServer(
    authServer pb.AuthServiceServer,
    laptopServer pb.LaptopServiceServer,
    jwtManager *service.JWTManager,
    enableTLS bool,
    listener net.Listener,
    grpcEndpoint string,
) error {
    mux := runtime.NewServeMux()
    dialOptions := []grpc.DialOption{grpc.WithInsecure()}
    // ...
    
    // in-process handler
    // err := pb.RegisterAuthServiceHandlerServer(ctx, mux, authServer)
    err := pb.RegisterAuthServiceHandlerFromEndpoint(ctx, mux, grpcEndpoint, dialOptions)
    if err != nil {
        return err
    }
    
    // err = pb.RegisterLaptopServiceHandlerServer(ctx, mux, laptopServer)
    err = pb.RegisterLaptopServiceHandlerFromEndpoint(ctx, mux, grpcEndpoint, dialOptions)
    if err != nil {
        return err
    }
    
    // ...
}
```

Then in the `main` function, let's add a new command line argument for the 
gRPC endpoint and pass it in the `runRESTServer()` function call. Finally, we
have to update our `make rest` command to pass in the address of the gRPC 
server, which should be at local host port 8080.

```makefile
rest:
	go run cmd/server/main.go -port 8081 -type rest -endpoint 0.0.0.0:8080
```

OK, let's test it! First we run

```shell
make server
```

to start the gRPC server on port 8080. Then on another tab, we run 

```shell
make rest
```

to start the REST server on port 8081.

Now go back to Postman and send the search laptop request. This time we got
200 OK status code. The body is empty because we haven't created any laptops
yet. So let's open another terminal tab and run

```shell
make client
```

Now 3 laptops are created. Let's send the search laptop again.

```json
{
    "result": {
        "laptop": {
            "id": "aaa8e8aa-c172-4afb-a726-95eb93bfef84",
            "brand": "Lenovo",
            "name": "Thinkpad P1",
            "cpu": {
                "brand": "Intel",
                "name": "Xeon-E-2286M",
                "numberCores": 2,
                "numberThreads": 2,
                "minGhz": 3.4397298380458095,
                "maxGhz": 3.7294466666413864
            },
            "ram": {
                "value": "23",
                "unit": "GIGABYTE"
            },
            "gpus": [
                {
                    "brand": "NVIDIA",
                    "name": "RTX 2070",
                    "minGhz": 1.2799156633669841,
                    "maxGhz": 1.9953732411161396,
                    "memory": {
                        "value": "2",
                        "unit": "GIGABYTE"
                    }
                }
            ],
            "storages": [
                {
                    "driver": "SSD",
                    "memory": {
                        "value": "864",
                        "unit": "GIGABYTE"
                    }
                },
                {
                    "driver": "HDD",
                    "memory": {
                        "value": "3",
                        "unit": "TERABYTE"
                    }
                }
            ],
            "screen": {
                "sizeInch": 13.702278,
                "resolution": {
                    "width": 7616,
                    "height": 4284
                },
                "panel": "IPS",
                "multitouch": true
            },
            "keyboard": {
                "layout": "QWERTY",
                "backlit": false
            },
            "weightKg": 1.8507416377914885,
            "priceUsd": 2133.7980430974994,
            "releaseYear": 2019,
            "updatedAt": "2021-05-14T07:06:28.129475620Z"
        }
    }
}
```
```json
{
    "result": {
        "laptop": {
            "id": "cbb0777a-fe62-4d16-95c6-e1634345c01b",
            "brand": "Lenovo",
            "name": "Thinkpad X1",
            "cpu": {
                "brand": "Intel",
                "name": "Xeon-E-2286M",
                "numberCores": 3,
                "numberThreads": 11,
                "minGhz": 2.220573657654501,
                "maxGhz": 2.62113176063615
            },
            "ram": {
                "value": "22",
                "unit": "GIGABYTE"
            },
            "gpus": [
                {
                    "brand": "NVIDIA",
                    "name": "RTX 2070",
                    "minGhz": 1.1780559656007965,
                    "maxGhz": 1.4235571769719728,
                    "memory": {
                        "value": "4",
                        "unit": "GIGABYTE"
                    }
                }
            ],
            "storages": [
                {
                    "driver": "SSD",
                    "memory": {
                        "value": "247",
                        "unit": "GIGABYTE"
                    }
                },
                {
                    "driver": "HDD",
                    "memory": {
                        "value": "1",
                        "unit": "TERABYTE"
                    }
                }
            ],
            "screen": {
                "sizeInch": 16.78862,
                "resolution": {
                    "width": 2634,
                    "height": 1482
                },
                "panel": "OLED",
                "multitouch": true
            },
            "keyboard": {
                "layout": "QWERTY",
                "backlit": true
            },
            "weightKg": 1.926311921250373,
            "priceUsd": 2600.928311353924,
            "releaseYear": 2018,
            "updatedAt": "2021-05-14T07:06:28.130963792Z"
        }
    }
}
```
```json
{
    "result": {
        "laptop": {
            "id": "ec2364f3-0805-4f84-b8df-e58dc3bc09de",
            "brand": "Lenovo",
            "name": "Thinkpad X1",
            "cpu": {
                "brand": "AMD",
                "name": "Ryzen 7 PRO 2700U",
                "numberCores": 7,
                "numberThreads": 12,
                "minGhz": 2.9806856701285236,
                "maxGhz": 4.919253043897466
            },
            "ram": {
                "value": "39",
                "unit": "GIGABYTE"
            },
            "gpus": [
                {
                    "brand": "NVIDIA",
                    "name": "GTX 1660-Ti",
                    "minGhz": 1.1418453526952794,
                    "maxGhz": 1.6620512959451756,
                    "memory": {
                        "value": "2",
                        "unit": "GIGABYTE"
                    }
                }
            ],
            "storages": [
                {
                    "driver": "SSD",
                    "memory": {
                        "value": "669",
                        "unit": "GIGABYTE"
                    }
                },
                {
                    "driver": "HDD",
                    "memory": {
                        "value": "1",
                        "unit": "TERABYTE"
                    }
                }
            ],
            "screen": {
                "sizeInch": 14.140779,
                "resolution": {
                    "width": 5676,
                    "height": 3193
                },
                "panel": "OLED",
                "multitouch": true
            },
            "keyboard": {
                "layout": "AZERTY",
                "backlit": true
            },
            "weightKg": 2.0491629125076383,
            "priceUsd": 1577.7405639846068,
            "releaseYear": 2019,
            "updatedAt": "2021-05-14T07:06:28.131226058Z"
        }
    }
}
```

Here we go. Some laptops are found. As you can see, they're 3 separate JSON
object, not an array. The reason is because this is a streaming result, so the
server sends the JSON body as a stream of multiple separate JSON objects.

And that's all I want to share with you in this lecture. You can try to play 
around with other types of gRPC such as client streaming or bidirectional 
streaming if you like. I hope you find gRPC gateway interesting and useful. 
Thanks a lot for reading, see you guys in the next lectures!