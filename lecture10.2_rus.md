# Реализация унарного API gRPC - Java
Здравствуйте, рад снова видеть вас на gRPC курсе. На первой половине лекции
мы узнали как реализовать унарный gRPC вызов в Go. На второй половине мы
изучим как это сделать на Java. План такой же. Мы рассмотрим следующие темы: 
как определить сервис с помощью protocol buffer, реализовать сервер, клиент, 
написать модульные тесты, обработать ошибки, коды состояний и задать 
максимальное время на выполнение gRPC.

## Определяем proto сервис и унарный RPC
Приступим. Я открою проект в IntelliJ IDEA, затем в окне терминала 
скопирую файл `laptop_service.proto`, который мы создали на первой половине 
лекции, в наш Java проект в папку `src/main/proto`. Я сделаю небольшой обзор
его структуры для тех, кто не читал лекцию, посвященную Go. Сначала мы 
определяем сообщение `CreateLaptopRequest` с единственным полем `Laptop`.

```protobuf
message CreateLaptopRequest {
  Laptop laptop = 1;
}
```

Затем мы создали сообщение `CreateLaptopResponse` со строковым полем: 
идентификатором ноутбука.

```protobuf
message CreateLaptopResponse {
  string id = 1;
}
```

Затем мы определяем `LaptopService` с помощью ключевого слова `service`. Внутри
этого сервиса мы задаём наш унарный gRPC вызов, используя ключевое слово `rpc`.
Назовём RPC `CreateLaptop`. Он принимает на вход `CreateLaptopRequest` и 
возвращает `CreateLaptopResponse`. Ничего сложного. Теперь давайте соберём 
проект, чтобы сгенерировать Java из него. При сборке получаем ошибку: 
`javax.annotation.Generated`. Чтобы исправить её, откройте браузер и найдите
"maven javax annotation". Откройте эту страницу `https://mvnrepository.com/artifact/javax.annotation/javax.annotation-api`
с Javax Annotation API, выберите последнюю версию (1.3.2 на момент написания),
скопируйте настройку для Gradle и вставьте её в блок зависимостей нашего файла 
`build.gradle`. Подождите, пока Gradle настроит её.

```
dependencies {
    // ...
    
    // https://mvnrepository.com/artifact/javax.annotation/javax.annotation-api
    implementation group: 'javax.annotation', name: 'javax.annotation-api', version: '1.3.2'
}
```

## Генерируем код для унарного RPC
Затем снова нажмите на кнопку `Build`. На этот раз сборка прошла успешно.
Давайте откроем папку `build/generated/source/proto/main/grpc`. В ней был 
создан класс `LaptopServiceGrpc`. Посмотрим что находится у него внутри. 
Прежде всего мы увидим `LaptopServiceStub`.

```java
 /**
  * Creates a new async stub that supports all call types for the service
  */
  public static LaptopServiceStub newStub(io.grpc.Channel channel) {
    // ...
  }
```

Это асинхронная заглушка, поддерживающая все типы вызовов для сервиса. За ней 
расположена другая блокирующая заглушка, которая поддерживает унарные и 
потоковые исходящие вызовы.

```java
 /**
  * Creates a new blocking-style stub that supports unary and streaming output calls on the service
  */
  public static LaptopServiceBlockingStub newBlockingStub(
     io.grpc.Channel channel){
     // ...
  }
```

На этой лекции мы будем использовать эту заглушку. Здесь также находится 
заглушка в стиле ListenableFuture. Вы можете использовать её, если хотите.

```java
 /**
  * Creates a new ListenableFuture-style stub that supports unary calls on the service
  */
  public static LaptopServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    // ...
  }
```

Далее следует абстрактный класс `LaptopServiceImplBase`. Нам нужно будет 
реализовать метод `createLaptop` этого класса на стороне сервера.

```java
 /**
  */
  public static abstract class LaptopServiceImplBase implements io.grpc.BindableService {
    // ...
  }
```

Классы в оставшейся часть файла, нас пока не интересуют, поэтому закройте файл
и давайте начнём писать код. 

## Реализуем серверный обработчик унарного RPC
Я создам новый пакет "service": `com.gitlab.techschool.pcbook.service`. На 
стороне сервера мы создадим новый класс `LaptopService`. Мы будем писать 
различные сообщения в логи, поэтому нам нужно объявить в нём логгер. Класс
`LaptopService` должен наследовать класс `LaptopServiceImplBase` и мы должны 
переопределить функцию `createLaptop` этого класса.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    private static final Logger logger = Logger.getLogger(LaptopService.class.getName());

    @Override
    public void createLaptop(CreateLaptopRequest request, StreamObserver<CreateLaptopResponse> responseObserver) {
        
    }
}
```

Сначала мы получаем объект-ноутбук из запроса. Мы используем идентификатор 
ноутбука и пишем его в лог, где сообщаем, что был принят запрос на создание 
ноутбука с этим идентификатором. Теперь проверяем является ли идентификатор 
ноутбука пустой строкой. Если да, то генерируем случайный UUID для него. В 
противном случае мы проверяем идентификатор на корректность. Если метод 
`UUID.fromString()` выдаёт исключение `IllegalArgumentException`, то это 
недопустимый UUID, поэтому необходимо использовать здесь блок `try-catch`. 
Если возникла ошибка, то мы возвращаем её клиенту. Достаточно использовать 
здесь метод `responseObserver.onError`. Вернем статус `InvalidArgument` вместе 
с сообщением, описывающим ошибку, и преобразуем её в исключение времени 
выполнения. Затем сразу выйдем из метода. В противном случае если 
всё пошло по плану, на этом этапе у нас будет корректный идентификатор 
ноутбука. Мы можем задать его объекту-ноутбуку. Для этого необходимо
преобразовать ноутбук в построитель и использовать метод-сеттер для 
присвоения значения идентификатору. Отлично, обычно после этого мы должны 
сохранить ноутбук в базе данных.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    private static final Logger logger = Logger.getLogger(LaptopService.class.getName());

    @Override
    public void createLaptop(CreateLaptopRequest request, StreamObserver<CreateLaptopResponse> responseObserver) {
        Laptop laptop = request.getLaptop();

        String id = laptop.getId();
        logger.info("got a create-laptop request with ID: " + id);

        UUID uuid;
        if (id.isEmpty()) {
            uuid = UUID.randomUUID();
        } else {
            try {
                uuid = UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                responseObserver.onError(
                        Status.INVALID_ARGUMENT
                                .withDescription(e.getMessage())
                                .asRuntimeException()
                );
                return;
            }
        }

        Laptop other = laptop.toBuilder().setId(uuid.toString()).build();
        // Save other laptop to the store
    }
}
```

## Реализуем хранилище для сохранения ноутбуков в памяти
Тем не менее, я не хочу отвлекать вас, поскольку этот курс о gRPC, а не о 
базах данных. Поэтому я буду использовать хранилище в оперативной памяти. 
Давайте создадим новый файл: `LaptopStore`. Он должен быть `Interface`, чтобы
мы могли реализовывать различные виды хранилищ, когда захотим. В этом 
интерфейсе будет функция `Save` для сохранения ноутбука в хранилище. Обратите 
внимание, что для упрощения в этой лекции я буду использовать объект probobuf
`Laptop` непосредственно как модель данных. Но вам следует рассмотреть 
возможность использования отдельной модели для разделения уровня передачи и 
хранения данных. Нам также понадобится функция `Find` для поиска ноутбука по 
его идентификатору.

```java
package com.gitlab.techschool.pcbook.service;

import com.github.techschool.pcbook.pb.Laptop;

public interface LaptopStore {
    void Save(Laptop laptop) throws Exception; // consider using a separate db model
    Laptop Find(String id);
}
```

Теперь давайте создадим новый файл, реализующий `InMemoryLaptopStore`. Мы 
будем использовать `ConcurrentMap` для хранения ноутбуков, где ключом будет 
строка идентификатора ноутбука, а значением — сам ноутбук. В конструкторе этого
класса создадим новую `ConcurrentHashmap` с начальной ёмкостью равной нулю. В 
функции `Save` мы проверяем существует ли идентификатор ноутбука в хранилище
или нет.

```java
package com.gitlab.techschool.pcbook.service;

import com.github.techschool.pcbook.pb.Laptop;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryLaptopStore implements LaptopStore {
    private ConcurrentMap<String, Laptop> data;

    public InMemoryLaptopStore() {
        data = new ConcurrentHashMap<>(0);
    }

    @Override
    public void Save(Laptop laptop) throws Exception {
        if (data.containsKey(laptop.getId())) {
            throw new AlreadyExistsException("laptop ID already exists");
        }
    }

    @Override
    public Laptop Find(String id) {
        return null;
    }
}
```

Если да, генерируем новое исключение `AlreadyExistsException`. Давайте 
определим это исключение в отдельном файле в папке `service`. Оно наследует 
класс `RuntimeException` и вызывает родительский конструктор с сообщением, 
переданным в него.

```java
package com.gitlab.techschool.pcbook.service;

public class AlreadyExistsException extends RuntimeException {
    public AlreadyExistsException(String message) {
        super(message);
    }
}
```

Теперь в функции `Save`, мы создадим глубокую копию ноутбука и сохраним его в 
хеш-таблице.

```java
public class InMemoryLaptopStore implements LaptopStore {
    // ...
    @Override
    public void Save(Laptop laptop) throws Exception {
        if (data.containsKey(laptop.getId())) {
            throw new AlreadyExistsException("laptop ID already exists");
        }

        // deep copy
        Laptop other = laptop.toBuilder().build();
        data.put(other.getId(), other);
    }

    // ...
}
```

В функции `Find`, если карта не содержит идентификатор ноутбука мы просто 
возвращаем `null`. В противном случае мы осуществляем глубокое копирование 
объекта из хеш-таблицы и возвращаем его.

```java
public class InMemoryLaptopStore implements LaptopStore {
    // ...

    @Override
    public Laptop Find(String id) {
        if (!data.containsKey(id)) {
            return null;
        }

        // deep copy
        Laptop other = data.get(id).toBuilder().build();
        return other;
    }
}
```

Давайте вернемся к классу `LaptopService`. Мы объявили объект `LaptopStore` и
инициализировали его в конструкторе.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    private static final Logger logger = Logger.getLogger(LaptopService.class.getName());

    private LaptopStore store;

    public LaptopService(LaptopStore store) {
        this.store = store;
    }
    
    // ...
}
```

Теперь в функции `createLaptop` мы можем вызвать `store.Save()`, чтобы 
сохранить ноутбук в хранилище. Если мы перехватим исключение 
`AlreadyExistsException`, то вернём статус `AlreadyExists` клиенту. Иначе, если
перехватим неожидаемое исключение, то возвращаем статус `Internal`, означающий,
что произошла внутренняя ошибка сервера. Если исключений нет, то мы создадим 
новый объект-ответ с идентификатором ноутбука и вернём его клиенту, используя 
функцию `responseObserver.onNext()`. Наконец, мы вызываем функцию 
`onCompelted()`, чтобы сообщить клиенту, что RPC завершена и ничего больше 
отправляться не будет. Мы пишем в лог сообщение, в котором говорится, что 
ноутбук с таким-то идентификатором был успешно сохранен в хранилище. На этом 
реализация класса `LaptopService` завершена.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...
    @Override
    public void createLaptop(CreateLaptopRequest request, StreamObserver<CreateLaptopResponse> responseObserver) {
        // ...

        // Save other laptop to the store
        try {
            store.Save(other);
        } catch (AlreadyExistsException e) {
            responseObserver.onError(
                    Status.ALREADY_EXISTS
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
            return;
        } catch (Exception e) {
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
            return;
        }

        CreateLaptopResponse response = CreateLaptopResponse.newBuilder().setId(other.getId()).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();

        logger.info("saved laptop with ID: " + other.getId());
    }
}
```

Теперь давайте создадим класс `LaptopServer` для прослушивания gRPC запросов и
вызова `LaptopService` для их обслуживания. Как и раньше, мы сначала создадим 
новый логгер для записи определенных логов. Затем мы напишем два конструктора.
Первый на вход принимает порт и хранилище ноутбуков, а второй принимает ещё 
один дополнительный входной параметр, которым является grpc `ServerBuilder`.
Этот конструктор нам пригодится позднее при написании модульного теста.

```java
public class LaptopServer {
    private static final Logger logger = Logger.getLogger(LaptopServer.class.getName());

    public LaptopServer(int port, LaptopStore store) {
        
    }

    public LaptopServer(ServerBuilder serverBuilder, int port, LaptopStore store) {
        
    }
}
```

В первом конструкторе мы создаём новый ServerBuilder, используя порт, 
подаваемый в качестве входного параметра и затем вызываем второй конструктор,
передавая его. Мы определим два приватных поля в классе `LaptopServer`, одно 
для порта, а второе — для gRPC сервера. Во втором конструкторе мы сохраним 
значение входного порта в приватное свойство класса и создадим сервис для 
ноутбуков, используя хранилище, переданное во входном параметре. Добавим сервис
для ноутбуков в `serverBuilder` и вызовем функцию `build()` для создания gRPC
сервера.

```java
public class LaptopServer {
    private static final Logger logger = Logger.getLogger(LaptopServer.class.getName());

    private final int port;
    private final Server server;

    public LaptopServer(int port, LaptopStore store) {
        this(ServerBuilder.forPort(port), port, store);
    }

    public LaptopServer(ServerBuilder serverBuilder, int port, LaptopStore store) {
        this.port = port;
        LaptopService laptopService = new LaptopService(store);
        server = serverBuilder.addService(laptopService).build();
    }
}
```

Затем мы реализуем функцию `start()` для запуска сервера. Мы пишем сообщение в
лог, сообщающее о том, что сервер запущен на заданном порту `port`. Затем
нам понадобится метод `stop()` для остановки сервера. Здесь мы будем 
использовать функцию `awaitTermination()`, чтобы дождаться полного завершения
работы сервера. Поскольку иногда работа JVM может быть завершена извне из-за 
некоторых прерываний или неожиданных ошибок, мы должны добавить хук при 
выключении к функции `start()`, чтобы gRPC сервер мог корректно завершиться. В
этом хуке мы просто записываем системную ошибку и вызываем метод `stop()` 
объекта `LaptopServer`.

```java
public class LaptopServer {
    // ...
    
    public void start() throws IOException {
        server.start();
        logger.info("server started on port " + port);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("shut down gRPC server because JVM shuts down");
                try {
                    LaptopServer.this.stop();
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                }
                System.err.println("server shut down");
            }
        });
    }

    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }
}
```

Затем мы создаём ещё одну функцию для блокировки основного потока до остановки
сервера, поскольку gRPC сервер использует потоки, работающие в фоновом режиме.

```java
public class LaptopServer {
    // ...

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
}
```

Итак, теперь мы можем реализовать функцию `main`. Сначала мы создадим новый
`InMemoryLaptopStore`, а затем `LaptopServer` на порту 8080, используя это 
хранилище. Мы вызываем `server.start()` для запуска сервера и наконец
`server.blockUntilShutdown()`. Сервер готов.

```java
public class LaptopServer {
    // ...
    
    public static void main(String[] args) throws InterruptedException, IOException {
        InMemoryLaptopStore store = new InMemoryLaptopStore();
        LaptopServer server = new LaptopServer(8080, store);
        server.start();
        server.blockUntilShutdown();
    }
}
```

Давайте нажмём на кнопку `Run` рядом с методом `public static void main`, чтобы 
запустить его. Как видно сервер запускается на порту 8080. Теперь я 
воспользуюсь клиентом на Golang, который был реализован нами на предыдущей 
лекции, чтобы подключиться к этому серверу. Я выполню команду `make client` в 
терминале для запуска клиента. Отлично! Был создан новый ноутбук с 
идентификатором `fa63a094-834d-46c4-be1d-e4f334f4be84`.

```shell
2021/04/01 19:28:18 created laptop with id: fa63a094-834d-46c4-be1d-e4f334f4be84
```

На стороне сервера мы также можем увидеть сообщение о том, что был получен 
запрос на создание ноутбука с пустым идентификатором и ещё одно сообщение о 
сохранении ноутбука в хранилище.

```shell
INFO: got a create-laptop request with ID: 
INFO: saved laptop with ID: fa63a094-834d-46c4-be1d-e4f334f4be84
```

Тем не менее, у вас может возникнуть сообщение о "сбое при передаче". Это не 
ошибка, поскольку сообщение относится к типу INFO. Когда я поискал 
информацию об этом сообщении в Интернете, оказалось, что это новый баг 
библиотеки gRPC-java, возникающий, когда клиент закрывает соединение слишком 
быстро и RPC вызов оказывается не до конца завершенным, хотя ответ от сервера 
был успешно доставлен. Сервер по-прежнему относит это сообщение к типу INFO, 
хотя оно должно принадлежать к типу FINE. Код, исправляющий эту ошибку был 
недавно объединен с основной версией библиотеки и я надеюсь, что в следующей 
версии её не будет. Пока что можно исправить эту ошибку следующим образом. 
После получения ответа от сервера мы просто можем подождать немного, скажем,
1 секунду.

`cmd/client/main.go`
```go
func main() {
	log.Printf("created laptop with id: %s", res.Id)
	time.Sleep(time.Second)
}
```

Затем, когда мы снова запустим `make client`, сообщений об ошибке не будет. 
Давайте рассмотрим случай, когда идентификатор уже сгенерирован на стороне 
клиента.

`cmd/client/main.go`
```go
func main() {
    // ...
	
    laptop := sample.NewLaptop()
    //laptop.Id = ""
    req := &pb.CreateLaptopRequest{
        Laptop: laptop,
    }
    
    // ...
}
```

Сервер успешно обработал его! Как видно из логов сервера, ноутбук был успешно
создан с идентификатором, который клиент послал в запросе. Теперь давайте 
рассмотрим случай, когда в хранилище существует ноутбук с таким же 
идентификатором. 

`cmd/client/main.go`
```go
func main() {
    // ...
	
    laptop := sample.NewLaptop()
    laptop.Id = "fa63a094-834d-46c4-be1d-e4f334f4be84"
    req := &pb.CreateLaptopRequest{
        Laptop: laptop,
    }
    
    // ...
}
```

Отлично! Сервер выдал сообщение о том, что такой ноутбук уже существует, как мы
и ожидали. 

```shell
2021/04/01 19:44:14 laptop already exists
```

А если передать некорректный идентификатор?

`cmd/client/main.go`
```go
func main() {
    // ...
	
    laptop := sample.NewLaptop()
    laptop.Id = "invalid"
    req := &pb.CreateLaptopRequest{
        Laptop: laptop,
    }
    
    // ...
}
```

Сервер вернет ошибку с кодом `InvalidArgument`.

```shell
2021/04/01 19:46:43 cannot create laptop: rpc error: code = InvalidArgument desc = Invalid UUID string: invalid
exit status 1
```

Отлично! Теперь давайте напишем клиент на Java. Я создам новый класс 
`LaptopClient` в пакете `service`. Как и раньше, мы создадим новый логгер. 
Затем конструктор с двумя аргументами: адресной строкой сервера `host` и 
портом `port`. Чтобы подключиться к серверу, нам понадобится объект 
`ManagedChannel`. Он нужен для взаимодействия клиента и сервера. Нам также 
понадобится блокирующая заглушка, чтобы вызывать унарный RPC.

```shell
import com.github.techschool.pcbook.pb.LaptopServiceGrpc;
import io.grpc.ManagedChannel;

import java.util.logging.Logger;

public class LaptopClient {
    private static final Logger logger = Logger.getLogger(LaptopClient.class.getName());

    private final ManagedChannel channel;
    private final LaptopServiceGrpc.LaptopServiceBlockingStub blockingStub;

    public LaptopClient(String host, int port) {

    }
}
```

В конструкторе `LaptopClient` мы используем `ManagedChannelBuilder` для 
создания канала с указанной адресной строкой сервера `host` и портом `port`.
Чтобы не усложнять, мы пока не используем защищенное соединение. В этом случае
данные передаются в виде обычного текста, без шифрования (`usePlaintext`). 
После создания канала, мы можем использовать его для создания новой блокирующей
заглушки.

```java
public class LaptopClient {
    // ...

    public LaptopClient(String host, int port) {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        blockingStub = LaptopServiceGrpc.newBlockingStub(channel);
    }
}
```

Теперь мы напишем функцию `shutdown`, чтобы корректно завершить работу канала. 
Мы будем ждать завершения работы канала не более 5 секунд.

```java
public class LaptopClient {
    // ...
    
    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
```

Затем мы напишем функцию `createLaptop` для вызова RPC на сервере, чтобы 
создать новый ноутбук. Сначала мы создаём новый запрос create-laptop
с объектом-ноутбуком в качестве входного параметра. Мы создаём экземпляр ответа
create-laptop, используемый по умолчанию, затем вызываем 
`blockStub.createlaptop()` и передаём запрос. Если мы перехватили исключение,
мы записываем сообщение с типом SEVERE и завершаем работу метода. В случае 
успешного вызова, мы получим `response` в качестве ответа. После этого мы 
запишем информационное сообщение в лог, сообщающее о создании ноутбука с 
требуемым идентификатором.

```java
public class LaptopClient {
    // ...
    
    public void createLaptop(Laptop laptop) {
        CreateLaptopRequest request = CreateLaptopRequest.newBuilder().setLaptop(laptop).build();
        CreateLaptopResponse response = CreateLaptopResponse.getDefaultInstance();
        try {
            response = blockingStub.createLaptop(request);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "request failed: " + e.getMessage());
            return;
        }
        
        logger.info("laptop created with ID: " + response.getId());
    }
}
```

Хорошо, теперь введите `psvm`, чтобы создать функцию `main`. Сначала создайте
новый клиент, который подключается по адресу `localhost` к порту `8080`. Затем
инициализируйте новый генератор и сгенерируйте случайный ноутбук.

```java
public class LaptopClient {
    // ...

    public static void main(String[] args) {
        LaptopClient client = new LaptopClient("0.0.0.0", 8080);

        Generator generator = new Generator();
        Laptop laptop = generator.NewLaptop();
        
    }
}
```

Кстати, в лекции 9, я забыл задать случайный идентификатор для 
объекта-ноутбука. Итак, давайте вызовем здесь функцию `setID()` в 
`NewLaptop()`, чтобы присвоить ему случайную UUID строку.

```java
public class Generator {
    // ...
    
    public Laptop NewLaptop() {
        // ...

        return Laptop.newBuilder()
                .setId(UUID.randomUUID().toString())
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

    // ...
}
```

Вернемся к нашей функции main, теперь мы можем вызвать `client.createLaptop`, 
используя случайный объект-ноутбук. Оберните его в блок `try`, а в блоке 
`finally` вызовите `client.shutdown()`, чтобы явно его завершить.

```java
public class LaptopClient {
    // ...

    public static void main(String[] args) throws InterruptedException {
        // ...

        try {
            client.createLaptop(laptop);
        } finally {
            client.shutdown();
        }
    }
}
```

Давайте запустим сервер, реализованный на Go, и подключим к нему этот Java 
клиент. Как видно из логов, ноутбук успешно создан.

```shell
INFO: laptop created with ID: 7fea0409-cffb-4b61-bac1-5b81111730e7
```

Давайте рассмотрим случай, когда клиент отправляет ноутбук с пустым 
идентификатором.

```java
public class LaptopClient {
    // ...

    public static void main(String[] args) throws InterruptedException {
        // ...

        Generator generator = new Generator();
        Laptop laptop = generator.NewLaptop().toBuilder().setId("").build();

        // ...
    }
}
```

В этом случае ноутбук всё равно создаётся.

```shell
INFO: laptop created with ID: 4a24b183-64da-42b4-8a5b-557912351a30
```

На стороне сервера мы видим два сообщения: первое сигнализирует о том, что был
получен ноутбук с пустым идентификатором, а второе, что он был сохранен со 
случайным UUID.

```shell
2021/04/01 20:35:32 receive a create-laptop request with id: 
2021/04/01 20:35:38 saved laptop with id: 4a24b183-64da-42b4-8a5b-557912351a30
```

Это именно то, что нам нужно. Теперь если мы попытаемся отправить ноутбук с 
идентификатором, который уже был сохранен в хранилище, то сервер вернёт ошибку
со статусом `ALREADY_EXISTS`.

```java
public class LaptopClient {
    // ...

    public static void main(String[] args) throws InterruptedException {
        // ...

        Generator generator = new Generator();
        Laptop laptop = generator.NewLaptop().toBuilder().setId("4a24b183-64da-42b4-8a5b-557912351a30").build();

        // ...
    }
}
```

```shell
SEVERE: request failed: ALREADY_EXISTS: cannot save laptop to the store: record already exists
```

Однако, ошибка отображается как сообщение с типом SEVERE, а мы хотим, чтобы 
это было обычное информационное сообщение. Поэтому давайте немного обновим код.
Здесь, если возникло исключение `StatusRuntimeException`, мы можем вызвать
`e.getStatus().getCode()`, чтобы получить код состояния ответа. Если он равен
`AlreadyExists`, то ничего страшного не произошло. Мы просто пишем 
информационное сообщение в лог и выходим из метода. В противном случае мы 
добавляем сообщение с типом SEVERE для любых других исключений.

```java
public class LaptopClient {
    // ...

    public void createLaptop(Laptop laptop) {
        // ...

        try {
            response = blockingStub.createLaptop(request);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.ALREADY_EXISTS) {
                // not a big deal
                logger.info("laptop ID already exists");
                return;
            }
            logger.log(Level.SEVERE, "request failed: " + e.getMessage());
            return;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "request failed: " + e.getMessage());
            return;
        }
        
        // ...
    }
}
```

Теперь сообщение является информационным и выглядит как "laptop ID already 
exists".

```shell
INFO: laptop ID already exists
```

Если мы изменим значение идентификатора на "invalid" и перезапустим клиент, то
в лог запишется сообщение с типом SEVERE и кодом состояния `Invalid Argument`.

```shell
SEVERE: request failed: INVALID_ARGUMENT: laptop ID is not a valid UUID: invalid UUID length: 7
```

Итак, Java клиент очень хорошо работает с Go сервером. Теперь давайте попробуем
проделать те же действия с Java сервером. Сервер запущен на порту 8080. Теперь 
запустим клиент с пустым идентификатором. Ноутбук был успешно создан.

```shell
INFO: laptop created with ID: 17877dd3-0f15-4087-9905-56e3eae8aa60
```

Теперь рассмотрим случай с идентификатором, который уже существует в хранилище.
Получаем сообщение `laptop ID already exists`.

```shell
INFO: laptop ID already exists
```

Затем случай с некорректным идентификатором. Запрос не выполнился, возникла 
ошибка `INVALID_ARGUMENT`.

```shell
SEVERE: request failed: INVALID_ARGUMENT: Invalid UUID string: invalid
```

И наконец случай с корректным идентификатором. Ноутбук был успешно создан.

```shell
INFO: laptop created with ID: 823095af-7bc0-4bfd-8d71-15651bf5e178
```

Итак, Java клиент также отлично работает с Java сервером. Теперь я покажу вам
как обрабатывать запросы, превысившие максимальное время выполнения. Допустим 
на сервере мы осуществляем какие-то ресурсоёмкие вычисления перед сохранением 
ноутбука в хранилище. Здесь в качестве примера я буду использовать простую 
задержку в 6 секунд. 

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...
    @Override
    public void createLaptop(CreateLaptopRequest request, StreamObserver<CreateLaptopResponse> responseObserver) {
        // ...

        // heavy processing
        try {
            TimeUnit.SECONDS.sleep(6);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // ...
    }
}
```

Затем на стороне клиента, установим максимальное время выполнения запроса 
в 5 секунд.

```java
public class LaptopClient {
    // ...
    public void createLaptop(Laptop laptop) {
        // ...

        try {
            response = blockingStub.withDeadlineAfter(5, TimeUnit.SECONDS).createLaptop(request);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.ALREADY_EXISTS) {
                // not a big deal
                logger.info("laptop ID already exists");
                return;
            }
            logger.log(Level.SEVERE, "request failed: " + e.getMessage());
            return;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "request failed: " + e.getMessage());
            return;
        }
        
        // ...
    }
}
```

Теперь запустите сервер и клиент. После 5 секунд, мы получим ошибку
`DEADLINE_EXCEEDED`.

```shell
SEVERE: request failed: DEADLINE_EXCEEDED: deadline exceeded after 4.984459972s. [buffered_nanos=141896411, remote_addr=0.0.0.0/[0:0:0:0:0:0:0:1]:8080]
```

Но, на стороне сервера, ноутбук всё равно создался, как видно из сообщения.

```shell
INFO: saved laptop with ID: 947bd987-5a1d-4fcc-8178-ecc10ec0aa9e
```

Давайте откроем код севера и исправим это. После ресурсоёмких вычислений, 
мы проверим текущее состояние контекста. Если он был отменен, мы записываем
в лог информационное сообщение, вызываем `responseObserver.onError()` со
статусом `Status.CANCELLED` и сразу же завершаем работу метода.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...
    @Override
    public void createLaptop(CreateLaptopRequest request, StreamObserver<CreateLaptopResponse> responseObserver) {
        // ...

        // heavy processing
        try {
            TimeUnit.SECONDS.sleep(6);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        if (Context.current().isCancelled()) {
            logger.info("request is cancelled");
            responseObserver.onError(
                    Status.CANCELLED
                            .withDescription("request is cancelled")
                            .asRuntimeException()
            );
            return;
        }
        
        // ...
    }
}
```

Теперь если мы перезапустим сервер и клиент, то в этот раз, на стороне сервера 
увидим сообщение "request is cancelled", а ноутбук больше не будет сохраняться
в хранилище.

```shell
INFO: request is cancelled
```

Превосходно! Прежде чем я покажу вам как написать модульные тесты для этого 
унарного RPC, я закомментирую этот кусок кода, имитирующий ресурсоёмкие 
вычисления.

## Тестируем обработчик унарного RPC
Наведите мышку на класс LaptopServer, нажмите option + Enter (на macOS) или 
Alt + Enter (на Win или Linux) и выберите в списке "Create Test".

![Создаём-тест](images/lecture10.2/create_test.png)

**Рисунок 1** - Создаём тест.

Поставьте галочки напротив `setUp` и `tearDown` и нажмите OK. Тестовый файл 
будет создан в папке `com/gitlab/techschool/pcbook/service`. В функции `setUp`
мы сгенерируем имя сервера и создадим новый внутрипроцессный построитель 
серверов с этим именем в качестве непосредственного исполнителя.

```java
public class LaptopServerTest {

    @Before
    public void setUp() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        InProcessServerBuilder serverBuilder = InProcessServerBuilder.forName(serverName).directExecutor();
    }

    @After
    public void tearDown() throws Exception {
    }
}
```

Затем мы объявляем 3 приватных объекта: хранилище ноутбуков, сервер ноутбуков и
управляемый канал.

```java
public class LaptopServerTest {

    private LaptopStore store;
    private LaptopServer server;
    private ManagedChannel channel;

    // ...
}
```

В функции `setUp` мы инициализируем хранилище, используя хранилище в памяти.
Мы создаём сервер, передавая в качестве входных параметров построитель серверов,
нулевой порт и хранилище. Вызываем `server.start()` и наконец создаём канал
как внутрипроцессный канал для сгенерированного имени сервера также в качестве
непосредственного исполнителя. Мы также хотим закрыть соединение после теста. 
Поэтому мы добавим здесь gRPC правило для закрытия и зарегистрируем новый канал
с ним в методе теста `setUp`.

```java
public class LaptopServerTest {
    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule(); // automatic shutdown channel at the end of test

    // ...

    @Before
    public void setUp() throws Exception {
        // ...

        store = new InMemoryLaptopStore();
        server = new LaptopServer(serverBuilder, 0, store);
        server.start();

        channel = grpcCleanup.register(
                InProcessChannelBuilder.forName(serverName).directExecutor().build()
        );
    }
    
    // ...
}
```

В функции `tearDown` всё что нам нужно сделать — это вызвать `server.stop()`.

```java
public class LaptopServerTest {
    // ...

    @After
    public void tearDown() throws Exception {
        server.stop();
    }
}
```

Теперь давайте напишем первый тест. Он протестирует RPC createLaptop с 
корректным идентификатором ноутбука. Сначала мы создадим новый генератор 
ноутбуков и сгенерируем случайный объект-ноутбук. Затем мы вызываем построитель
запроса create-laptop, используя сгенерированный ноутбук. Мы создаём новую 
блокирующую заглушку с управляемым каналом, который был проинициализирован в 
методе `setUp`. Затем мы используем заглушку для вызова `createLaptop` с 
объектом-запросом. Сервер возвращает ответ. Мы проверяем, что ответ не равен
`null` и что идентификатор ноутбука в ответе должен быть равен идентификатору
в запросе. Наконец, если мы находим идентификатор в хранилище, то он не должен
быть равен `null`. Отлично, давайте запустим тест.

```java
public class LaptopServerTest {
    // ...
    
    @Test
    public void createLaptopWithAValidID() {
        Generator generator = new Generator();
        Laptop laptop = generator.NewLaptop();
        CreateLaptopRequest request = CreateLaptopRequest.newBuilder().setLaptop(laptop).build();

        LaptopServiceGrpc.LaptopServiceBlockingStub stub = LaptopServiceGrpc.newBlockingStub(channel);
        CreateLaptopResponse response = stub.createLaptop(request);
        assertNotNull(response);
        assertEquals(laptop.getId(), response.getId());

        Laptop found = store.Find(response.getId());
        assertNotNull(found);
    }
}
```

Он успешно пройден. Теперь я скопирую этот тест и изменю его для второго 
случая: создания ноутбука с пустым идентификатором. Итак, я преобразую ноутбук
в построитель. Присвоим идентификатору значение пустой строки и заново создадим
ноутбук. Теперь если мы запустим этот тест, то он завершиться с ошибкой,
поскольку мы всё ещё ожидаем, что созданный идентификатор ноутбука будет равен
идентификатору в запросе.

```java
public class LaptopServerTest {
    // ...
    @Test
    public void createLaptopWithAnEmptyID() {
        Generator generator = new Generator();
        Laptop laptop = generator.NewLaptop().toBuilder().setId("").build();
        CreateLaptopRequest request = CreateLaptopRequest.newBuilder().setLaptop(laptop).build();

        LaptopServiceGrpc.LaptopServiceBlockingStub stub = LaptopServiceGrpc.newBlockingStub(channel);
        CreateLaptopResponse response = stub.createLaptop(request);
        assertNotNull(response);
        assertEquals(laptop.getId(), response.getId()); // response laptop ID not equal to the input laptop ID.

        Laptop found = store.Find(response.getId());
        assertNotNull(found);
    }
}
```

Итак, давайте изменим эту команду assert, чтобы просто проверить, что 
идентификатор ответа не пустой. Теперь тест пройден.

```java
public class LaptopServerTest {
    // ...
    @Test
    public void createLaptopWithAnEmptyID() {
        // ...
        assertFalse(response.getId().isEmpty());

        Laptop found = store.Find(response.getId());
        assertNotNull(found);
    }
}
```

Третий случай — когда клиент отправляет некорректный идентификатор. Здесь я 
просто изменю этот идентификатор ноутбука на "invalid" и проверю, что 
идентификатор ответа равен `null`. Самое важное здесь: мы ожидаем перехватить
исключение `StatusRuntimeException` в этом случае. Давайте запустим тест.

```java
public class LaptopServerTest {
    // ...
    @Test(expected = StatusRuntimeException.class)
    public void createLaptopWithAnInvalidID() {
        Generator generator = new Generator();
        Laptop laptop = generator.NewLaptop().toBuilder().setId("invalid").build();
        CreateLaptopRequest request = CreateLaptopRequest.newBuilder().setLaptop(laptop).build();

        LaptopServiceGrpc.LaptopServiceBlockingStub stub = LaptopServiceGrpc.newBlockingStub(channel);
        CreateLaptopResponse response = stub.createLaptop(request);
        assertNotNull(response);
        assertNull(response.getId());
    }
}
```

На самом деле я думаю, что ответ тоже может быть равен `null`.

```java
public class LaptopServerTest {
    // ...
    @Test(expected = StatusRuntimeException.class)
    public void createLaptopWithAnInvalidID() {
        Generator generator = new Generator();
        Laptop laptop = generator.NewLaptop().toBuilder().setId("invalid").build();
        CreateLaptopRequest request = CreateLaptopRequest.newBuilder().setLaptop(laptop).build();

        LaptopServiceGrpc.LaptopServiceBlockingStub stub = LaptopServiceGrpc.newBlockingStub(channel);
        CreateLaptopResponse response = stub.createLaptop(request);
        assertNull(response);
        assertNull(response.getId());
    }
}
```

Тест всё равно успешно пройден. Таким образом, похоже, что команда assert не 
выполняется после того, как исключение было поймано, и мы можем удалить её.

```java
public class LaptopServerTest {
    // ...
    @Test(expected = StatusRuntimeException.class)
    public void createLaptopWithAnInvalidID() {
        Generator generator = new Generator();
        Laptop laptop = generator.NewLaptop().toBuilder().setId("invalid").build();
        CreateLaptopRequest request = CreateLaptopRequest.newBuilder().setLaptop(laptop).build();

        LaptopServiceGrpc.LaptopServiceBlockingStub stub = LaptopServiceGrpc.newBlockingStub(channel);
        CreateLaptopResponse response = stub.createLaptop(request);
    }
}
```

Теперь последний случай: клиент отправляет идентификатор, который уже 
существует в хранилище. Здесь я сгенерирую обычный случайный ноутбук и сохраню
его в магазине перед тем как сделать запрос. В этом случае мы также ожидаем 
перехватить исключение `StatusRuntimeException`. Так что менять больше нечего.
Давайте запустим тест.

```java
public class LaptopServerTest {
    // ...
    @Test(expected = StatusRuntimeException.class)
    public void createLaptopWithAnAlreadyExistsID() throws Exception{
        Generator generator = new Generator();
        Laptop laptop = generator.NewLaptop();
        store.Save(laptop);
        CreateLaptopRequest request = CreateLaptopRequest.newBuilder().setLaptop(laptop).build();

        LaptopServiceGrpc.LaptopServiceBlockingStub stub = LaptopServiceGrpc.newBlockingStub(channel);
        CreateLaptopResponse response = stub.createLaptop(request);
    }
}
```

Он успешно пройден. Теперь давайте запустим все модульные тесты вместе. Если 
вы увидите четыре зеленых галочки, то все тесты успешно пройдены. Превосходно!

![Все-тесты-успешно-пройдены](images/lecture10.2/all_tests_passed.png)

**Рисунок 2** - Все тесты успешно пройдены.

На этом закончим лекцию. Теперь вы знаете как реализовать и протестировать 
унарный RPC как в Go, так и в Java.

На следующей лекции мы узнаем как реализовать второй тип gRPC, а именно 
серверную потоковую передачу. Надеюсь, что информацию, которую вы узнали из 
курса, пригодится вам. Желаю вам успехов в написании программ и до новых встреч!