// ...
package client

type HelloRequest struct {
	Name string
}

type HelloResponse struct {
	Greet string
}

type WelcomeServiceClient interface {
	Hello(*HelloRequest) (*HelloResponse, error)
}

type WelcomeServiceServer interface {
	Hello(*HelloRequest) (*HelloResponse, error)
}

// ...
