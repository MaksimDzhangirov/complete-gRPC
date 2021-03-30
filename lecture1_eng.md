# The complete gRPC course
Hello and welcome to Tech School. In this course, we are going to learn about
[gRPC](https://ru.wikipedia.org/wiki/GRPC) and how to use it with
[protocol buffer](https://ru.wikipedia.org/wiki/Protocol_Buffers)
to develop an application in Go and Java.
## The motivation of gRPC
OK. Let's start with this simple question: What is the motivation of gRPC?
Or, what's the problem that gRPC is trying to solve? Well the answer is 
communication. Applications are written using different programming languages
for example the back end can be written in Go while the front end like the 
Android app is written in Java and iOS app is written in Swift. So how do they
talk to each other?

Today's trend is using micro-services architecture so even on the backend side
we might have many services written in different languages like Go, Python or
Rust depending on the business needs and the technical constraints so in order
to communicate with each other, they must all agree on a set of API contracts
for instance: the communication channel (REST, SOAP, message queue), the 
authentication mechanism (Basic, OAuth, JWT), the payload format (JSON, XML, 
binary), the data model, and how to handle errors.

There are so many things that needs to be considered and that's why building
an API is really hard. More than that, we want the communication to be 
efficient by which I mean fast and lightweight. You know, the number of
exchange messages between microservices are huge so the faster communication
the better. Also, in some environments like mobile app, where the network 
speed and bandwidth is limited. It's very important to have a lightweight 
communication protocol to interact with the server.

Last but not least, we want the communication to be simple. Let's 
imagine that we are building a system that had hundreds or maybe thousands of
micro-services, we definitely don't want to spend most of the time writing 
codes only to enable them to talk to each other, right?

What we would like to have is some kind of framework that allow developers to 
focus on implementing the core logic of their services and leave everything 
else to the framework to handle. And that framework is nothing else but gRPC.

OK, so now you've got some ideas about the problems that gRPC is trying to
solve.

In the next lecture we're going to learn exactly what gRPC is and how it works
in order to achieve that goal.