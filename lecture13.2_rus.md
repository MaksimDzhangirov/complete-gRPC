# Реализация двунаправленного потокового gRPC - Java
Привет всем! На этой лекции мы собираемся реализовать двунаправленный потоковый
RPC с помощью Java! Мы напишем то же самое API для подсчета рейтинга, которое 
мы создали с помощью Golang на предыдущей лекции. Оно позволяет клиенту 
оценивать несколько ноутбуков и получать обратно среднюю оценку для каждого из
них. Мы также научимся писать unit тесты для этого нового потокового RPC.

## Определяем двунаправленный потоковый gRPC в protobuf
Итак, давайте начнём! Поскольку мы уже определили RPC для подсчета рейтинга на 
последней лекции по Golang я просто перейду в Golang проект `pcbook` и скопирую 
содержимое файла `laptop_service.proto`. В него мы добавили новое сообщение 
`RateLaptopRequest`. Оно содержит два поля: `laptopID` типа `string` и `score`
типа `double`.

```protobuf
message RateLaptopRequest {
  string laptop_id = 1;
  double score = 2;
}
```

Также в файле появилось сообщение `RateLaptopResponse` с тремя полями:
идентификатором ноутбука, числом, указывающее сколько раз ноутбук оценивался, и
средней оценкой.

```protobuf
message RateLaptopResponse {
  string laptop_id = 1;
  uint32 rated_count = 2;
  double average_score = 3;
}
```

`RateLaptop` RPC - это RPC с двунаправленной потоковой передачей, принимающий
на вход поток запросов и возвращающий поток ответов от сервера. Ничего 
сложного, не так ли? Теперь давайте нажмём кнопку сборки проекта, чтобы
сгенерировать Java код из этого protobuf определения. Сборка должна пройти 
успешно.

## Реализуем хранилище рейтингов
Прежде всего определим новый класс `Rating`. Этот класс будет содержать данные
о рейтинге конкретного ноутбука. Зададим в нём свойство `count` типа `integer` 
для хранения количества раз сколько ноутбук оценивался и `sum` типа `double` - 
для суммы всех оценок. Я сгенерирую конструктор для них, а также два 
метода-геттера.

```java
package com.gitlab.techschool.pcbook.service;

public class Rating {
    private int count;
    private double sum;

    public Rating(int count, double sum) {
        this.count = count;
        this.sum = sum;
    }
    
    public int getCount() {
        return count;
    }
    
    public double getSum() {
        return sum;
    }
}
```

Отлично, теперь мы напишем ещё один метод, которая складывает два объекта 
класса `Rating`. Этот метод нам пригодится позднее для обновления рейтинга
ноутбука в хранилище. Он очень простой, мы возвращаем новый объект `Rating`,
у которого `count` и `sum` вычисляются путём сложения соответствующих свойств
двух входных объектов.

```java
public class Rating {
    // ...
    
    public static Rating add(Rating r1, Rating r2) {
        return new Rating(r1.count + r2.count, r1.sum + r2.sum);
    }
}
```

Теперь давайте определим новый интерфейс `RatingStore`. Он будет содержать 
только один метод: `Add` с двумя входными параметрами `laptopID` и `score`.
На выходе мы получаем обновленный рейтинг ноутбука.

```java
package com.gitlab.techschool.pcbook.service;

public interface RatingStore {
    Rating Add(String laptopID, double score);
}
```

Давайте создадим `InMemoryRatingStore` для реализации этого интерфейса. 
Как и для `InMemoryLaptopStore`, у нас будет `ConcurrentMap` для хранения 
данных рейтинга, где ключом будет `laptopID`, а значением — объект класса 
`Rating`. Мы инициализируем карту внутри конструктора. Теперь в методе `Add` 
мы должны атомарно обновить рейтинг ноутбука, поскольку одновременно может 
существовать несколько запросов на обновление рейтинга одного и того же 
ноутбука. Для этого мы воспользуемся метод `merge()` `ConcurrentMap`. По сути этот 
метод принимает ключ `laptopID`, объект класса `Rating`, который должен 
применяться, если ключ не был ранее связан с каким-либо значением, и функцию
переопределения для обновления данных существующего ключа. В нашем случае мы 
хотим добавить единицу к `count` и `score` к `sum` объекта класса `Rating`, 
поэтому применяем здесь функцию `Rating:add`. Этот метод `merge()` 
действительно хорош и очень удобен в работе.

```java
package com.gitlab.techschool.pcbook.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryRatingStore implements RatingStore {
    private ConcurrentMap<String, Rating> data;

    public InMemoryRatingStore() {
        data = new ConcurrentHashMap<>();
    }

    @Override
    public Rating Add(String laptopID, double score) {
        return data.merge(laptopID, new Rating(1, score), Rating::add);
    }
}
```

Но я хочу убедиться в правильности его работы, поэтому давайте напишем unit
тест, в котором мы одновременно вызовем `ratingStore.Add` из нескольких 
потоков. Сначала мы создадим новое хранилище рейтингов в памяти. Затем мы 
создадим список задач типа `Callable`, которые возвращают рейтинг. Мы 
сгенерируем случайный идентификатор ноутбука и пусть все задачи будут вызывать
`ratingStore.Add` с одной и той же оценкой 5. Я добавлю 10 задач в список, 
поэтому давайте воспользуемся здесь циклом `for`. Внутри мы запускаем 
`task.add` с лямбдой без входного аргумента. Она вернёт `ratingStore.Add()` с
`laptopID` и `score`. Здесь мы используем `Set` из целых чисел для отслеживания
значения `count`, которое было записано в хранилище после каждого вызова. После
мы вызываем для создания нового пула воркеров и по цепочке `.invokeAll()`, 
передавая список задач, превращаем его в поток и перебираем элементы с помощью
`forEach`. Каждый элемент будет объектом типа `future`, поэтому мы используем
`future.get()`, чтобы получить рейтинг на выходе после каждого вызова. Если мы
поймаем здесь исключение, просто сгенерируйте `IllegalStateException()`. В 
противном случае мы ожидаем, что `sum` в объекте `rating` должно быть равно 
произведению количеству раз сколько ноутбук оценивался на оценку. Мы записываем 
`count` объекта `rating` в множество, чтобы исключить одинаковые числа, и 
ожидаем, что будет ровно `n` различных значений `count` и они должны быть равны
от 1 до `n` (или от 1 до 10 в нашем случае). Итак, давайте запустим этот тест.

```java
package com.gitlab.techschool.pcbook.service;

import org.junit.Test;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

public class InMemoryRatingStoreTest {

    @Test
    public void add() throws InterruptedException {
        InMemoryRatingStore ratingStore = new InMemoryRatingStore();

        List<Callable<Rating>> tasks = new LinkedList<>();
        String laptopID = UUID.randomUUID().toString();
        double score = 5;

        int n = 10;
        for (int i = 0; i < n; i++) {
            tasks.add(() -> ratingStore.Add(laptopID, score));
        }

        Set<Integer> ratedCount = new HashSet<>();
        Executors.newWorkStealingPool()
                .invokeAll(tasks)
                .stream()
                .forEach(future -> {
                    try {
                        Rating rating = future.get();
                        assertEquals(rating.getSum(), rating.getCount() * score, 1e-9);
                        ratedCount.add(rating.getCount());
                    } catch (Exception e) {
                        throw new IllegalStateException();
                    }
                });

        assertEquals(n, ratedCount.size());
        for (int cnt = 1; cnt <= n; cnt++) {
            assertTrue(ratedCount.contains(cnt));
        }
    }
}
```

Он успешно пройден. Таким образом, метод `ratingStore.Add` отлично работает в 
многопоточном приложении.

## Реализуем двунаправленный потоковый gRPC сервер
Теперь давайте реализуем сервер. Сначала нам нужно добавить новое хранилище 
рейтингов в класс `LaptopService` и обновить его конструктор.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...
    
    private RatingStore ratingStore;

    public LaptopService(LaptopStore laptopStore, ImageStore imageStore, RatingStore ratingStore) {
        // ...
        this.ratingStore = ratingStore;
    }
}
```

Из-за этого изменения нам также необходимо обновить конструкторы класса 
`LaptopServer`.

```java
public class LaptopServer {
    // ...

    public LaptopServer(int port, LaptopStore laptopStore, ImageStore imageStore, RatingStore ratingStore) {
        this(ServerBuilder.forPort(port), port, laptopStore, imageStore, ratingStore);
    }

    public LaptopServer(ServerBuilder serverBuilder, int port, LaptopStore laptopStore, ImageStore imageStore, RatingStore ratingStore) {
        this.port = port;
        LaptopService laptopService = new LaptopService(laptopStore, imageStore, ratingStore);
        server = serverBuilder.addService(laptopService).build();
    }
}
```

А в функции `main`, мы должны создать новый объект `InMemoryRatingStore` и 
передать его в конструктор для создания сервера.

```java
public class LaptopServer {
    // ...
    public static void main(String[] args) throws InterruptedException, IOException {
        // ...
        InMemoryRatingStore ratingStore = new InMemoryRatingStore();

        LaptopServer server = new LaptopServer(8080, laptopStore, imageStore, ratingStore);
    }
}

public class LaptopServerTest {
    // ...

    @Before
    public void setUp() throws Exception {
        //...
        
        RatingStore ratingStore = new InMemoryRatingStore();
        server = new LaptopServer(serverBuilder, 0, laptopStore, imageStore, ratingStore);
        
        // ...
    }
}
```

Отлично, давайте вернемся к `LaptopService`, чтобы реализовать API для подсчета 
рейтинга ноутбука. Как и в случае с API для загрузки изображений, который мы 
реализовали на прошлой лекции, нам необходимо переопределить новый метод 
`rateLaptop`. На его вход поступает `responseObserver` и он должен возвращать
реализацию интерфейса `StreamObserver<RateLaptopRequest>`.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...
    
    @Override
    public StreamObserver<RateLaptopRequest> rateLaptop(StreamObserver<RateLaptopResponse> responseObserver) {
        return new StreamObserver<RateLaptopRequest>() {
            @Override
            public void onNext(RateLaptopRequest request) {
                
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

Затем в методе `onNext` мы получаем идентификатор ноутбука и оценку из 
запроса. Добавим сообщение в лог о том, что получен запрос rate-laptop. Потом
мы находим ноутбук по его идентификатору в хранилище. Если он не найден, мы
вызываем `responseObserver.onError` с кодом состояния `NOT_FOUND` и сразу 
выходим из метода. В противном случае мы используем `ratingStore.Add()`, чтобы
добавить новую оценку ноутбука к рейтингу и вернуть обновленное значение. Затем
создаём новый ответ от сервера, где значение идентификатора ноутбука берется
из запроса, ratedCount из объекта `rating`, а среднее значение вычисляется
путём деления суммы всех оценок на их число. Мы вызываем 
`responseObserver.onNext()`, чтобы послать этот ответ клиенту.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...
    
    @Override
    public StreamObserver<RateLaptopRequest> rateLaptop(StreamObserver<RateLaptopResponse> responseObserver) {
        return new StreamObserver<RateLaptopRequest>() {
            @Override
            public void onNext(RateLaptopRequest request) {
                String laptopId = request.getLaptopId();
                double score = request.getScore();

                logger.info("received rate-laptop request: id = " + laptopId + ", score = " + score);

                Laptop found = laptopStore.Find(laptopId);
                if (found == null) {
                    responseObserver.onError(
                            Status.NOT_FOUND
                                    .withDescription("laptop ID doesn't exists")
                                    .asRuntimeException()
                    );
                    return;
                }

                Rating rating = ratingStore.Add(laptopId, score);
                RateLaptopResponse response = RateLaptopResponse.newBuilder()
                        .setLaptopId(laptopId)
                        .setRatedCount(rating.getCount())
                        .setAverageScore(rating.getSum() / rating.getCount())
                        .build();

                responseObserver.onNext(response);
            }
            
            // ...
        };
    }
}
```

В методе `onError` мы просто пишем в лог предупреждающее сообщение с текстом 
ошибки. И, наконец, в методе `onCompleted` мы просто вызываем
`responseObserver.onCompleted()`.

```java
public class LaptopService extends LaptopServiceGrpc.LaptopServiceImplBase {
    // ...
    
    @Override
    public StreamObserver<RateLaptopRequest> rateLaptop(StreamObserver<RateLaptopResponse> responseObserver) {
        return new StreamObserver<RateLaptopRequest>() {
            // ...

            @Override
            public void onError(Throwable t) {
                logger.warning(t.getMessage());
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
}
```

Давайте попробуем запустить сервер. После этого подключим к нему Golang 
клиент, который мы написали на предыдущей лекции. Из логов видно, что было 
создано 3 ноутбука, введите "y" и нажмите "Enter", чтобы оценить их.

```shell
INFO: received rate-laptop request: id = f8c2b615-97a3-4878-bc98-27ffd8e5476b, score = 8.0
INFO: received rate-laptop request: id = 734d852c-cfb5-4b8a-9e87-000565602f4c, score = 10.0
INFO: received rate-laptop request: id = 7473c005-be2b-4dcb-9445-040f7f964b27, score = 7.0
```

Ноутбуки получили оценки 8, 10 и 7 соответственно. Превосходно! Повторите 
действия, чтобы оценить их ещё раз.

```shell
INFO: received rate-laptop request: id = f8c2b615-97a3-4878-bc98-27ffd8e5476b, score = 1.0
INFO: received rate-laptop request: id = 734d852c-cfb5-4b8a-9e87-000565602f4c, score = 4.0
INFO: received rate-laptop request: id = 7473c005-be2b-4dcb-9445-040f7f964b27, score = 6.0
```

Теперь новые оценки равны 1, 4 и 6. Рейтинг был обновлен, `rated count` стал 
равным 2, а средние оценки - 4,5, 7 и 6,5. Легко проверить, что значения были 
посчитаны верно.

```shell
2021/04/14 19:41:02 received response: laptop_id:"f8c2b615-97a3-4878-bc98-27ffd8e5476b" rated_count:2 average_score:4.5
2021/04/14 19:41:02 received response: laptop_id:"734d852c-cfb5-4b8a-9e87-000565602f4c" rated_count:2 average_score:7
2021/04/14 19:41:02 received response: laptop_id:"7473c005-be2b-4dcb-9445-040f7f964b27" rated_count:2 average_score:6.5
```

Здорово! Сервер на Java отлично выполняет свою работу.

## Реализуем двунаправленный потоковый gRPC клиент
Реализуем Java клиент. Я определю метод `rateLaptop`, который имеет два входных
параметра: массив идентификаторов ноутбуков и массив оценок. Как и для клиента,
загружающего изображения, нам понадобится `CountDownLatch`, чтобы дождаться 
завершения потока ответа. Затем мы вызовем `asyncStub.withDeadlineAfter` с
таймаутом в 5 секунд, `.rateLaptop()` и передадим в него реализацию интерфейса
`StreamObserver<RateLaptopResponse>`.

```java
public class LaptopClient {
    // ...
    
    public void rateLaptop(String[] laptopIDs, double[] scores) {
        CountDownLatch finishLatch = new CountDownLatch(1);
        StreamObserver<RateLaptopRequest> requestObserver = asyncStub.withDeadlineAfter(5, TimeUnit.SECONDS)
                .rateLaptop(new StreamObserver<RateLaptopResponse>() {
                    @Override
                    public void onNext(RateLaptopResponse value) {
                        
                    }

                    @Override
                    public void onError(Throwable t) {

                    }

                    @Override
                    public void onCompleted() {

                    }
                });
    }
}
```

В методе `onNext` мы только добавляем в лог сообщение о том, что мы получили 
ответ от сервера и его значения: идентификатор ноутбука, количество раз сколько 
ноутбук оценивался и среднюю оценку. В методе `onError` мы пишем в лог 
сообщение типа SEVERE со следующим текстом ошибки `"rate laptop
failed: " + t.getMessage()` и вызываем `finishLatch.countDown()`. В методе
`onCompleted` добавляем в лог информационное сообщение с текстом 
`"rate laptop completed"` и также используем `finishLatch.countDown()`.

```java
public class LaptopClient {
    // ...

    public void rateLaptop(String[] laptopIDs, double[] scores) {
        CountDownLatch finishLatch = new CountDownLatch(1);
        StreamObserver<RateLaptopRequest> requestObserver = asyncStub.withDeadlineAfter(5, TimeUnit.SECONDS)
                .rateLaptop(new StreamObserver<RateLaptopResponse>() {
                    @Override
                    public void onNext(RateLaptopResponse response) {
                        logger.info("laptop rated: id = " + response.getLaptopId() +
                                ", count = " + response.getRatedCount() +
                                ", average = " + response.getAverageScore());
                    }

                    @Override
                    public void onError(Throwable t) {
                        logger.log(Level.SEVERE, "rate laptop failed: " + t.getMessage());
                        finishLatch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        logger.info("rate laptop completed");
                        finishLatch.countDown();
                    }
                });
    }
}
```

Теперь нам нужно начать отправку потока запросов. Давайте переберём в цикле 
списки `laptopIDs` и `scores` и создадим новый запрос, используя i-тые значения
для `laptopID` and `score`. Вызовем `requestObserver.onNext()`, чтобы отправить
`request` на сервер. Затем добавим в лог информационное сообщение о том, что 
запрос был послан. Окружим этот цикл `for` блоком try-catch. Если было 
обнаружено исключение, запишем в лог сообщение типа SEVERE с текстом ошибки,
вызовем `requestObserver.onError()` и выйдем из метода. Наконец, мы используем
`requestObserver.onCompleted()`, чтобы сообщить серверу, что больше 
отправляться запросы в поток не будут. Затем вызываем `finishLatch.await()`,
чтобы дождаться потока ответа. Итак, метод `rateLaptop` реализован.

```java
public class LaptopClient {
    // ...

    public void rateLaptop(String[] laptopIDs, double[] scores) throws InterruptedException {
        // ...

        int n = laptopIDs.length;
        try {
            for (int i = 0; i < n; i++) {
                RateLaptopRequest request = RateLaptopRequest.newBuilder()
                        .setLaptopId(laptopIDs[i])
                        .setScore(scores[i])
                        .build();
                requestObserver.onNext(request);
                logger.info("sent rate-laptop request: id = " + request.getLaptopId() + ", score = " + request.getScore());
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

Теперь прежде чем вызвать этот метода, давайте немного модифицируем код. Я 
создам отдельный метод для тестирования API поиска ноутбуков. Скопируйте и 
вставьте код в метод `main` как показано ниже. Давайте также добавим метод
для тестирования API по созданию ноутбуков и загрузки изображений.

```java
public class LaptopClient {
    // ...
    
    public static void testCreateLaptop(LaptopClient client, Generator generator) {
        Laptop laptop = generator.NewLaptop();
        client.createLaptop(laptop);
    }

    public static void testSearchLaptop(LaptopClient client, Generator generator) {
        for (int i = 0; i < 10; i++) {
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

        client.searchLaptop(filter);
    }

    public static void testUploadImage(LaptopClient client, Generator generator) throws InterruptedException {
        // Test upload laptop image
        Laptop laptop = generator.NewLaptop();
        client.createLaptop(laptop);
        client.uploadImage(laptop.getId(), "tmp/laptop.jpg");
    }
    
    // ...
}
```

Отлично, теперь давайте реализуем новый метод для тестирования API подсчета 
рейтинга ноутбука и вызовем его из метода `main`. Допустим мы хотим оценить 
3 ноутбука несколько раз. Здесь я объявлю массив для хранения идентификаторов
ноутбуков. Используйте цикл `for` для генерации ноутбуков со случайными 
параметрами, сохраните идентификаторы в массив и вызовите API `createLaptop`,
чтобы создать их на сервере. После этого нам понадобится `Scanner`, чтобы 
считать данные со стандартного входа. Используйте здесь цикл `while` и внутри 
спрашивайте у пользователя хочет ли он осуществить ещё одну итерацию или нет.
Считайте введённое значение и если ответ - "нет", то выходим из цикла.

```java
public class LaptopClient {
    // ...
    
    public static void testRateLaptop(LaptopClient client, Generator generator) {
        int n = 3;
        String[] laptopIDs = new String[n];

        for (int i = 0; i < n; i++) {
            Laptop laptop = generator.NewLaptop();
            laptopIDs[i] = laptop.getId();
            client.createLaptop(laptop);
        }

        Scanner scanner = new Scanner(System.in);
        while (true) {
            logger.info("rate laptop (y/n)?");
            String answer = scanner.nextLine();
            if (answer.toLowerCase().trim().equals("n")) {
                break;
            }
        }
    }
    
    // ...
}
```

В противном случае мы сгенерируем новый массив с оценками.

```java
public class LaptopClient {
    // ...

    public static void testRateLaptop(LaptopClient client, Generator generator) {
        // ...
        
        while (true) {
            // ...
            
            double[] scores = new double[n];
            for (int i = 0; i < n; i++) {
                
            }
        }
    }
    
    // ...
}
```

Я добавлю новый метод в класс `Generator`, который будет возвращать оценку
ноутбука в виде целого числа от 1 до 10, сгенерированного случайным образом.

```java
public class Generator {
    // ...
    
    public double NewLaptopScore() {
        return randomInt(1, 10);
    }
    
    // ...    
}
```

После этого вызовите этот метод в цикле `for`. Наконец, используйте
`client.RateLaptop`, передав в него массивы `laptopIDs` и `scores`. Реализация
`testRateLaptop` завершена.

```java
public class LaptopClient {
    // ...

    public static void testRateLaptop(LaptopClient client, Generator generator) throws InterruptedException {
        // ...
        
        while (true) {
            // ...
                        
            for (int i = 0; i < n; i++) {
                scores[i] = generator.NewLaptopScore();
            }
            client.rateLaptop(laptopIDs, scores);
        }
    }
    
    // ...
}
```

## Запускаем двунаправленный gRPC сервер и клиент
Давайте запустим сервер и клиент. Было создано 3 ноутбука. Введите "y", чтобы
оценить их. По логам всё работает как надо. Запросы посылаются и принимаются
ответы.

```shell
INFO: sent rate-laptop request: id = 01eab4b0-c77d-45ec-948d-8cf9a034e2e1, score = 8.0
INFO: sent rate-laptop request: id = 653e64c9-ee0b-4883-a2eb-c906311c0569, score = 9.0
INFO: sent rate-laptop request: id = f02073f0-776d-4b50-9be4-1b0c900dfa9a, score = 8.0
INFO: laptop rated: id = 01eab4b0-c77d-45ec-948d-8cf9a034e2e1, count = 1, average = 8.0
INFO: laptop rated: id = 653e64c9-ee0b-4883-a2eb-c906311c0569, count = 1, average = 9.0
INFO: laptop rated: id = f02073f0-776d-4b50-9be4-1b0c900dfa9a, count = 1, average = 8.0
```

Оценим их ещё раз. Здесь мы видим, что были отправлены новые оценки, а обратно
были получены обновленные средние оценки.

```shell
INFO: sent rate-laptop request: id = 01eab4b0-c77d-45ec-948d-8cf9a034e2e1, score = 7.0
INFO: sent rate-laptop request: id = 653e64c9-ee0b-4883-a2eb-c906311c0569, score = 3.0
INFO: sent rate-laptop request: id = f02073f0-776d-4b50-9be4-1b0c900dfa9a, score = 10.0
INFO: laptop rated: id = 01eab4b0-c77d-45ec-948d-8cf9a034e2e1, count = 2, average = 7.5
INFO: laptop rated: id = 653e64c9-ee0b-4883-a2eb-c906311c0569, count = 2, average = 6.0
INFO: laptop rated: id = f02073f0-776d-4b50-9be4-1b0c900dfa9a, count = 2, average = 9.0
```

Отлично!

## Тестируем двунаправленный gRPC
Прежде чем мы закончим, я покажу вам как протестировать RPC с двунаправленной
потоковой передачей. Используя этот `LaptopServerTest` можно написать тест для 
клиентского потокового RPC. Они очень похожи. Сначала нам нужно добавить новое 
хранилище рейтингов в этот класс. Инициализируем его внутри метода `setUp`. 
Передадим его в конструктор, чтобы создать новый `LaptopServer`.

```java
public class LaptopServerTest {
    // ...
    
    private RatingStore ratingStore;
    
    // ...

    @Before
    public void setUp() throws Exception {
        // ...
        ratingStore = new InMemoryRatingStore();

        server = new LaptopServer(serverBuilder, 0, laptopStore, imageStore, ratingStore);
        // ...
    }
```

Затем в конце файла мы добавляем новый текст для `rateLaptop` API. Давайте 
создадим новый генератор. Сгенерируем новый ноутбук со случайными 
характеристиками и сохраним его в хранилище. Для простоты я буду оценивать 
один и тот же ноутбук несколько раз. Здесь мы должны создать новую заглушку,
используя канал. Не забывайте, что это асинхронная заглушка, а не блокирующая,
как в случае унарного RPC.

```java
public class LaptopServerTest {
    // ...
    
    @Test
    public void rateLaptop() throws Exception {
        Generator generator = new Generator();
        Laptop laptop = generator.NewLaptop();
        laptopStore.Save(laptop);
        
        LaptopServiceGrpc.LaptopServiceStub stub = LaptopServiceGrpc.newStub(channel);
    }
}
```

Затем нам нужно определить новый класс, который будет реализовывать интерфейс
`StreamObserver<RateLaptopResponse>`. В этом классе мы будем отслеживать 3 
параметра: список ответов от сервера, ошибку, если она возникает, и логическое
значение, указывающее завершился ли он нормально или нет. Поскольку этот класс
будет использоваться только для unit тестов, я сделаю все эти поля публичными.
Нам также нужно инициализировать список ответов внутри конструктора. В методе
`onError()` мы сохраняем `t` типа `Throwable` в свойство `err`, а в 
`onCompleted` - присваиваем свойству `completed` значение `true`.

```java
public class LaptopServerTest {
    // ...
    
    private class RateLaptopResponseStreamObserver implements StreamObserver<RateLaptopResponse> {
        public List<RateLaptopResponse> responses;
        public Throwable err;
        public boolean completed;

        public RateLaptopResponseStreamObserver() {
            responses = new LinkedList<>();
        }

        @Override
        public void onNext(RateLaptopResponse response) {
            responses.add(response);
        }

        @Override
        public void onError(Throwable t) {
            err = t;
        }

        @Override
        public void onCompleted() {
            completed = true;
        }
    }
}
```

Давайте вернемся к нашему тесту. Мы создадим новый 
`RateLaptopResponseStreamObserver` и вызовем функцию `stub.ratelaptop()` с 
этим `responseObserver`. Теперь мы отправим 3 запроса с оценками 8, 7,5 и 10.
Тогда ожидаемые средние оценки после каждого запроса будут равны 8, 7,75 
((8+7,5)/2) и 8,5 ((8+7,5+10)/3) соответственно. Мы используем цикл `for`
для последовательной отправки запросов. Внутри цикла мы создаём новый запрос
с одним и тем же идентификатором ноутбука и оценкой из массива `scores`. Потом
вызываем `requestObserver.onNext()`, чтобы отправить его на сервер. После цикла
нужно обратиться к методу `requestObserver.onCompleted()`. При тестировании 
ошибка должна быть равна `null`, а значение переменной `completed` 
`responseObserver` - `true`. Количество ответов должно быть равно количеству 
запросов. И, наконец, когда мы просматриваем в цикле список `responses`, 
идентификатор ноутбука в ответе и запросе должен совпадать. Значение 
`ratedСount` должно быть равно `idx + 1`, а средняя оценка должна быть равна 
ожидаемой.

```java
public class LaptopServerTest {
    // ...
    
    @Test
    public void rateLaptop() throws Exception {
        // ...
        
        RateLaptopResponseStreamObserver responseObserver = new RateLaptopResponseStreamObserver();
        StreamObserver<RateLaptopRequest> requestObserver = stub.rateLaptop(responseObserver);

        double[] scores = {8, 7.5, 10};
        double[] averages = {8, 7.75, 8.5};
        int n = scores.length;

        for (int i = 0; i < n; i++) {
            RateLaptopRequest request = RateLaptopRequest.newBuilder()
                    .setLaptopId(laptop.getId())
                    .setScore(scores[i])
                    .build();
            requestObserver.onNext(request);
        }

        requestObserver.onCompleted();
        assertNull(responseObserver.err);
        assertTrue(responseObserver.completed);
        assertEquals(n, responseObserver.responses.size());

        int idx = 0;
        for (RateLaptopResponse response : responseObserver.responses) {
            assertEquals(laptop.getId(), response.getLaptopId());
            assertEquals(idx + 1, response.getRatedCount());
            assertEquals(averages[idx], response.getAverageScore(), 1e-9);
            idx++;
        }
    }
}
```

Давайте запустим этот unit тест. Он успешно пройден! Здорово! Давайте запустим
все тесты в классе `LaptopServerTest`. Все тесты пройдены! На этом завершим 
лекции, посвященные реализации 4 типов gRPC. Надеюсь, они были для вас 
интересными и полезными. Спасибо за время, потраченное на чтение, и до новых 
встреч!
