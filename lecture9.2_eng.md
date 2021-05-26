# Serialize protobuf message - Java
Hi everyone, welcome back to the second half of the gRPC lecture 9. In the 
previous lecture, we have learned how to serialize protobuf message in Go. Now 
let's switch to Java.
## Generate random protobuf messages
Open the `Gradle` project that we've set up in previous lecture. I'm gonna create
a new package name `com.gitlab.techschool.pcbook.sample` inside `src/main/java`
folder and create a `Generator` class in it. The implementation will be very 
similar to what we have done in Go.
### Generate a random keyboard
First, the `NewKeyboard` function. It's super easy to create a protobuf message 
in Java because `protoc` has generated for us a lot of builder and setter 
functions. Here, we just call `Keyboard.newBuilder()` and chain it with 
`setLayout()` function with `randomKeyboardLayout()` as its parameter.

```java
Keyboard.newBuilder().
    .setLayout(randomKeyboardLayout())
```

In Intellij IDEA, we can use `Option + Enter` (on macOS) or `Alt + Enter`
(on Win and Linux) to open the code suggestion. I will choose "Create a 
new method" and the `randomKeyboardLayout()` method will be automatically
created. Similarly, we can chain the builder with the `setBacklit()` function. 
Now we need to use a `Random` object to generate random values. So I will add
a private `rand` here.

```java
private Random rand;
```

And initialize it inside the `Generator` constructor.

```java
public Generator() {
    rand = new Random();
}
```

If we press Command (on macOS) or Ctrl (Win or Linux) and click on this
`new Random()` we will go to its implementation. Here we can see that the 
random generator is already using a time-based seed. So we don't need to set 
it manually as we did in Go. OK, now we can use `rand.nextBoolean()` to set 
the backlit field like this.

```java
.setBacklit(rand.nextBoolean())
```

And finally call `build()` function to build the keyboard object and return it.

```java
public Keyboard NewKeyboard() {
    return Keyboard.newBuilder().
                .setLayout(randomKeyboardLayout())
                .setBacklit(rand.nextBoolean())
                .build();
}
```

The implementation of `randomKeyboardLayout()` function is pretty simple. With 
the help of `rand.NextInt()`, just like what we did in Go with rand.Intn() we 
can return QWERTY, QWERTZ or AZERTY based on the random value.

```java
private Keyboard.Layout randomKeyboardLayout() {
    switch (rand.nextInt(3)) {
        case 1:
            return Keyboard.Layout.QWERTY;
        case 2:
            return Keyboard.Layout.QWERTZ;
        default:
            return Keyboard.Layout.AZERTY;
    }
}
```
### Generate a random CPU
Next, `NewCPU()` function. We will need a random CPU brand, which can be either
"Intel" or "AMD".

```java
public CPU NewCPU() {
    String brand = randomCPUBrand();
}

private String randomCPUBrand() {
    return randomStringFromSet("Intel", "AMD");
}
```

Let's write a `randomStringFromSet` function to return a random string in a set.
The idea is all the same as before. We use `rand.NextInt` to get a random index
and return the string at that position.

```java
private String randomStringFromSet(String ...a) {
    int n = a.length;
    if (n == 0) {
        return "";
    }
    return a[rand.nextInt(n)];
}
```

The CPU name will be randomly generated based on the brand. As we have only 2
brands, I will just use a simple `if` here.

```java
public CPU NewCPU() {
    // ...
    String name = randomCPUName(brand);
}

private String randomCPUName(String brand) {
    if (brand == "Intel") {
        return randomStringFromSet(
                "Xeon E-2286M",
                "Core i9-9980HK",
                "Core i7-9750H",
                "Core i5-9400F",
                "Core i3-1005G1"
        );
    }
    return randomStringFromSet(
            "Ryzen 7 PRO 2700U",
            "Ryzen 5 PRO 3500U",
            "Ryzen 3 PRO 3200GE"
    );
}
```

The number of cores can be easily generated with the `randomInt` function. It
will return a random integer between `min` and `max`. This formula is the same
with what we have used in Go. Similar for the number of threads. We use 
`randomInt` to generate a number between number cores and 12.

```java
public CPU NewCPU() {
    /// ...

    int numberCores = randomInt(2, 8);
    int numberThreads = randomInt(numberCores, 12);
}
private int randomInt(int min, int max) {
    return min + rand.nextInt(max - min + 1);
}
```

Same for the frequencies, except that we must define a function to generate a 
random double number between `min` and `max`. It's a bit different from 
`randomInt` because the `nextDouble` function returns a number between 0 and 1.

```java
public CPU NewCPU() {
    // ...
    
    double minGhz = randomDouble(2.0, 3.5);
    double maxGhz = randomDouble(minGhz, 5.0);
}
private double randomDouble(double min, double max) {
    return min + rand.nextDouble() * (max - min);
}
```

Now all we have to do is to create a new builder, use the setter functions to 
set the value for all fields, then build the object and return it.

```java
public CPU NewCPU() {
    // ...

    return CPU.newBuilder()
        .setBrand(brand)
        .setName(name)
        .setNumberCores(numberCores)
        .setNumberThreads(numberThreads)
        .setMinGhz(minGhz)
        .setMaxGhz(maxGhz)
        .build();
}
```
### Generate a random GPU
OK, the CPU is done, now the GPU. We will write a function to return a random
GPU brand which can be NVIDIA or AMD. Then we generate a random GPU name
based on the brand. Again, I will use a simple `if` here and paste in some 
values.

```java
public GPU NewGPU() {
    String brand = randomGPUBrand();
    String name = randomGPUName(brand);
}

private String randomGPUBrand() {
    return randomStringFromSet("NVIDIA", "AMD");
}

private String randomGPUName(String brand) {
    if (brand == "NVIDIA") {
        return randomStringFromSet(
                "RTX 2060",
                "RTX 2070",
                "GTX 1660-Ti",
                "GTX 1070"
        );
    }

    return randomStringFromSet(
            "RX 590",
            "RX 580",
            "RX 5700-XT",
            "RX Vega-56"
    );
}
```

The `min` and `max` frequencies are generated using the `randomDouble` function
that we have defined before. It's very similar to the CPU, just one different 
thing is that we have to build the memory object. Let's say we want it to be 
between 2 and 6 gigabytes. The Memory Unit enum was generated for us, so all we
need to do is to use it.

```java
public GPU NewGPU() {
    // ...

    double minGhz = randomDouble(1.0, 1.5);
    double maxGhz = randomDouble(minGhz, 2.0);

    Memory memory = Memory.newBuilder()
            .setValue(randomInt(2, 6))
            .setUnit(Memory.Unit.GIGABYTE)
            .build();
}
```

Then we can build the GPU and set value for its fields. The setter functions 
that `protoc` has generated for us are very convenient.

```java
public GPU NewGPU() {
    // ...
    
    return GPU.newBuilder()
            .setBrand(brand)
            .setName(name)
            .setMinGhz(minGhz)
            .setMaxGhz(maxGhz)
            .setMemory(memory)
            .build();
}
```

### Generate a random RAM
Now we're done with GPU, the next thing is RAM. It's almost identical to the
GPU memory. We create a builder, set the memory size from 4 to 64 gigabytes, 
then build and return it.

```java
public Memory NewRAM() {
    return Memory.newBuilder()
            .setValue(randomInt(4, 64))
            .setUnit(Memory.Unit.GIGABYTE)
            .build();
}
```
### Generate a random storage
OK, now the storage. We have 2 separate methods for creating SSD and HDD.
For the SSD, the memory size will be from 128 to 1024 gigabytes. Alright, now we 
will set the driver to be SSD, then we set the memory and build the object.

```java
public Storage NewSSD() {
    Memory memory = Memory.newBuilder()
            .setValue(randomInt(128, 1024))
            .setUnit(Memory.Unit.GIGABYTE)
            .build();
    
    return Storage.newBuilder()
            .setDriver(Storage.Driver.SSD)
            .setMemory(memory)
            .build();
}
```

I will duplicate this function and change it for HDD. This time
the memory size will be from 1 to 6 terabytes and the driver must be HDD.

```java
public Storage NewHDD() {
    Memory memory = Memory.newBuilder()
            .setValue(randomInt(1, 6))
            .setUnit(Memory.Unit.TERABYTE)
            .build();

    return Storage.newBuilder()
            .setDriver(Storage.Driver.HDD)
            .setMemory(memory)
            .build();
}
```
### Generate a random screen
The screen is also not difficult at all. We will set the height to be a random
integer between 1080 and 4320 and calculate the width from the height with 
the ratio of 16 by 9. Now we create a new resolution object with the random 
generated value of height and width. Then we will make a new screen. The size
of the screen will be between 13 and 17 inches. We will write a `randomFloat`
function for this which is similar to `randomDouble` function we wrote before.
Now the screen panel. Let's write a separate random function for it. There are
only 2 types of panel, either `IPS` or `OLED`. The last field we have to set is
the multitouch which is just a random boolean.

```java
public Screen NewScreen() {
    int height = randomInt(1080, 4320);
    int width = height * 16 / 9;

    Screen.Resolution resolution = Screen.Resolution.newBuilder()
            .setHeight(height)
            .setWidth(width)
            .build();

    return Screen.newBuilder()
            .setSizeInch(randomFloat(13, 17))
            .setResolution(resolution)
            .setPanel(randomScreenPanel())
            .setMultitouch(rand.nextBoolean())
            .build();
}

private Screen.Panel randomScreenPanel() {
    if (rand.nextBoolean()) {
        return Screen.Panel.IPS;
    }
    return Screen.Panel.OLED;
}

private float randomFloat(float min, float max) {
    return min + rand.nextFloat() * (max-min);
}
```
### Generate a random laptop
Finally we can build a random Laptop. We need a random brand, "Apple", "Dell",
or "Lenovo". And a random name depending on the brand. We use `switch case` 
statement here to generate the correct name of the brand. Then define the
weight in kilograms, the price in USD, and the release year. Now just call 
`Laptop.newBuilder()` and chain it with all setter functions of each
component. Note that for GPUs and Storages, we use `Add` instead of `Set` 
because they're `repeated` fields, which is a list of objects instead of 1 
single object. Other fields are quite simple to set. The `updateAt` field is a 
bit tricky to set compared to Go. Since we don't have a function to get the 
current time as `protobuf.Timestamp` object. So let's implement this 
`timestampNow()` function on our own. First we use the `Instant.now()` of the 
`java.time` package to get the time at the moment. Then we build the 
`Timestamp` object from it. OK, now the `NewLaptop` function is ready.

```java
public Laptop NewLaptop() {
    String brand = randomLaptopBrand();
    String name = randomLaptopName(brand);

    double weightKg = randomDouble(1.0, 3.0);
    double priceUsd = randomDouble(1500, 3500);

    int releaseYear = randomInt(2015, 2019);

    return Laptop.newBuilder()
            .setBrand(brand)
            .setName(name)
            .setCpu(NewCPU())
            .setRam(NewRAM())
            .addGpus(NewGPU())
            .addStorages(NewSSD())
            .addStorages(NewHDD())
            .setScreen(NewScreen())
            .setKeyboard(NewKeyboard())
            .setWeightKg(weightKg)
            .setPriceUsd(priceUsd)
            .setReleaseYear(releaseYear)
            .setUpdatedAt(timestampNow())
            .build();
}

private Timestamp timestampNow() {
    Instant now = Instant.now();
    return Timestamp.newBuilder()
        .setSeconds(now.getEpochSecond())
        .setNanos(now.getNano())
        .build();
}

private String randomLaptopName(String brand) {
    switch (brand) {
        case "Apple":
            return randomStringFromSet("Macbook Air", "MacbookPro");
        case "Dell":
            return randomStringFromSet("Latitude", "Vostro", "XPS", "Alienware");
        default:
            return randomStringFromSet("ThinkPad X1", "Thinkpad P1", "Thinkpad P53");
    }
}

private String randomLaptopBrand() {
    return randomStringFromSet("Apple", "Dell", "Lenovo");
}
```

We will type psvm to create a `main` function and try it. First we create a new 
`Generator`, then `generator.NewLaptop()` to create a new laptop. And print
its data to the standard output. OK, let's run it.

```java
public static void main(String[] args) {
    Generator generator = new Generator();
    Laptop laptop = generator.NewLaptop();
    System.out.println(laptop);
}
```

Very nice! We can see the laptop information here.
## Serialize protobuf messages
Next, we will create a new serializer package 
(`com.gitlab.techschool.pcbook.serializer`) and add a `Serializer` class inside
it.
### Read/write protobuf message from/to binary file
Similar as before, we will implement 2 functions to write a `Laptop` object 
to a binary file and read it back. For the writing part, all we have to do is 
to create a `FileOutputStream` with the specified filename and call 
`laptop.writeTo()` that output stream. Similarly, for the reading part we 
create a new `FileInputStream` with the file we want to read. Then we just 
call `Laptop.parseFrom()` that input stream.

```java
package com.gitlab.techschool.pcbook.serializer;

import com.github.techschool.pcbook.pb.Laptop;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class Serializer {
    public void WriteBinaryFile(Laptop laptop, String filename) throws IOException {
        FileOutputStream outStream = new FileOutputStream(filename);
        laptop.writeTo(outStream);
        outStream.close();
    }
    
    public Laptop ReadBinaryFile(String filename) throws IOException {
        FileInputStream inStream = new FileInputStream(filename);
        Laptop laptop = Laptop.parseFrom(inStream);
        inStream.close();
        return laptop;
    }
    
    public void WriteJSONFile(Laptop laptop, String filename) {
        
    }
}
```
### Write unit tests
Now I'm gonna show you how to write unit tests for these functions with JUnit.
Put cursor on class name (`Serializer`). As you can see, there's a light bulb
icon on top of the `Serializer` class. Just click it, then select "Create 
Test". A window appears to allow us config the unit test we want to generate.

![Config-test-options](images/lecture9.2/window.png)
**Picture 1** - Config test options.

Here I'm gonna use JUnit4. The class name `SerializerTest` looks good. In the 
"Generate test methods for" section, I will choose the `WriteBinaryFile` 
function and click OK. Actually in this unit test, I'm gonna test both write 
and read functions, so I will change this method name a bit to reflect that. 
Alright, first we declare a binary file name which is `laptop.bin`, then we 
generate a new `laptop1` object. We create a new `Serializer` object and 
call `serializer.WriteBinaryFile` to write `laptop1` to the file. After that, 
we read back the content of the file into another `laptop2` object. And we 
assert that the 2 objects: `laptop1` and `laptop2` should be equal. Go to 
`Settings -> Build, Execution, Deployment -> Build Tools -> Gradle` and 
change `Run tests using:` from `Gradle` (Default) to `IntelliJ IDEA`. OK, 
now let's click this icon to run the test.

```java
package com.gitlab.techschool.pcbook.serializer;

import com.github.techschool.pcbook.pb.Laptop;
import com.gitlab.techschool.pcbook.sample.Generator;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class SerializerTest {

    @Test
    public void writeAndReadBinaryFile() throws IOException {
        String binaryFile = "laptop.bin";
        Laptop laptop1 = new Generator().NewLaptop();

        Serializer serializer = new Serializer();
        serializer.WriteBinaryFile(laptop1, binaryFile);

        Laptop laptop2 = serializer.ReadBinaryFile(binaryFile);
        Assert.assertEquals(laptop1, laptop2);
    }
}
```

On the bottom left corner, we can see the test results. If you see a green tick
like this, it means the test passed.

![Tests-passed](images/lecture9.2/test_ok.png)

**Picture 2** - Tests passed.

And yes, the `laptop.bin` file was generated here.

### Write protobuf message to JSON file
Next we will write a function to save laptop object to a JSON file. To do 
this, we need to add 1 more dependency to the `build.gradle` file. It's the 
`protobuf-java-util`. You can search for it on the maven repository if you 
want. But actually, we just need to duplicate this line

```
implementation group: 'com.google.protobuf', name: 'protobuf-java', version: '3.15.6'
```

and add `-util` to the name. Then save the file. Now if we expand the `External 
Libraries` section, we can see that Intellij IDEA has downloaded the 
`protobuf-java-util` library for us right here. Now we can use the 
`JsonFormat.printer()` function from the library. Chain it with some 
configurations, such as including default value fields, and preserving proto
field names. Then create the JSON string by calling `printer.print(laptop)`. 
The rest is just writing that JSON string to a file. Now let's create a `main`
function to test it. I will read the `laptop.bin` file into a laptop object. 
Then write it to `laptop.json` file. The `bin` file is here, in root folder, so
let's run this to create the JSON file.

```java
public class Serializer {
    // ...

    public void WriteJSONFile(Laptop laptop, String filename) throws IOException {
        JsonFormat.Printer printer = JsonFormat.printer()
                .includingDefaultValueFields()
                .preservingProtoFieldNames();

        String jsonString = printer.print(laptop);
        FileOutputStream outStream = new FileOutputStream(filename);
        outStream.write(jsonString.getBytes());
        outStream.close();
    }

    public static void main(String[] args) throws IOException {
        Serializer serializer = new Serializer();
        Laptop laptop = serializer.ReadBinaryFile("laptop.bin");
        serializer.WriteJSONFile(laptop, "laptop.json");
    }
}
```

Yee, the file is created! Just like before, the size of the JSON file is about
5 times as big as the binary file. One last thing before we finish I will try
to read a binary file that was generated by our Go code. First, let's delete 
the `laptop.bin` and `laptop.json` file. Then go to the Go project and copy
the `tmp/laptop.bin` to our `IdeaProjects/pcbook` folder. OK it's here. Now
lets run the main file. The JSON file was successfully generated. Now let's
compare this JSON file with the one in out Go project. Yes, they are 
completely identical! So it worked! It proves that a binary protobuf message 
generated by one program can be read correctly by any other program written in
another language. And that wraps up our lecture about protobuf message 
serialization in Go and Java.
In the next lecture, we will learn how to implement our first gRPC. To recall
there are 4 types of gRPC: unary, client streaming, server streaming and 
bidirectional streaming. We will start with the first and simplest one: Unary.
So happy coding and I will see you later!

