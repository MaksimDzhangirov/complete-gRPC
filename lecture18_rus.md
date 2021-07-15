# Генерируем RESTful сервис и swagger документацию с помощью gRPC шлюза
Здравствуйте, рад снова видеть вас! Курс по gRPC оказался действительно долгим,
но мы многое узнали об его удивительных возможностях. Тем не менее, как вы 
наверное догадались, gRPC - это не универсальный инструмент. Бывают случаи,
когда мы по-прежнему хотим предоставлять доступ к традиционному RESTful JSON
API. Причины могут быть разными: от сохранения обратной совместимости до 
поддержки языков программирования или клиентов, в которых плохо реализован 
gRPC. Однако создавать API для REST довольно трудоемко и утомительно. Можно
ли написать код единожды, но чтобы API поддерживало и gRPC, и REST? На самом 
деле — да, можно.

## Устанавливаем и настраиваем gRPC шлюз
Один из способов добиться этого — использовать gRPC шлюз. gRPC шлюз — это 
плагин компилятора protocol buffer. Он считывает определения protobuf сервисов 
и создаёт прокси-сервер, который переводит RESTful HTTP-вызов в gRPC запрос.
Всё что нам нужно сделать — это внести небольшие изменения в сервис. Этим мы
и займёмся на этой лекции. [Эта ссылка](https://github.com/grpc-ecosystem/grpc-gateway) 
ведёт на github страницу gRPC шлюза. Я буду использовать его последнюю версию,
то есть вторую. Документацию к нему доступна на [этой странице](https://grpc-ecosystem.github.io/grpc-gateway/).
Перед тем как начать писать код установим несколько пакетов. Во-первых,
`protoc-gen-grpc-gateway`. Скопируем github ссылку и выполним

```shell
go get -u github.com/grpc-ecosystem/grpc-gateway/v2/protoc-gen-grpc-gateway
```

в терминале. Одно из преимуществ использования gRPC шлюза заключается в том, 
что он также генерирует swagger файлы за нас, создающие документацию к API.
Итак, давайте установим также пакет `protoc-gen-openapiv2`, отвечающий за это.

```shell
go get -u github.com/grpc-ecosystem/grpc-gateway/v2/protoc-gen-openapiv2
```

Теперь если мы перейдём в папку `go/bin`, то увидим там исполняемые двоичные 
файлы `protoc-gen-openapiv2` и `protoc-gen-grpc-gateway`. Затем нам нужно 
добавить `google.api.http` аннотацию к proto файлам. Можно настроить множество
параметров. Более подробную информацию можно получить по ссылке [a_bit_of_everything.proto](https://github.com/grpc-ecosystem/grpc-gateway/blob/master/examples/internal/proto/examplepb/a_bit_of_everything.proto).
Пока что я покажу вам как осуществить базовую конфигурацию. Чтобы добавить
`google.api.http` аннотацию, нам нужно скопировать сторонние protobuf файлы в
наш проект. Их можно найти в репозитории [googleapis repository](https://github.com/googleapis/googleapis).
Внутри него будет папка `google`. Вам понадобятся следующие файлы:

```shell
google/api/annotations.proto
google/api/field_behaviour.proto
google/api/http.proto
google/api/httpbody.proto
```

Давайте скопируем их в наш `pcbook` golang проект. После этого мы сможем 
добавить аннотацию к нашим сервисам в proto файлах.

## Добавляем аннотацию к proto файлам
Начнем с `auth_service.proto`. Сначала импортируем 
`google/api/annotations.proto`. Затем внутри Login RPC, добавим параметр
`google.api.http`. Мы объявляем маршрут, использующий метод POST и путь
`v1/auth/login`. У POST запроса должно быть `body`, поэтому добавим сюда 
поле `body` со значением `*`.

```protobuf
// ...

import "google/api/annotations.proto";

// ...

service AuthService {
  rpc Login(LoginRequest) returns (LoginResponse) {
    option (google.api.http) = {
      post : "/v1/auth/login"
      body : "*"
    };
  };
}
```

Хорошо теперь давайте проделаем то же самое с файлом `laptop_service.proto`. 
Сначала импортируем аннотации, затем добавим параметр `google.api.http` 
в `CreateLaptop` RPC. Это тоже POST запрос, но путь должен быть другим. Я буду
использовать `/v1/laptop/create`. К RPC для поиска ноутбука будем обращаться
через GET запрос с путём `v1/laptop/search`. По аналогии для RPC загрузки
изображений имеем POST и `/v1/laptop/upload_image`, а для RPC подсчета 
рейтинга - POST и `/v1/laptop/rate`.

```protobuf
// ...

import "google/api/annotations.proto";

// ...

service LaptopService {
  rpc CreateLaptop(CreateLaptopRequest) returns (CreateLaptopResponse) {
    option (google.api.http) = {
      post : "/v1/laptop/create"
      body : "*"
    };
  };
  rpc SearchLaptop(SearchLaptopRequest) returns (stream SearchLaptopResponse) {
    option (google.api.http) = {
      get  : "/v1/laptop/search"
    };
  };
  rpc UploadImage(stream UploadImageRequest) returns (UploadImageResponse) {
    option (google.api.http) = {
      post : "/v1/laptop/search"
      body : "*"
    };
  };
  rpc RateLaptop(stream RateLaptopRequest) returns (stream RateLaptopResponse) {
    option (google.api.http) = {
      post : "/v1/laptop/rate"
      body : "*"
    };
  };
}
```

## Генерируем файлы gRPC шлюза и swagger
Proto файлы обновлены, теперь нам нужно модифицировать нашу `make gen` команду,
чтобы `protoc` генерировал для нас файлы gRPC шлюза и swagger. Мы воспользуемся
опцией `grpc-gateway_out`, которая сообщит `protoc` сохранять файлы в папку 
`pb`, то есть туда же где генерируются gRPC коды. Затем мы используем опцию
`openapiv2_out` со значением `openapiv2` для указания папки, куда будут 
генерироваться swagger файлы.

```makefile
gen:
	protoc --proto_path=proto --go_out=pb --go-grpc_out=pb --grpc-gateway_out ./pb --openapiv2_out ./openapiv2  proto/*.proto
# ...
```

Давайте создадим папку `openapiv2` в корне нашего проекта `pcbook`. Теперь 
откроем терминал и выполним

```shell
make gen
```

чтобы сгенерировать код.

Как видите в папке `pb` было создано два новых файла: первый - 
`auth_service.pb.gw.go` для сервиса аутентификации. Он содержит функцию для
обработки POST запроса входа в систему. Обратите внимание, что эта функция
`RegisterAuthServiceHandlerServer` используется для внутрипроцессного 
преобразования из REST в gRPC. Таким образом, нам не нужно запускать отдельный
gRPC сервер для обслуживания запросов от REST сервера через сетевой вызов.
К сожалению, на данный момент внутрипроцессное преобразование поддерживает 
только унарный RPC. Для потовокого RPC необходимо использовать функцию
`RegisterAuthServiceHandlerFromEndpoint`, которая преобразует входящие
RESTful запросы в gRPC формат и вызывает соответствующий RPC по указанной
конечной точке. Файл `laptop_service.pb.gw.go` имеет аналогичное содержимое,
можете проверить, если хотите. Папка `openapiv2` состоит из большого числа 
сгенерированных swagger файлов. Но нас интересуют только файлы 
`auth_service.swagger.json` и `laptop_service.swagger.json`. Эти файлы
пригодятся нам при создании документации к API. Мы можем легко сгенерировать 
её, перейдя на сайт `swagger.io`, залогинившись, затем щелкнув на `Create New`
и выбрав `Import and Document API`. Нажмите `Browse`, найдите файл
`auth_service.swagger.json` и щелкните по кнопке `Upload`. Введите название и 
версию вашего API. В имени не должно быть пробелов, поэтому напишите
`pcbook-auth-service` и нажмите `Import OpenAPI`. Вуаля, у нас есть прекрасная 
документация по API для нашего сервиса аутентификации. Изучим, например, 
маршрут для входа в систему, тело запроса — это JSON с именем пользователя и
паролем. Успешный ответ будет иметь код состояния 200 и JSON body с токеном
доступа, а ответ с ошибкой будет содержать следующие поля в body.

```json
{
  "error": "string",
  "code": 0,
  "message": "string",
  "details": [
    {
      "type_url": "string",
      "value": "string"
    }
  ]
}
```

Здорово! Давайте также загрузим swagger файл сервиса для работы с ноутбуками.
Выберите файл `laptop_service.swagger.json` и нажмите `Upload File`. Затем
измените имя на `pcbook-laptop-service`, обновите версию до 1.0 и щелкните 
`Import OpenAPI`. Отлично, теперь у нас есть документация с четырьмя 
маршрутами: для создания, подсчета рейтинга, поиска ноутбука и загрузки
изображения. API для создания ноутбука представляет собой `POST` запрос с 
очень длинным JSON body. API для подсчета рейтинга также будет `POST` запросом,
но тело запроса — это поток ввода. Для поиска ноутбуков используется метод
`GET`. И как видите все условия фильтрации представлены здесь в виде 
параметров запроса. Отлично, мы ещё вернемся к документации позднее. 

## Реализуем REST сервер
Теперь давайте реализуем REST сервер. Сначала я немного реорганизую 
существующий код gRPC сервера. Давайте зададим функцию для запуска gRPC
сервера. Затем перенесём часть кода в эту функцию. Для работы функции 
потребуется несколько входных параметров: сервер аутентификации, сервер 
для работы с ноутбуками, JWT менеджер, параметр, определяющий хотим ли мы 
включить TLS или нет, и, наконец, объект `net.Listener`. Обратите внимание, что
переменная `enableTLS` больше не является указателем и нам не нужно писать в 
лог сообщение об ошибке и аварийно выходить из программы, а мы можем просто 
обернуть ошибку и вернуть её. Наконец, мы просто вернём 
`grpcServer.Serve(listener)`. После этого `runGRPCServer` готова.

`cmd/server/main.go`
```go
func runGRPCServer(
    authServer pb.AuthServiceServer,
    laptopServer pb.LaptopServiceServer,
    jwtManager *service.JWTManager,
    enableTLS bool,
    listener net.Listener,
) error {
    interceptor := service.NewAuthInterceptor(jwtManager, accessibleRoles())
    serverOptions := []grpc.ServerOption{
        grpc.UnaryInterceptor(interceptor.Unary()),
        grpc.StreamInterceptor(interceptor.Stream()),
    }
    
    if enableTLS {
        tlsCredentials, err := loadTLSCredentials()
        if err != nil {
            return fmt.Errorf("cannot load TLS credentials: %w", err)
        }
    
        serverOptions = append(serverOptions, grpc.Creds(tlsCredentials))
    }
    
    grpcServer := grpc.NewServer(serverOptions...)
    
    pb.RegisterAuthServiceServer(grpcServer, authServer)
    pb.RegisterLaptopServiceServer(grpcServer, laptopServer)
    reflection.Register(grpcServer)
    
    return grpcServer.Serve(listener)
}
```

В функции `main` мы просто вызываем `runGRPCServer`, передаём все необходимые
аргументы и проверяем возвращаемую ошибку. Если она не равна `nil`, добавляем
сообщение об ошибке и аварийно выходим из программы.

```go
func main() {
    // ...
    
    laptopStore := service.NewInMemoryLaptopStore()
    imageStore := service.NewDiskImageStore("img")
    ratingStore := service.NewInMemoryRatingStore()
    laptopServer := service.NewLaptopServer(laptopStore, imageStore, ratingStore)
    
    address := fmt.Sprintf("0.0.0.0:%d", *port)
    listener, err := net.Listen("tcp", address)
    if err != nil {
        log.Fatal("cannot start server: ", err)
    }
    
    err = runGRPCServer(authServer, laptopServer, jwtManager, *enableTLS, listener)
    if err != nil {
        log.Fatal("cannot start server: ", err)
    }
}
```

Теперь REST серверу потребуются аналогичные входные аргументы, поэтому я 
просто продублирую сигнатуру функции `runGRPCServer` и изменю название на
`runRESTServer`. Сначала мы вызываем `runtime.NewServerMux()`, чтобы создать
новый мультиплексор HTTP-запросов. Убедитесь, что импортирован правильный 
пакет, а именно `grpc-gateway/v2/runtime`. После этого создаём новый контекст с
отменой, вызываем `defer cancel`, чтобы избежать утечек памяти. Чтобы 
осуществить внутрипроцессное преобразование из REST в gRPC, мы вызовем функцию
`pb.RegisterAuthServiceHandlerServer()`, передадим контекст, мультиплексор
и объект сервера аутентификации. Если ошибка не равна `nil`, верните 
её. Сделаем то же самое, чтобы зарегистрировать сервер для работы с ноутбуками.
Затем добавим в лог сообщение о том, что был запущен REST сервер, указав его
адрес и значение опции TLS.

```go
log.Printf("start REST server on port %d, TLS = %t", listener.Addr().String, enableTLS)
```

Я добавлю этот лог также в функцию `runGRPCServer()`, но изменю в сообщении
REST на GRPC.

```go
func runGRPCServer(
    authServer pb.AuthServiceServer,
    laptopServer pb.LaptopServiceServer,
    jwtManager *service.JWTManager,
    enableTLS bool,
    listener net.Listener,
) error {
    // ...
    
    log.Printf("start GRPC server on port %s, TLS = %t", listener.Addr().String(), enableTLS)
	return grpcServer.Serve(listener)
}
```

Теперь мы проверяем включен ли TLS, затем, чтобы запустить REST сервер, 
вызываем `http.ServeTLS`, передавая `listener` и мультиплексор. Нам также 
необходимо передать путь к сертификату сервера и файлу приватного ключа. 
Они используются в функции `loadTLSCredentials`. Давайте определим константу
для пути к файлу сертификата сервера, приватного ключа сервера и сертификату 
CA клиента.

```go
const (
    serverCertFile   = "cert/server-cert.pem"
    serverKeyFile    = "cert/server-key.pem"
    clientCACertFile = "cert/ca-cert.pem"
)

func loadTLSCredentials() (credentials.TransportCredentials, error) {
    // Загружаем сертификат CA, подписавшего сертификат клиента
    pemClientCA, err := ioutil.ReadFile(clientCACertFile)
    // ...
    
    // Загружаем сертификат сервера и приватный ключ
    serverCert, err := tls.LoadX509KeyPair(serverCertFile, serverKeyFile)
    if err != nil {
        return nil, err
    }
    
    // ...
}
```

Отлично, теперь вернёмся к нашей функции `runRESTServer`. Теперь мы можем 
передать этой функции сертификат и приватный ключ сервера. Если TLS не 
включен, мы просто вызываем `http.Serve()` с `listener` и мультиплексором. На 
этом всё!

```go
func runRESTServer(
    authServer pb.AuthServiceServer,
    laptopServer pb.LaptopServiceServer,
    jwtManager *service.JWTManager,
    enableTLS bool,
    listener net.Listener,
) error {
    mux := runtime.NewServeMux()
    ctx, cancel := context.WithCancel(context.Background())
    defer cancel()

    // внутрипроцессный обработчик
    err := pb.RegisterAuthServiceHandlerServer(ctx, mux, authServer)
    if err != nil {
        return err
    }
    
    err = pb.RegisterLaptopServiceHandlerServer(ctx, mux, laptopServer)
    if err != nil {
        return err
    }
    
    log.Printf("start REST server on port %s, TLS = %t", listener.Addr().String(), enableTLS)
    if enableTLS {
        return http.ServeTLS(listener, mux, serverCertFile, serverKeyFile)
    }
    return http.Serve(listener, mux)
}
```

REST-сервер готов.

## Тестируем REST сервер с помощью Postman
Теперь в функцию `main` давайте добавим ещё один флаг, чтобы получить тип 
сервера из аргумента командной строки. Его значение может быть равно `grpc` 
(по умолчанию) или `rest`. Затем если тип сервера - `grpc`, то вызываем 
функцию `runGRPCServer()`, иначе - `runRESTServer()`.

```go
func main() {
    // ...
    serverType := flag.String("type", "grpc", "type of server (grpc/rest")
    
    // ...

    if *serverType == "grpc" {
        err = runGRPCServer(authServer, laptopServer, jwtManager, *enableTLS, listener)
    } else {
        err = runRESTServer(authServer, laptopServer, jwtManager, *enableTLS, listener)
    }
    
    // ...
}
```

Чтобы протестировать этот сервер мы должны добавить ещё одну команду в
`Makefile`. Эта команда запустит REST сервер. Давайте назовём её `make rest`.
Я буду использовать для неё другой порт, скажем, `8081`.

```makefile
rest:
	go run cmd/server/main.go -port 8081 -type rest
```

Теперь выполним 

```shell
make rest
```

в терминале.

Как видите, REST сервер стартовал на порту 8081. Теперь давайте откроем 
страницу swagger, посвященную сервису аутентификации и скопируем путь для 
входа в систему. Я воспользуюсь Postman для тестирования API. Нажмите на 
кнопку `+`, чтобы создать новый запрос, измените метод на `POST`, вставьте 
путь и допишите перед ним URL сервера, чтобы он выглядел как 
`http://localhost:8081/v1/auth/login`. Внизу можно задать тело JSON запроса.
На вкладке `body` выбираем `raw` и тип `JSON`. Вернемся на swagger страницу и
скопируем строку с телом, приведенную в качестве примера.

```json
{
    "username": "string",
    "password": "string"
}
```

Вставьте её в форму. Мы можем использовать значения для `username` и `password`
из кода. Я задам `admin1` для имени и пароль `secret`. После этого нажмите
"Send".

```json
{
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MjA5MTE3NDMsInVzZXJuYW1lIjoiYWRtaW4xIiwicm9sZSI6ImFkbWluIn0.LQfZIHHgXemWP4oP4Eg2PBbAdA3qjmnP-iI05Vqk2ig"
}
```

Ура, мы получили ответ с кодом состояния `200 OK` и токеном доступа. Всё 
работает как надо! Если мы изменим имя пользователя, например, на `admin2` и
опять нажмём `Send`, то получим код состояния `400 Not Found` и ошибку:
"incorrect username/password".

```json
{
    "code": 5,
    "message": "incorrect username/password",
    "details": []
}
```

Не забывайте, что работает только REST сервер, поскольку мы используем
внутрипроцессное преобразование, а оно поддерживает только унарные запросы.
Посмотрим, что произойдет, если мы попытаемся вызвать потоковый запрос. Я 
открою swagger страницу сервиса для работы с ноутбуками и скопирую путь для
поиска ноутбука `/v1/laptop/search`. Напомню, что изначально это серверный 
потоковый RPC. Давайте вставим путь в Postman и зададим параметры фильтрации.
Сначала максимальную цену в 5000, затем количество ядер ЦПУ: 2 ядра, 
минимальную частоту ЦПУ 2,0 ГГц, минимальный объём ОЗУ 3 и единицу изменения:
GIGABYTE. Теперь нажмите "Send".

```
filter.max_price_usd    5000
filter.min_cpu_cores    2
filter.min_cpu_ghz      2.0
filter.min_ram_value    3
filter.min_ram_unit     GIGABYTE
```

В этот раз мы получили ошибку 501 Not Implemented и сообщение о том, что 
потоковые вызовы всё ещё не поддерживаются при внутрипроцессной передаче. 

```json
{
    "code": 12,
    "message": "streaming calls are not yet supported in the in-process transport",
    "details": []
}
```

Если мы откроем сгенерированный код, то увидим ссылку на эту проблему в 
репозитории grpc-gateway с просьбой реализовать поддержку потоковой передачи
при внутрипроцессной передаче. При обычном RESTful вызове используется механизм
один запрос — один ответ, поэтому возможно перевести унарный RPC в REST. Но 
если мы действительно хотим преобразовать потоковые RPC, то для этого нужно 
воспользоваться gRPC шлюзом как прокси и 
`RegisterAuthServiceHandlerFromEndpoint`. Итак, давайте немного обновим код 
нашего REST сервера. Я закомментирую этот внутрипроцессный вызов. 

```go
func runRESTServer(
    authServer pb.AuthServiceServer,
    laptopServer pb.LaptopServiceServer,
    jwtManager *service.JWTManager,
    enableTLS bool,
    listener net.Listener,
) error {
    // ...
	
    // внутрипроцессный обработчик
    // err := pb.RegisterAuthServiceHandlerServer(ctx, mux, authServer)
	
    // ...
}
```

Заменим его на `RegisterAuthServiceHandlerFromEndpoint()`. Нам нужно передать
конечную точку gRPC сервера, поэтому давайте определим её как входной параметр
функции `runRESTServer()`. Поскольку это сетевой вызов, мы также должны 
предоставить функции `RegisterAuthServiceHandlerFromEndpoint` объект 
`dialOptions`. Объявим его в начале функции `runRESTServer()`. Для простоты
в этом курсе я буду использовать `grpc.WithInsecure()`. Теперь нужно сделать
то же самое для сервиса, работающего с ноутбуками.

```go
func runRESTServer(
    authServer pb.AuthServiceServer,
    laptopServer pb.LaptopServiceServer,
    jwtManager *service.JWTManager,
    enableTLS bool,
    listener net.Listener,
    grpcEndpoint string,
) error {
    mux := runtime.NewServeMux()
    dialOptions := []grpc.DialOption{grpc.WithInsecure()}
    // ...
    
    // внутрипроцессный обработчик
    // err := pb.RegisterAuthServiceHandlerServer(ctx, mux, authServer)
    err := pb.RegisterAuthServiceHandlerFromEndpoint(ctx, mux, grpcEndpoint, dialOptions)
    if err != nil {
        return err
    }
    
    // err = pb.RegisterLaptopServiceHandlerServer(ctx, mux, laptopServer)
    err = pb.RegisterLaptopServiceHandlerFromEndpoint(ctx, mux, grpcEndpoint, dialOptions)
    if err != nil {
        return err
    }
    
    // ...
}
```

Затем в функцию `main` давайте добавим новый аргумент командной строки для
конечной точки gRPC и передадим его при вызове функции `runRESTServer()`. 
Наконец, мы должны обновить нашу команду `make rest`, передав ей адрес
gRPC сервера, который находится на локальном хосте и порту 8080.

```makefile
rest:
	go run cmd/server/main.go -port 8081 -type rest -endpoint 0.0.0.0:8080
```

Давайте протестируем работу модифицированного REST сервера. Сначала выполните

```shell
make server
```

чтобы запустить gRPC сервер на порту 8080. Затем в другой вкладке мы вводим

```shell
make rest
```

чтобы стартовал REST сервер на порту 8081.

Теперь вернитесь в Postman и отправьте запрос на поиск ноутбука. В этот 
раз мы получили код состояния 200 OK. Тело ответа пустое, потому что мы ещё
не создали ни одного ноутбука. Давайте откроем ещё одну вкладку терминала и 
выполним

```shell
make client
```

Было создано 3 ноутбука. Отправим запрос на поиск ещё раз.

```json
{
    "result": {
        "laptop": {
            "id": "aaa8e8aa-c172-4afb-a726-95eb93bfef84",
            "brand": "Lenovo",
            "name": "Thinkpad P1",
            "cpu": {
                "brand": "Intel",
                "name": "Xeon-E-2286M",
                "numberCores": 2,
                "numberThreads": 2,
                "minGhz": 3.4397298380458095,
                "maxGhz": 3.7294466666413864
            },
            "ram": {
                "value": "23",
                "unit": "GIGABYTE"
            },
            "gpus": [
                {
                    "brand": "NVIDIA",
                    "name": "RTX 2070",
                    "minGhz": 1.2799156633669841,
                    "maxGhz": 1.9953732411161396,
                    "memory": {
                        "value": "2",
                        "unit": "GIGABYTE"
                    }
                }
            ],
            "storages": [
                {
                    "driver": "SSD",
                    "memory": {
                        "value": "864",
                        "unit": "GIGABYTE"
                    }
                },
                {
                    "driver": "HDD",
                    "memory": {
                        "value": "3",
                        "unit": "TERABYTE"
                    }
                }
            ],
            "screen": {
                "sizeInch": 13.702278,
                "resolution": {
                    "width": 7616,
                    "height": 4284
                },
                "panel": "IPS",
                "multitouch": true
            },
            "keyboard": {
                "layout": "QWERTY",
                "backlit": false
            },
            "weightKg": 1.8507416377914885,
            "priceUsd": 2133.7980430974994,
            "releaseYear": 2019,
            "updatedAt": "2021-05-14T07:06:28.129475620Z"
        }
    }
}
```
```json
{
    "result": {
        "laptop": {
            "id": "cbb0777a-fe62-4d16-95c6-e1634345c01b",
            "brand": "Lenovo",
            "name": "Thinkpad X1",
            "cpu": {
                "brand": "Intel",
                "name": "Xeon-E-2286M",
                "numberCores": 3,
                "numberThreads": 11,
                "minGhz": 2.220573657654501,
                "maxGhz": 2.62113176063615
            },
            "ram": {
                "value": "22",
                "unit": "GIGABYTE"
            },
            "gpus": [
                {
                    "brand": "NVIDIA",
                    "name": "RTX 2070",
                    "minGhz": 1.1780559656007965,
                    "maxGhz": 1.4235571769719728,
                    "memory": {
                        "value": "4",
                        "unit": "GIGABYTE"
                    }
                }
            ],
            "storages": [
                {
                    "driver": "SSD",
                    "memory": {
                        "value": "247",
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
                "sizeInch": 16.78862,
                "resolution": {
                    "width": 2634,
                    "height": 1482
                },
                "panel": "OLED",
                "multitouch": true
            },
            "keyboard": {
                "layout": "QWERTY",
                "backlit": true
            },
            "weightKg": 1.926311921250373,
            "priceUsd": 2600.928311353924,
            "releaseYear": 2018,
            "updatedAt": "2021-05-14T07:06:28.130963792Z"
        }
    }
}
```
```json
{
    "result": {
        "laptop": {
            "id": "ec2364f3-0805-4f84-b8df-e58dc3bc09de",
            "brand": "Lenovo",
            "name": "Thinkpad X1",
            "cpu": {
                "brand": "AMD",
                "name": "Ryzen 7 PRO 2700U",
                "numberCores": 7,
                "numberThreads": 12,
                "minGhz": 2.9806856701285236,
                "maxGhz": 4.919253043897466
            },
            "ram": {
                "value": "39",
                "unit": "GIGABYTE"
            },
            "gpus": [
                {
                    "brand": "NVIDIA",
                    "name": "GTX 1660-Ti",
                    "minGhz": 1.1418453526952794,
                    "maxGhz": 1.6620512959451756,
                    "memory": {
                        "value": "2",
                        "unit": "GIGABYTE"
                    }
                }
            ],
            "storages": [
                {
                    "driver": "SSD",
                    "memory": {
                        "value": "669",
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
                "sizeInch": 14.140779,
                "resolution": {
                    "width": 5676,
                    "height": 3193
                },
                "panel": "OLED",
                "multitouch": true
            },
            "keyboard": {
                "layout": "AZERTY",
                "backlit": true
            },
            "weightKg": 2.0491629125076383,
            "priceUsd": 1577.7405639846068,
            "releaseYear": 2019,
            "updatedAt": "2021-05-14T07:06:28.131226058Z"
        }
    }
}
```

Другое дело! Было найдено несколько ноутбуков. Как видите, мы получили 3 
отдельных JSON объекта, а не массив. Поскольку gRPC отправляет ответ, 
используя потоковую передачу, REST сервер посылает JSON body в виде 
последовательности из нескольких отдельных JSON объектов.

Это всё о чём бы я хотел вам рассказать на этой лекции. Вы можете 
поэкспериментировать с другими типами gRPC, например, клиентской
или двунаправленной потоковой передачей, если хотите. Надеюсь, что вам 
понравилось работать с gRPC шлюзом, а полученные значения окажутся полезными.
Большое спасибо за время, потраченное на чтение, до новых встреч на следующих
лекциях!