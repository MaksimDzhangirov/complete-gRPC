// ...

pub struct HelloRequest {
    pub name: ::std::string::String,
}

pub struct HelloResponse {
    pub greet: ::std::string::String,
}

pub struct WelcomeServiceClient {
    client: ::grpcio::Client,
}

pub trait WelcomeService {
    fn hello(&mut self, req: HelloRequest,
        sink: ::grpcio::UnarySink<HelloResponse>);
}

// ...