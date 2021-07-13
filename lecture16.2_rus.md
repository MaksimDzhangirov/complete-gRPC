# Безопасное gRPC подключение с помощью SSL/TLS - Java
На предыдущей лекции мы узнали как включить TLS для защиты gRPC подключения
в Golang. Сегодня мы узнаем как сделать то же самое в Java. Если вы не читали
мою лекцию о [SSL/TLS](SSL_TLS_lecture_rus.md) я настоятельно рекомендую вам 
сначала прочитать её, чтобы лучше понимать TLS, прежде чем продолжить.

## Типы gRPC подключений
Напомним, что существует 3 типа gRPC подключений. Первый — это небезопасное 
подключение, когда все данные, передаваемые между клиентом и сервером, не
зашифрованы. Мы не должны применять его в продакшене. Второй тип — 
соединение, защищенное TLS на стороне сервера. В этом случае все данные 
зашифрованы, но только сервер должен предоставить свой сертификат TLS клиенту. 
Мы используем этот тип подключения, если серверу неважно какой клиент вызывает
его API. Третий и самый надежный тип — это подключение, защищенное 
двухсторонним TLS, когда и клиент, и сервер должны предоставить друг другу 
свои TLS сертификаты. Мы применяем его, когда серверу также необходимо 
проверить кто вызывает его сервисы. Начнём с TLS на стороне сервера!

## Генерируем TLS сертификаты
Сначала я скопирую скрипты для генерации TLS сертификатов из `pcbook golang` в
`pcbook java` проект. Вы можете прочитать лекцию о том как [создать и подписать
TLS сертификаты](create_SSL_TLS_certificates_rus.md), чтобы понять, как этот
скрипт работает. По сути, этот скрипт генерирует приватный ключ и 
самоподписанный сертификат CA. Затем он создаёт приватный ключ и CSR для 
сервера и использует приватный ключ CA для подписи этого CSR, чтобы получить
сертификат сервера. Точно так же он сгенерирует приватный ключ, CSR для клиента
и воспользуется тем же приватным ключом CA для подписи CSR. Таким образом, 
создаётся сертификат клиента. В этом курсе мы используем один и тот же CA для
подписи сертификатов клиента и сервера, но на самом деле может существовать
несколько клиентов, чьи сертификаты подписаны разными CA. Когда мы запускаем
этот скрипт `gen.sh` в терминале, он повторно генерирует все приватные ключи
и сертификаты для CA, сервера и клиента. А именно: сертификат CA `ca-cert.pem`,
приватный ключ CA `ca-key.pem`, сертификат клиента `client-cert.pem`, приватный
ключ клиента `client-key.pem`, сертификат сервера `server-cert.pem` и 
приватный ключ сервера `server-key.pem`.

## Реализуем TLS на стороне сервера
Теперь на сервер я добавлю новый статический метод для загрузки TLS данных и
возврата объекта `SslContext`. Сначала мы создаём два файловых объекта, где
будут храниться сертификат сервера и приватный ключ. Затем вызываем метод 
`SslContextBuilder.forServer()` и передаём эти два файла. Для TLS на стороне
сервера мы можем присвоить `clientAuth` значение `None`, то есть нам не нужно, 
чтобы клиент отправлял свой сертификат. После этого мы вызываем 
`GrpcSslContexts.configure()`, передаём объект построителя SSL контекста, 
затем обращаемся к методу `.build()`, чтобы создать `SslContext` и вернуть
его вызывающей стороне.

```java
public class LaptopServer {
    // ...
    
    public static SslContext loadTLSCredentials() throws SSLException {
        File serverCertFile = new File("cert/server-cert.pem");
        File serverKeyFile = new File("cert/server-key.pem");

        SslContextBuilder ctxBuilder = SslContextBuilder.forServer(serverCertFile, serverKeyFile)
                .clientAuth(ClientAuth.NONE);

        return GrpcSslContexts.configure(ctxBuilder).build();
    }
    
    // ...
}
```

Затем я добавлю новый конструктор для `LaptopServer`, который будет иметь 
ещё один дополнительный входной параметр: SSL контекст. Этот конструктор будет
создавать объект `LaptopServer` с SSL/TLS. Для этого здесь вместо 
`grpc.ServerBuilder`, мы должны использовать `NettyServerBuilder`. Нужно 
вызвать метод `.sslContext()` этого построителя, чтобы передать SSL контекст.

```java
public class LaptopServer {
    // ...
    
    public LaptopServer(int port, LaptopStore laptopStore, ImageStore imageStore, RatingStore ratingStore,
                        SslContext sslContext) {
        this(NettyServerBuilder.forPort(port).sslContext(sslContext), port, laptopStore, imageStore, ratingStore);
    }
    
    // ...   
}
```

Теперь в методе `main` всё что нам нужно сделать, это: загрузить TLS данные для
создания SSL контекста. Затем передать `sslContext` в новый конструктор
`LaptopServer` и на этом модификация сервера завершена.

```java
public class LaptopServer {
    // ...
    
    public static void main(String[] args) throws InterruptedException, IOException {
        // ...

        SslContext sslContext = LaptopServer.loadTLSCredentials();
        LaptopServer server = new LaptopServer(8080, laptopStore, imageStore, ratingStore, sslContext);
        
        // ...
    }
}
```

Давайте запустим его. После того как сервер стартовал, давайте попробуем 
подсоединить текущий клиент к нему. Запрос не будет выполнен, потому что мы 
пока не включили TLS на стороне клиента.

```shell
SEVERE: request failed: UNAVAILABLE: Network closed for unknown reason
```

Итак, давайте сделаем это! По аналогии с сервером, я определю метод для загрузки
TLS данных из PEM файлов. Но в этот раз нам нужно только загрузить сертификат
CA, подписавшего сертификат сервера. Смысл в том, что клиенту необходимо 
использовать сертификат CA для проверки сертификата, который он получает от 
сервера. Здесь мы просто вызываем `GrpcSslContexts.forClient()`, затем
`.trustManager()` и передаём файл сертификата CA. Наконец, обращаемся к методу
`.build()`, чтобы создать SSL контекст и вернуть его.

```java
public class LaptopClient {
    // ...
    
    public static SslContext loadTLSCredentials() throws SSLException {
        File serverCACertFile = new File("cert/ca-cert.pem");

        return GrpcSslContexts.forClient()
                .trustManager(serverCACertFile)
                .build();
    }
    
    // ...
}
```

После этого мы добавляем новый конструктор для создания `LaptopClient` с 
включенным TLS. Этот конструктор будет принимать SSL контекст в качестве 
входных данных и в нём мы будем использовать `NettyChannelBuilder` вместо
`ManagerChannelBuilder`. Также заменим вызов `usePlaintext()` на `sslContext`.

```java
public class LaptopClient {
    // ...
    
    public LaptopClient(String host, int port, SslContext sslContext) {
        channel = NettyChannelBuilder.forAddress(host, port)
                .sslContext(sslContext)
                .build();

        // ...
    }
    
    // ...
}
```

Отлично, теперь в методе `main` нам осталось загрузить TLS данные для создания
SSL контекста и передать его в новый конструктор `LaptopClient`. Клиент готов!

```java
public class LaptopClient {
    // ...
    
    public static void main(String[] args) throws InterruptedException, SSLException {
        SslContext sslContext = LaptopClient.loadTLSCredentials();
        LaptopClient client = new LaptopClient("0.0.0.0", 8080, sslContext);
        
        // ...
    }
        
    // ...
}
```

Давайте протестируем его!

```shell
INFO: laptop created with ID: 14bc64d0-c790-42ac-99b2-4b4c22f1b479
INFO: laptop created with ID: 325ecb33-da89-4b8d-8a51-c5f990934d8e
INFO: laptop created with ID: 96e6f0e7-fe0b-455e-b067-b3a609324a6e
INFO: rate laptop (y/n)?
```

На этот раз запросы успешно отправлены на сервер. TLS на стороне сервера
работает так, как мы и ожидали. 

## Реализация двухстороннего TLS
Для двухстороннего TLS требуется, чтобы клиент также поделился своим 
сертификатом с сервером. Для этого на серверной стороне, мы изменим значение
`clientAuth` на `REQUIRE` и загрузим сертификат CA, подписавшего сертификат 
клиента, чтобы проверить его. В нашем случае это тот же CA, который подписал 
сертификат сервера. Как и на стороне клиента здесь мы добавляем ещё один 
вызов `.trustManager()` и передаём сертификат CA клиента. На этом реализация 
необходимых изменений на сервере завершена.

```java
public class LaptopServer {
    // ...
    
    public static SslContext loadTLSCredentials() throws SSLException {
        // ...

        File clientCACertFile = new File("cert/ca-cert.pem");

        SslContextBuilder ctxBuilder = SslContextBuilder.forServer(serverCertFile, serverKeyFile)
                .clientAuth(ClientAuth.REQUIRE)
                .trustManager(clientCACertFile);

        // ...
    }
    
    // ...
}
```

Теперь если мы перезапустим сервер и попытаемся подключить клиент к нему, то
запрос завершиться ошибкой, поскольку мы не обновили клиент, чтобы он посылал
свой сертификат на сервер.

```shell
SEVERE: request failed: UNAVAILABLE: ssl exception
```

Давайте сделаем это. Я просто скопирую и вставлю фрагменты кода с сервера и 
изменю названия переменных и файлов с `server` на `client`. Затем нам осталось
добавить `.keyManager()` к `GrpcSslContexts` и передать сертификат клиента и 
приватный ключ. На этом всё!

```java
public class LaptopClient {
    // ...
    
    public static SslContext loadTLSCredentials() throws SSLException {
        File serverCACertFile = new File("cert/ca-cert.pem");
        File clientCertFile = new File("cert/client-cert.pem");
        File clientKeyFile = new File("cert/client-key.pem");

        return GrpcSslContexts.forClient()
                .keyManager(clientCertFile, clientKeyFile)
                .trustManager(serverCACertFile)
                .build();
    }
    
    // ...
}
```

Давайте запустим клиент.

```shell
INFO: laptop created with ID: 110c2b34-6104-4ef6-ad13-ea61d1091eb2
INFO: laptop created with ID: e4eb6195-a4e3-4c6c-8e34-6c3df66712d2
INFO: laptop created with ID: 337d4f10-f164-4429-ba77-612ace3ad594
INFO: rate laptop (y/n)?
```

Теперь все запросы успешно выполнены. Итак, у нас получилось включить 
двухсторонний TLS для нашего gRPC соединения. Спасибо за время, потраченное на
чтение, и до встречи на следующей лекции!