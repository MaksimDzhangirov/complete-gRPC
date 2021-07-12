# Secure gRPC connection with SSL/TLS - Golang
In the previous lecture, we have learned how to use gRPC interceptors to 
authenticate users. However, the API that we used to login user was insecure,
which means the username and password were being sent in plaintext and can be
read by anyone who listens to the communication between the client and server.
So today we will learn how to secure the gRPC connection using TLS. If you 
haven't read [my lecture about SSL/TLS](SSL_TLS_lecture_eng.md), I highly 
recommend you to read it first to have a deep understanding about TLS before 
continue.

## Types of gRPC connection
There are 3 types of gRPC connections. The first one is insecure connection, 
which we've been using since the beginning of this course. In this connection,
all data transferred between client and server is not encrypted. So please 
don't use it for production! The second type is connection secured by 
server-side TLS. In this case, all the data is encrypted but only the server 
needs to provide its TLS certificate to the client. You can use this type of 
connection if the server doesn't care which client is calling its API. The 
third and strongest type is connection secured by mutual TLS. We use it when 
the server also needs to verify who's calling its services. So in this case, 
both client and server must provide their TLS certificates to the other. In 
this lecture we will learn how to implement both server-side and mutual TLS in 
Golang. So let's start!

## Generate TLS certificates
First we need the script to generate TLS certificates. Create files `gen.sh` 
and `server-ext.cnf` in folder `cert` . I encourage you to read lecture about 
how to [create and sign TLS certificate](create_SSL_TLS_certificates_eng.md) 
to understand how this script works.

`cert/gen.sh`
```shell
rm *.pem

# 1. Generate CA's private key and self-signed certificate
openssl req -x509 -newkey rsa:4096 -days 365 -nodes -keyout ca-key.pem -out ca-cert.pem -subj "/C=FR/ST=Occitanie/L=Toulouse/O=Tech School/OU=Education/CN=*.techschool.guru/emailAddress=techschool.guru@gmail.com"

echo "CA's self-signed certificate"
openssl x509 -in ca-cert.pem -noout -text

# 2. Generate web server's private key and certificate signing request (CSR)
openssl req -newkey rsa:4096 -nodes -keyout server-key.pem -out server-req.pem -subj "/C=FR/ST=Ile de France/L=Paris/O=PC Book/OU=Computer/CN=*.pcbook.com/emailAddress=pcbook@gmail.com"

# 3. Use CA's private key to sign web server's CSR and get back the signed certificate
openssl x509 -req -in server-req.pem -days 60 -CA ca-cert.pem -CAkey ca-key.pem -CAcreateserial -out server-cert.pem -extfile server-ext.cnf

echo "Server's signed certificate"
openssl x509 -in server-cert.pem -noout -text
```
`cert/server.ext.cnf`
```
subjectAltName=DNS:*.pcbook.com,DNS:*.pcbook.org,IP:0.0.0.0
```

Basically this script contains 3 parts: first, generate CA's private key and 
its self-signed certificate, second, create web server's private key and CSR 
and third, use CA's private key to sign the web server's CSR and get back its 
certificate. The generated files that we care about in this lecture are: the 
CA's certificate, the CA's private key, the server's certificate, and the 
server's private key. I'm gonna add a new command to the `Makefile` to run the 
certificate generation script. It's very simple! We just cd to the `cert` 
folder, run `gen.sh`, then get out of that folder, and we should add this 
`cert` command to the PHONY list.

```makefile
# ...
cert:
	cd cert; ./gen.sh; cd ..

.PHONY: gen clean server client test cert
```

Now let's try it in the terminal. Run

```shell
make cert
```

Excellent! All files are generated successfully. Next step, I will show you
how to secure our gRPC connection with server-side TLS.

## Implement server-side TLS
Let’s open `cmd/server/main.go` file. I will add a function to load TLS 
credentials. It will returns a `TransportCredentials` object or an error. For
server side TLS, we need to load server's certificate and private key, so we 
use `tls.LoadX509KeyPair` function to load the `server-cert.pem` and 
`server-key.perm` files from the `cert` folder. If there's an error, just 
return it. Else, we create the transport credentials from them. We make a
`tls.Config` object with the server certificate, and we set the `ClientAuth` 
field to `NoClientCert` because we're just using server-side TLS. Finally, we 
call `credentials.NewTLS()` with that config and return it to the caller.

`cmd/server/main.go`
```go
func loadTLSCredentials() (credentials.TransportCredentials, error) {
    // Load server's certificate and private key
    serverCert, err := tls.LoadX509KeyPair("cert/server-cert.pem", "cert/server-key.pem")
    if err != nil {
        return nil, err
    }
    
    // Create the credentials and return it
    config := &tls.Config{
        Certificates: []tls.Certificate{serverCert},
        ClientAuth: tls.NoClientCert,
    }
    
    return credentials.NewTLS(config), nil
}
```

OK, the `loadTLSCredentials()` function is done. In the `main` function we call
that function to get the TLS credential object. If an error occurs, we just
write a fatal log. Otherwise, we add the TLS credentials to the gRPC server
by using the `grpc.Creds()` option. That's it for the server.

`cmd/server/main.go`
```go
func main() {
    // ...
    
    tlsCredentials, err := loadTLSCredentials()
    if err != nil {
        log.Fatal("cannot load TLS credentials: ", err)
    }
    
    interceptor := service.NewAuthInterceptor(jwtManager, accessibleRoles())
    grpcServer := grpc.NewServer(
        grpc.Creds(tlsCredentials),
        grpc.UnaryInterceptor(interceptor.Unary()),
        grpc.StreamInterceptor(interceptor.Stream()),
    )
    
    // ...
}
```

Let's run it in the terminal.

```shell
make server
```

The server is started. Now if we run the client,

```shell
2021/05/05 19:45:00 cannot create auth interceptor: rpc error: code = Unavailable desc = connection closed
```

it failed because we haven't enabled TLS on the client side yet. So let's do
that! Similar to what we did on the server, I also add a function to load TLS
credentials from files. But, this time, we only need to load the certificate 
of the CA who signed the server's certificate. The reason is, client needs to
verify the authenticity of the certificate it gets from the server to make sure
that it's the right server it wants to talk to. So here we load the 
`ca-cert.pem` file, then create a new x509 cert pool, and append the CA's pem 
to that pool. Finally, we create the credentials and return it. Note that we 
only need to set the `RootCAs` field, which contains the trusted CA's 
certificate.

`cmd/client/main.go`
```go
func loadTLSCredentials() (credentials.TransportCredentials, error) {
    // Load certificate of the CA who signed server's certificate
    pemServerCA, err := ioutil.ReadFile("cert/ca-cert.pem")
    if err != nil {
        return nil, err
    }
    
    certPool := x509.NewCertPool()
    if !certPool.AppendCertsFromPEM(pemServerCA) {
        return nil, fmt.Errorf("failed to add server CA's certificate")
    }
    
    // Create the credentials and return it
    config := &tls.Config{
        RootCAs: certPool,
    }
    
    return credentials.NewTLS(config), nil
}
```

Now in the `main` function there are 2 connections which are still insecure. 
We will need to replace them with the secure TLS. Let's call 
`loadTLSCredentials()` to get the credentials object then change the 
`grpc.WithInsecure()` call to `grpc.WithTransportCredentials()` and pass in 
the TLS credentials object that we've created.

`cmd/client/main.go`
```go
func main() {
    // ...
    
    tlsCredentials, err := loadTLSCredentials()
    if err != nil {
        log.Fatal("cannot load TLS credentials: ", err)
    }
    
    cc1, err := grpc.Dial(*serverAddress, grpc.WithTransportCredentials(tlsCredentials))
    
    // ...
}
```

Similar for this connection and we're done.

`cmd/client/main.go`
```go
func main() {
    // ...
	
	cc2, err := grpc.Dial(
        *serverAddress,
        grpc.WithTransportCredentials(tlsCredentials),
        grpc.WithUnaryInterceptor(interceptor.Unary()),
        grpc.WithStreamInterceptor(interceptor.Stream()),
    )
	
	// ...
}
```

Let's try it out! This time the requests are successfully sent to the server.
Perfect!

## Subject Alternative Name (SAN)
There's 1 thing I want to show you here. Remember that when we develop on 
localhost it's important to add the `IP:0.0.0.0` as an Subject Alternative 
Name (SAN) extension to the certificate. Let's see what will happen if we
remove this from the config file, then regenerate the certificates, restart 
the server and run the client again.

```shell
2021/05/05 20:37:43 cannot create auth interceptor: rpc error: code = Unavailable desc = connection error: desc = "transport: authentication handshake failed: x509: cannot validate certificate for 0.0.0.0 because it doesn't contain any IP SANs"
```

As you can see, there's an error saying that TLS handshake failed because it
cannot validate the certificate for 0.0.0.0 since the SAN doesn't contain this
IP address. On production, it will be OK because we use domain names instead.
Alright, so now you know how to enable server-side TLS for your gRPC 
connection. Let's learn how to enable mutual TLS!

## Implement mutual TLS
At the moment, the server has already shared its certificate with the client. 
For mutual TLS, the client also has to share its certificate with the server. 
So now let's update this script to create and sign a certificate for the 
client. Let's say for this tutorial, we use the same CA to sign both server 
and client's certificates. In the real world we might have multiple clients 
with different certificates signed by different CAs.

`cert/client-ext.cnf`
```
subjectAltName=DNS:*.pcclient.com,IP:0.0.0.0
```

`cert/gen.sh`
```sh
# ...

# 4. Generate client's private key and certificate signing request (CSR)
openssl req -newkey rsa:4096 -nodes -keyout client-key.pem -out client-req.pem -subj "/C=FR/ST=Alsace/L=Strasbourg/O=PC Client/OU=Computer/CN=*.pcclient.com/emailAddress=pcclient@gmail.com"

# 5. Use CA's private key to sign client's CSR and get back the signed certificate
openssl x509 -req -in client-req.pem -days 60 -CA ca-cert.pem -CAkey ca-key.pem -CAcreateserial -out client-cert.pem -extfile client-ext.cnf

echo "Client's signed certificate"
openssl x509 -in client-cert.pem -noout -text
```

Now let's run 

```shell
make cert
```

to regenerate the certificates. OK the client's certificate and private key
are ready. To enable mutual TLS, on the server side we should change this 
`ClientAuth` field to `RequireAndVerifyClientCert`. We also need to provide a
list of certificates of the trusted CA who signs our clients' certificates.

`cmd/server/main.go`
```go
func loadTLSCredentials() (credentials.TransportCredentials, error) {
    // ...
    
    // Create the credentials and return it
    config := &tls.Config{
        Certificates: []tls.Certificate{serverCert},
        ClientAuth: tls.RequireAndVerifyClientCert,
        ClientCAs: certPool,
    }
    
    // ...
}
```

In our case, we only have 1 single CA that signs both server's and client's
certificates. So we can simply copy the codes that we've written on the client
side to load CA's certificate and create a new certificate pool. Then just 
update the variable names and error messages a bit to reflect the facts that
this should be the CA who signs client's certificate, and we're done with the
server.

`cmd/server/main.go`
```go
func loadTLSCredentials() (credentials.TransportCredentials, error) {
    // Load certificate of the CA who signed server's certificate
    pemClientCA, err := ioutil.ReadFile("cert/ca-cert.pem")
    if err != nil {
        return nil, err
    }
    
    certPool := x509.NewCertPool()
    if !certPool.AppendCertsFromPEM(pemClientCA) {
        return nil, fmt.Errorf("failed to add server CA's certificate")
    }
    
    // Load server's certificate and private key
    serverCert, err := tls.LoadX509KeyPair("cert/server-cert.pem", "cert/server-key.pem")
    if err != nil {
        return nil, err
    }

    // ...
}
```

Let's run it in the terminal.

```shell
make server
```

Now if we connect the current client to this new server, it will fail because
the server now also requires client to send its certificate.

```shell
2021/05/06 19:32:07 cannot create auth interceptor: rpc error: code = Unavailable desc
```

Let's go to the client code to fix this. I will just copy the code to load
certificate on the server side and change these files to `client-cert.pem` and
`client-key.pem`. Then we have to add the client certificate to this TLS 
config by setting the `Certificates` field, just like what we did on the
server side.

`cmd/client/main.go`
```go
func loadTLSCredentials() (credentials.TransportCredentials, error) {
    // ...

    // Load client's certificate and private key
    clientCert, err := tls.LoadX509KeyPair("cert/client-cert.pem", "cert/client-key.pem")
    if err != nil {
        return nil, err
    }
    // Create the credentials and return it
    config := &tls.Config{
        Certificates: []tls.Certificate{clientCert},
        RootCAs: certPool,
    }
    
    // ...
}
```

OK, now if we re-run the client, all the requests will be successful. Awesome!

## Private key encryption
One last thing before we finish, the client's and server's private key that we
used are not encrypted. It's because we use the `-nodes` option when generating
them. If we remove this `-nodes` option and run

```sh
# ...

# 2. Generate web server's private key and certificate signing request (CSR)
openssl req -newkey rsa:4096 -keyout server-key.pem -out server-req.pem -subj "/C=FR/ST=Ile de France/L=Paris/O=PC Book/OU=Computer/CN=*.pcbook.com/emailAddress=pcbook@example.com"

# ...
```

```shell
make cert
```

we will be asked to provide a passphrase to encrypt the server's private key, 
and the generated private key of the server is encrypted as you can see here.

`cert/server-key.pem`
```
-----BEGIN ENCRYPTED PRIVATE KEY-----
```

If we try to start the server with this key, it will return an error:

```shell
2021/05/06 20:13:08 cannot load TLS credentials: tls: failed to parse private key
```

That's because the key is encrypted. We add more codes to decrypt the key with
the passphrase, but I think, in the end, we still have to protect the 
passphrase by keeping it somewhere safe. So we can always store our unencrypted 
private key in that place as well. For example, if you use Amazon web service,
you can store your private key or any other secrets in encrypted format with
AWS secrets manager, or you can use HashiCorp's Vault for the same purpose. 

That's everything I wanted to share with you in this lecture. I hope you find 
it useful. Thanks a lot for reading, see you guys in the next one!