# Write a simple protobuf message and generate Go codes
Hello everyone, let's start the hands-on section of the gRPC course. The target
of the whole section is to build a PC book web service that will allow us to 
manage and search for laptop configurations. In this lecture, we will learn how
to write a very simple protocol buffer message with some basic datatypes and how
to write comments for them. We will also learn how to install Visual Studio Code
plugins to work with protobuf. Finally, we will install protocol buffer
compiler and write a make file to run code generation for Go. But before start 
make sure that you already have Go and Visual Studio Code, up and running 
properly on your computer. If not, you can watch [tutorial](https://youtu.be/jRLV-1GVET4)
video on how to install Go and set up Visual Studio Code. The tutorial will 
guide you step-by-step to install Go, add the `bin` folder to your PATH, 
install Visual Studio Code, customise its theme and setup go extensions to work
with it. Once everything is ready, you can come back here and continue this
lecture.

## Install VSCode plugins
Alright. Let's start by creating a new project. First, I will create a simple
[Hello world program](code/lecture6/main.go) in Go and run it just to make sure
that Go is working properly. Then I'm gonna create a new folder named `proto` 
and add a `processor_message.proto` file under it. Now VSCode is asking if we 
want to install extensions for the proto file or not. Yes, we do. So let's 
click search marketplace. There are 2 extensions shown at the top that we 
should install: `clang-format` and `VSCode-proto3`.

## How to define a protobuf message
Okay, now come back to our proto file. This file will contain the message 
definition of the CPU of a laptop. We start with `syntax="proto3"`. At the 
time I wrote this lecture there are 2 versions of protocol buffer on [Google's 
official documentation](https://developers.google.com/protocol-buffers/docs/overview):
`proto2` and `proto3`. For simplicity, we will only use `proto3` (the newer 
version) in this course. Now, let's see how to define a message. The syntax is
pretty simple. Just use the `message` keyword followed by the name of the 
message. Then inside the message block, we define all of its fields as shown 
in this picture.

![Proto-file-example](images/lecture6/proto_file_example.png)

**Picture 1** - Proto file example.

The name of the message should be upper camel case and the name of the field 
should be lower snake case. There are many build-in scalar-value data types 
that we can use. For instance `string`, `bool`, `byte`, `float` or `double` and
many `integer` types. We can also use our own data types such as enums or other
messages.

Each message field should be assigned a unique tag and tag is more important 
than the field name because protobuf will use it to serialise the message. A 
tag is simply an arbitrary integer with the smallest value of 1 and the biggest
value of 2 to power of 29 minus 1 except for numbers from 19000 to 19999 as 
they are reserved for internal protocol buffer implementation.

Note that tags from 1 to 15 take only byte to encode while those from 16 to 
2047 take two bytes. So you should use them wisely like saving tags from 1 to 
15 for very frequently occurring fields. Remember that the tags don't need 
to be in-order or sequential, but they must be unique for the same level fields 
of the message.

## Define the CPU message
OK, so let's get back to our proto file and define the CPU message. CPU will
have a brand of type `string`, such as "Intel" and a name also of the type 
`string`, for example "Core i7-9850". We can write comment for each field like 
this simular to command in Go (//) or other way, simular to Java like this
/* ... */. It's super easy. So I don't want to waste time on writing comments 
in this course. But keep in mind that you should always write detail comments 
when you work on a real project. Because it will help your current and future 
teammates a lot. Alright, now we want to know how many cores or threads the CPU
has. They cannot be negative. So let's use unsigned int 32 here. Next, it has 
the minimum and maximum frequency. For example 2.4 gigahertz or something like 
that. So it's safe to use `float` here or we can use `double` if we want. 
Also, you gave to add `go_package` option to `.proto` files to specify a name of 
the package where generated code will be stored. Just add line 
`option go_package = ".;pb";` at the beginning of proto file.

```protobuf
syntax = "proto3";

option go_package = "./pb";

message CPU {
  // Brand of the CPU
  string brand = 1;
  /*
   * Name of the CPU
   */
  string name = 2;
  uint32 number_cores = 3;
  uint32 number_threads = 4;
  double min_ghz = 5;
  double max_ghz = 6;
}
```

## Generate Go codes
Okay, looks like we've finished our first protobuf message. Now, how can we
generate Go codes from it? First, we need to install protocol buffer compiler
(or protoc). On macOS, we can easily do that with the help of Homebrew. Don't
worry of you don't have Homebrew installed, just open your browser, search for 
it then copy the install command on the site and run it in the terminal. On 
Linux you can use this [link](https://grpc.io/docs/protoc-installation/). Once
Homebrew is installed, we can go ahead to run:

```sh
brew install protobuf
```

That's it! We can check if is working or not by running the `protoc` command. 
Next we will go to `grpc.io` to copy and run 2 commands to install 2 go 
libraries. First the `golang gRPC` library

```shell
go get -u google.golang.org/grpc
```

and second, the `protoc-gen-go` library

```shell
go get -u google.golang.org/protobuf/cmd/protoc-gen-go
```

Now we are all set to generate Go codes, I will create a new folder named `pb` 
to store the generated Go codes. Then run this command:

```shell
protoc --proto_path=proto --go_out=pb proto/*.proto
```

Our proto file is located inside the `proto` folder, so we tell `protoc` to 
look for it in that folder. With the `go_out` parameter we tell `protoc` to use 
the gRPC plugins to generate Go codes and store them inside the pb folder that 
we've created before. Now if we open that folder in Visual Studio Code we will 
see a new file `processor_message.pb.go`. Look inside, there's a CPU struct and 
all fields with the correct data types as we defined in our protocol buffer 
file. There are some special fields used internally by gRPC to serialise the 
message, but we don't need to care about them. The bottom line is, some useful 
getter functions are also generated. So it looks great.

## Create a Makefile
However, the command that we used to generate codes is pretty long. So it's not 
very convenient to type when we update the proto file and want to regenerate 
the codes. Let's create a make-file with a short and simple command to do that.
Add a `gen` task, then paste the code generation command under it, also add a 
`clean` task to remove all generated go files whenever we want. We can define 
the run task to run the `main.go` file as well.

```makefile
gen:
	protoc --proto_path=proto --go_out=pb proto/*.proto
clean:
	rm pb/*.go
run:
	go run main.go
```

OK, let's try them in the terminal:

```shell
make clean
```

the generated file is now deleted,

```shell
make gen
```

the file is regenerated in `pb` folder.

Finally,

```shell
make run
```

`Hello world` is printed. Wow, it's been a long lecture. Thank you for
reading! We still have a lot to discover so stay tuned for the next one. Until 
then, happy coding!