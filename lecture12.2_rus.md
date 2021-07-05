# Загружаем файл по частям с помощью клиентского потокового gRPC - Java
Здравствуйте, рад снова вас приветствовать на курсе по gRPC. На этой лекции
мы узнаем как реализовать клиентский потоковый RPC с помощью Java. Мы реализуем
API, которое позволяет клиентам загружать файл изображения по частям.

## Определяем клиентский потоковый RPC в proto файле
Итак, давайте начнём! Откроем проект pcbook-java, над которым мы работаем.
Во-первых, нам нужно определить новую RPC для загрузки изображения. Поскольку
мы это уже сделали на предыдущей лекции по Golang, я просто открою pcbook 
golang проект и скопирую содержимое файла `laptop_service.proto`. Мы добавили
в файл сообщение `UploadImageRequest`. Оно имеет поле `oneof`, которое может 
содержать информацию об изображении или фрагменты данных изображения.

```protobuf
message UploadImageRequest {
  oneof data {
    ImageInfo info = 1;
    bytes chunk_data = 2;
  }
}

message ImageInfo {
  string laptop_id = 1;
  string image_type = 2;
}
```

`ImageInfo` содержит идентификатор ноутбука и тип изображения, например, `.jpg`
или `.png`. `chunk_data` - это последовательность байт. Идея заключается в том,
чтобы разделить изображение на несколько частей по 1 килобайту и отправить их
на сервер поочереди, используя потоковую передачу.
Затем сервер посылает один, единственный ответ, который содержит идентификатор
загруженного изображения и общий размер этого изображения.

```protobuf
message UploadImageResponse {
  string id = 1;
  uint32 size = 2;
}
```

Итак `UploadImage` RPC будет принимать на вход поток из `UploadImageRequest` и
возвращать `UploadImageResponse`.

```protobuf
service LaptopService {
  rpc CreateLaptop(CreateLaptopRequest) returns (CreateLaptopResponse) {};
  rpc SearchLaptop(SearchLaptopRequest) returns (stream SearchLaptopResponse) {};
  rpc UploadImage(stream UploadImageRequest) returns (UploadImageResponse) {};
}
```

Давайте соберём проект, чтобы сгенерировать Java из него. Сборка должна пройти 
успешно, без ошибок.

## Реализуем хранилище изображений
Теперь прежде чем мы реализуем RPC, нам нужно будет добавить новое хранилище
для сохранения загруженных изображений. Я создам новый интерфейс `ImageStore`
в папке `service`. У него будет один метод: `Save`, который принимает на вход
`laptopID`, `imageType` и `imageData` и возвращает `imageID` или генерирует
исключение `IOException`.

```java
package com.gitlab.techschool.pcbook.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public interface ImageStore {
    String Save(String laptopID, String imageType, ByteArrayOutputStream imageData) throws IOException;
}
```

Допустим мы хотим хранить изображение на диске, а его метаданные в памяти. 
Исходя из этого я создам новый класс `DiskImageStore`, который будет 
реализовывать этот интерфейс. В этом классе нам понадобится поле с путём к 
папке, где будут храниться изображения. Также нам будет нужна карта с защитой 
доступа в многопоточном приложении для хранения метаданных изображений. Ключом
карты будет идентификатор изображения, а значением — метаданные.

```java
package com.gitlab.techschool.pcbook.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentMap;

public class DiskImageStore implements ImageStore {
    private String imageFolder;
    private ConcurrentMap<String, ImageMetadata> data;

    @Override
    public String Save(String laptopID, String imageType, ByteArrayOutputStream imageData) throws IOException {
        return null;
    }
}
```

Я создам новый класс для `ImageMetadata`. В этом классе мы будем хранить 
идентификатор ноутбука, тип изображения и путь к изображению на диске. Давайте
напишем конструктор для инициализации объекта, а также создадим геттер-методы 
для каждого из полей.

```java
package com.gitlab.techschool.pcbook.service;

public class ImageMetadata {
    private String laptopID;
    private String type;
    private String path;

    public ImageMetadata(String laptopID, String type, String path) {
        this.laptopID = laptopID;
        this.type = type;
        this.path = path;
    }
    
    public String getLaptopID() {
        return laptopID;
    }
    
    public String getType() {
        return type;
    }
    
    public String getPath() {
        return path;
    }
}
```

Хорошо, теперь вернемся к нашему `DiskImageStore`. Сначала мы создадим новый
конструктор, который принимает на вход только `imageFolder`. Мы инициализируем
карту, используя `ConcurrentHashMap`. Затем в методе `Save` мы генерируем 
случайный UUID, который будет использоваться в качестве идентификатора 
изображения. Мы создаём путь, где будет храниться изображение, объединяя 
строки `imageFolder`, `imageID` и `imageType`. Затем мы создаём новый
`FileOutputStream` с путём к изображению. Мы вызываем `imageData.writeTo()`
для записи данных об изображении в этот поток и затем закрываем его 
`fileOutputStream.close()`. После успешной записи файла на диск мы создаём 
новый объект метаданных и добавляем его в карту с ключом `imageID`. Наконец, мы
возвращаем `imageID`. На этом реализация `DiskImageStore` завершена.

```java
public class DiskImageStore implements ImageStore {
    // ...

    public DiskImageStore(String imageFolder) {
        this.imageFolder = imageFolder;
        this.data = new ConcurrentHashMap<>(0);
    }
    
    @Override
    public String Save(String laptopID, String imageType, ByteArrayOutputStream imageData) throws IOException {
        String imageID = UUID.randomUUID().toString();
        String imagePath = String.format("%s/%s%s", imageFolder, imageID, imageType);

        FileOutputStream fileOutputStream = new FileOutputStream(imagePath);
        imageData.writeTo(fileOutputStream);
        fileOutputStream.close();
        
        ImageMetadata metadata = new ImageMetadata(laptopID, imageType, imagePath);
        data.put(imageID, metadata);
        
        return imageID;
    }
}
```

## Реализуем UploadImage RPC
Теперь давайте реализуем `UploadImage` RPC в классе `LaptopService`. Сначала
я изменю поле `store` на `laptopStore`. Затем мы создадим новое поле для
`imageStore`. Также добавьте его в конструктор.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...

    private LaptopStore laptopStore;
    private ImageStore imageStore;

    public LaptopService(LaptopStore laptopStore, ImageStore imageStore) {
        this.laptopStore = laptopStore;
        this.imageStore = imageStore;
    }

    @Override
    public void createLaptop(CreateLaptopRequest request, StreamObserver<CreateLaptopResponse> responseObserver) {
        // ...
        try {
            laptopStore.Save(other);
        } catch (AlreadyExistsException e) {
            // ...
        } catch (Exception e) {
            // ...
        }

        // ...
    }

    @Override
    public void searchLaptop(SearchLaptopRequest request, StreamObserver<SearchLaptopResponse> responseObserver) {
        // ...

        laptopStore.Search(Context.current(), filter, new LaptopStream() {
            @Override
            public void Send(Laptop laptop) {
                logger.info("found laptop with ID: " + laptop.getId());
                SearchLaptopResponse response = SearchLaptopResponse.newBuilder().setLaptop(laptop).build();
                responseObserver.onNext(response);
            }
        });

        // ...
    }
}
```

Итак, теперь нам нужно переопределить метод `uploadImage()`. Как видите, этот
метод содержит параметр `responseObserver`, который будет использоваться для
отправки ответа клиенту точно так же, как и в `searchLaptop` RPC. А что делать
с потоком запросов? Это большое отличие от серверного потокового RPC, поскольку
поток является не входным параметром, а возвращаемым значением этого метода.
Здесь мы видим, что метод `uploadImage` должен возвращать `StreamObserver` из
`UploadImageRequest` и этот `StreamObserver` - это просто интерфейс из трёх 
функций: `onNext`, `onError` и `onCompleted`. Нам нужно только вернуть 
реализацию этого интерфейса. Давайте займемся этим.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...

    @Override
    public StreamObserver<UploadImageRequest> uploadImage(StreamObserver<UploadImageResponse> responseObserver) {
        return new StreamObserver<UploadImageRequest>() {
            private String laptopID;
            private String imageType;
            private ByteArrayOutputStream imageData;

            @Override
            public void onNext(UploadImageRequest value) {
                if (request.getDataCase() == UploadImageRequest.DataCase.INFO) {
                    ImageInfo info = request.getInfo();
                }
            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onCompleted() {

            }
        };
    }
}
```

Сначала определим три поля: `laptopID`, `imageType` и `imageData`. Теперь в
функции `onNext()` мы проверяем что это за данные. Если это информация об
изображении, то мы пишем об этом сообщение в лог. Затем из этой информации мы 
получаем `laptopID` и `imageType`. Мы также инициализируем `imageData` с 
помощью нового `ByteArrayOutputStream` и выходим из метода. В противном случае
это должен быть новый фрагмент данных. Таким образом, мы извлекаем фрагмент из
запроса и пишем в лог сообщение, что получили фрагмент и указываем его размер. 
Затем мы проверяем является ли `imageData` равным `null` или нет. Если оно 
равно `null`, то это означает, что клиент не отправил информацию об 
изображении, поэтому мы просто отправляем ошибку с состоянием 
`INVALID_ARGUMENT` и сразу выходим из метода. В противном случае мы вызываем
метод `chunkData().writeTo()`, чтобы добавить этот фрагмент к данным 
изображения. Если возникло исключение, то посылаем клиенту ошибку с 
состоянием `INTERNAL` и выходим из метода. Логика метода `onNext()` 
реализована.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...

    @Override
    public StreamObserver<UploadImageRequest> uploadImage(StreamObserver<UploadImageResponse> responseObserver) {
        return new StreamObserver<UploadImageRequest>() {
            private String laptopID;
            private String imageType;
            private ByteArrayOutputStream imageData;

            @Override
            public void onNext(UploadImageRequest request) {
                if (request.getDataCase() == UploadImageRequest.DataCase.INFO) {
                    ImageInfo info = request.getInfo();
                    logger.info("receive image info:\n" + info);

                    laptopID = info.getLaptopId();
                    imageType = info.getImageType();
                    imageData = new ByteArrayOutputStream();

                    return;
                }

                ByteString chunkData = request.getChunkData();
                logger.info("receive image chunk with size: " + chunkData.size());

                if (imageData == null) {
                    logger.info("image info wasn't sent before");
                    responseObserver.onError(
                            Status.INVALID_ARGUMENT
                                    .withDescription("image info wasn't sent beefore")
                                    .asRuntimeException()
                    );
                    return;
                }

                try {
                    chunkData.writeTo(imageData);
                } catch (IOException e) {
                    responseObserver.onError(
                            Status.INTERNAL
                                    .withDescription("cannot write chunk data: " + e.getMessage())
                                    .asRuntimeException()
                    );
                    return;
                }
            }

            // ...
        };
    }
}
```

Метод `onError()` вызывается всякий раз, когда возникает ошибка во время 
получения сервером данных потока. Здесь мы пишем в лог предупреждающее 
сообщение с текстом ошибки. 

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...

    @Override
    public StreamObserver<UploadImageRequest> uploadImage(StreamObserver<UploadImageResponse> responseObserver) {
        return new StreamObserver<UploadImageRequest>() {
            // ...
            
            @Override
            public void onError(Throwable t) {
                logger.warning(t.getMessage());
            }
            
            // ...
        };
    }
}
```

Теперь давайте реализуем функцию `onCompleted()`. Эта функция вызывается, когда
сервер получил все фрагменты данных изображения. Поэтому мы просто сохраняем
данные изображения в хранилище с помощью `imageStore.Save()`. Окружите вызов 
этого метода блоком try-catch. Если обнаружена ошибка, то используя 
`responseObserver.onError()` мы отправляем её клиенту. Сохраним идентификатор
изображения и общий размер изображения в отдельные переменные `imageID` и
`imageSize`. Затем мы создаём новый объект `UploadImageResponse`, используя
`imageID` и `imageSize`. Вызовем `responseObserver.onNext()`, чтобы послать 
ответ клиенту и, наконец, `responseObserver.onCompleted()` для закрытия 
текущее соединение.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...

    @Override
    public StreamObserver<UploadImageRequest> uploadImage(StreamObserver<UploadImageResponse> responseObserver) {
        return new StreamObserver<UploadImageRequest>() {
            // ...

            @Override
            public void onCompleted() {
                String imageID = "";
                int imageSize = imageData.size();
                try {
                    imageID = imageStore.Save(laptopID, imageType, imageData);
                } catch (IOException e) {
                    responseObserver.onError(
                            Status.INTERNAL
                                    .withDescription("cannot save image to the store: " + e.getMessage())
                                    .asRuntimeException()
                    );
                }

                UploadImageResponse response = UploadImageResponse.newBuilder()
                        .setId(imageID)
                        .setSize(imageSize)
                        .build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        };
    }
}
```

`uploadImage` RPC готов к работе.

## Обновляем сервер
Теперь нам нужно немного обновить `LaptopServer`. Сначала изменим `store` в 
конструкторе `LaptopServer` на `laptopStore`. Добавим новый `imageStore` в 
этот конструктор, затем передадим его в `LaptopService`. Сделаем то же самое
для другого конструктора. В методе `main` мы также изменим переменную `store`
на `laptopStore` и создадим новое `DiskImageStore` c папкой для изображений
равной `"img"`. Затем передаём его в новый конструктор `LaptopServer`. Создаём
папку для изображений `"img"` в проекте. Итак, все готово для работы.

```java
public class LaptopServer {
    // ...

    public LaptopServer(int port, LaptopStore laptopStore, ImageStore imageStore) {
        this(ServerBuilder.forPort(port), port, laptopStore, imageStore);
    }

    public LaptopServer(ServerBuilder serverBuilder, int port, LaptopStore laptopStore, ImageStore imageStore) {
        // ...
        LaptopService laptopService = new LaptopService(laptopStore, imageStore);
        // ...
    }

    // ...
    
    public static void main(String[] args) throws InterruptedException, IOException {
        InMemoryLaptopStore laptopStore = new InMemoryLaptopStore();
        DiskImageStore imageStore = new DiskImageStore("img");

        LaptopServer server = new LaptopServer(8080, laptopStore, imageStore);
        // ...
    }
}

public class LaptopServerTest {
    // ...

    @Before
    public void setUp() throws Exception {
        // ...

        laptopStore = new InMemoryLaptopStore();
        ImageStore imageStore = new DiskImageStore("img");
        server = new LaptopServer(serverBuilder, 0, laptopStore, imageStore);
        // ...
    }
    
    // ...
}
```

Запустим сервер. Теперь попробуем обратиться к этому серверу с помощью клиента
на языке Golang, который мы написали на предыдущей лекции. Изображение ноутбука
успешно загружено. Его можно увидеть в папке `img`. Так что всё работает!

## Реализуем клиент
Теперь реализуем Java клиент. Мы не можем использовать `blockingStub` для 
вызова клиентского потокового RPC. Вместо этого нам понадобится асинхронная 
заглушка. Давайте зададим её здесь и инициализируем внутри конструктора
`LaptopClient`, вызвав `LaptopServiceGrpc.newStub()`. Отлично, теперь 
определите метод `uploadImage()` с двумя входными параметрами: идентификатором
ноутбука и путём к изображению.

```java
public class LaptopClient {
    // ...
    private final LaptopServiceGrpc.LaptopServiceStub asyncStub;

    public LaptopClient(String host, int port) {
        // ...
        asyncStub = LaptopServiceGrpc.newStub(channel);
    }

    // ...
    
    public void uploadImage(String laptopID, String imagePath) {
        
    }
}
```

В методе `main` я закомментирую блок кода для тестирования RPC, предназначенных
для создания и поиска ноутбуков, которые мы написали на предыдущих лекциях. Но
добавлю новый код для тестирования загрузки изображения. Сначала сгенерируем 
новый случайный ноутбук. Мы вызываем `client.createLaptop()`, чтобы создать 
этот ноутбук на сервере. Затем `client.uploadImage()`, передавая в метод 
идентификатор ноутбука и файл `laptop.jpg` внутри папки `tmp`. Давайте создадим
эту папку `tmp` и скопируем `laptop.jpg` из проекта на Golang в эту папку. 
Убедитесь, что файл скопирован.

```java
public class LaptopClient {
    // ...
    
    public static void main(String[] args) throws InterruptedException {
        // ...

        try {
/*            for (int i = 0; i < 10; i++) {
                Laptop laptop = generator.NewLaptop();
                client.createLaptop(laptop);
            }

            Memory minRam = Memory.newBuilder()
                    .setValue(8)
                    .setUnit(Memory.Unit.GIGABYTE)
                    .build();

            Filter filter = Filter.newBuilder()
                    .setMaxPriceUsd(3000)
                    .setMinCpuCores(4)
                    .setMinCpuGhz(2.5)
                    .setMinRam(minRam)
                    .build();

            client.searchLaptop(filter);*/

            // Test upload laptop image
            Laptop laptop = generator.NewLaptop();
            client.createLaptop(laptop);
            client.uploadImage(laptop.getId(), "tmp/laptop.jpg");
        } finally {
            client.shutdown();
        }
    }
}
```

Теперь в методе `uploadImage()` мы вызовем `asyncStub.withDeadlineAfter` с 
таймаутом в 5 секунд и затем `uploadImage()`. Мы создаём новый `StreamObserver`
из `UploadImageResponse`. Результатом этого вызова будет ещё один 
`StreamObserver` из `UploadImageResponse`. В методе `onNext()` мы просто пишем
сообщение в лог, что мы получили ответ от сервера. В методе `onError()` мы
пишем сообщение с типом SEVERE: upload failed (загрузка не удалась). Обратите 
внимание, что заглушка является асинхронной, т. е. код, отвечающий за 
отправку запроса, и код, отвечающий за получение ответа, выполняются 
асинхронно. Таким образом, нам нужно использовать `CountDownLatch()`, чтобы 
дождаться завершения всего процесса. Здесь мы просто используем счетчик равный
1, поскольку нам нужно только дождаться потока ответа. Теперь если произойдёт
ошибка, мы вызываем `countDown()` внутри метода `onError()`. Точно так же в 
методе `onCompleted()` мы также пишем сообщение в лог и вызываем
`finishLatch.countDown()`. В конце метода `uploadImage()` мы вызываем
`finishLatch.await()`, чтобы дождаться завершения потока ответа. Здесь мы 
ждём не более одной минуты, что более чем достаточно, потому что выше мы 
установили максимальное время выполнения запроса в 5 секунд. Затем мы создадим
новый `FileInputStream` для чтения файла изображения. Если возникло 
исключение, просто добавим сообщение с типом SEVERE в лог и выйдем из метода. 
В противном случае мы получаем тип изображения из расширения файла и создаём 
новый класс `ImageInfo`, используя идентификатор ноутбука и тип изображения. 
Затем инициализируем новый  `UploadImageRequest` с помощью `ImageInfo` и 
вызываем `requestObserver.onNext()`, чтобы отправить запрос на сервер. 
Поместите этот блок в try-catch. Если произошло исключение, мы пишем ошибку с
типом SEVERE в лог, вызываем `requestObserver.onError()`, чтобы сообщить об
этом серверу, и выходим из метода. Наконец, мы вызываем 
`requestObserver.onCompleted()`. Внутри блока try-catch после того как мы 
отправили информацию об изображении, начинаем отправлять данные изображения
по частям. Каждый фрагмент будет иметь размер в 1 килобайт, поэтому создадим
новый байтовый буфер размером 1024. Мы используем здесь цикл `while` для 
многократного чтения и отправки данных. Я вынесу переменную `fileInputStream` 
за пределы цикла. Затем внутри цикла мы можем вызвать `fileInputStream.read()`,
чтобы считать следующий фрагмент данных в буфер. Метод вернет количество 
прочитанных байт. Присвоим их переменной `n`. Если `n` меньше или равно 0, то
был достигнут конец файла и мы можем спокойно выйти из цикла. Теперь проверяем
не произошло ли какой-либо непредвиденной ошибки. Если да, то не нужно больше
отправлять данные и можно выйти из метода. В противном случае мы создаём новый
запрос с фрагментом данных. В него мы просто копируем первые `n` байт из 
буфера. Как и раньше, мы вызываем `requestObserver.onNext()`, чтобы послать 
запрос на сервер и пишем в лог, что был отправлен фрагмент, указав его размер. 
На этом всё! Мы закончили реализацию логики работы клиента.

```java
public class LaptopClient {
    // ...
    
    public void uploadImage(String laptopID, String imagePath) throws InterruptedException {
        final CountDownLatch finishLatch = new CountDownLatch(1);

        StreamObserver<UploadImageRequest> requestObserver = asyncStub.withDeadlineAfter(5, TimeUnit.SECONDS)
                .uploadImage(new StreamObserver<UploadImageResponse>() {
                    @Override
                    public void onNext(UploadImageResponse response) {
                        logger.info("receive response:\n" + response);
                    }

                    @Override
                    public void onError(Throwable t) {
                        logger.log(Level.SEVERE, "upload failed: " + t);
                        finishLatch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        logger.info("image uploaded");
                        finishLatch.countDown();
                    }
                });

        FileInputStream fileInputStream;
        try {
            fileInputStream = new FileInputStream(imagePath);
        } catch (FileNotFoundException e) {
            logger.log(Level.SEVERE, "cannot read image file: " + e.getMessage());
            return;
        }

        String imageType = imagePath.substring(imagePath.lastIndexOf("."));
        ImageInfo info = ImageInfo.newBuilder().setLaptopId(laptopID).setImageType(imageType).build();
        UploadImageRequest request = UploadImageRequest.newBuilder().setInfo(info).build();

        try {
            requestObserver.onNext(request);
            logger.info("sent image info:\n" + info);

            byte[] buffer = new byte[1024];
            while (true) {
                int n = fileInputStream.read(buffer);
                if (n <= 0) {
                    break;
                }

                if (finishLatch.getCount() == 0) {
                    return;
                }

                request = UploadImageRequest.newBuilder()
                        .setChunkData(ByteString.copyFrom(buffer, 0, n))
                        .build();
                requestObserver.onNext(request);
                logger.info("sent image chunk with size: " + n);
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "unexpected error: " + e.getMessage());
            requestObserver.onError(e);
            return;
        }

        requestObserver.onCompleted();

        if (!finishLatch.await(1, TimeUnit.MINUTES)) {
            logger.warning("request cannot finish within 1 minute");
        }
    }
}
```

Теперь давайте запустим сервер и клиент. Изображение должно быть успешно 
загружено; мы получим ответ с идентификатором и размером изображения. В логах 
на стороне сервера нет ошибок и если мы откроем папку `img`, то увидим внутри 
изображение ноутбука. Теперь предположим, что мы хотим ограничить максимальный 
размер изображения. Например, можно загружать изображения размером не более
1 килобайта: `private static final int maxImageSize = 1 << 10`. Тогда в методе
`onNext()` перед записью фрагмента данных изображения, мы вычисляем текущий 
размер изображения. Если он превышает максимально допустимый размер, то 
записываем в лог сообщение о том, что "изображение слишком большое". Мы 
сообщаем об ошибке клиенту с кодом состояния `INVALID_ARGUMENT` и выходим из 
метода.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...

    @Override
    public StreamObserver<UploadImageRequest> uploadImage(StreamObserver<UploadImageResponse> responseObserver) {
        return new StreamObserver<UploadImageRequest>() {
            private static final int maxImageSize = 1 << 10; // 1 kilobyte
            // ...

            @Override
            public void onNext(UploadImageRequest request) {
                // ...

                int size = imageData.size() + chunkData.size();
                if (size > maxImageSize) {
                    logger.info("image is too large: " + size);
                    responseObserver.onError(
                            Status.INVALID_ARGUMENT
                                    .withDescription("image is too large: " + size)
                                    .asRuntimeException()
                    );
                    return;
                }

                // ...
            }

            // ...
        };
    }
}
```

Давайте протестируем поведение работу программы в этом случае. Запустите сервер 
и клиент. Как видите, после отправки нескольких фрагментов на сервер, мы 
получили ошибку `INVALID_ARGUMENT`: изображение слишком большое. Таким образом,
всё работает, как и планировалось. Обратите внимание, что отправка и получение
работают параллельно, поэтому вполне возможно, что клиент отправит больше двух
фрагментов, прежде чем получит ошибку от сервера и прекратит. В результате мы
можем увидеть предупреждение на стороне сервера: `WARNING: Stream Error`, 
поскольку сервер уже закрыл соединение, когда отправил ошибку клиенту. Давайте
изменим значение `maxImageSize` на 1 мегабайт.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...

    @Override
    public StreamObserver<UploadImageRequest> uploadImage(StreamObserver<UploadImageResponse> responseObserver) {
        return new StreamObserver<UploadImageRequest>() {
            private static final int maxImageSize = 1 << 20; // 1 megabyte
            // ...
        };
    }
}
```

И, напоследок, хотел бы обратить внимание на следующее. Когда мы получили
информацию об изображении, нам нужно проверить, что ноутбук с таким 
идентификатором существует в хранилище. Для этого достаточно вызвать метод
`laptopStore.Find(laptopID)`. Если ноутбук не найден, то просто вызываем
`responseObserver.onError()` с кодом состояния `NOT_FOUND`. Для проверки 
правильности работы программы в этом случае, на стороне клиента мы можем 
закомментировать команду `client.createLaptop(laptop);`, чтобы ноутбук не 
создавался на сервере.

```java
public class LaptopClient {
    public static void main(String[] args) throws InterruptedException {
        // ...
        
        try {
            // ...
            // Test upload laptop image
            Laptop laptop = generator.NewLaptop();
            // client.createLaptop(laptop);
            client.uploadImage(laptop.getId(), "tmp/laptop.jpg");
        } finally {
            client.shutdown();
        }
    }
    // ...
}
```

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...
    
    @Override
    public StreamObserver<UploadImageRequest> uploadImage(StreamObserver<UploadImageResponse> responseObserver) {
        return new StreamObserver<UploadImageRequest>() {
            private static final int maxImageSize = 1 << 20; // 1 megabyte
            // ...

            @Override
            public void onNext(UploadImageRequest request) {
                if (request.getDataCase() == UploadImageRequest.DataCase.INFO) {
                    // ...

                    // check laptop exists
                    Laptop found = laptopStore.Find(laptopID);
                    if (found == null) {
                        responseObserver.onError(
                                Status.NOT_FOUND
                                        .withDescription("laptop ID doesn't exists")
                                        .asRuntimeException()
                        );
                    }

                    return;
                }

                // ...
            }

            // ...
        };
    }
}
```

Итак, давайте запустим сервер и клиент. Мы получили ошибку NOT_FOUND.

```shell
SEVERE: upload failed: io.grpc.StatusRuntimeException: NOT_FOUND: laptop ID doesn't exists
```

Таким образом, всё работает, как и ожидалось. На этом закончим сегодняшнюю 
лекцию о клиентском потоковом RPC. На следующей лекции мы узнаем как 
реализовать последний тип gRPC - двунаправленную потоковую передачу. Надеюсь, 
что до сих пор информация, которую вы узнали из курса, была полезной для вас.
Спасибо за время, потраченное на чтение, и до новых встреч!