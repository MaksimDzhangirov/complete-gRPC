# Config Gradle to generate Java code from protobuf
In the last lecture, we have finished writing out protocol buffer messages and
generate Go codes from them. Now we're gonna do the same for Java. First we 
will install Java Development Kit, install Intellij IDEA. Then we will create a
new Gradle project, setup some plugins and config them to automatically 
generate Java codes. We also learn how to use option to customize the generated
codes.

## Install JDK
OK, let's start! First, open the terminal and run `javac` to check if Java
runtime is available or not. If it's not there, you will be asked to install a
JDK or Java Development Kit. We will need at least Java 8 in order to use all
features of gRPC. So let's click "More info" to open Java Oracle website. The
latest version is JDK 13, which is more than enough for us. We must accept the 
License Agreement, then choose the correct package for our OS. I'm using macOS,
so I will download this dmg file. Once the download is completed, open the file
to start installation. Alright, now let's run `javac` to make sure that it's 
working.

```shell
javac -version
```

## Install Intellij IDEA
Cool! Next step is installing Intellij IDEA. Search for `Intellij IDEA java` in 
your browser. Open this `jetbrains` website and click `Download`. We will use 
free community version. The installation is very straight-forward. Just open 
the dmg file and drag `IntelliJ IDEA` to the `Applications` folder. When we 
open the app, macOS will ask for confirmation, because this app is downloaded 
from the internet, not from the App store. We just go ahead and click `Open`. 
No need to import settings. We must accept the terms of the Jetbrains privacy 
policy. Click `Continue` and we're all set.

## Setup a Gradle project and plugins
Let's create a new project. We will use `Gradle` because Google has official 
gradle plugin for protobuf. For the project SDK, make sure that the correct 
Java version is selected. The click `Next`. Fill in the `GroupID`. I'm gonna 
use `com.gitlab.techschool`. The `ArtifactID` will be `pcbook`. Check the 
project name and its location to be exactly what you want. Then click `Finish`.
It might take a few seconds for IntelliJ IDEA to setup the project. OK the 
project is ready. Now we will open the `build.grandle` file to setup some 
plugins. The first one is `protobuf-grandle` plugins from Google. Open the 
browser and search for `protobuf java grandle`. This is the one we're looking 
for. Now scroll down a bit, copy `Using the Gradle plugin DSL` block and paste 
it to our `build.grandle` file. Next, we need to add a dependency: 
`protobuf-java` to our project. Let's get back to our browser and look for 
`maven protobuf java`. This will bring us to the maven repository. As you can 
see, we're inside the `protobuf-java` artifact of `com.google.protobuf` 
package. The latest released version is 3.15.6 so let's select it. Click on the
`Gradle` tab and copy this setting,

```
// https://mvnrepository.com/artifact/com.google.protobuf/protobuf-java
implementation group: 'com.google.protobuf', name: 'protobuf-java', version: '3.15.6'
```

then paste it into the dependencies block of our `build.gradle` file. We
will need a package to work with gRPC as well, so let's go back to the maven 
repository and search for `grpc-all`. Select the latest version. For me it's 
1.36.1. Similar to before, open the `Gradle` tab and copy the setting. Then 
paste it to dependencies block. IntelliJ IDEA will automatically detect and 
configure it for us. Now we will setup protobuf compiler. By default, the 
`protobuf-gradle-plugin` will search for `protoc` executable in the system. We 
already installed it in previous lecture with Homebrew. However, if you come 
here directly for Java, I will show you another way to get the pre-compiled
`protoc`. First, go to the maven repository and look for `protoc`. The latest 
version is 3.15.6 but we don't add it to the dependencies block as before. 
Instead, we will config it in a separate block. Let's go back to the 
`protobuf-gradle-plugin` github page, and copy `Locate external executables`
block. Paste it in our `build.gradle` file. Replace the version with the latest
one: 3.15.6 and IntelliJ will take care of the rest. Now, a very important
thing that we should do is to tell `protoc` to use the gRPC plugin when 
generating Java codes. We can do that by getting back to the github page of
`protobuf-gradle-plugin` and look for this plugin block.

```
protobuf {
  ...
  // Locate the codegen plugins
  plugins {
    // Locate a plugin with name 'grpc'. This step is optional.
    // If you don't locate it, protoc will try to use "protoc-gen-grpc" from
    // system search path.
    grpc {
      artifact = 'io.grpc:protoc-gen-grpc-java:1.0.0-pre2'
      // or
      // path = 'tools/protoc-gen-grpc-java'
    }
    // Any other plugins
    ...
  }
  ...
}
```

There's an artifact for `protoc-gen-grpc-java`. So let's search for its latest 
version on maven repository. It's 1.36.1. Alright, now we copy this block and
paste it here. Change the version to 1.36.1. Let's clean up the code a little
bit. Then we should add `generateProtoTasks` settings to guide `protoc` to use
out `grpc` plugin.

```
protobuf {
    protoc {
        artifact = 'com.google.protobuf:protoc:3.15.6'
    }

    plugins {
        grpc {
            artifact = 'io.grpc:protoc-gen-grpc-java:1.36.1'
        }
    }

    generateProtoTasks {
        all()*.plugins {
            grpc {}
        }
    }
}
```

I know it looks complicated and there are too many stuffs. But believe me, it 
will help us speed up a lot in the development phase. There's one more thing 
left before we're good to go. That's to tell IntelliJ IDEA where our generated 
codes will be located. That way it can easily and correctly do code analysis 
and code suggestion for us later. To do that, we use the `sourceSets` block. 
And inside this block, we specify 2 source directories: one for gRPC and the 
other for normal protobuf messages. OK, now we are really good to go.

## Generate code from proto files
Let's create a new `proto` folder inside `src/main` (Create `src/main` if not
exists). Then copy all proto files that we have written in previous lectures 
to this folder. We can see the files here, but there's no colours in the codes
yet. IntelliJ IDEA is asking to install plugins for that so let's go ahead and 
click `Install`. We have to restart IntelliJ IDEA for the plugins to take 
effects. Still no colours because it's indexing. This will take a while. Yee, 
the colours are back. I love it! Now comes the interesting part. Once we click
this `Build` icon IntelliJ IDEA will start some background tasks to generate 
codes for us. As you can see in this build output logs, there are quite a 
number of tasks. When they finished, we can find the generated codes in 
`build/generated/source/proto/main/java`. The `grpc` folder is empty now because 
we haven't written any RPC yet. In `java` folder, there are 6 java files, one 
for each of the messages. There are a lot of codes in 1 single file. And the 
generated package name is the same as protobuf package: `techschool_pcbook`. We
can easily change this package name similar to what we did for Go by setting
the `option java_package`. I'm gonna use `com.github.techschool.pcbook.pb`. We
can also tell `protoc` to split the codes into smaller files instead of putting
them inside 1 single big file. It's pretty simple, just set the 
`java_multiple_files` option to `true`.

```protobuf
option java_package = "com.github.techschool.pcbook.pb";
option java_multiple_files = true;
```

Let's copy and paste these new options to all of our proto files. Now I'm 
gonna delete the old generated codes, then rebuild the project. Cool, there are
many java files now, and the package name is changed to what we want. One last
thing before we finish, I will copy all of our modified proto files back to the
Go project, since we always want our proto files in all components of the
system to be synchronised.

And that's it! We're done! In the next lecture, we will start writing codes in
Go and Java to serialise protobuf messages. Thank you for reading and see you
then.