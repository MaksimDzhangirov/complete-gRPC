# Load balancing gRPC service with NGINX
So far we've learned a lot about how to develop backend web services with gRPC.
When it comes to deployment, one important thing we should consider is load
balancing. A large scale gRPC deployment typically has a number of identical
backend servers, and a number of clients. Load balancing is used for 
distributing the load from clients optimally across available servers.

## Types of load balancing
There are 2 main options of gRPC load balancing: server side and client side. 
Deciding which one to use is a primary architectural choice.

### Server side load balancing
In server-side load balancing, the client issues RPCs to a load balancer or 
proxy such as Nginx or Envoy. The load balancer distributes the RPC call to one
of the available backend servers. It also keeps track of load on each server 
and implements algorithms for distributing load fairly. The clients themselves 
do not know about the backend servers.

![Server-side_load_balancing](images/lecture17/server_side_load_balancing_eng.png)

**Picture 1** - Server-side load balancing.

### Client side load balancing
In client-side load balancing the client is aware of multiple backend servers
and chooses one to use for each RPC. Usually, the backend servers register 
themselves with a service discovery infrastructure, such as Consul or Etcd. 
Then the client communicates with that infrastructure to know the addresses of
the servers. A thick client implements the load balancing algorithms itself. 
For example, in a simple configuration, where server load is not considered,
client can just round-robin between available servers. Another approach is to
use a look-aside load balancer, where the load balancing smarts are 
implemented in a special load-balancing server. Clients query the look-aside 
load balancer to get the best server(s) to use. The heavy lifting of keeping
server state, service discovery and implementation of load balancing algorithm
is consolidated in the look-aside load balancer. Let's look at some pros and
cons of these approaches.

![Client-side_load_balancing](images/lecture17/client_side_load_balancing_eng.png)

**Picture 2** - Client-side load balancing.

### Pros and cons
One pros of server-side load balancing is simple client implementation. All 
client needs to know is the address of the proxy, no more coding is needed. And
this approach works even for untrusted client, which means the gRPC service 
can be open for everyone from the public internet. However, its cons is adding
1 more extra hop to the call. All RPCs have to go through the proxy before
reaching the backend server. Thus causing higher latency. Therefore, this 
server side load balancing is suitable for the cases where there are many 
clients, possibly untrusted from the open internet who want to connect to our
gRPC servers in a data center.

The client-side load balancing, on the other hand, doesn't add any extra hop 
to the call, and thus giving us higher performance in general. However, the
client implementation now becomes complex, especially for thick client 
approach. And it should only be used for trusted clients, or we will need to 
use a look-aside load balancer to stand in front of the trust boundary network.
Because of those attributes, client-side load balancing is often used in a very 
high traffic system and microservices architecture.

In this lecture, we will learn how to set up server-side load balancing for 
our gRPC services with Nginx.

## Code refactoring
Since I'm gonna show you different Nginx configurations where TLS can be 
enabled or disabled on the server and client, let's update our code a bit to
take in a new command line argument.

### Update server 
This `tls` argument is a boolean flag, which will tell us whether we want to 
enable TLS on our gRPC server or not. The default value is `false`. Let's also
add it to log so we can see its value when the server starts.

```go
func main() {
    port := flag.Int("port", 0, "the server port")
    enableTLS := flag.Bool("tls", false, "enable SSL/TLS")
    flag.Parse()
    log.Printf("start server on port %d, TLS = %t", *port, *enableTLS)
    
    // ...
}
```

Then I will extract interceptors to a separate `serverOptions` variable. Now we
check the TLS flag. Only in case it's enabled then we load the TLS credentials,
and append that credentials to the `serverOptions` slice. Finally, we just pass
the server options to the `grpc.NewServer()` function call. And that's it for 
the server.

```go
func main() {
    // ...
	
    interceptor := service.NewAuthInterceptor(jwtManager, accessibleRoles())
    serverOptions := []grpc.ServerOption{
        grpc.UnaryInterceptor(interceptor.Unary()),
        grpc.StreamInterceptor(interceptor.Stream()),
    }
    
    if *enableTLS {
        tlsCredentials, err := loadTLSCredentials()
        if err != nil {
            log.Fatal("cannot load TLS credentials: ", err)
        }
    
        serverOptions = append(serverOptions, grpc.Creds(tlsCredentials))
    }
    
    grpcServer := grpc.NewServer(serverOptions...)
    
    // ...
}
```

Now let's do similar thing for the client.

### Update client
First we add the TLS flag to the command line argument. Update log message to 
include the TLS value. Then we define a `transportOption` variable with the 
default value `grpc.WithInsecure()`. Only when the TLS flag value is `true` we 
load the TLS credentials from PEM files and change the `transportOption` to 
`grpc` with that TLS transport credentials. Finally, we pass the 
`transportOption` to gRPC connections. Now the client is done.

```go
func main() {
    serverAddress := flag.String("address", "", "the server address")
    enableTLS := flag.Bool("tls", false, "enable SSL/TLS")
    flag.Parse()
    log.Printf("dial server %s, TLS = %t", *serverAddress, *enableTLS)
    
    transportOption := grpc.WithInsecure()
    
    if *enableTLS {
        tlsCredentials, err := loadTLSCredentials()
        if err != nil {
            log.Fatal("cannot load TLS credentials: ", err)
        }
        
        transportOption = grpc.WithTransportCredentials(tlsCredentials)
    }
    
    cc1, err := grpc.Dial(*serverAddress, transportOption)
    
    // ...
    
    cc2, err := grpc.Dial(
        *serverAddress,
        transportOption,
        grpc.WithUnaryInterceptor(interceptor.Unary()),
        grpc.WithStreamInterceptor(interceptor.Stream()),
    )
    
    // ...
}
```

### Test the new flag 
We will have to update the Makefile a little bit to pass in the TLS flag. But
first let's try running the service without TLS. For now if we run 

```shell
make server
```

we can see that the server is running with TLS disabled.

```shell
2021/05/07 19:50:59 start server on port 8080, TLS = false
```

And if we run

```shell
make client
```

It's also running with no TLS and all the RPC calls are successful. Now if we
add this `-tls` flag to the server and restart it, the TLS will be enabled.

```makefile
# ...
server:
    go run cmd/server/main.go -port 8080 -tls
# ...
```

```shell
2021/05/07 19:54:33 start server on port 8080, TLS = true
```

If we run 

```shell
make client
```

now, the requests will fail.

```shell
2021/05/07 19:55:30 cannot create auth interceptor: rpc error: code = Unavailable desc = connection closed
```

We have to enable TLS on client side as well, by adding `-tls` flag to the 
`make cleint` command. And we can see the requests are successful again.

## Update Makefile
Alright, now the TLS flag is working as we wanted.

```makefile
# ...
client:
    go run cmd/client/main.go -address 0.0.0.0:8080 -tls
# ...
```

It's time to do some load balancing on our server. Let's start with insecure 
gRPC first. So I'm gonna remove these `-tls` flags so that the `make server` 
and `make client` commands will run without TLS. I will add 2 more `make` 
command to run 2 instances of the server on different ports. Let's say first 
server will run on port 50051, and the second server will run on port 50052.

```makefile
# ...
server1:
	go run cmd/server/main.go -port 50051
server2:
	go run cmd/server/main.go -port 50052
server:
	go run cmd/server/main.go -port 8080
client:
	go run cmd/client/main.go -address 0.0.0.0:8080
# ...
```

## Install Nginx
The next thing we need to do is to install Nginx. Since I'm on a Mac, I can
simply use Homebrew:

```shell
brew install nginx
```

You can also run Nginx using docker-compose file.

```shell
docker-compose up -d
```

After Nginx is installed, we can go to this `usr/local/etc/nginx` folder to
config it. Let's open the `nginx.conf` file with Visual Studio Code.

```shell
cd /usr/local/etc/nginx
code nginx.conf
```

This is the default configuration:

```
#user  nobody;
worker_processes  1;

#error_log  logs/error.log;
#error_log  logs/error.log  notice;
#error_log  logs/error.log  info;

#pid        logs/nginx.pid;


events {
    worker_connections  1024;
}


http {
    include       mime.types;
    default_type  application/octet-stream;

    #log_format  main  '$remote_addr - $remote_user [$time_local] "$request" '
    #                  '$status $body_bytes_sent "$http_referer" '
    #                  '"$http_user_agent" "$http_x_forwarded_for"';

    #access_log  logs/access.log  main;

    sendfile        on;
    #tcp_nopush     on;

    #keepalive_timeout  0;
    keepalive_timeout  65;

    #gzip  on;

    server {
        listen       8080;
        server_name  localhost;

        #charset koi8-r;

        #access_log  logs/host.access.log  main;

        location / {
            root   html;
            index  index.html index.htm;
        }

        #error_page  404              /404.html;

        # redirect server error pages to the static page /50x.html
        #
        error_page   500 502 503 504  /50x.html;
        location = /50x.html {
            root   html;
        }

        # proxy the PHP scripts to Apache listening on 127.0.0.1:80
        #
        #location ~ \.php$ {
        #    proxy_pass   http://127.0.0.1;
        #}

        # pass the PHP scripts to FastCGI server listening on 127.0.0.1:9000
        #
        #location ~ \.php$ {
        #    root           html;
        #    fastcgi_pass   127.0.0.1:9000;
        #    fastcgi_index  index.php;
        #    fastcgi_param  SCRIPT_FILENAME  /scripts$fastcgi_script_name;
        #    include        fastcgi_params;
        #}

        # deny access to .htaccess files, if Apache's document root
        # concurs with nginx's one
        #
        #location ~ /\.ht {
        #    deny  all;
        #}
    }

    include servers/*;
}
```

There are several things that we don’t need to care about in this tutorial, so
let's update this config file.

## Config Nginx for insecure gRPC
First, let's remove the user config, uncomment the error log, remove the config
for log levels and process id, and let’s say we just need 10 worker connections
for now.

One important thing we need to do is to config the right location to store the
error log and access log files. In my case, Homebrew has already created a 
`log` folder for Nginx at `/usr/local/var/log/nginx`, so I just go ahead and 
use it in the error/access log setting.

```
worker_processes  1;

error_log  /usr/local/var/log/nginx/error.log;

events {
    worker_connections  10;
}

http {
    access_log  /usr/local/var/log/nginx/access.log;

    server {
        listen       8080 http2;

        location / {
        }
    }
}
```

Now in the `server` block, we have a `listen` command to listen to incoming 
requests from client on port `8080`. This is the default config for a normal
HTTP server. Since gRPC uses `HTTP/2`, we should add `http2` at the end of this 
command. Let's remove this `server_name` and `charset` since we don't need them 
now. Similar for the `access_log` because we've already defined it above. 
Let's delete this config for default root HTML file and everything after the 
`location` block as we don’t care about them for now.

OK, now we want to load balance the incoming requests to our 2 server 
instances. So we should define a `upstream` for them. I'm gonna call it 
`upstream pcbook_services`. Inside this block, we use the `server` keyword to
declare a server instance. The first one is running on `localhost` port 
`50051`, and the second one is running on port `50052`. To route all RPC calls 
to the `upstream`, in the `location` block, we use `grpc_pass` keyword, 
followed by the `grpc://` scheme and the name of the `upstream`, which is 
`pcbook_services`.

```
worker_processes  1;

error_log  /usr/local/var/log/nginx/error.log;

events {
    worker_connections  10;
}

http {
    access_log  /usr/local/var/log/nginx/access.log;

    upstream pcbook_services {
        server 0.0.0.0:50051;
        server 0.0.0.0:50052;
    }

    server {
        listen       8080 http2;

        location / {
            grpc_pass grpc://pcbook_services;
        }
    }
}
```

And that’s it! The load balancing for our insecure gRPC server is done.

Let's run `nginx` in the terminal to start it. We can check if it’s running or 
not using the `ps` and `grep` command.

```shell
ps aux | grep nginx
```

Let’s check out the log folder:

```shell
cd /usr/local/var/log/nginx
ls -l
```

As you can see, 2 log files are generated: `access.log` and `error.log`. 
They’re empty at the moment because we haven’t sent any requests yet.

Now let’s run `make server1` to start the first server on port `50051` with 
`TLS = false`. Then on another tab, run `make server2` to start the second 
server on port `50052`, also with TLS disabled. Finally, we run `make client` 
on another new tab.

Looks good. All RPC calls are successful.

```shell
2021/05/12 19:48:13 token refreshed: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MjA4MDU2OTMsInVzZXJuYW1lIjoiYWRtaW4xIiwicm9sZSI6ImFkbWluIn0.Tc6w-sU9-hUvB84MUYDSnydea4D2YuItZ6gc5HrNH0I
2021/05/12 19:48:13 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/12 19:48:13 created laptop with id: 437cd13f-312a-4cec-b014-7a66141d7681
2021/05/12 19:48:13 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/12 19:48:13 created laptop with id: 871432d9-8f65-4838-821b-8f2a3a6f2a4e
2021/05/12 19:48:13 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/12 19:48:13 created laptop with id: 58ec02c4-59d4-4873-8c13-3f858e99a108
```

Let’s check the logs on our servers.

```shell
server1       | 2021/05/12 19:48:13 --> unary interceptor:  /techschool_pcbook.AuthService/Login
server2       | 2021/05/12 19:48:13 --> unary interceptor:  /techschool_pcbook.LaptopService/CreateLaptop
server2       | 2021/05/12 19:48:13 receive a create-laptop request with id: 437cd13f-312a-4cec-b014-7a66141d7681
server2       | 2021/05/12 19:48:13 saved laptop with id: 437cd13f-312a-4cec-b014-7a66141d7681
server1       | 2021/05/12 19:48:13 --> unary interceptor:  /techschool_pcbook.LaptopService/CreateLaptop
server1       | 2021/05/12 19:48:13 receive a create-laptop request with id: 871432d9-8f65-4838-821b-8f2a3a6f2a4e
server1       | 2021/05/12 19:48:13 saved laptop with id: 871432d9-8f65-4838-821b-8f2a3a6f2a4e
server2       | 2021/05/12 19:48:13 --> unary interceptor:  /techschool_pcbook.LaptopService/CreateLaptop
server2       | 2021/05/12 19:48:13 receive a create-laptop request with id: 58ec02c4-59d4-4873-8c13-3f858e99a108
server2       | 2021/05/12 19:48:13 saved laptop with id: 58ec02c4-59d4-4873-8c13-3f858e99a108
```

The `server2` receives 2 create laptop requests. And the `server1` receives 1 
login request and 1 create laptop request. Excellent!

And after a while, there’s another login request comming to this server. 
That’s because our client is still running, and it periodically calls login to
refresh the token.

I hope you still remember the codes that we’ve written in the [gRPC 
interceptors lecture](lecture15_eng.md).

OK, now let’s look at the nginx access log file.

```
nginx  | 192.168.48.1 - - [12/May/2021:19:48:13 +0000] "POST /techschool_pcbook.AuthService/Login HTTP/2.0" 200 160 "-" "grpc-go/1.37.0"
nginx  | 192.168.48.1 - - [12/May/2021:19:48:13 +0000] "POST /techschool_pcbook.LaptopService/CreateLaptop HTTP/2.0" 200 43 "-" "grpc-go/1.37.0"
nginx  | 192.168.48.1 - - [12/May/2021:19:48:13 +0000] "POST /techschool_pcbook.LaptopService/CreateLaptop HTTP/2.0" 200 43 "-" "grpc-go/1.37.0"
nginx  | 192.168.48.1 - - [12/May/2021:19:48:13 +0000] "POST /techschool_pcbook.LaptopService/CreateLaptop HTTP/2.0" 200 43 "-" "grpc-go/1.37.0"
nginx  | 192.168.48.1 - - [12/May/2021:19:48:43 +0000] "POST /techschool_pcbook.AuthService/Login HTTP/2.0" 200 160 "-" "grpc-go/1.37.0"
```

You can see first there’s a login call, then 3 create laptop calls, and 
finally another login call. So everything is working exactly as we expect.

Next I’m gonna show you how to enable `SSL/TLS` for `Nginx`.

## Config Nginx for gRPC with TLS
In a typical deployment, the gRPC servers are already running inside a trusted
network, and only the load balancer (`Nginx` in this case) is exposed to the 
public internet. So we can leave our gRPC servers running without `TLS` as 
before, and only add `TLS` to `Nginx`.

### Enable TLS on Nginx but keep gRPC servers insecure
To do that, we will need to copy 3 pem files to the nginx config folder:

* The server’s certificate `server-cert.pem`
* The server’s private key `server-key.pem`
* And the certificate of the CA `ca-cert.pem` who signed client’s certificate 
  if we use mutual TLS.

OK, so now I’m gonna `cd` to the `/usr/local/etc/nginx` folder and create a new 
`cert` folder. Then I will copy those 3 pem files from our `pcbook` project
to this folder.

```shell
cd /usr/local/etc/nginx
mkdir cert
cp ~/go/src/github.com/MaksimDzhangirov/complete-gRPC/code/lecture17/cert/server-cert.pem cert
cp ~/go/src/github.com/MaksimDzhangirov/complete-gRPC/code/lecture17/cert/server-key.pem cert
cp ~/go/src/github.com/MaksimDzhangirov/complete-gRPC/code/lecture17/cert/ca-cert.pem cert
```

Alright, now all the certificate and key files are ready.

Let's add one more `client-tls` command to the `Makefile` to run the client 
with TLS.

```makefile
# ...
client-tls:
	go run cmd/client/main.go -address 0.0.0.0:8080 -tls
# ...
```

Let's go back to our nginx config file.

To enable TLS, we first need to add `ssl` to the `listen` command. Then we use 
the `ssl_certificate` command to give `Nginx` the location of the server’s 
certificate file. And use the `ssl_certificate_key` command to give it the 
location of the server’s private key file.

```
...

    server {
        listen       8080 ssl http2;

        ssl_certificate cert/server-cert.pem;
        ssl_certificate_key cert/server-key.pem;

        ssl_client_certificate cert/ca-cert.pem;
        ssl_verify_client on;

        location / {
            grpc_pass grpc://pcbook_services;
        }
    }

...
```

As we’re using mutual TLS, we also need to use the `ssl_client_certificate` 
command to tell Nginx the location of the client CA’s certificate file. And 
finally we set `ssl_verify_client` to `on` to tell nginx to verify the 
authenticity of the certificate that client will send. And we’re done.

Let's restart nginx. We run `nginx -s stop` to stop it first. Then we start it 
with `nginx` command.

```shell
nginx -s stop
nginx
```

If we just run `make client`, it will run without TLS, so the request will 
fail, because `Nginx` is now running with TLS enabled.

```shell
2021/05/13 00:28:07 cannot create auth interceptor: rpc error: code = Unavailable desc = connection closed
```

Now let’s run `make client-tls`.

```shell
2021/05/13 00:29:37 token refreshed: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MjA4MjI1NzcsInVzZXJuYW1lIjoiYWRtaW4xIiwicm9sZSI6ImFkbWluIn0.xHvRFcHwoAEzXZMn6rfxUN6SL0W2QFgSyxyM83jPLMo
2021/05/13 00:29:37 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/13 00:29:37 created laptop with id: 40573d0a-7a86-4acc-8388-7773772c660e
2021/05/13 00:29:37 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/13 00:29:37 created laptop with id: c3f65296-8983-42af-9a4b-e1b1c32c5aa3
2021/05/13 00:29:37 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/13 00:29:37 created laptop with id: ec526761-a01a-4774-af03-26491b7f3be2
```

This time the client is running with TLS, and all requests are successful. 
Keep in mind that our servers are still running without TLS. So basically what
happens is, only the connection between client and `Nginx` is secure, and 
`Nginx` is connecting to our backend servers via another insecure connection.

Once `Nginx` receives the encrypted data from client, it will decrypt the 
data before forwarding it to the backend servers. Therefore, you should only 
use this approach if the `Nginx` and backend servers stay in the same trusted 
network.

OK, but what if they are not on the same trusted network? Well in that case, 
we have no choice but to enable TLS on our backend servers as well, and config
`Nginx` to work with it.

## Enable TLS on both Nginx and gRPC servers
Let's add 2 more `make` commands to start 2 server instances with TLS. The 
`server1-tls` command will start TLS server on port 50051 and the `server2-tls`
command will start another TLS server on port 50052.

```makefile
# ...
server1-tls:
	go run cmd/server/main.go -port 50051 -tls
server2-tls:
	go run cmd/server/main.go -port 50052 -tls
# ...
```

Alright, let's stop the current `server1` and start `server1` with `TLS`. 
Similarly, I will stop the current `server2` and start `server2` with `TLS`. 
Now if we run `make client-tls` immediately, the request will fail.

```shell
2021/05/13 00:45:35 cannot create auth interceptor: rpc error: code = Unavailable desc = Bad Gateway: HTTP status code 502; transport: received the unexpected content-type "text/html"
```

The reason is, although the TLS handshake between client and `Nginx` succeeded,
the TLS handshake between `Nginx` and our backend servers failed since the 
backend servers are now expecting a secure TLS connection, while `Nginx` is 
still using an insecure connection when connecting to the backend servers. As 
you can see in the error log, the failure happened when `Nginx` talked to the
`upstream` servers.

```shell
nginx  | 2021/05/13 00:45:35 [error] 31#31: *1 upstream prematurely closed connection while reading response header from upstream, client: 192.168.48.1, server: , request: "POST /techschool_pcbook.AuthService/Login HTTP/2.0", upstream: "grpc://192.168.48.3:50051", host: "0.0.0.0:8080"
```

To enable secure TLS connection between `Nginx` and `upstream`, in the 
`nginx.conf` file, we have to change the `grpc` scheme to `grpcs`.

```
...

    server {
        ...

        location / {
            grpc_pass grpcs://pcbook_services;
        }
    }

...
```

This should be enough if you just use server-side TLS. However, in this case,
we’re using mutual TLS, so if we just restart `Nginx` now and rerun 
`make client-tls`, the request will still fail because `Nginx` is not 
configured to send its certificate to the upstream servers yet.

```shell
nginx  | 2021/05/13 01:09:24 [error] 31#31: *1 SSL_do_handshake() failed (SSL: error:14094412:SSL routines:ssl3_read_bytes:sslv3 alert bad certificate:SSL alert number 42) while SSL handshaking to upstream, client: 192.168.48.1, server: , request: "POST /techschool_pcbook.AuthService/Login HTTP/2.0", upstream: "grpcs://192.168.48.3:50051", host: "0.0.0.0:8080"
nginx  | 2021/05/13 01:09:24 [error] 31#31: *1 SSL_do_handshake() failed (SSL: error:14094412:SSL routines:ssl3_read_bytes:sslv3 alert bad certificate:SSL alert number 42) while SSL handshaking to upstream, client: 192.168.48.1, server: , request: "POST /techschool_pcbook.AuthService/Login HTTP/2.0", upstream: "grpcs://192.168.48.2:50052", host: "0.0.0.0:8080"
```

So we've got the `bad certificate` error as you can see in the error log.

Let's see what happens if we go to the server code `cmd/server/main.go` and 
change the `ClientAuth` filed from `tls.RequireAndVerifyClientCert` to 
`NoClientCert`, which means we will just use server-side TLS.

```go
func loadTLSCredentials() (credentials.TransportCredentials, error) {
	// ...

	// Create the credentials and return it
	config := &tls.Config{
		Certificates: []tls.Certificate{serverCert},
		ClientAuth:   tls.NoClientCert,
		ClientCAs:    certPool,
	}

	return credentials.NewTLS(config), nil
}
```

Then restart `server1-tls` and `server2-tls`, and run `make client-tls`
again.

```shell
2021/05/13 01:20:35 token refreshed: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MjA4MjU2MzUsInVzZXJuYW1lIjoiYWRtaW4xIiwicm9sZSI6ImFkbWluIn0.gzA0FnWa5fBi2huYWQGMivbsSLNPfcZp_VUgcZDTuno
2021/05/13 01:20:35 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/13 01:20:35 created laptop with id: 5eadb784-1cf5-4b1d-99b8-9f251eaffd01
2021/05/13 01:20:35 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/13 01:20:35 created laptop with id: 79aac545-60c3-401a-bf80-ae8af3d6df1a
2021/05/13 01:20:35 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/13 01:20:35 created laptop with id: 8fe4f365-8705-423e-a27a-3d186a2bd84d
```

This time all requests are successful, exactly what we expected.

OK, now what if we really want mutual TLS between `Nginx` and `upstream`?

Let’s change the `ClientAuth` field back to `tls.RequireAndVerifyClientCert`, 
restart the 2 TLS backend servers, and get back to our `nginx.conf` file.

We must instruct `Nginx` to do mutual TLS with the backend servers by giving 
it the location of the certificate and private key. We use the 
`grpc_ssl_certificate` keyword for the certificate, and the 
`grpc_ssl_certificate_key` keyword for the private key.

You can generate a different pair of certificate and private key for `Nginx`
if you want. Here I simply use the same certificate and private key of the 
servers.

```
worker_processes  1;

error_log  /usr/local/var/log/nginx/error.log;

events {
    worker_connections  10;
}

http {
    access_log  /usr/local/var/log/nginx/access.log;

    upstream pcbook_services {
        server 0.0.0.0:50051;
        server 0.0.0.0:50052;
    }

    server {
        listen       8080 ssl http2;

        # Mutual TLS between gRPC client and nginx
        ssl_certificate cert/server-cert.pem;
        ssl_certificate_key cert/server-key.pem;

        ssl_client_certificate cert/ca-cert.pem;
        ssl_verify_client on;

        location / {
            grpc_pass grpcs://pcbook_services;

            # Mutual TLS between nginx and gRPC server
            grpc_ssl_certificate cert/server-cert.pem;
            grpc_ssl_certificate_key cert/server-key.pem;
        }
    }
}
```

OK, let's try it. First stop the current `Nginx` process, then start a new 
one. And run `make client-tls` again.

```shell
2021/05/13 01:31:33 token refreshed: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MjA4MjYyOTMsInVzZXJuYW1lIjoiYWRtaW4xIiwicm9sZSI6ImFkbWluIn0.Ej9eBFdXJ9vBTdwOix0OrNvRahpxd1p31tHWDpuxIJs
2021/05/13 01:31:33 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/13 01:31:33 created laptop with id: 1c5c430c-7eb0-4900-b6ae-6fdc3d13f1b5
2021/05/13 01:31:33 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/13 01:31:33 created laptop with id: f8d9561c-6a1b-46a6-82af-ffd1386ac5c1
2021/05/13 01:31:33 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/13 01:31:33 created laptop with id: 1782b1b9-d4a9-49e2-932b-de9cd214bcda
```

This time all requests are successful. Perfect!

## Multiple routing locations
There's one more thing I want to show you before we finish. As you've already 
seen, the login and create-laptop requests are now evenly distributed between
our 2 backend servers. But sometimes, we might want to separate the 
authentication service and the business logic service. For example, let's
say we want all login requests to go to server 1, and all other requests to go
to server 2. In that case, we can also tell nginx to route the requests based 
on its path.

```
worker_processes  1;

error_log  /usr/local/var/log/nginx/error.log;

events {
    worker_connections  10;
}

http {
    access_log  /usr/local/var/log/nginx/access.log;

    upstream auth_services {
        server 0.0.0.0:50051;
    }

    upstream laptop_services {
        server 0.0.0.0:50052;
    }

    server {
        listen       8080 ssl http2;

        # Mutual TLS between gRPC client and nginx
        ssl_certificate cert/server-cert.pem;
        ssl_certificate_key cert/server-key.pem;

        ssl_client_certificate cert/ca-cert.pem;
        ssl_verify_client on;

        location /techschool_pcbook.AuthService {
            grpc_pass grpcs://auth_services;

            # Mutual TLS between nginx and gRPC server
            grpc_ssl_certificate cert/server-cert.pem;
            grpc_ssl_certificate_key cert/server-key.pem;
        }

        location /techschool_pcbook.LaptopService {
            grpc_pass grpcs://laptop_services;

            # Mutual TLS between nginx and gRPC server
            grpc_ssl_certificate cert/server-cert.pem;
            grpc_ssl_certificate_key cert/server-key.pem;
        }
    }
}
```

Here I just copy the path `/techschool_pcbook.AuthService` of the `AuthService`
and paste it to `location`. Then I change this `upstream` name to 
`auth_services`. And it should only connect to the `server1` at port `50051`.

I'm gonna add another upstream for the laptop services. And make it connect to
only `server2` at port `50052`. Then duplicate the location block, change the 
upstream name to `laptop_services`, and update the path to 
`/techschool_pcbook.LaptopService`.

OK, let's try this! We just need to restart `Nginx` and run 
`make client-tls`.

```shell
server1       | 2021/05/13 01:47:38 --> unary interceptor:  /techschool_pcbook.AuthService/Login
server2       | 2021/05/13 01:47:39 --> unary interceptor:  /techschool_pcbook.LaptopService/CreateLaptop
server2       | 2021/05/13 01:47:39 receive a create-laptop request with id: cfd3aee1-c258-4949-89b3-2449860ca1e0
server2       | 2021/05/13 01:47:39 saved laptop with id: cfd3aee1-c258-4949-89b3-2449860ca1e0
server2       | 2021/05/13 01:47:39 --> unary interceptor:  /techschool_pcbook.LaptopService/CreateLaptop
server2       | 2021/05/13 01:47:39 receive a create-laptop request with id: e3bcd979-9d3e-4b81-ad9f-0a8bf0a54d43
server2       | 2021/05/13 01:47:39 saved laptop with id: e3bcd979-9d3e-4b81-ad9f-0a8bf0a54d43
server2       | 2021/05/13 01:47:39 --> unary interceptor:  /techschool_pcbook.LaptopService/CreateLaptop
server2       | 2021/05/13 01:47:39 receive a create-laptop request with id: ca181f5b-b331-48d7-834a-c56a8b73d06a
server2       | 2021/05/13 01:47:39 saved laptop with id: ca181f5b-b331-48d7-834a-c56a8b73d06a
```

Now we can see only login request goes to `server1`.

And all other create laptop requests go to `server2`. Even if we run this 
`make client-tls` multiple times. So it works!

And that wraps up our lecture about load balancing gRPC with Nginx. I'm gonna
push this `Nginx` config file to the `pcbook` repository so that you can 
download and play with it if you like.

Thanks a lot for reading and following the course. Happy coding and see you in the
next one!
