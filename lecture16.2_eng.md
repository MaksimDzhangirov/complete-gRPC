# Secure gRPC connection with SSL/TLS - Java
In the previous lecture, we've learned how to enable TLS to secure the gRPC
connection in Golang. Today we will learn to do the same thing in Java. If 
you haven't read my lecture about [SSL/TLS](SSL_TLS_lecture_eng.md), I highly 
recommend you to read it first to have a deep understanding about TLS before 
continue.

## Types of gRPC connection
To recall, there are 3 types of gRPC connections. The first one is insecure 
connection, where all data transferred between client and server is not 
encrypted. We should not use it in production. The second type is connection 
with server-side TLS. In this case, all the data is encrypted, but only the 
server needs to provide its TLS certificate to the client. We use this type of 
connection if the server doesn't care which client is calling its API. The 
third type and strongest type is mutual TLS connection, where both client and
server must provide their TLS certificates to the other. We use it when the 
server also needs to verify who's calling its services. Let's start with
server-side TLS!

## Generate TLS certificates
First I will copy the TLS certificate generation scripts from `pcbook golang` 
to `pcbook java` project. You can read lecture about how to [create and sign
TLS certificates](create_SSL_TLS_certificates_eng.md) to understand how this
script works. Basically, this script will generate a private key and 
self-signed certificate of the CA. Then it creates a private key and a CSR
for the server and use the CA's private key to sign that CSR to create 
server's certificate. Similarly, it will create a private key and CSR for the
client and use the same CA's private key to sign that CSR to create client's
certificate. For this tutorial, we use the same CA to sign both client and 
server's certificates, but in reality, there can be multiple clients whose 
certificates are signed by different CAs. When we run this `gen.sh` script in
the terminal, it will regenerate all private keys and certificates for the CA,
server and client. This is the CA's certificate `ca-cert.pem`, the CA's private
key `ca-key.pem`, the client's certificate `client-cert.pem`, the client's 
private key `client-key.pem`, the server's certificate `server-cert.pem` and
the server's private key `server-key.pem`.

## Implement server-side TLS
Now on the server, I will add a new static function to load TLS credentials 
and returns a `SslContext` object. First we create 2 file objects to load the 
server's certificate and private key. Then we call 
`SslContextBuilder.forServer()` function, and pass in those 2 files. For 
server-side TLS, we can set client auth to `None` which means we don't need 
client to send its certificate. After that, we call 
`GrpcSslContexts.configure()` pass in the SSL context builder object, then 
call `.build()` to build the SSL context and return it to the caller.

```java
public class LaptopServer {
    // ...
    
    public static SslContext loadTLSCredentials() throws SSLException {
        File serverCertFile = new File("cert/server-cert.pem");
        File serverKeyFile = new File("cert/server-key.pem");

        SslContextBuilder ctxBuilder = SslContextBuilder.forServer(serverCertFile, serverKeyFile)
                .clientAuth(ClientAuth.NONE);

        return GrpcSslContexts.configure(ctxBuilder).build();
    }
    
    // ...
}
```

Next I will add a new constructor for the `LaptopServer` which will 1 more 
additional input: the SSL context. This constructor will create a 
`LaptopServer` object with SSL/TLS. To do that, here instead of using 
`grpc.ServerBuilder`, we should use `NettyServerBuilder`. We call 
`.sslContext()` function of that builder to pass in the SSL context.

```java
public class LaptopServer {
    // ...
    
    public LaptopServer(int port, LaptopStore laptopStore, ImageStore imageStore, RatingStore ratingStore,
                        SslContext sslContext) {
        this(NettyServerBuilder.forPort(port).sslContext(sslContext), port, laptopStore, imageStore, ratingStore);
    }
    
    // ...   
}
```

Now in the `main` function, all we need to do is: load the TLS credentials to
build a SSL context. Then pass that context into the new `LaptopServer` 
constructor, and we're done with the server.

```java
public class LaptopServer {
    // ...
    
    public static void main(String[] args) throws InterruptedException, IOException {
        // ...

        SslContext sslContext = LaptopServer.loadTLSCredentials();
        LaptopServer server = new LaptopServer(8080, laptopStore, imageStore, ratingStore, sslContext);
        
        // ...
    }
}
```

Let's run it. The server is started. Now if we try to connect the current 
client to this new server. The request will fail, because we haven't enabled 
TLS on the client side yet.

```shell
SEVERE: request failed: UNAVAILABLE: Network closed for unknown reason
```

So let's do that! Similar to the server, I will define a function to load TLS
credentials from PEM files. But this time, we only need to load the certificate
of the CA who signed server's certificate. The reason is: the client needs to 
use CA's certificate to verify the certificate it receives from server. Here 
we just call `GrpcSslContexts.forClient()`, then call `.trustManager()` and 
pass in the CA's certificate file. Finally, call `.build()` to build the SSL 
context and return it.

```java
public class LaptopClient {
    // ...
    
    public static SslContext loadTLSCredentials() throws SSLException {
        File serverCACertFile = new File("cert/ca-cert.pem");

        return GrpcSslContexts.forClient()
                .trustManager(serverCACertFile)
                .build();
    }
    
    // ...
}
```

After that, we add a new constructor to create a `LaptopClient` with TLS 
enabled. This constructor will take a SSL context as input and inside, we use
`NettyChannelBuilder` instead of `ManagerChannelBuilder`. We replace
`usePlaintext()` call with the `sslContext`.

```java
public class LaptopClient {
    // ...
    
    public LaptopClient(String host, int port, SslContext sslContext) {
        channel = NettyChannelBuilder.forAddress(host, port)
                .sslContext(sslContext)
                .build();

        // ...
    }
    
    // ...
}
```

Alright, now in the `main` function we just load the TLS credentials to make
a SSL context and pass it into the new `LaptopClient` constructor. And the 
client is done!

```java
public class LaptopClient {
    // ...
    
    public static void main(String[] args) throws InterruptedException, SSLException {
        SslContext sslContext = LaptopClient.loadTLSCredentials();
        LaptopClient client = new LaptopClient("0.0.0.0", 8080, sslContext);
        
        // ...
    }
        
    // ...
}
```

Let's try it!

```shell
INFO: laptop created with ID: 14bc64d0-c790-42ac-99b2-4b4c22f1b479
INFO: laptop created with ID: 325ecb33-da89-4b8d-8a51-c5f990934d8e
INFO: laptop created with ID: 96e6f0e7-fe0b-455e-b067-b3a609324a6e
INFO: rate laptop (y/n)?
```

This time the requests are successfully sent to the server. So the server-side
TLS is working as expected.

## Implement mutual TLS
For mutual TLS, it's required that the client also shares its certificate with 
the server. So on the server side, we change this client auth to `REQUIRE` and
we need to load the certificate of the CA who signed client's certificate in
order to verify it. In our case it's the same CA who signed the server's 
certificate. Just like what we did on the client side, here we just add 1 more 
command `.trustManager()` and pass in the client CA's certificate. And we're
done with server.

```java
public class LaptopServer {
    // ...
    
    public static SslContext loadTLSCredentials() throws SSLException {
        // ...

        File clientCACertFile = new File("cert/ca-cert.pem");

        SslContextBuilder ctxBuilder = SslContextBuilder.forServer(serverCertFile, serverKeyFile)
                .clientAuth(ClientAuth.REQUIRE)
                .trustManager(clientCACertFile);

        // ...
    }
    
    // ...
}
```

Now if we restart the server and try to connect the client to it, the request
will fail because we haven't updated the client to send its certificate to the
server.

```shell
SEVERE: request failed: UNAVAILABLE: ssl exception
```

So let's do that. I will just copy and paste this block of codes from the 
server and change the variable names and file names from `server` to `client`. 
Then we just add `.keyManager()` to `GrpcSslContexts` and pass in the client's 
certificate and private key. And that's it!

```java
public class LaptopClient {
    // ...
    
    public static SslContext loadTLSCredentials() throws SSLException {
        File serverCACertFile = new File("cert/ca-cert.pem");
        File clientCertFile = new File("cert/client-cert.pem");
        File clientKeyFile = new File("cert/client-key.pem");

        return GrpcSslContexts.forClient()
                .keyManager(clientCertFile, clientKeyFile)
                .trustManager(serverCACertFile)
                .build();
    }
    
    // ...
}
```

Let's run the client.

```shell
INFO: laptop created with ID: 110c2b34-6104-4ef6-ad13-ea61d1091eb2
INFO: laptop created with ID: e4eb6195-a4e3-4c6c-8e34-6c3df66712d2
INFO: laptop created with ID: 337d4f10-f164-4429-ba77-612ace3ad594
INFO: rate laptop (y/n)?
```

All requests are successful now. So we have successfully enabled mutual TLS
for our gRPC connection. Thank you for reading, and I will see you in the next
lecture!