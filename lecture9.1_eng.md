# Serialize protobuf message - Golang
Hello and welcome to the gRPC course. In previous lectures, we have learned how
to write protobuf messages and generate Go and Java codes from them. Today we 
will start using those generated codes to serialize an object to binary and 
JSON. In the first half of the lecture, we will code in Go. Specifically, we 
will write a protobuf message to a binary file. Then read back the content of
that file into another message. We will also write the message to a JSON file, 
then compare its size with the binary file to see which one is smaller. In the 
second half of the lecture, we will do similar things, but code in Java. Write a
protobuf message to a binary file, read it back, then write a JSON file. But 
this time, we will also try to read the binary file that was previously written
by Go code and write it to another JSON file to compare it with the original 
one. Alright, let's start!

## Generate random protobuf messages
I will open the `pcbook` Go project that we were working on in previous 
lectures. The first thing we need to do is to run `go mod init` to initialize
our package. I will name it `gitlab.com/techschool/pcbook`.

```shell
go mod init gitlab.com/techschool/pcbook
```

As you can see, a `go.mod` file is generated for us. Now let's create a `sample` 
package to generate some laptop data. I love using random data because it's 
very useful when writing unit tests since it will return different values for
each call, and the data look very natural and close to reality.

### Generate a random keyboard
OK, first we need a keyboard, so I define a function `NewKeyboard` which 
returns a pointer to the `pb.Keybord` object. It's really nice that Visual 
Studio Code has automatically imported the correct package for us. There's a 
warning here because Go expects to have a comment for all exported functions, 
the ones that start with an uppercase letter. Now we can create a keyboard 
object like this.

`sample/generator.go`
```go
package sample

// NewKeyboard returns a new sample keyboard
func NewKeyboard() *pb.Keyboard {
    keyboard := &pb.Keyboard{
        Layout:  randomKeyboardLayout(),
        Backlit: randomBool(),
    }
    return keyboard
}
```

It will have a layout, so I will have to write a function to generate a random 
keyboard layout. And also a function to generate a random boolean for the 
backlit field. Let's create a new file `random.go`. It's in the same `sample` 
package. I will write 2 placeholder functions here with no content yet. 

`sample/random.go`
```go
package sample

func randomBool() bool {
    
}

func randomKeyboardLayout() pb.Keyboard_Layout {
    
}
```

Now let's implement the `randomBool` function first because it's easier. A bool
only has 2 possible values: `true` or `false`. So I will use the `rand.Intn` 
function of the `math/rand` package, with `n` equal to 2. It will give us a 
random integer, which is either a 0 or 1. So let's return `true` if the value 
is 1. OK, now the `randomKeyboardLayout` function there are 3 possible values, 
so let's use `rand.Intn(3)`. If the value is 1 then return `QWERTY`. If the 
value is 2, then return `QWERTZ`, otherwise, return `AZERTY`.

`sample/generator.go`
```go
package sample

// NewKeyboard returns a new sample keyboard
func NewKeyboard() *pb.Keyboard {
    keyboard := &pb.Keyboard{
        Layout:  randomKeyboardLayout(),
        Backlit: randomBool(),
    }
    return keyboard
}
```

`sample/random.go`
```go
package sample

func randomBool() bool {
    return rand.Intn(2) == 1
}

func randomKeyboardLayout() pb.Keyboard_Layout {
    switch rand.Intn(3) {
    case 1:
        return pb.Keyboard_QWERTY
    case 2:
        return pb.Keyboard_QWERTZ
    default:
        return pb.Keyboard_AZERTY
    }
}
```

### Generate a random CPU
Next, a function to generate a random CPU. First I will create an empty CPU 
object and return it.

`sample/generator.go`
```go
func NewCPU() *pb.CPU {
    cpu := &pb.CPU{
        
    }
    
    return cpu
}
```

As you can see here, there are many fields that we need to fill. We will need
a function to return a random CPU brand. Let's go to the `random.go` file to
define it. One easy way to do that is to select a random value from a
predefined set of brands, such as "Intel" and "AMD".

`sample/random.go`
```go
func randomCPUBrand() string {
    return randomStringFromSet("Intel", "AMD")
}

func randomStringFromSet(a ...string) string {
    n := len(a)
    if n == 0 {
        return ""
    }
    return a[rand.Intn(n)]
}
```


Here I defined a `randomStringFromSet` function, which takes a set of variable
number of strings as input, and return 1 random string from that set. It's very
easy, just use the `rand.Intn()` function as before. Alright, now we can set 
the brand field if the CPU. Next we will generate a random CPU name based on 
the brand with this function. Since we know there are only 2 brands, a simple 
`if` here would be enough. To save time, I will paste in some sample CPU names 
that I've prepared before. Now set the name field of the CPU.

`sample/random.go`
```go
func randomCPUName(brand string) string {
    if brand == "Intel" {
        return randomStringFromSet(
            "Xeon E-2286M",
            "Core i9-9980HK",
            "Core i7-9750H",
            "Core i5-9400F",
            "Core i3-1005G1",
        )
    }

    return randomStringFromSet(
        "Ryzen 7 PRO 2700U",
        "Ryzen 5 PRO 3500U",
        "Ryzen 3 PRO 3200GE",
    )
}
```

The next field we have to fill is the number of cores. Let's say we want it to 
be between 2 cores and 8 cores. So we will need a `randomInt()` function to 
generate a random integer between `min` and `max`.

`sample/random.go`
```go
func randomInt(min, max int) int {
    return min + rand.Int(max-min+1)
}
```

The formula is `min + rand.Intn(max-min+1)`. In this formula, the `rand.Intn` 
function will return an integer from `0` to `max-min`. So if we add `min` to 
that function, we will get a value from `min` to `max`. It's pretty simple, 
right? OK, now we can set the `numberCores` field. It expects an 
`unsigned int32`, so we need to do a type conversion here. Similar for the 
number of threads, we will use a random integer between the number of cores and
12 threads. Alright, next field is `minGhz`, which is a `float64`. I want the 
CPU to have the minimum frequency between 2.0 and 3.5, so we need a function 
to generate a `float64` in range from `min` to `max`.

`sample/random.go`
```go
func randomFloat64(min, max float64) float64 {
    return min + rand.Float64()*(max-min)
}
```

It's a bit different from the `randomInt` function. Because the 
`rand.Float64()` function will return a random float between `0` and `1`. So we
will multiply it with `(max-min)` to get a value between `0` and `max-min`. 
When we add `min` to this value, we will get a number from `min` to `max`. 
I hope it's easy for you to understand. Let's come back to our generator. 
We will generate the max frequency to be a random `float64` between `min` 
frequency and `5.0` Ghz. And then set the `minGhz` and `maxGhz` field of the 
CPU object.

`sample/generator.go`
```go
func NewCPU() *pb.CPU {
    brand := randomCPUBrand()
    name := randomCPUName(brand)
    
    numberCores := randomInt(2, 8)
    numberThreads := randomInt(numberCores, 12)
    
    minGhz := randomFloat64(2.0, 3.5)
    maxGhz := randomFloat64(minGhz, 5.0)
    
    cpu := &pb.CPU{
        Brand:         brand,
        Name:          name,
        NumberCores:   uint32(numberCores),
        NumberThreads: uint32(numberThreads),
        MinGhz:        minGhz,
        MaxGhz:        maxGhz,
    }
    
    return cpu
}
```

### Generate a random GPU
The `NewGPU` function will be implemented the same way. We write a function to
return a random GPU brand, which can be either NVIDIA or AMD. Then we generate 
a random GPU name based on the brand.

`sample/random.go`
```go
func randomGPUBrand() string {
    return randomStringFromSet("NVIDIA", "AMD")
}

func randomGPUName(brand string) string {
    if brand == "NVIDIA" {
        return randomStringFromSet(
            "RTX 2060",
            "RTX 2070",
            "GTX 1660-Ti",
            "GTX 1070",
        )
    }
    
    return randomStringFromSet(
        "RX 590",
        "RX 580",
        "RX 5700-XT",
        "RX Vega-56",
    )
}
```

Just like before, I will paste in some values here to save us some time. Now 
we can set the value for the GPU brand and name. The `minGhz` and `maxGhz` 
fields are generated using the `randomFloat64` function that we have defined 
before. Let's say min frequency will be from `1.0` to `1.5`, and max frequency
will be from that `min` value up to `2.0` Ghz. Now there's one field left: 
the memory. We have to create a new `Memory` object here. Let's say we want it 
to be between 2 and 6 gigabytes. So we will use a `randomInt` function here 
with a type conversion to `unsigned int64`. For the unit, just use the 
`Memory_GIGABYTE` enum generated by `protoc`. It's very convenient! Now we're 
done with the GPU.

`sample/generator.go`
```go
func NewGPU() *pb.GPU {
    brand := randomGPUBrand()
    name := randomGPUName(brand)
    
    minGhz := randomFloat64(1.0, 1.5)
    maxGhz := randomFloat64(minGhz, 2.0)
    
    memory := &pb.Memory{
        Value: uint64(randomInt(2, 6)),
        Unit:  pb.Memory_GIGABYTE,
    }
    
    gpu := &pb.GPU{
        Brand: brand,
        Name: name,
        MinGhz: minGhz,
        MaxGhz: maxGhz,
        Memory: memory,
    }
    
    return gpu
}
```

### Generate a random RAM
The next thing is RAM. Well this one is too easy because it's almost identical 
to the GPU memory.

`sample/generator.go`
```go
func NewRAM() *pb.Memory {
    ram := &pb.Memory{
        Value: uint64(randomInt(4, 64)),
        Unit:  pb.Memory_GIGABYTE,
    }
    
    return ram
}
```

### Generate a random storage
Then comes the storage. We will define 2 different functions: 1 for SSD
and 1 for HDD. For the SSD, we will set the driver to be `Storage_SSD` and the 
memory size will be from `128` to `1024` gigabytes. I will duplicate this 
`NewSSD()` function and change it for the HDD. This time, the driver must be
`Storage_HDD`, and the memory size will be between 1 and 6 terabytes. Cool!

`sample/generator.go`
```go
func NewSSD() *pb.Storage {
    ssd := &pb.Storage{
        Driver: pb.Storage_SSD,
        Memory: &pb.Memory{
            Value: uint64(randomInt(128, 1024)),
            Unit: pb.Memory_GIGABYTE,
        },
    }
    
    return ssd
}

func NewHDD() *pb.Storage {
    hdd := &pb.Storage{
        Driver: pb.Storage_HDD,
        Memory: &pb.Memory{
            Value: uint64(randomInt(1, 6)),
            Unit: pb.Memory_TERABYTE,
        },
    }
    
    return hdd
}
```

### Generate a random screen
Now we will make a new screen. The size of the screen will be between 13 and 17
inches. It's a `float32` number, so let's define a `randomFloat32` function. 
It's the same as `randomFloat64` function, except for the types should be 
`float32`.

`sample/random.go`
```go
func randomFloat32(min float32, max float32) float32 {
    return min + rand.Float32()*(max-min)
}
```

Next, the screen resolution. We will set the height to be a random integer 
between 1080 and 4320. And calculate the width from the height with the ratio 
of 16 by 9.

`sample/random.go`
```go
func randomScreenResolution() *pb.Screen_Resolution {
    height := randomInt(1080, 4320)
    width := height * 16 / 9
    
    resolution := &pb.Screen_Resolution{
        Height: uint32(height),
        Width:  uint32(width),
    }
    
    return resolution
}
```

We must do a type conversion here because it expects an `unsigned int32`. Now 
the screen panel. Let's write a separate random function for it. In our 
application, there are only 2 types of panel: either `IPS` or `OLED`. So we 
just use `rand.Intn(2)` here, and a simple `if` would do the job.

`sample/random.go`
```go
func randomScreenPanel() pb.Screen_Panel {
    if rand.Intn(2) == 1 {
        return pb.Screen_IPS
    }
    return pb.Screen_OLED
}
```

The last field we have to set is the multitouch, which is just a random 
boolean.

`sample/generator.go`
```go
func NewScreen() *pb.Screen {
    screen := &pb.Screen{
        SizeInch: randomFloat32(13, 17),
        Resolution: randomScreenResolution(),
        Panel: randomScreenPanel(),
        Multitouch: randomBool(),
    }
    
    return screen
}
```

### Generate a random laptop
Alright, all the components are ready. Now we can generate a new laptop. First 
it needs a unique random identifier. So let's create a `randomID()` function 
for that. I'm gonna use google UUID. We can search for it on the browser, copy 
this `go get` command, and run it in the terminal to install the package.

```shell
go get github.com/google/uuid
```

Now go back to our code. We can call `uuid.New()` to get a random ID and convert
it to string.

`sample/random.go`
```go
func randomID() string {
    return uuid.New().String()
}
```

Next, we will generate the laptop brand and name similar to what we've done 
with the CPU and GPU. Again, I will just copy and paste some values
here to buy us some time.

`sample/random.go`
```go
func randomLaptopBrand() string {
    return randomStringFromSet("Apple", "Dell", "Lenovo")
}

func randomLaptopName(brand string) string {
    switch brand {
    case "Apple":
        return randomStringFromSet("Macbook Air", "Macbook Pro")
    case "Dell":
        return randomStringFromSet("Lalitude", "Vostro", "XPS", "Alienware")
    default:
        return randomStringFromSet("Thinkpad X1", "Thinkpad P1", "Thinkpad P53")
    }
}
```

The brands we use are `Apple`, `Dell` and `Lenovo`. We use `switch-case`
statement here to generate the correct name of the brand. Next, we will add 
the CPU and the RAM by calling their generator functions. The GPUs should be a 
list of values, so I define a slice here. Let's say we only have 1 GPU for now.
Similar for the storages, but this time I will add 2 items: 1 for the SSD and 
the other for the HDD. The screen and keyboard fields are pretty 
straight-forward. Then comes the `oneof` field: the `Weight`. We can either 
specify the weight in kilograms or pounds. There are 2 structs that `protoc` 
has generated for us. I'm gonna use the kilograms here. In `pb.Laptop_WeightKg`
struct, there's a `WeightKg` field. I will set it to a random value 
between 1 and 3 kilograms. The price is a random float between 1500 and 3000. 
The release year will be between 2015 and 2019. And finally the `updateAt` 
timestamp. We can use the `Now()` function provided by 
`google.golang.org/protobuf/types/known/timestamppb` package. We're done!

`sample/generator.go`
```go
func NewLaptop() *pb.Laptop {
    brand := randomLaptopBrand()
    name := randomLaptopName(brand)
    
    laptop := &pb.Laptop{
        Id: randomID(),
        Brand: brand,
        Name: name,
        Cpu: NewCPU(),
        Ram: NewRAM(),
        Gpus: []*pb.GPU{NewGPU()},
        Storages: []*pb.Storage{NewSSD(), NewHDD()},
        Screen: NewScreen(),
        Keyboard: NewKeyboard(),
        Weight: &pb.Laptop_WeightKg{
            WeightKg: randomFloat64(1.0, 3.0),
        },
        PriceUsd: randomFloat64(1500, 3000),
        ReleaseYear: uint32(randomInt(2015, 2019)),
        UpdatedAt: timestamppb.Now(),
    }
    
    return laptop
}
```

## Serialize protobuf messages
Now we will create a new `serializer` package and write some functions to
serialize the laptop objects to files. So let's create a `file.go` file in 
`serializer` folder.

### Write protobuf message to binary file
The first function will be used to write a protobuf message to a file in binary
format. In our case, the message would be the laptop object. We can use the 
`proto.Message` interface to make it more general. In this function, we first 
need to call `proto.Marshal` to serialize the message to binary. If an error 
occurs, we just wrap it and return to the caller. Else we will use 
`ioutil.WriteFile()` function to save the data to the specified filename. Again
wrap and return any error that occurs during this process. If every goes well, 
we just return `nil` here, meaning no errors. Now the function is completed.

```go
package serializer

import (
    "fmt"
    "io/ioutil"
    
    "github.com/golang/protobuf/proto"
)

// WriteProtobufToBinaryFile writes protocol buffer message to binary file
func WriteProtobufToBinaryFile(message proto.Message, filename string) error {
    data, err := proto.Marshal(message)
    if err != nil {
        return fmt.Errorf(
            "cannot marshal proto message to binary: %w",
            err,
        )
    }
    
    err = ioutil.WriteFile(filename, data, 0644)
    if err != nil {
        return fmt.Errorf("cannot write binary data to file: %w", err)
    }
    
    return nil
}
```

I'm gonna show you how to write a unit test for it. Let's create a 
`file_test.go` in `serializer` folder. Note that having the `_test` suffix in 
the filename is a must so Go can understand that it's a test file. Similarly, 
we have a convention for the unit test function name. It must start with Test 
prefix and takes a pointer to `testing.T` object as input. I usually call 
`t.Parallel()` for almost all of my unit tests, so that they can be run in  
parallel, and any racing condition can be easily detected.

```go
func TestFileSerializer(t *testing.T) {
    t.Parallel()
}
```

Alright, let's say we want to serialize the object to `laptop.bin` file inside 
the `tmp` folder. So we need to create the `tmp` folder first. Then use the 
`NewLaptop()` function to make a new `laptop1`. And call the 
`WriteProtobufToBinaryFile()` function to save it to the `laptop.bin` file. 
Since this function returns an error, we must check that this error is `nil`, 
which means the file is successfully written. To do that, I often use the 
`testify` package. Open the github page to copy this `go get` command, and run
it in your terminal.

```shell
go get github.com/stretchr/testify
```

Now we can simply call `require.NoError()` function here with `t` and `err`
parameters.

```go
package serializer

import (
    "testing"
    
    "github.com/MaksimDzhangirov/complete-gRPC/code/lecture9.1/sample"
    "github.com/MaksimDzhangirov/complete-gRPC/code/lecture9.1/serializer"
    "github.com/stretchr/testify/require"
)

func TestFileSerializer(t *testing.T) {
    t.Parallel()
    
    binaryFile := "../tmp/laptop.bin"
    
    laptop1 := sample.NewLaptop()
    err := serializer.WriteProtobufToBinaryFile(laptop1, binaryFile)
    require.NoError(t, err)
}
```

In Visual Studio Code, we can click this "Run test" link to run this test. 
There's an issue saying `import cycle not allowed in test`. It's because we're
in the same `serializer` package, but also import it. Just add `_test` to our
package name to make it a different package, and also tell Go that this is a
test package. Now if we re-run the test, it passed. Great! As we can see, the
`laptop.bin` file is written to the `tmp` folder.

### Read protobuf message from binary file
Now we will write another function to read back that binary file into a 
protobuf message object. I will name it function `ReadProtobufFromBinaryFile()`.
First we need to use `ioutil.ReadFile()` to read the binary data from the
file. Then we call `proto.Unmarchal()` to deserialize the binary data into a 
protobuf message.

`file/file.go`
```go
// ...
func ReadProtobufFromBinaryFile(filename string, message proto.Message) error {
    data, err := ioutil.ReadFile(filename)
    if err != nil {
        return fmt.Errorf("cannot read binary data from file: %w", err)
    }
    
    err = proto.Unmarshal(data, message)
    if err != nil {
        return fmt.Errorf(
            "canot unmarshal binary to proto message: %w",
            err,
        )
    }
    
    return nil
}
```

OK let's test it. In our unit test, I will define a new `laptop2` object, and 
call `ReadProtobufFromBinaryFile()` to read the file data into that object. We 
will check that there are no errors, and we also want to check that `laptop2` 
contains the same data as `laptop1`. To do that, we can use the `proto.Equal` 
function provided by the `golang/protobuf` package. This function must return
`true`, so we use `require.True()` here. Now let's run the test. It passed! 

```go
// ...

func TestFileSerializer(t *testing.T) {
    // ...
    
    laptop2 := &pb.Laptop{}
    err = serializer.ReadProtobufFromBinaryFile(binaryFile, laptop2)
    require.NoError(t, err)
    require.True(t, proto.Equal(laptop1, laptop2))
}
```

### Write protobuf message to JSON file
Now since the data is written in binary format, we cannot read it. Let's write 
another function to serialize it to `JSON` format instead. In this function, we 
must convert the protobuf message into a `JSON` string first. To do that, I will 
create a new function named `ProtobufToJSon`. And code it in a separate 
`json.go` file, under the same `serializer` package. OK, now to convert
a protobuf message to `JSON`, we can use the `jsonb.Marshaler` struct. 
Basically, we just need to call `marshaler.MarshalToString()` function.

`serializer/json.go`
```go
package serializer

import (
    "github.com/golang/protobuf/jsonpb"
    "github.com/golang/protobuf/proto"
)

func ProtobufToJSON(message proto.Message) (string, error) {
    marshaler := jsonpb.Marshaler{
        EnumsAsInts:  false,
        EmitDefaults: true,
        Indent:       " ",
        OrigName:     false,
    }
    return marshaler.MarshalToString(message)
}
```

There's a couple of things that we can config, such as write enumbs as integers
or strings, write fields with default value or not, what's the indentation we 
want to use, or do we want to use the original field name defined in the proto 
file. Let's use these configs for now, and we will try other values later. Now 
come back to our function. After calling `ProtobufToJSON`, we got the `JSON` 
string. All we need to do is to write that string to the file.

`serializer/file.go`
```go
func WriteProtobufToJSONFile(message proto.Message, filename string) error {
    data, err := ProtobufToJSON(message)
    if err != nil {
        return fmt.Errorf(
            "cannot marshal proto message to JSON: %w",
            err,
        )
    }
    
    err = ioutil.WriteFile(filename, []byte(data), 0644)
    if err != nil {
        return fmt.Errorf("cannot write JSON data to file: %w", err)
    }
    
    return nil
}
```

OK, now let's call this function in our unit test.

```go
// ...

func TestFileSerializer(t *testing.T) {
    // ...
    err = serializer.WriteProtobufToJSONFile(laptop1, jsonFile)
    require.NoError(t, err)
}
```

Check there's no errors returned, and run the test. Voila, the `laptop.json` 
file is successfully created. As you can see, the field names are exactly the 
same as we defined in our proto files, which is `lower_snake_case`. 
Now if we change this config `OrigName` to `false`, and rerun the test, we can
see that the field names have changed to `lowerCamelCase`. Similarly, all the 
enum fields are now written in `string` format, such as this `IPS` panel. If
we change the `EnumsAsInts` config to `true`, and rerun the test. Then the 
panel will become an `integer` 1 instead.
Now I want to make sure that the generated laptops are different every time we 
run the test. So let's run `go test ./...` multiple times to see what happens.

```shell
go test ./...
```

The `...` means that we want to run unit tests in all sub-packages. OK, looks
like only the unique ID is changed, the rest stays the same. It's because by
default, the `rand` package uses a fixed seed value. We have to tell it to use a 
different seed for each run. So let's create a `init()` function in `random.go`
file. This is special function that will be called exactly once before any 
other code in the package is executed. In this function, we will tell `rand` to
use the current unix nano as the seed value.

`sample/random.go`
```go
func init() {
    rand.Seed(time.Now().UnixNano())
}
```

Now let's run the test multiple times. We can see that the laptop data has 
changed. Excellent! 
We can also use command `go test ./serializer/file_test.go` to run only 1 
single test file. Alright, now let's define a `make test` command to run the 
unit tests. We can use the `-cover` flag to measure the code coverage of our 
tests and the `-race` flag to detect any racing condition in our codes.

```makefile
# ...
test:
    go test -cover -race ./...
# ...
```

Now run `make test` in the terminal.

```shell
make test
```

As you can see, 73.9% of the codes in the `serializer` package are covered. We
can also go to the test file, and click "run package tests" at the top. It will
run all test in that package and report the code coverage. Then we can open the
files to see which part of our code is covered and which one is not. The 
covered code is in blue and uncovered code is in red. This is very useful for 
us to write a strong test set to cover different branches.

### Compare size of binary and JSON file
There's one more thing I want to show you before we switch to Java. Let's open
the terminal, go to `tmp` folder and run `ls -l` command. We can see that the 
size of the json file is about 5 times bigger than of the binary file. So we 
will save a lot of bandwidth when using gRPC instead of a normal JSON API. And
since it's smaller, it's also faster to transport. That's the beautiful thing
of a binary protocol. See you in the Java section!