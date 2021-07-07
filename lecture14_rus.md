# gRPC reflection и Evans CLI
Привет всем! Рад снова вас приветствовать на курсе по gRPC. На этой лекции мы
узнаем о gRPC reflection и как использовать клиент Evans для взаимодействия с
ним. gRPC reflection - это дополнительное, необязательное расширение для 
сервера, которое позволяет клиентам создавать запросы без предварительной 
генерации заглушек. Оно может действительно пригодится клиентам, поскольку
позволяет изучить gRPC API перед тем как реализовать его.

## Включаем gRPC reflection на сервере

### Golang
Давайте добавим gRPC reflection на Golang сервер. Откройте эту страницу 
`https://github.com/grpc/grpc-go/blob/master/Documentation/server-reflection-tutorial.md`
в вашем браузере. Как видно из инструкции, никаких сложностей не должно 
возникнуть. Нам просто нужно импортировать пакет `reflection` и вызвать
`reflection.Register`. Итак, давайте сделаем это! В нашем файле 
`server/main.go` после того как мы зарегистрировали `LaptopService`, мы 
обращаемся к методу `reflection.Register` и передаём в него gRPC сервер. Вот и
всё!

```go
func main() {
    // ...
    
    grpcServer := grpc.NewServer()
    pb.RegisterLaptopServiceServer(grpcServer, laptopServer)
    reflection.Register(grpcServer)
    
    // ...
}
```

### Java
Как это сделать на Java? Тоже достаточно просто. Нам нужно добавить зависимость
`grpc-services` в наш проект и затем `ProtoReflectionService` в сервер. 
Перейдём в репозиторий `maven` и найдём `grpc-services`. Скопируем эту
настройку для Gradle

```
// https://mvnrepository.com/artifact/io.grpc/grpc-services
implementation group: 'io.grpc', name: 'grpc-services', version: '1.37.0'
```

и вставим её в файл `build.gradle`. Подождём пока IntelliJ IDEA обновит нужные
зависимости. Затем перейдём в файл `LaptopServer.java`. В конструкторе
`public LaptopServer(ServerBuilder serverBuilder, int port, LaptopStore 
laptopStore, ImageStore imageStore, RatingStore ratingStore)` после добавления
`laptopService` к серверу добавим в него затем новый экземпляр 
`ProtoReflectionService`. И вуаля, на сервере включено gRPC reflection.

```java
public class LaptopServer {
    // ...

    public LaptopServer(ServerBuilder serverBuilder, int port, LaptopStore laptopStore, ImageStore imageStore, RatingStore ratingStore) {
        // ...
        server = serverBuilder.addService(laptopService)
                .addService(ProtoReflectionService.newInstance())
                .build();
    }
}
```

## Устанавливаем Evans клиент
Затем мы установим Evans клиент для взаимодействия с gRPC reflection сервера. 
Откройте в браузере `https://github.com/ktr0731/evans`. Evans - это очень 
крутой gRPC клиент, который позволяет вам создавать и отправлять запросы на
gRPC сервер в интерактивной оболочке. Его можно установить несколькими 
способами, как описано на этой странице [Github](https://github.com/ktr0731/evans). 
У меня Mac, поэтому я установлю его с помощью Homebrew. Сначала выполните в 
командной строке

```shell
brew tap ktr0731/evans
```

чтобы добавить этот репозиторий в Homebrew. Затем 

```shell
brew install evans.
```

## Используем Evans CLI для работы с gRPC reflection
После того как Evans готов к работе, давайте запустим Golang gRPC сервер.
Поскольку gRPC reflection включено на нашем сервере, выполним команду `evans` c 
ключом `-r (--reflection)`. Итак, давайте скопируем эту команду

```shell
evans -r repl
```

и вызовем её в терминале. Нам также нужно указать порт сервера: 8080 

```shell
evans -r repl -p 8080
```

И вот мы внутри интерактивной оболочки Evans.

## Команды Show и Describe
Мы можем воспользоваться командой `show package`, чтобы увидеть все пакеты, 
доступные на сервере.

```shell
show package
```

Затем выполнить команду `package`, чтобы выбрать конкретный пакет.

```shell
package techschool_pcbook
```

Мы можем просмотреть все сервисы этого пакета

```shell
show service
```

или все сообщения.

```shell
show message
```

Чтобы получить формат сообщения, можно воспользоваться командой `description`.

```shell
desc CreateLaptopRequest
```

## Тестируем RPC для создания ноутбуков
Теперь давайте выберем `LaptopService` и вызовем `CreateLaptop` API.

```shell
service LaptopService
```

```shell
call CreateLaptop
```

Evans запросит у нас данные для создания запроса. Первое поле — это 
идентификатор ноутбука, который мы можем оставить пустым. Затем 
фирма-производитель, скажем `Apple`, название ноутбука: `Macbook Pro`, 
фирма-производитель ЦПУ: `Intel`, название процессора: пусть будет `Core i9`,
количество ядер - `8`, число потоков - `16`, минимальная частота - `2.5` ГГц,
максимальная частота - `4.5` ГГц.

```shell
laptop::id (TYPE_STRING) => 
laptop::brand (TYPE_STRING) => Apple
laptop::name (TYPE_STRING) => Macbook Pro
laptop::cpu::brand (TYPE_STRING) => Intel
laptop::cpu::name (TYPE_STRING) => Core i9
laptop::cpu::number_cores (TYPE_UINT32) => 8
laptop::cpu::number_threads (TYPE_UINT32) => 16
laptop::cpu::min_ghz (TYPE_DOUBLE) => 2.5
laptop::cpu::max_ghz (TYPE_DOUBLE) => 4.5
```

Затем нужно ввести объём ОЗУ. Допустим, пусть он будет равен 32 Гб. Evans 
позволяет нам выбирать `enum` из списка, что довольно удобно. Теперь что 
касается GPU. Пусть это будет `NVIDIA GTX2020` с частотой от `2.0` до `2.5`
ГГц и объёмом памяти в `16` Гб. Поскольку GPU является `repeated` полем, Evans
запросит у нас не хотим ли мы ввести значения для следующего GPU. Если "нет", то 
просто нажмите Ctrl+D.

```shell
✔ dig down
laptop::ram::value (TYPE_UINT64) => 32
✔ GIGABYTE
<repeated> laptop::gpus::brand (TYPE_STRING) => NVIDIA
<repeated> laptop::gpus::name (TYPE_STRING) => GTX2020
<repeated> laptop::gpus::min_ghz (TYPE_DOUBLE) => 2.0
<repeated> laptop::gpus::max_ghz (TYPE_DOUBLE) => 2.5
<repeated> laptop::gpus::memory::value (TYPE_UINT64) => 16
✔ GIGABYTE
```

Следующее поле — это накопитель, который также является полем типа `repeated`.
Допустим у создаваемого нами ноутбука будет SSD объёмом `512` Гб и HDD в 1 Тб. 
Нажмите Ctrl+D, чтобы перейти к следующему параметру.

```shell
✔ SSD
<repeated> laptop::gpus::storages::memory::value (TYPE_UINT64) => 512
✔ GIGABYTE
✔ HDD
<repeated> laptop::gpus::storages::memory::value (TYPE_UINT64) => 1
✔ TERABYTE
```

Размер экрана будет равен `16` дюймам, разрешение экрана - `3072` на `1920`,
тип матрицы `IPS`. Это не мультитач экран, поэтому я введу в поле `multitouch`
`false`. Для клавиатуры будет использоваться раскладка `QWERTY` и она будет 
иметь подсветку, поэтому в поле `backlit` введите `true`.

```shell
laptop::gpus::storages::screen::size_inch (TYPE_FLOAT) => 16
laptop::gpus::storages::screen::resolution::width (TYPE_UINT32) => 3072
laptop::gpus::storages::screen::resolution::height (TYPE_UINT32) => 1920
✔ IPS
laptop::gpus::storages::screen::multitouch (TYPE_BOOL) => false
✔ QWERTY
laptop::gpus::storages::keyboard::backlit (TYPE_BOOL) => true
```

Вес ноутбука будем указывать в килограмах (`kilograms`) и его значение будет 
равно `2.2`. Цена `3000` USD, год выпуска `2019` и, наконец, поле `updated_at`
можно не заполнять.

```shell
✔ weight_kg
laptop::gpus::storages::weight_kg (TYPE_DOUBLE) => 2.2
laptop::gpus::storages::price_usd (TYPE_DOUBLE) => 3000
laptop::gpus::storages::release_year (TYPE_UINT32) => 2019
laptop::gpus::storages::updated_at::seconds (TYPE_INT64) => 
laptop::gpus::storages::updated_at::nanos (TYPE_INT32) => 
```

После ввода, запрос отправится на сервер
Now as you can see the request is sent to the server 

```shell
{
  "id": "06612505-b18e-4401-a4d7-4bf1e769c74b"
}
```

и мы получим в качестве ответа идентификатор созданного ноутбука.

## Тестируем RPC для поиска ноутбуков
Давайте воспользуемся API для поиска ноутбуков, чтобы посмотреть, сможем ли мы 
найти этот ноутбук на сервере или нет.

```shell
call SearchLaptop
```

Максимальная цена будет равна `4000`, минимальное количество ядер ЦПУ `2.0` и
минимальный объём ОЗУ - `8` Гб. Здорово, ноутбук нашелся!

```shell
filter::max_price_usd (TYPE_DOUBLE) => 4000
filter::min_cpu_cores (TYPE_UINT32) => 4
filter::min_cpu_ghz (TYPE_DOUBLE) => 2.0
filter::min_ram::value (TYPE_UINT64) => 8
✔ GIGABYTE
{
  "laptop": {
    "id": "06612505-b18e-4401-a4d7-4bf1e769c74b",
    "brand": "Apple",
    "name": "Macbook Pro",
    "cpu": {
      "brand": "Intel",
      "name": "Core i9",
      "numberCores": 8,
      "numberThreads": 16,
      "minGhz": 2.5,
      "maxGhz": 4.5
    },
    "ram": {
      "value": "32",
      "unit": "GIGABYTE"
    },
    "gpus": [
      {
        "brand": "NVIDIA",
        "name": "GTX2020",
        "minGhz": 2,
        "maxGhz": 2.5,
        "memory": {
          "value": "16",
          "unit": "GIGABYTE"
        }
      }
    ],
    "storages": [
      {
        "driver": "SSD",
        "memory": {
          "value": "512",
          "unit": "GIGABYTE"
        }
      },
      {
        "driver": "HDD",
        "memory": {
          "value": "1",
          "unit": "TERABYTE"
        }
      }
    ],
    "screen": {
      "sizeInch": 16,
      "resolution": {
        "width": 3072,
        "height": 1920
      },
      "panel": "IPS"
    },
    "keyboard": {
      "layout": "QWERTY",
      "backlit": true
    },
    "weightKg": 2.2,
    "priceUsd": 3000,
    "releaseYear": 2019,
    "updatedAt": "1970-01-01T00:00:00Z"
  }
}
```

Итак, gRPC reflection работает без проблем на нашем Golang сервере. Теперь
давайте остановим его и выйдем из Evans.

```shell
exit
```

Попробуем сделать то же самое, используя Java сервер. Запустите сервер, 
вызвав `LaptopServer.main()`. Теперь вернитесь в терминал и выполните
команду для запуска Evans CLI.

```shell
evans -r repl -p 8080
```

Введите команды 
```shell
show package
show service
```

и вызовите API для создания ноутбуков.

```shell
call CreateLaptop
```

Evans запросит необходимые поля. Повторите последовательность действий,
которую мы выполнили для Golang сервера, и убедитесь, что всё работает 
правильно. На этом закончим сегодняшнюю лекцию. Спасибо за время, потраченное 
на чтение, и до скорой встречи на следующей лекции!