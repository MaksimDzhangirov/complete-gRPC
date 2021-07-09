# Implement gRPC interceptor + JWT authentication in Go
Hello everyone and welcome back to the gRPC course! Today we will learn how to
use gRPC interceptors to authenticate and authorize users.

## What is a gRPC interceptor?
Well, basically, it's like a middleware function that can be added on both 
server side and client side. A server-side interceptor is a function that will 
be called by the gRPC server before reaching the actual RPC method. It can be 
used for multiple purposes such as logging, tracing, rate-limiting, 
authentication and authorization. Similarly, a client-size interceptor is a 
function that will be called by the gRPC client before invoking the actual 
RPC.

![Picture 1](images/lecture15/grpc_interceptor_eng.png)

**Picture 1** - gRPC interceptor.

In this lecture, we will first implement a server interceptor to authorize 
access to our gRPC APIs with JSON web token (JWT). With this interceptor, we
will make sure that only users with some specific roles can call a specific 
API on our server. Then we will implement a client interceptor to login user 
and attach the JWT to the request before calling the gRPC API.

## A simple server interceptor
Alright, let's start by adding a simple interceptor to our gRPC server. 
There are 2 types of interceptor: 1 for unary RPCs and the other for stream 
RPCs.

### Unary interceptor
In this `grpc.NewServer()` function, let's add a new `grpc.UnaryInterceptor()`
option.

`cmd/sever/main.go`
```go
func main() {
    // ...
    
    grpcServer := grpc.NewServer(
        grpc.UnaryInterceptor(),
    )
    // ...
}
```

It expects a unary server interceptor function as input. We can press Command 
on macOS (Ctrl on Windows and Linux) and click on the function name to go to 
its definition. Let's copy this function signature and paste it to our 
`main.go` file. I'm gonna name it `unaryInterceptor()`. It has some input 
parameters, such as a context, a request, a unary server info, and the actual
unary handler. It returns a response and an error. Now we can pass this 
function to the unary interceptor option. Here we have to add the `grpc` package
name to unary server info and unary handler.

```go
func unaryInterceptor(
    ctx context.Context,
    req interface{},
    info *grpc.UnaryServerInfo,
    handler grpc.UnaryHandler,
) (interface{}, error) {

}

func main() {
    // ...
    
    grpcServer := grpc.NewServer(
        grpc.UnaryInterceptor(unaryInterceptor),
    )
    // ...
}
```

OK, for now let's just write a simple log saying unary interceptor together 
with the full method name of the RPC being called. Then we just call actual
handler with the original context and request and return its result.

```go
func unaryInterceptor(
    ctx context.Context,
    req interface{},
    info *grpc.UnaryServerInfo,
    handler grpc.UnaryHandler,
) (interface{}, error) {
    log.Println("--> unary interceptor: ", info.FullMethod)
    return handler(ctx, req)
}
```

### Stream interceptor 
We can do the same for the stream interceptor. Add `grpc.StreamInterceptor()` 
option. Follow the definition to get the function signature. Copy and paste it
to the `main.go` file. Pass that function to the stream interceptor option. 
Write a log with the full RPC method name. This time the handler will be called
with the original server and stream parameter.

```go
func streamInterceptor(
    srv interface{},
    stream grpc.ServerStream,
    info *grpc.StreamServerInfo,
    handler grpc.StreamHandler,
) error {
    log.Println("--> stream interceptor: ", info.FullMethod)
    return handler(srv, stream)
}

func main() {
    // ...
        
    grpcServer := grpc.NewServer(
        grpc.UnaryInterceptor(unaryInterceptor),
        grpc.StreamInterceptor(streamInterceptor),
    )
    // ...
}
```

## Run client & server
Alright, let's try it! First I will start the server, then I open a new tab and
run the client. As you can see on the server logs, the unary interceptor is 
called 3 times for the `CreateLaptop` RPC. This is the full name of that RPC: 
`/techschool_pcbook.LaptopService/CreateLaptop`, which includes the package 
name `techschool_pcbook` and the service name `LaptopService`.

```shell
2021/04/15 19:38:55 --> unary interceptor:  /techschool_pcbook.LaptopService/CreateLaptop
2021/04/15 19:38:55 receive a create-laptop request with id: 2b6bc3c0-22fb-4eec-a991-98336ee1090d
2021/04/15 19:38:55 saved laptop with id: 2b6bc3c0-22fb-4eec-a991-98336ee1090d
2021/04/15 19:38:55 --> unary interceptor:  /techschool_pcbook.LaptopService/CreateLaptop
2021/04/15 19:38:55 receive a create-laptop request with id: 968c098c-13b9-4ff3-aa3f-f74ed22f1e6f
2021/04/15 19:38:55 saved laptop with id: 968c098c-13b9-4ff3-aa3f-f74ed22f1e6f
2021/04/15 19:38:55 --> unary interceptor:  /techschool_pcbook.LaptopService/CreateLaptop
2021/04/15 19:38:55 receive a create-laptop request with id: ab034232-f0aa-48c4-a20e-108c6766286a
2021/04/15 19:38:55 saved laptop with id: ab034232-f0aa-48c4-a20e-108c6766286a
```

Now let's enter "y" to rate the 3 created laptops. The stream interceptor is
called once.

```shell
2021/04/15 19:42:27 --> stream interceptor:  /techschool_pcbook.LaptopService/RateLaptop
```

Awesome.

So now you've got some ideas of how the server interceptor works. We will 
extend its functionality to authenticate and authorize user requests. To do 
that, we will need to add users to our system, and add a service to login user 
and return JWT access token.

## User struct
So let's create a `user.go` file and define a new `User` struct. It will contain
a username, a hashed password, and a role. We add a `NewUser()` function to 
make a new user which takes a username, a password, and a role as inputs and 
returns a `User` object and an error.

```go
package service

// User contains user's information
type User struct {
    Username       string
    HashedPassword string
    Role           string
}

// NewUser returns a new user
func NewUser(username string, password string, role string) (*User, error) {

}
```

We should never store plaintext password in the system, so here we use `bcrypt` 
to hash the password first. Let's use the default cost for now. If an error 
occurs, just wrap and return it. Else we create a new user object with the 
username, the hashed password, the role and return it. Next we define a method
on the user to check if a given password is correct or not. All we need to do 
is to call `bcrypt.CompareHashAndPassword()` function, pass in the user's 
hashed password, and the given plaintext password and return `true` if error 
is `nil`. I will add one more function to clone the user. This function will
be useful for us later because we're gonna store users in memory.

```go
// NewUser returns a new user
func NewUser(username string, password string, role string) (*User, error) {
    hashedPassword, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
    if err != nil {
        return nil, fmt.Errorf("cannot hash password: %w", err)
    }
    
    user := &User{
        Username:       username,
        HashedPassword: string(hashedPassword),
        Role:           role,
    }
    
    return user, nil
}

// IsCorrectPassword if the provided password is correct or not
func (user *User) IsCorrectPassword(password string) bool {
    err := bcrypt.CompareHashAndPassword([]byte(user.HashedPassword), []byte(password))
    return err == nil
}

// Clone returns a clone of this user
func (user *User) Clone() *User {
    return &User{
        Username:       user.Username,
        HashedPassword: user.HashedPassword,
        Role:           user.Role,
    }
}
```

## User store
Alright, now let's create a `user_store.go` file and define a `UserStore` 
interface just like what we did for laptop store and rating store in previous
lectures. It will have 2 functions: one function for saving a user to the 
store and the other function to find a user in the store by username. Now 
let's implement this interface with the in-memory user store. It has a mutex 
to control concurrent access and a `users` map with key is the `username`. I 
will write a function to build a new in-memory user store and initialize the 
`users` map in it.

```go
package service

import "sync"

// UserStore is an interface to store users
type UserStore interface {
    // Save saves a user to the store
    Save(user *User) error
    // Find finds a user by username
    Find(username string) (*User, error)
}

// InMemoryUserStore stores users in memory
type InMemoryUserStore struct {
    mutex sync.RWMutex
    users map[string]*User
}

// NewInMemoryUserStore returns a new in-memory user store
func NewInMemoryUserStore() *InMemoryUserStore {
    return &InMemoryUserStore{
        users: make(map[string]*User),
    }
}
```

Then we need to implement the `Save` and `Find` function of the interface. In 
this `Save` function first we acquire the write lock then check if a user with
the same username already exists or not. If it does, we return an error. 
Otherwise, we just clone the input user and put it into the map.

```go
// Save saves a user to the store
func (store *InMemoryUserStore) Save(user *User) error {
    store.mutex.Lock()
    defer store.mutex.Unlock()
    
    if store.users[user.Username] != nil {
        return ErrAlreadyExists
    }
    
    store.users[user.Username] = user.Clone()
    return nil
}
```

For the `Find` function we acquire a read lock, then we get the user by 
username from the map. If it is `nil`, we just return `nil, nil`. Else we 
return a clone of that user.

```go
// Find finds a user by username
func (store *InMemoryUserStore) Find(username string) (*User, error) {
    store.mutex.RLock()
    defer store.mutex.RUnlock()
    
    user := store.users[username]
    if user == nil {
        return nil, nil
    }
    
    return user.Clone(), nil
}
```

And that's it. The user store is ready.

## JWT manager
Now let's implement a JWT manager to generate and verify access token for 
users. The `JWTManager` struct contains 2 fields: the secret key to sign and 
verify the access token, and the valid duration of the token. These 2 fields
should be passed as input parameters of the `NewJWTManager()` function.

```go
package service

import "time"

// JWTManager is a JSON web token manager
type JWTManager struct {
    secretKey     string
    tokenDuration time.Duration
}

// NewJWTManager returns a new JWT manager
func NewJWTManager(secretKey string, tokenDuration time.Duration) *JWTManager {
    return &JWTManager{secretKey: secretKey}
}
```

In order to work with JWT, I will use the `jwt-go` library. Let's search for it 
on the browser. Copy the github link `https://github.com/dgrijalva/jwt-go` of
the library and run 

```shell
go get github.com/dgrijalva/jwt-go
```

to install it. OK, now back to our JWT manager. The JSON web token should 
contain a claims object, which has some useful information about the user who 
owns it. So I'm gonna declare a custom `UserClaims`. It will contain the JWT
standard claims as a composite field. This standard claims has several useful
information that we can set, but for the purpose of this demo, I'm just gonna 
use 1 field, which is the `expiresAt` time of the token. As you might notice,
the claims has a `Valid()` function that will automatically check if the token
is expired or not for us when we call the parse token function later. OK, now
beside this standard claims, let's add a username field and another field to
store the role of the user because we want to do role-based authorization.

```go
// UserClaims is a custom JWT claims that contains some user's information
type UserClaims struct {
    jwt.StandardClaims
    Username string `json:"username"`
    Role     string `json:"role"`
}
```

## Generate & sign access token
Next, let's write a function to generate and sign a new access token for a 
specific user. In this function we first create a new user claims object. For
standard claims, I will only set the `expiresAt` field. We have to add the 
token duration to the current time and convert it to Unix time. Then we set
the username and role of the user. We call `jwt.NewWithClaims()` function to
generate a token object. For simplicity, here I use a HMAC based signing 
method, which is `HS256`. For production, you should consider using stronger
methods, such as RSA or Eliptic-Curve based digital signature algorithms. The
last and also the most important step is to sign the generated token with your 
secret key. This will make sure that no one can fake an access token, since 
they don't have your secret key.

```go
// Generate generates and signs a new token for a user
func (manager *JWTManager) Generate(user *User) (string, error) {
    claims := UserClaims{
        StandardClaims: jwt.StandardClaims{
            ExpiresAt: time.Now().Add(manager.tokenDuration).Unix(),
        },
        Username: user.Username,
        Role: user.Role,
    }
    
    token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
    return token.SignedString([]byte(manager.secretKey))
}
```

## Parse & Verify access token
OK, cool, now let's add another function to verify an access token, and return
a user claims if the token is valid. We just have to call 
`jwt.ParseWithClaims`, pass in the access token, an empty user claims, and a 
custom key function. In this function, it's very important to check the signing
method of the token to make sure that it matches with the algorithm our server
uses, which in our case is `HMAC`. If it matches, then we just return the 
secret key that is used to sign the token. The parse function will return a 
token object and an error. If the error is not `nil`, we return invalid token.
Else we get the claims from the token and convert it to a `UserClaims` object.
If the conversion fails, we return invalid token claims error. Otherwise,
just return the user claims. And we're done!

```go
// Verify verifies the access token string and return a user claim if the token is valid
func (manager *JWTManager) Verify(accessToken string) (*UserClaims, error) {
    token, err := jwt.ParseWithClaims(
        accessToken,
        &UserClaims{},
        func (token *jwt.Token) (interface{}, error) {
            _, ok := token.Method.(*jwt.SigningMethodHMAC)
            if !ok {
                return nil, fmt.Errorf("unexpected token signing method")
            }
    
            return []byte(manager.secretKey), nil
        },
    )
    
    if err != nil {
        return nil, fmt.Errorf("invalid token: %w", err)
    }
    
    claims, ok := token.Claims.(*UserClaims)
    if !ok {
        return nil, fmt.Errorf("invalid token claims")
    }
    
    return claims, nil
}
```

## Implement Auth service server
The next thing we need to do is to provide a new service for client to login 
and get the access token. Let's create a new `auth_service.proto` file. The 
package and options are the same as in other proto files. We define a 
`LoginRequest` message with 2 fields: a string username, and a string password.
Then a `LoginResponse` message with only 1 field: the access token. We define
a new `AuthService`. It was only 1 unary RPC: `Login`, which takes a login 
request as input and returns a login response.

```protobuf
syntax = "proto3";

package techschool_pcbook;

option go_package = ".;pb";
option java_package = "com.github.techschool.pcbook.pb";
option java_multiple_files = true;

message LoginRequest {
  string username = 1;
  string password = 2;
}

message LoginResponse { string access_token = 1; }

service AuthService {
  rpc Login(LoginRequest) returns (LoginResponse) {};
}
```

Alright, now let's run `make gen` to generate new codes for this service. As 
you can see, the `auth_service.pb.go` and `auth_service_grpc.pb.go` file are 
generated. Just like what we did with the laptop server in previous lecture,
I'm gonna create a new `auth_server.go` file to implement this new service. 
The `AuthServer` struct will contains a `userStore` and a `JWTManager`. Let's 
add a function to build and return a new `AuthServer` object.

```go
package service

// AuthServer is the server for authentication
type AuthServer struct {
    userStore UserStore
    jwtManager *JWTManager
    pb.AuthServiceServer
}

// NewAuthServer returns a new auth server
func NewAuthServer(userStore UserStore, jwtManager *JWTManager) *AuthServer {
    return &AuthServer{userStore: userStore, jwtManager: jwtManager}
}
```

Then we go to `auth_service_grpc.pb.go` file to copy the `Login` function 
signature. Paste it to our `auth_server.go` file and make it a method of the 
`AuthServer` struct. This method is very easy to implement. First we call 
`userStore.Find()` to find the user by username. If there's an error, just 
return it with an `Internal` error code, else if the user is not found or the 
password is incorrect then we return status code `NotFound` with a message 
saying the username or password is incorrect. If the user is found and the 
password is correct, we call `jwtManager.Generate()` to generate a new access 
token. If an error occurs, we return it with `Internal` status code. Otherwise, 
we create a new login response object with the generated access token, and 
return it to the client.

```go
// Login is a unary RPC to login user
func (server *AuthServer) Login(ctx context.Context, req *pb.LoginRequest) (*pb.LoginResponse, error) {
    user, err := server.userStore.Find(req.GetUsername())
    if err != nil {
        return nil, status.Errorf(codes.Internal, "cannot find user: %v", err)
    }
    
    if user == nil || !user.IsCorrectPassword(req.GetPassword()) {
        return nil, status.Errorf(codes.NotFound, "incorrect username/password")
    }
    
    token, err := server.jwtManager.Generate(user)
    if err != nil {
        return nil, status.Errorf(codes.Internal, "cannot generate access token")
    }
    
    res := &pb.LoginResponse{AccessToken: token}
    return res, nil
}
```

## Add auth service to gRPC server
OK, now we have to add this new authentication service to the gRPC server. 
First we need to create a new in-memory user store. Then we create a new JWT 
manager a secret key and token duration.

```go
func main() {
    // ...
    
    userStore := service.NewInMemoryUserStore()
    jwtManager := service.NewJWTManager(secretKey, tokenDuration)
    
    // ...
}
```

To make it simple, I will just define them as constants here.

```go
const (
    secretKey = "secret"
    tokenDuration = 15 * time.Minute
)
```

In reality, we should load them from environment variables or a config file. 
For the demo purpose, let's set the token duration to be 15 minutes.

Next, we create a new auth server with the `userStore` and `jwtManager`. And 
we call `pb.RegisterAuthServiceServer` to add it to the gRPC server. 

```go
func main() {
    // ...
    jwtManager := service.NewJWTManager(secretKey, tokenDuration)
    authServer := service.NewAuthServer(userStore, jwtManager)
    
    // ...
    
    grpcServer := grpc.NewServer(
        grpc.UnaryInterceptor(unaryInterceptor),
        grpc.StreamInterceptor(streamInterceptor),
    )
    
    pb.RegisterAuthServiceServer(grpcServer, authServer)
    // ...
}
```

## Seed users
In order to test the new login API, we have to add some seed users. So let's do 
that. I write a function to create a user given its username, password and 
role and save it to the user store.

```go
func createUser(userStore service.UserStore, username, password, role string) error {
    user, err := service.NewUser(username, password, role)
    if err != nil {
        return err
    }
    
    return userStore.Save(user)
}
```

Then in this `seedUsers()` function, I call the `createUser()` function 2 
times to create 1 admin user and 1 normal user. Let's say they have the same
`secret` password.

```go
func seedUsers(userStore service.UserStore) error {
    err := createUser(userStore, "admin1", "secret", "admin")
    if err != nil {
        return err
    }
    return createUser(userStore, "user1", "secret", "user")
}
```

Then in `main` function we call `seedUsers()` right after creating the 
`userStore`.

```go
func main() {
    // ...
    
    userStore := service.NewInMemoryUserStore()
    err := seedUsers(userStore)
    if err != nil {
        log.Fatal("cannot seed users")
    }
    jwtManager := service.NewJWTManager(secretKey, tokenDuration)
    // ...
}
```

## Try the auth service with Evans CLI
Alright, let's try it! I will start the gRPC server. Then use Evans client to
connect to it.

```shell
evans -r -p 8080
```

When we call `show service`, we can see the new `AuthService` with a `Login`
RPC in the result. Let's select the `AuthService`

```shell
service AuthService
```

and call `Login` RPC

```shell
call Login
```

Username `admin1`. Let's try an incorrect password

```shell
username (TYPE_STRING) => admin1
password (TYPE_STRING) => 1
command call: rpc error: code = NotFound desc = incorrect username/password
```

The server returns incorrect username/password as expected. Let's call `Login`
again, but this time enter a correct password for `admin1`.

```shell
username (TYPE_STRING) => admin1
password (TYPE_STRING) => secret
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MTg0ODkyMjMsInVzZXJuYW1lIjoiYWRtaW4xIiwicm9sZSI6ImFkbWluIn0.Bxxo2193YtuxWXg4Id2r4YDZmRzYgzdkgj5NOxaB8Kw"
}
```

The request is successful, and we've got an access token. Let's copy this 
token and go to `jwt.io` on the browser. Paste the token into this `Encoded` 
area. We can see on the `Decoded` area the header part contains the algorithm
`HS256` and token type JWT. The payload part contains the user claims with an
expire time, a username, and a role. So login RPC is working perfectly.

However we haven't written a proper interceptor to verify the token yet. So
let's go back to the code and do that.

## Implement server's auth interceptor
I will create a new `auth_interceptor.go` file and define a new 
`AuthInterceptor` struct inside it.

```go
package service

type AuthInterceptor struct {
    jwtManager *JWTManager
    accessibleRoles map[string][]string
}
```

This interceptor will contains a JWT manager and a map that define for each 
RPC method a list of roles that can access it. So the key of the map is the 
full method name and its value is a slice of role names. Let's write a 
`NewAuthInterceptor()` function to build and return a new auth interceptor 
object. The `jwtManager` and `accessibleRoles` map will be the input parameters
of this function.

```go
// NewAuthInterceptor returns a new auth interceptor
func NewAuthInterceptor(jwtManager *JWTManager, accessibleRoles map[string][]string) *AuthInterceptor {
    return &AuthInterceptor{jwtManager: jwtManager, accessibleRoles: accessibleRoles}
}
```

Now I will add a new `Unary()` method to the auth interceptor object, which 
will create and return a gRPC unary server interceptor function.

```go
// Unary returns a server interceptor function to authenticate and authorize unary RPC
func (interceptor *AuthInterceptor) Unary() grpc.UnaryServerInterceptor {
	
}
```

Then let's open `main.go` file, and copy the `unaryInterceptor()` function that
we've written before and paste it inside this `Unary()` function. It's exactly
the function that we need to return.

```go
// Unary returns a server interceptor function to authenticate and authorize unary RPC
func (interceptor *AuthInterceptor) Unary() grpc.UnaryServerInterceptor {
    return func(
        ctx context.Context,
        req interface{},
        info *grpc.UnaryServerInfo,
        handler grpc.UnaryHandler,
    ) (interface{}, error) {
        log.Println("--> unary interceptor: ", info.FullMethod)
        return handler(ctx, req)
    }
}
```

Similarly, we add a new `Stream()` method, which will create and return a gRPC
stream server interceptor function. Then copy and paste the stream interceptor
function here.

```go
// Stream returns a server interceptor function to authenticate and authorize stream RPC
func (interceptor *AuthInterceptor) Stream() grpc.StreamServerInterceptor {
    return func(
        srv interface{},
        stream grpc.ServerStream,
        info *grpc.StreamServerInfo,
        handler grpc.StreamHandler,
    ) error {
        log.Println("--> stream interceptor: ", info.FullMethod)
        return handler(srv, stream)
    }
}
```

Now in the `main.go` file, we have to create a new interceptor object with the
`jwtManager` and a map of `accessibleRoles`.

`cmd/server/main.go`
```go
func main() {
    // ...
    
    interceptor := service.NewAuthInterceptor(jwtManager, accessibleRoles())
    grpcServer := grpc.NewServer(
        grpc.UnaryInterceptor(interceptor.Unary()),
        grpc.StreamInterceptor(interceptor.Stream()),
    )
    // ...
}
```

For this demo, I will just write a function to return this constant map.

```go
func accessibleRoles() map[string][]string {
	
}
```

Then in the `grpc.NewServer()` function we can pass in the 
`interceptor.Unary()` and `interceptor.Stream()`.

## Define accessible roles
Now for the `accessibleRoles` function we need to prepare a list of RPC 
methods and the roles that can access each of them. To get the full RPC method
name, let's open the terminal and run

```shell
make client
```

Then in the server logs, we can see the full method name of the `CreateLaptop`
RPC: `/techschool_pcbook.LaptopService/CreateLaptop`. So let's copy it. We know
that all methods of `LaptopService` will starts with the same path so I define
a constant for it here. Then we can create and return a map like this.

```go
func accessibleRoles() map[string][]string {
    const laptopServicePath = "/techschool_pcbook.LaptopService/"
    
    return map[string][]string{
        laptopServicePath + "CreateLaptop": {"admin"},
        laptopServicePath + "UploadImage": {"admin"},
        laptopServicePath + "RateLaptop": {"admin", "user"},
    }
}
```

The first method is `CreateLaptop`, which only `admin` users can call. The 
`UploadImage` method is also accessible for `admin` only. The `RateLaptop` 
method can be called by both `admin` and `user`. And let's say the 
`SearchLaptop` API is accessible by everyone, even for non-registered users.
So the idea is: we don't put `SearchLaptop` or any other publicly accessible 
RPCs in this map.

OK, now let's get back to the auth interceptor. We will define a new 
`authorize()` function, which will take a context and method as input and will
return an error if the request is unauthorized.

```go
func (interceptor *AuthInterceptor) authorize(ctx context.Context, method string) error {

}
```

Then in `Unary()` function, we call `interceptor.authorize()` with the input 
context and `info.FullMethod`. If error is not `nil`, we will return 
immediately.

```go
// Unary returns a server interceptor function to authenticate and authorize unary RPC
func (interceptor *AuthInterceptor) Unary() grpc.UnaryServerInterceptor {
    return func(
        ctx context.Context,
        req interface{},
        info *grpc.UnaryServerInfo,
        handler grpc.UnaryHandler,
    ) (interface{}, error) {
        // ...
    
        err := interceptor.authorize(ctx, info.FullMethod)
        if err != nil {
            return nil, err
        }
    
        // ...
    }
}
```

In similar fashion, for the `Stream()` function we call 
`interceptor.authorize()` with stream context and `info.FullMethod` and return
right away if an error is returned.

```go
// Stream returns a server interceptor function to authenticate and authorize stream RPC
func (interceptor *AuthInterceptor) Stream() grpc.StreamServerInterceptor {
    return func(
        srv interface{},
        stream grpc.ServerStream,
        info *grpc.StreamServerInfo,
        handler grpc.StreamHandler,
    ) error {
        // ...
    
        err := interceptor.authorize(stream.Context(), info.FullMethod)
        if err != nil {
            return err
        }
    
        // ...
    }
}
```

## Implement authorize function
Now let's implement the `authorize()` function. First we get the list of roles
that can access the target RPC method. If it's not in the map, then it means
the RPC is publicly accessible. So we simply return `nil` in this case. Else, we
should get the access token from the context. To do that, we use the 
`grpc/metadata` package. We call `metadata.FromIncomingContext` to get the 
metadata of the request. If it's not provided, we return an error with 
`Unauthenticated` status code. Else we get the values from the authorization
metadata key. If it's empty, we return `Unauthenticated` code because the 
token is not provided. Otherwise, the access token should be stored in the 
1st element of the values. We then call `jwtManager.Verify` to verify the 
token and get back the claims. If an error occurs, it means the token is 
invalid. So we just return `Unauthenticated` code to the client. Else, we 
iterate through the accessible roles to check if the user's role can access 
this RPC or not. If the user's role is found in the list, we simply return 
`nil`. If not, we return `PermissionDenied` status code and a message saying
user doesn't have permission to access this RPC. And we're done with the server 
authorization interceptor!

```go
func (interceptor *AuthInterceptor) authorize(ctx context.Context, method string) error {
    accessibleRoles, ok := interceptor.accessibleRoles[method]
    if !ok {
        // everyone can access
        return nil
    }
    
    md, ok := metadata.FromIncomingContext(ctx)
    if !ok {
        return status.Errorf(codes.Unauthenticated, "metadata is not provided")
    }
    
    values := md["authorization"]
    if len(values) == 0 {
        return status.Errorf(codes.Unauthenticated, "authorization token is not provided")
    }
    
    accessToken := values[0]
    claims, err := interceptor.jwtManager.Verify(accessToken)
    if err != nil {
        return status.Errorf(codes.Unauthenticated, "access token is invalid: %v", err)
    }
    
    for _, role := range accessibleRoles {
        if role == claims.Role {
            return nil
        }
    }
    
    return status.Error(codes.PermissionDenied, "no permission to access this RPC")
}
```

Let's test it on the terminal. First start the server. 

```shell
make server
```

Then run the client.

```shell
make client
```

We got `Unauthenticated` error.

```shell
2021/04/16 02:01:29 cannot create laptop: rpc error: code = Unauthenticated desc = authorization token is not provided
```

With reason authorization token is not provided. That's exactly what we expect,
because we haven't login and add access token to the request on the client 
side yet.

So let's do that now!

## Implement Auth service client
As our client code is getting bigger, I will create a separate `client` package
for it. Then create a new `auth_client.go` file inside this folder. Let's 
define the `AuthClient` struct to call authentication service. This struct 
will contain a `service` field, which is the `AuthServiceClient` generated by 
`protoc`. It also contains a username and a password that will be used to 
login and get access token.

```go
package client

// AuthClient is a client to call authentication RPC
type AuthClient struct {
    service pb.AuthServiceClient
    username string
    password string
}
```

As usual, we define a function to create and return a new auth client object. 
It will have 3 input parameters: a grpc client connection, a username and a 
password. The service will be created by calling `pb.NewAuthServiceClient()`
and pass in the connection.

```go
// NewAuthClient returns a new auth client
func NewAuthClient(cc *grpc.ClientConn, username string, password string) *AuthClient {
    service := pb.NewAuthServiceClient(cc)
    return &AuthClient{service: service, username: username, password: password}
}
```

Next, we write a `Login` function to call `Login` RPC to get access token. Just
like other RPC, we create a context with timeout after 5 seconds, call 
`defer cancel()`. Then we make a login request with the provided username and 
password. And call `service.Login` with that request. If error is not `nil`, 
we simply return it. Else, we return the responded access token to the caller.
And the auth client is completed.

```go
// Login login user and returns the access token
func (client *AuthClient) Login() (string, error) {
    ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
    defer cancel()
    
    req := &pb.LoginRequest{
        Username: client.username,
        Password: client.password,
    }
    
    res, err := client.service.Login(ctx, req)
    if err != nil {
        return "", err
    }
    
    return res.GetAccessToken(), nil
}
```

## Implement client interceptor
Now let's learn how to use it to build a client interceptor. I'm gonna create
a new `auth_interceptor.go` file in the `client` package. The idea is: we will
intercept all gRPC requests and attach an access token to them (if necessary)
before invoking the server. Similar to what we did on the server, I will define
an `AuthInterceptor` struct. It contains an auth client object that will be 
used to login user, a map to tell us which method needs authentication, and 
latest acquired access token. What does that mean? Well basically what we will
do is to launch a separate go routine to periodically call login API to get a
new access token before the current token expired.

```go
package client

// AuthInterceptor is a client interceptor for authentication
type AuthInterceptor struct {
    authClient *AuthClient
    authMethods map[string]bool
    accessToken string
}
```

So in the `NewAuthInterceptor()` function, beside the auth client and auth 
methods, we also need a refresh token duration parameter. It will tell us how 
often we should call the login API to get a new token. In this function, first 
we will create a new interceptor object. Then we will call an internal function
to schedule refreshing access token and pass in the refresh duration. If an 
error occurs, just return it or else return the interceptor.

```go
// NewAuthInterceptor returns a new auth interceptor
func NewAuthInterceptor(
    authClient *AuthClient,
    authMethods map[string]bool,
    refreshDuration time.Duration,
) (*AuthInterceptor, error) {
    interceptor := &AuthInterceptor{
        authClient: authClient,
        authMethods: authMethods,
    }
    
    err := interceptor.scheduleRefreshToken(refreshDuration)
    if err != nil {
        return nil, err
    }
    
    return interceptor, nil
}
```

## Setup schedule to refresh token
Now let's implement the `scheduleRefreshToken` function. But, first we will 
need a function to just refresh token with no scheduling. In this function, we
just use the auth client to login user. Once the token is returned we simply
store it in the `interceptor.accessToken` field. Let's write a simple log here 
to observe later, then return `nil`.

```go
func (interceptor *AuthInterceptor) refreshToken() error {
    accessToken, err := interceptor.authClient.Login()
    if err != nil {
        return err
    }
    
    interceptor.accessToken = accessToken
    log.Printf("token refreshed: %v", accessToken)
    
    return nil
}
```

Now in the `scheduleRefreshToken()` function, we should make sure to call 
`refreshToken()` successfully for the first time so that a valid access token
is always available to be used. Then after that, we launch a new go routine. 
Here I use a `wait` variable to store how much time we need to wait before 
refreshing the token. Then let's launch an infinite loop, call `time.Sleep` to
wait, then after that amount of waiting time, call 
`interceptor.refreshToken()`. If an error occurs, we should only wait a short 
period of time let's say 1 second, before retrying it. If there's no error, 
then we definitely should wait for refresh duration, and the token refreshing
schedule is done.

```go
func (interceptor *AuthInterceptor) scheduleRefreshToken(refreshDuration time.Duration) error {
    err := interceptor.refreshToken()
    if err != nil {
        return err
    }
    
    go func() {
        wait := refreshDuration
        for {
            time.Sleep(wait)
            err := interceptor.refreshToken()
            if err != nil {
                wait = time.Second
            } else {
                wait = refreshDuration
            }
        }
    }()
    
    return nil
}
```

## Attach token to the request context
Now comes the important part: adding interceptors to attach the token to the
request context. Just like what we did on the server side, this time we define
a `Unary()` function to return a gRPC unary client interceptor. Let's follow
this `grpc.UnaryClientInterceptor` to see how the interceptor function should
look like.

```go
type UnaryClientInterceptor func(ctx context.Context, method string, req, reply interface{}, cc *ClientConn, invoker UnaryInvoker, opts ...CallOption) error
```

I will copy its signature and paste it here.

```go
func (interceptor *AuthInterceptor) Unary() grpc.UnaryClientInterceptor {
    type UnaryClientInterceptor func(ctx context.Context, method string, req, reply interface{}, cc *ClientConn, invoker UnaryInvoker, opts ...CallOption) error
}
```

This is the function that we should return. I'm gonna reformat it a bit and add
the missing `grpc` package.

```go
// Unary returns a client interceptor to authenticate unary RPC
func (interceptor *AuthInterceptor) Unary() grpc.UnaryClientInterceptor {
    return func(
        ctx context.Context,
        method string,
        req, reply interface{},
        cc *grpc.ClientConn,
        invoker grpc.UnaryInvoker,
        opts ...grpc.CallOption,
    ) error {
    
    }
}
```

Now inside this interceptor function, let's write a simple log with the calling
method name. Then check if this method needs authentication or not. If it does,
we must attach the access token to the context before invoking the actual RPC.

```go
func (interceptor *AuthInterceptor) Unary() grpc.UnaryClientInterceptor {
    return func(
        ctx context.Context,
        method string,
        req, reply interface{},
        cc *grpc.ClientConn,
        invoker grpc.UnaryInvoker,
        opts ...grpc.CallOption,
    ) error {
        log.Printf("--> unary interceptor: %s", method)
        
        if interceptor.authMethods[method] {
            return invoker(interceptor.attachToken(ctx), method, req, reply, cc, opts...)
        }
    }
}
```

So I will define a new function to attach the token to the input context and
return the result. In this function, we just use 
`metadata.AppendToOutgoingContext()` pass in the input context together with
an "authorization" key and the access token value. Make sure that the 
authorization key string matches with the one we used on the server side.

```go
func (interceptor *AuthInterceptor) attachToken(ctx context.Context) context.Context {
    return metadata.AppendToOutgoingContext(ctx, "authorization", interceptor.accessToken)
}
```

Now come back to our unary interceptor. If the method doesn't require 
authentication, then nothing to be done, we simply invoke the RPC with the 
original context.

```go
// Unary returns a client interceptor to authenticate unary RPC
func (interceptor *AuthInterceptor) Unary() grpc.UnaryClientInterceptor {
    return func(
        ctx context.Context,
        method string,
        req, reply interface{},
        cc *grpc.ClientConn,
        invoker grpc.UnaryInvoker,
        opts ...grpc.CallOption,
    ) error {
        // ...
        
        return invoker(ctx, method, req, reply, cc, opts...)
    }
}
```

The stream interceptor can be implemented in a similar way. Follow this 
`grpc.StreamClientInterceptor` function name to copy its signature, paste it
here with a `return`, fix the code format, add the missing `grpc` package.

```go
// Stream returns a client interceptor to authenticate stream RPC
func (interceptor *AuthInterceptor) Stream() grpc.StreamClientInterceptor {
    return func(
        ctx context.Context,
        desc *grpc.StreamDesc,
        cc *grpc.ClientConn,
        method string,
        streamer grpc.Streamer,
        opts ...grpc.CallOption,
    ) (grpc.ClientStream, error) {
        
    }
}
```

Then write a log, check if the method requires authentication or not. If it 
does, call streamer with the context that has been attached with access token
or else just call streamer with the original one. And we're done with the 
client-side auth interceptor.

```go
// Stream returns a client interceptor to authenticate stream RPC
func (interceptor *AuthInterceptor) Stream() grpc.StreamClientInterceptor {
    return func(
        ctx context.Context,
        desc *grpc.StreamDesc,
        cc *grpc.ClientConn,
        method string,
        streamer grpc.Streamer,
        opts ...grpc.CallOption,
    ) (grpc.ClientStream, error) {
        log.Printf("--> stream interceptor: %s", method)
    
        if interceptor.authMethods[method] {
            return streamer(interceptor.attachToken(ctx), desc, cc, method, opts...)
        }
        
        return streamer(ctx, desc, cc, method, opts...)
    }
}
```

## Refactor the gRPC client
Now let's open the `cmd/client/main.go` file. Before adding the interceptors to the
client, I'm gonna refactor this a bit. Let's move this functions `createLaptop`,
`searchLaptop`, `uploadImage`, `rateLaptop` to a separate `laptop_client.go` 
file inside the new `client` package. In this file, let's define a 
`LaptopClient` struct.

```go
// LaptopClient is a client to call laptop service RPCs
type LaptopClient struct {
    service pb.LaptopServiceClient
}
```

It will contain the `LaptopServiceClient` object. Then as usual, we
define a function to create a new laptop client object. It will receive a 
client connection as input. Then use that connection to make a new laptop 
service client.

```go
// NewLaptopClient returns a new laptop client
func NewLaptopClient(cc *grpc.ClientConn) *LaptopClient {
    service := pb.NewLaptopServiceClient(cc)
    return &LaptopClient{
        service: service,
    }
}
```

Now we must turn this `createLaptop` function into a method of `LaptopClient` 
object and make it public with an uppercase letter `C`. We need to add 
`.service` to fix error.

```go
// Create Laptop calls create laptop RPC
func (laptopClient *LaptopClient) CreateLaptop(laptop *pb.Laptop) {
    // ...
    
    res, err := laptopClient.service.CreateLaptop(ctx, req)
}
```

Then we do similar things for this `searchLaptop` function, this `uploadImage`
function and finally this `rateLaptop` function. 

```go
// SearchLaptop calls search laptop RPC
func (laptopClient *LaptopClient) SearchLaptop(filter *pb.Filter) {
    // ...
    stream, err := laptopClient.service.SearchLaptop(ctx, req)
}

// UploadImage calls upload image RPC
func (laptopClient *LaptopClient) UploadImage(laptopID string, imagePath string) {
    // ...
    
    stream, err := laptopClient.service.UploadImage(ctx)
}

// RateLaptop calls rate laptop RPC
func (laptopClient *LaptopClient) RateLaptop(laptopIDs []string, scores []float64) error {
    // ...
    
    stream, err := laptopClient.service.RateLaptop(ctx)
}
```

All is done. Now let's go back to the `cmd/client/main.go` file. We have to 
change this `pb.LaptopServiceClient` to `client.LaptopClient` then call
`laptopClient.CreateLaptop` here. We do the same things for all other test
functions.

```go
func testCreateLaptop(laptopClient *client.LaptopClient) {
    laptopClient.CreateLaptop(sample.NewLaptop())
}

func testUploadImage(laptopClient *client.LaptopClient) {
    laptop := sample.NewLaptop()
    laptopClient.CreateLaptop(laptop)
    laptopClient.UploadImage(laptop.GetId(), "tmp/laptop.jpg")
}

func testSearchLaptop(laptopClient *client.LaptopClient) {
    for i := 0; i < 10; i++ {
        laptopClient.CreateLaptop(sample.NewLaptop())
    }
    
    // ...
    
    laptopClient.SearchLaptop(filter)
}

func testRateLaptop(laptopClient *client.LaptopClient) {
    // ...
	
    for i := 0; i < n; i++ {
        // ...
        laptopClient.CreateLaptop(laptop)
    }

    for {
    	// ...
    	
        err := laptopClient.RateLaptop(laptopIDs, scores)
        
        // ...
    }
}
```

## Add auth interceptor to the client
OK, now in the `main` function, here we should call `client.NewLaptopClient()`
and pass in the gRPC connection. Now this is a bit tricky, but we're gonna 
need a separate connection for the auth client because it will be used to 
create an auth interceptor, which will be used to create another connection
for the laptop client. So I've changed the connection name to `cc1` and create
a new auth client with it.

```go
func main() {
    // ...
    
    cc1, err := grpc.Dial(*serverAddress, grpc.WithInsecure())
    // ...
    
    authClient := client.NewAuthClient(cc1, username, password)
    interceptor, err := client.NewAuthInterceptor(authClient, authMethods(), refreshDuration)
}
```

To make it simple I will define username and password as constants.

```go
const (
    username = "admin1"
    password = "secret"
)
```

Then let's create a new interceptor with the auth client. The refresh duration
will also be a constant. Let's say for this demo, we will refresh it every 30
seconds.

```go
const (
    // ...
    refreshDuration = 30 * time.Second
)
```

And we also need a function to define the list of methods that requires 
authentication. I'm just gonna copy and paste them from the server interceptor.
Change `map[string][]string` to a `map[string]bool` and change all these values to `true`. 

```go
func authMethods() map[string]bool {
    const laptopServicePath = "/techschool_pcbook.LaptopService/"
    
    return map[string]bool{
        laptopServicePath + "CreateLaptop": true,
        laptopServicePath + "UploadImage":  true,
        laptopServicePath + "Ratelaptop":   true,
    }
}
```

Alright, back to the `main` function. If an error occurs during calling
`client.NewAuthInterceptor`, we write a fatal log and exit. If not, we will
dial server to create another connection, but this time, we also add 2 dial
options: the unary interceptor and the stream interceptor. Then we change this
connection to `cc2`. And we're done with the client.

```go
func main() {
    // ...
    if err != nil {
        log.Fatal("cannot create auth interceptor: ", err)
    }
    
    cc2, err := grpc.Dial(
        *serverAddress,
        grpc.WithInsecure(),
        grpc.WithUnaryInterceptor(interceptor.Unary()),
        grpc.WithStreamInterceptor(interceptor.Stream()),
    )
    if err != nil {
        log.Fatal("cannot dial server: ", err)
    }
    
    laptopClient := client.NewLaptopClient(cc2)
    testRateLaptop(laptopClient)
}
```

## Test the client's auth interceptor
Let's open the terminal and try it. First run the server,

```shell
make server
```

then run 

```shell
make client
```

Oops, it says: client is up to date. That's because "make" confuses our new 
`client` folder with the `client` command. To fix this, we must add it to the 
PHONY list in the `Makefile`. Actually, I'm gonna add all of these commands to
the PHONY list.

```makefile
gen:
	protoc --proto_path=proto --go_out=pb --go-grpc_out=pb proto/*.proto
clean:
	rm pb/*.go
server:
	go run cmd/server/main.go -port 8080
client:
	go run cmd/client/main.go -address 0.0.0.0:8080
test:
	go test -cover -race ./...

.PHONY: gen clean server client test
```

Then back to the terminal and run `make client` again. This time we can see 
the "token refreshed" and "unary interceptor" logs, and 3 laptops were created
successfully.

```shell
2021/04/16 19:10:28 token refreshed: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MTg1NzUwMjgsInVzZXJuYW1lIjoiYWRtaW4xIiwicm9sZSI6ImFkbWluIn0.gQJ71LrEqYggkksDAXhpzpyMs7sjcOSrgft-H23T47w
2021/04/16 19:10:28 created laptop with id: 7f2d7032-4a62-4e0b-9f63-217bf53afc94
2021/04/16 19:10:28 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/04/16 19:10:28 created laptop with id: 26dc6266-cd4e-4dff-8f37-62ab2d20e58f
2021/04/16 19:10:28 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/04/16 19:10:28 created laptop with id: 7b1dcd35-5c32-4a1a-b565-3de4c87ef83f
```

Let's enter "y" to rate them. All successful, and we can now see the log for
stream interceptor.

```shell
2021/04/16 19:13:04 --> stream interceptor: /techschool_pcbook.LaptopService/RateLaptop
```

On the server side we also see some logs for both unary and stream 
interceptors.

```shell
2021/04/16 19:10:28 --> unary interceptor:  /techschool_pcbook.LaptopService/CreateLaptop
```
```shell
2021/04/16 19:13:04 --> stream interceptor:  /techschool_pcbook.LaptopService/RateLaptop
```

And after a while, on the client side, we will see that the token is getting 
refreshed as well. Before we finish let's change this username to `user1`

```go
const (
    username = "user1"
    password = "secret"
)
```

and see what happens. As you might expect, we've got permission denied error,

```shell
2021/04/16 19:18:55 cannot create laptop: rpc error: code = PermissionDenied desc = no permission to access this RPC
```

because only a user with the admin role can call `CreateLaptop` API. So 
everything is working perfectly. Awesome! And that's it for today's lecture. I
hope you enjoyed it. Thanks for reading, and I'll catch you guys later.