# gRPC reflection and Evans CLI
Hello everyone! Welcome back to the gRPC courses. In this lecture, we will
learn about gRPC reflection. And how to use Evans client to play with it. 
gRPC reflection is an optional extension for the server to assist clients to
construct requests without having to generate stubs beforehand. This is very 
useful for the clients to explore the gRPC API before actually going into 
implementation.

## Enable gRPC reflection on the server

### Golang
Let's start by adding gRPC reflection to the Golang server. Open this page 
`https://github.com/grpc/grpc-go/blob/master/Documentation/server-reflection-tutorial.md`
in your browser. As you can see there, it's super simple. We just need to 
import the reflection package and call `reflection.Register`. So let's do it! 
In our `server/main.go` file after register the laptop service, we call 
`reflection.Register` and pass in the gRPC server and that's it.

```go
func main() {
    // ...
    
    grpcServer := grpc.NewServer()
    pb.RegisterLaptopServiceServer(grpcServer, laptopServer)
    reflection.Register(grpcServer)
    
    // ...
}
```

### Java
How about Java? It's also pretty straightforward. We have to add the gRPC 
service dependency to our project and then add the `ProtoReflectionService` to
the server. Let's go to `maven` repository and search for gRPC service. Let's 
copy this Gradle setting

```
// https://mvnrepository.com/artifact/io.grpc/grpc-services
implementation group: 'io.grpc', name: 'grpc-services', version: '1.37.0'
```

and paste it to the `build.gradle` file. Wait a bit for IntelliJ IDEA to 
update. Then we go to `LaptopServer.java` file. In this constructor `public 
LaptopServer(ServerBuilder serverBuilder, int port, LaptopStore laptopStore, 
ImageStore imageStore, RatingStore ratingStore)` after 
adding the laptop service to the server we just add a new instance of the 
`ProtoReflectionService`. And voila, the gRPC reflection is enabled on the 
server.

```java
public class LaptopServer {
    // ...

    public LaptopServer(ServerBuilder serverBuilder, int port, LaptopStore laptopStore, ImageStore imageStore, RatingStore ratingStore) {
        // ...
        server = serverBuilder.addService(laptopService)
                .addService(ProtoReflectionService.newInstance())
                .build();
    }
}
```

## Install Evans client
Next step, we will install the Evans client to play around with the server
reflection. Open `https://github.com/ktr0731/evans` in your browser. Evans is 
a super cool gRPC client that allows you to construct and send requests to 
gRPC server in an interactive shell. There are several ways to install it as
described in this [Github](https://github.com/ktr0731/evans) page. I'm on a 
mac, so I will install it by Homebrew. First use

```shell
brew tap ktr0731/evans
```

to add this repository to Homebrew. Then 

```shell
brew install evans.
```

## Use Evans CLI with gRPC reflection
Ok Evans is ready. Now let's start the Golang gRPC server. Since gRPC 
reflection is enabled on our server, we will run Evans with `-r (--reflection)` 
option. So let's copy this command

```shell
evans -r repl
```

and run it in the terminal. We also need to pass in the port 8080 of the 
server

```shell
evans -r repl -p 8080
```

Then here we are, inside the Evans interactive shell.

## Show and Describe commands
We can call `show package` to see all packages available on the server.

```shell
show package
```

Then use the `package` command to choose a specific package.

```shell
package techschool_pcbook
```

We can show all services of that package

```shell
show service
```

or show all messages as well.

```shell
show message
```

We can use the `describe` command to get the message format.

```shell
desc CreateLaptopRequest
```

## Test the create laptop RPC
Now let's choose the `LaptopService` and call the `CreateLaptop` API.

```shell
service LaptopService
```

```shell
call CreateLaptop
```

Evans will ask us to provide the data to construct a request. The first field
is ID, which we can leave empty. Then the laptop brand, let's say `Apple`, the
laptop name: `Macbook Pro`, the CPU brand: `Intel`, the CPU name: let's use 
`Core i9`, the number of cores is `8`, the number of threads is `16`, min 
frequency is `2.5` GHz, max frequency is `4.5` GHz.

```shell
laptop::id (TYPE_STRING) => 
laptop::brand (TYPE_STRING) => Apple
laptop::name (TYPE_STRING) => Macbook Pro
laptop::cpu::brand (TYPE_STRING) => Intel
laptop::cpu::name (TYPE_STRING) => Core i9
laptop::cpu::number_cores (TYPE_UINT32) => 8
laptop::cpu::number_threads (TYPE_UINT32) => 16
laptop::cpu::min_ghz (TYPE_DOUBLE) => 2.5
laptop::cpu::max_ghz (TYPE_DOUBLE) => 4.5
```

Next will be the RAM. Let's say it will be 32 GB. Evans allows us to select 
the `enum` value from the list. So it's very comfortable to use. Now the GPU. 
Let's say it's a `NVIDIA GTX2020` with frequency from `2.0` to `2.5` GHz and 
`16` gigabyte of memory. Since GPU is a `repeated` field, Evans asks us for 
more value, If we want to stop, just press Ctrl+D.

```shell
✔ dig down
laptop::ram::value (TYPE_UINT64) => 32
✔ GIGABYTE
<repeated> laptop::gpus::brand (TYPE_STRING) => NVIDIA
<repeated> laptop::gpus::name (TYPE_STRING) => GTX2020
<repeated> laptop::gpus::min_ghz (TYPE_DOUBLE) => 2.0
<repeated> laptop::gpus::max_ghz (TYPE_DOUBLE) => 2.5
<repeated> laptop::gpus::memory::value (TYPE_UINT64) => 16
✔ GIGABYTE
```

The next field is storage, which is also a `repeated` field. Let's say we have
a SSD of `512` gigabytes and HDD of `1` terabytes. Then Ctrl+D to stop.

```shell
✔ SSD
<repeated> laptop::gpus::storages::memory::value (TYPE_UINT64) => 512
✔ GIGABYTE
✔ HDD
<repeated> laptop::gpus::storages::memory::value (TYPE_UINT64) => 1
✔ TERABYTE
```

The screen size will be `16` inches, the screen resolution is `3072` by `1920`,
panel type `IPS`. It's not a multitouch screen, so I'll put `false` here. The 
keyboard layout is `QWERTY` and it is a backlit keyboard, so let's put `true`
here.

```shell
laptop::gpus::storages::screen::size_inch (TYPE_FLOAT) => 16
laptop::gpus::storages::screen::resolution::width (TYPE_UINT32) => 3072
laptop::gpus::storages::screen::resolution::height (TYPE_UINT32) => 1920
✔ IPS
laptop::gpus::storages::screen::multitouch (TYPE_BOOL) => false
✔ QWERTY
laptop::gpus::storages::keyboard::backlit (TYPE_BOOL) => true
```

Next the weight of the laptop will be in `kilograms` and the value is `2.2`. 
The price is `3000` USD, the release year is `2019` and finally the 
`updated_at` field we can leave empty.

```shell
✔ weight_kg
laptop::gpus::storages::weight_kg (TYPE_DOUBLE) => 2.2
laptop::gpus::storages::price_usd (TYPE_DOUBLE) => 3000
laptop::gpus::storages::release_year (TYPE_UINT32) => 2019
laptop::gpus::storages::updated_at::seconds (TYPE_INT64) => 
laptop::gpus::storages::updated_at::nanos (TYPE_INT32) => 
```

Now as you can see the request is sent to the server 

```shell
{
  "id": "06612505-b18e-4401-a4d7-4bf1e769c74b"
}
```

And we get back a response with the ID of the created laptop.

## Test the search laptop RPC
Let's try the Search Laptop API to see if we can find that laptop on the 
server or not.

```shell
call SearchLaptop
```

The max price is `4000`, the min CPU cores is `4`, the min CPU frequency is 
`2.0` and the minimum RAM is `8` gigabytes. Cool, the laptop is found!

```shell
filter::max_price_usd (TYPE_DOUBLE) => 4000
filter::min_cpu_cores (TYPE_UINT32) => 4
filter::min_cpu_ghz (TYPE_DOUBLE) => 2.0
filter::min_ram::value (TYPE_UINT64) => 8
✔ GIGABYTE
{
  "laptop": {
    "id": "06612505-b18e-4401-a4d7-4bf1e769c74b",
    "brand": "Apple",
    "name": "Macbook Pro",
    "cpu": {
      "brand": "Intel",
      "name": "Core i9",
      "numberCores": 8,
      "numberThreads": 16,
      "minGhz": 2.5,
      "maxGhz": 4.5
    },
    "ram": {
      "value": "32",
      "unit": "GIGABYTE"
    },
    "gpus": [
      {
        "brand": "NVIDIA",
        "name": "GTX2020",
        "minGhz": 2,
        "maxGhz": 2.5,
        "memory": {
          "value": "16",
          "unit": "GIGABYTE"
        }
      }
    ],
    "storages": [
      {
        "driver": "SSD",
        "memory": {
          "value": "512",
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
      "sizeInch": 16,
      "resolution": {
        "width": 3072,
        "height": 1920
      },
      "panel": "IPS"
    },
    "keyboard": {
      "layout": "QWERTY",
      "backlit": true
    },
    "weightKg": 2.2,
    "priceUsd": 3000,
    "releaseYear": 2019,
    "updatedAt": "1970-01-01T00:00:00Z"
  }
}
```

So the gRPC reflection is working well on our Golang server. Now let's stop it

```shell
exit
```

and try the Java server. Let's click button to run the `LaptopServer.main()`. 
The server is started. Now let's go back to the terminal and run the Evans 
CLI.

```shell
evans -r repl -p 8080
```

Execute commands 
```shell
show package
show service
```

and call create laptop API.

```shell
call CreateLaptop
```

Evans is asking for input fields, So everything is working properly. And 
that's all for today's lecture. Thanks for reading and see you soon in the 
next one!