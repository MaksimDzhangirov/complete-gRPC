# Protobuf deep-dive and Go package option
Welcome to the second hans-on lecture of protocol buffer. In the last lecture, 
we have learned some basic syntax and data types. So now let's dive deeper into 
it. Here are the things that we're going to do:

* we will define and use custom types in protocol-buffer message fields, such 
  as enums or other messages.
* we will discuss when to use nested types and when not to. We will also learn
about some well-known types that were already defined by Google.
* we're gonna write many messages and organise them into multiple proto files,
  put them into a package, then import them into other places.
* we will learn about `repeated` field, `one of` field, and how to use option 
  to tell `protoc` to generate go code with the package name that we want.

## Multiple messages in 1 file
OK, let's start from where we left off in the last lecture. In 1 proto file, 
we can define multiple messages. So I will add a GPU message here. It makes 
sense because GPU is also a processor. It will have some similar fields as the
CPU, such as the brand, name, min and max frequency. Just one different thing 
is that it has its own memory.

## Custom types: message and enum
Memory is a very popular term that can be used in other places, such as the 
RAM or storage (or persistent drive).

`processor_message.proto`
```protobuf
syntax = "proto3";

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

message GPU {
  string brand = 1;
  string name = 2;
  double min_ghz = 3;
  double max_ghz = 4;
  // Memory?
}
```

And it has many different measurement units, such as kilobyte, megabyte,
gigabyte, or terabyte. So I will define it as a custom type, in a separate 
proto file, so that we can reuse it later. Let's call it `memory_message.proto`.

`memory_message.proto`
```protobuf
syntax = "proto3";

message Memory {
  enum Unit {
    UNKNOWN = 0;
    BIT = 1;
    BYTE = 2;
    KILOBYTE = 3;
    MEGABYTE = 4;
    GIGABYTE = 5;
    TERABYTE = 6;
  }
  
  uint64 value = 1;
  Unit unit = 2;
}
```

First, we need to define the measurement units. To do that, we will use `enum`. 
And because this unit should only exists with the context of the memory, we 
should define it as a nested type inside the `Memory` message. The convention
is, always use a special value to serve as default value of your enum. And 
assign the tag 0 for it. Then we add other units, from `BIT` to `TERABYTE`. I 
know there are more, but for this app I think TERABYTE is enough. OK, so now 
the `Memory` message will have a `value` field and a `unit` field. Looks good!

## Import proto files
Let's go back to the `processor_message.proto` file. We have to import the 
`memory_message.proto` file in order to use it. In the GPU message, we add a 
new memory field of type `Memory`.

`processor_message.proto`
```protobuf
syntax = "proto3";

import "memory_message.proto";

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

message GPU {
  string brand = 1;
  string name = 2;
  double min_ghz = 3;
  double max_ghz = 4;
  Memory memory = 5;
}
```

Now let's try to regenerate Go codes.

```shell
make gen
```

If we don't specify the `package` option in the files, then we will receive 
an error 

```shell
Go package "." has inconsistent names memory_message (memory_message.proto)
and processor_message (processor_message.proto)
```

We got this error because if we haven't specified the package name in the 
proto files, by default, `protoc` will use the file name as the Go package. 
The reason `protoc` throws an error here is that, the 2 generated Go files will 
belong to 2 different packages. But in Go, we cannot put 2 files of different 
packages in the same folder. In this case, the `pb` folder.

So we must tell `protoc` to put them in the same package by specifying it in 
out proto files. Let's call it `techschool_pcbook`. Now let's run `make gen`
again.

```protobuf
syntax = "proto3";

package techschool_pcbook;

message Memory {
  enum Unit {
    UNKNOWN = 0;
    BIT = 1;
    BYTE = 2;
    KILOBYTE = 3;
    MEGABYTE = 4;
    GIGABYTE = 5;
    TERABYTE = 6;
  }

  uint64 value = 1;
  Unit unit = 2;
}
```

```shell
make gen
```

It works! If we open the 2 generated Go files, we can see that they have the 
same package `techschool_pcbook`.

## Update proto_path setting for VSCode
Now there's one thing I want to show you here. Let's get back to our processor 
proto file. Although we have successfully generated the Go codes, Visual Studio
Code is still showing some red lines on the `Memory` and import command. The 
problem is, by default, the `vscode proto3` extension uses out current working 
folder as the `proto_path` when it runs `protoc` for code analysis. So it 
cannot find the "memory_message.proto" file in `lecture6` folder to import. If 
we change the path to `proto/memory_message.proto` then it won't complain 
anymore. However, I don't want to do that, because later we will use these 
proto files in out Java project with different directory structure. So I'm 
gonna show you how to fix this by changing the `proto_path` settings of the 
`vscode-proto3` extension. Let's open the extension tab and look for 
`vscode-proto3`. We can see the settings right here, but somehow it doesn't 
allow me to copy. So I'm gonna click on this link to copy it from the [web](https://github.com/zxh0/vscode-proto3/blob/master/example/.vscode/settings.json) 
instead. Alright, now go to `Code` menu, `Preference`, `Settings`, and search 
for `protoc`. Click `Edit in settings.json`. Paste the config here. We can 
get the `protoc` path by running:

```shell
which protoc
```

in the terminal.

Then set the `proto_path` to proto. OK, let's save this file and restart the 
Visual Studio Code. The error is gone.

```json
{
    "protoc": {
        "path": "/usr/local/bin/protoc",
        "options": [
            "--proto_path=proto"
        ]
    }
}
```

## Install clang-format to automatic format code
However, when I add a tab here and save the file. It's not automatically 
formatted. Although, in the last lecture, we have installed the extension to 
call `clang-format` library, we haven't installed the library yet. So let's 
install it with Homebrew and restart Visual Studio Code. Now I'm gonna add a 
tab here, and save the file. It works! Awesome! `Clang-format` has updated the 
files with 2-space indentation.

## Define Storage message
Let's continue with our project. I'm gonna create a new message for the storage.
A storage could be a hard disk driver or a solid state driver. So we should 
define a `Driver` enum with these 2 values. And don't forget the default value.
Now let's add 2 fields to the `Storage` message: the driver type, and the 
memory size.

`storage_message.proto`
```protobuf
syntax = "proto3";

package techschool_pcbook;

import "memory_message.proto";

message Storage {
  enum Driver {
    UNKNOWN = 0;
    HDD = 1;
    SSD = 2;
  }

  Driver driver = 1;
  Memory memory = 2;
}
```

Then run `make gen`. The code is generated successfully.

## Use option to generate custom package name for Go
But the package name that `protoc` generates for us is a bit too long, and 
doesn't match with the name of the `pb` folder. So I want to tell it to use 
`pb` as the package name, but just for Go because Java or other languages will
use a different package naming convention. We can do that by setting 
`option go_package=".;pb"` in our proto files. We can run commands directly 
from the `Terminal` tab. Now if we run `make gen` to generate codes, all the 
generated Go files will use the same `pb` package.

## Define Keyboard message
Next, we will define the `Keyboard` message. It can use a QWERTY, QWERTZ or 
AZERTY layout. For your information, QWERTZ is used widely in Germany while in
France, AZERTY is more popular. The keyboard can be backlit or not, so let's 
use a boolean field for it. It's very simple, right?

`keyboard_message.proto`
```protobuf
syntax = "proto3";

package techschool_pcbook;

option go_package = ".;pb";

message Keyboard {
  enum Layout {
    UNKNOWN = 0;
    QWERTY = 1;
    QWERTZ = 2;
    AZERTY = 3;
  }

  Layout layout = 1;
  bool backlit = 2;
}
```

## Define Screen message
Now let's write a more complex message: the screen. It has a nested message
type: Resolution. The reason we use nested type here is that: resolution is an 
entity that has a close connection with the screen. It doesn't have any meaning
when standing alone. Similarly, we have an enum for screen panel, which can be
`IPS` or `OLED`. Then the screen size in inch, and finally a bool field to 
tell if it's a multitouch screen or not. OK, now we can regenerate the codes.

`screen_message.proto`
```protobuf
syntax = "proto3";

package techschool_pcbook;

option go_package = ".;pb";

message Screen {
  message Resolution {
    uint32 width = 1;
    uint32 height = 2;
  }
  
  enum Panel {
    UNKNOWN = 0;
    IPS = 1;
    OLED = 2;
  }
  
  float size_inch = 1;
  Resolution resolution = 2;
  Panel panel = 3;
  bool multitouch = 4;
}
```

## Define Laptop message
Alright, I think basically we've defined all necessary components of a laptop. 
So let's define the `Laptop` message now. It has a unique identifier of type
`string`. It has a brand, a name, then CPU and RAM. We need to import other 
proto files to use the types.

### Repeated field
A laptop can have more than 1 GPU, so we use the "repeated" keyword to tell 
`protoc` that this is a list of GPUs. Similarly, it's normal for a laptop to 
have multiple storages, so this field should be `repeated` as well. Then comes 
2 normal fields: screen and keyboard. It's pretty straight-forward.

### Oneof field
How about the weight of the laptop? Let's say, we allow it to be specified in 
either kilograms or pounds. In order to do that, we can use a new keyword: 
`oneof`. In this block, we define 2 fields, one for kilograms and the other for
pounds. Remember that when you use `oneof` fields group, only the field that 
get assigned last will keep its value.

### Well-known types
OK, now let's add 2 more fields: the price in USD and the release year of the 
laptop. And finally, we need a timestamp to store the last update time of the 
record in our system. Timestamp is one of the well-known types that have 
already been defined by Google. So we just need to import the package and use 
it. There are may other well-known types. Please check out the link in the 
description to learn more about [them](https://developers.google.com/protocol-buffers/docs/reference/google.protobuf).
Now, let's run `make gen` to make sure that everything is working properly. 
Yes! All files are generated perfectly.

`laptop_message.proto`
```protobuf
syntax = "proto3";

package techschool_pcbook;

option go_package = ".;pb";

import "processor_message.proto";
import "memory_message.proto";
import "storage_message.proto";
import "screen_message.proto";
import "keyboard_message.proto";
import "google/protobuf/timestamp.proto";

message Laptop {
  string id = 1;
  string brand = 2;
  string name = 3;
  CPU cpu = 4;
  Memory ram = 5;
  repeated GPU gpus = 6;
  repeated Storage storages = 7;
  Screen screen = 8;
  Keyboard keyboard = 9;
  oneof weight {
    double weight_kg = 10;
    double weight_lb = 11;
  }
  double price_usd = 12;
  uint32 release_year = 13;
  google.protobuf.Timestamp updated_at = 14;
}
```

Hooray! We've learned a lot about protocol buffer and how to generate Golang
codes from it. In the next hans-on lecture, we will switch to Java. I will show
you how to setup a Gradle project to automatically generate Java codes from 
out proto files. Thanks a lot for reading, and see you later!