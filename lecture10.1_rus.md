# Реализация унарного API gRPC - Golang
Существует четыре типа gRPC. На этой лекции мы узнаем как реализовать 
простейший: унарный gRPC. Мы будем использовать Golang в этой лекции и Java - 
на следующей. Придерживаемся следующего плана. Сначала мы определим proto 
сервис, который содержит унарный gRPC для создания ноутбука. Затем мы 
реализуем сервер для обработки запроса и сохранения ноутбука в хранилище, 
находящимся в оперативной памяти. Мы создадим клиента для вызова RPC и напишем
модульные тесты, осуществляющие взаимодействие между клиентом и сервером. 
Наконец, мы узнаем как обрабатывать ошибки, возвращать правильные коды 
состояния gRPC, задавать максимальное время на выполнение gRPC. Итак, давайте 
начнём! Я открою проект `pcbook` с помощью Visual Studio Code.

## Определяем proto сервис и унарный RPC
В первую очередь мы создадим новый файл `laptop_service.proto`. Синтаксис, 
название пакета и опции не будут отличаться от других proto файлов, поэтому я 
просто скопирую их и вставлю в него. Нам понадобится сообщение `Laptop`, 
поэтому давайте импортируем его. Затем определим сообщение 
`CreateLaptopRequest`. Оно будет содержать только одно поле: ноутбук. Затем 
задайте сообщение `CreateLaptopResponse`. Оно также будет состоять из одного 
поля: идентификатора ноутбука. И самое важное, мы определим `LaptopService` с
помощью ключевого слова "service". Затем внутри него, зададим унарный RPC 
вызов. Напишем ключевое слово "rpc", затем название RPC - "CreateLaptop". Он 
принимает на вход `CreateLaptopRequest` и возвращает `CreateLaptopResponse`. 
Далее следует пара фигурных скобок и точка с запятой. На этом всё! Ничего 
сложного!

```protobuf
syntax = "proto3";

package techschool_pcbook;

option go_package = ".;pb";
option java_package = "com.github.techschool.pcbook.pb";
option java_multiple_files = true;

import "laptop_message.proto";

message CreateLaptopRequest {
  Laptop laptop = 1;
}

message CreateLaptopResponse {
  string id = 1;
}

service LaptopService {
  rpc CreateLaptop(CreateLaptopRequest) returns (CreateLaptopResponse) {};
}
```

## Генерируем код для унарного RPC
Теперь давайте откроем терминал и выполним команду `make gen`, чтобы 
сгенерировать код. Будет создан файл `laptop_service.pb.go`. Давайте изучим 
его. Внутри вы найдёте структуру `CreateLaptopRequest`. 

```go
type CreateLaptopRequest struct {
    state         protoimpl.MessageState
    sizeCache     protoimpl.SizeCache
    unknownFields protoimpl.UnknownFields
    
    Laptop *Laptop `protobuf:"bytes,1,opt,name=laptop,proto3" json:"laptop,omitempty"`
}
func (x *CreateLaptopRequest) GetLaptop() *Laptop {
    if x != nil {
        return x.Laptop
    }
    return nil
}
```

Она содержит функцию `GetLaptop()`, возвращающую объект-ноутбук, поданный на 
вход.
Это структура `CreateLaptopResponse`.

```go
type CreateLaptopResponse struct {
    state         protoimpl.MessageState
    sizeCache     protoimpl.SizeCache
    unknownFields protoimpl.UnknownFields
    
    Id string `protobuf:"bytes,1,opt,name=id,proto3" json:"id,omitempty"`
}
func (x *CreateLaptopResponse) GetId() string {
    if x != nil {
        return x.Id
    }
    return ""
}
```

Она содержит функцию `GetId()`, возвращающую идентификатор созданного 
ноутбука.
Это интерфейс `LaptopServiceClient` из файла `laptop_service_grpc.pb.go`. Он 
содержит функцию `CreateLaptop`. Её название совпадает с тем, что мы определили
в proto файле.

```go
type LaptopServiceClient interface {
    CreateLaptop(ctx context.Context, in *CreateLaptopRequest, opts ...grpc.CallOption) (*CreateLaptopResponse, error)
}
```

Почему это интерфейс? Потому что это позволит реализовать, удовлетворяющий 
нашим требованиям, собственный пользовательский клиент. Например, для 
модульного тестирования можно использовать клиент, имитирующий работу 
настоящего.
Ниже приведена структура `laptopServiceClient`, начинающаяся с маленькой буквы
"l", которая является реализацией этого интерфейса.  

```go
type laptopServiceClient struct {
    cc grpc.ClientConnInterface
}
func (c *laptopServiceClient) CreateLaptop(ctx context.Context, in *CreateLaptopRequest, opts ...grpc.CallOption) (*CreateLaptopResponse, error) {
    out := new(CreateLaptopResponse)
    err := c.cc.Invoke(ctx, "/techschool_pcbook.LaptopService/CreateLaptop", in, out, opts...)
    if err != nil {
        return nil, err
    }
    return out, nil
}
```

Далее рассмотрим `LaptopServiceServer`. Это тоже интерфейс, но без реализации.
По сути, наша задача заключается в написании собственной реализации сервера.  

```go
type LaptopServiceServer interface {
    CreateLaptop(context.Context, *CreateLaptopRequest) (*CreateLaptopResponse, error)
    mustEmbedUnimplementedLaptopServiceServer()
}
```

Но в любом случае эта реализация должна содержать функцию `CreateLaptop`, 
определенную в этом интерфейсе. 
В файле `laptop_service_grpc.pb.go` также содержится функция, регистрирующая
создаваемый сервис на определённом gRPC сервере, который будет получать и 
обрабатывать запросы от клиента. 

```go
func RegisterLaptopServiceServer(s grpc.ServiceRegistrar, srv LaptopServiceServer) {
    s.RegisterService(&LaptopService_ServiceDesc, srv)
}
```

## Реализуем серверный обработчик унарного RPC
Теперь давайте реализуем `LaptopServiceServer`! Я создам новую папку "service"
и файл `laptop_server.go` внутри неё. Я объявлю структуру `LaptopServer`. 
Напишу комментарий к ней и определю функцию `NewLaptopServer`, которая будет 
возвращать новый экземпляр `LaptopServer`.

```go
package service

// LaptopServer - это сервер, предоставляющий различные сервисы по работе с ноутбуком
type LaptopServer struct {
	pb.UnimplementedLaptopServiceServer
}

// NewLaptopServer возвращает новый экземпляр LaptopServer
func NewLaptopServer() *LaptopServer {
    return &LaptopServer{}
}
```

Теперь нам нужно реализовать функцию `CreateLaptop`, которая необходима, чтобы
структура удовлетворяла интерфейсу `LaptopServiceServer`. Кроме того, 
все реализации интерфейса должны содержать структуру 
`UnimplementedLaptopServiceServer` для прямой совместимости. Функция 
`CreateLaptop` принимает контекст и `CreateLaptopRequest` в качестве входных 
параметров и возвращает `CreateLaptopResponse` с ошибкой. Добавим к нему также 
комментарий.

```go
// CreateLaptop - это унарный RPC вызов для создания нового ноутбука
func (server *LaptopServer) CreateLaptop(ctx context.Context, req *pb.CreateLaptopRequest) (*pb.CreateLaptopResponse, error) {

}
```

Эта строка слишком длинная, поэтому я разобью её, чтобы она проще читалась.
Сначала мы вызываем функцию `GetLaptop`, чтобы получить объект-ноутбук из
запроса. Затем мы пишем в лог сообщение, в котором говорится, что был принят 
запрос на создание ноутбука с таким-то идентификатором.

```go
func (server *LaptopServer) CreateLaptop(
    ctx context.Context,
    req *pb.CreateLaptopRequest,
) (*pb.CreateLaptopResponse, error) {
    laptop := req.GetLaptop()
    log.Printf("receive a create-laptop request with id: %s", laptop.Id)
}
```

Если клиент уже сгенерировал идентификатор ноутбука, то мы должны проверить 
его на корректность. Для этого мы будем использовать пакет Google UUID. Введите 
в поисковой строке браузера `golang google uuid`, откройте страницу пакета 
на github `https://github.com/google/uuid`, скопируйте эту `go get` команду

```shell
go get github.com/google/uuid
```

и выполните в терминале, чтобы установить пакет. Теперь мы можем использовать
функцию `uuid.Parse()`, чтобы проанализировать идентификатор ноутбука. Если 
она возвращает ошибку, то это означает, что был передан некорректный 
идентификатор. Мы должны вернуть клиенту `nil` в качестве ответа на запрос 
вместе с кодом состояния ошибки. Для этого мы можем использовать подпакеты 
`status` и `codes` пакета `grpc`. В нашем случае мы возвращаем код 
`InvalidArgument` (Недопустимый Аргумент), поскольку неправильный идентификатор
ноутбука передал клиент. Мы отправляем код с сообщением "laptop ID is not a 
valid UUID" и ошибкой, возникшей при проверке. Если клиент не посылал 
идентификатор ноутбука, то мы сгенерируем его на сервере с помощью команды: 
`uuid.NewRandom()`. Если в ходе выполнения этой команды возникнет ошибка, то
мы вернем её с кодом `codes.Internal`, что означает внутреннюю ошибку сервера.
В противном случае, если всё пройдет хорошо, мы присвоим сгенерированное 
случайное значение UUID полю `laptop.Id`. Поле `laptop.Id` имеет тип `string`,
поэтому нужно преобразовать UUID в строку.

```go
func (server *LaptopServer) CreateLaptop(
	ctx context.Context,
	req *pb.CreateLaptopRequest,
) (*pb.CreateLaptopResponse, error) {
	// ...

    if len(laptop.Id) > 0 {
        // проверяем UUID на корректность
        _, err := uuid.Parse(laptop.Id)
        if err != nil {
            return nil, status.Errorf(codes.InvalidArgument, "laptop ID is not a valid UUID: %v", err)
        }
    } else {
        id, err := uuid.NewRandom()
        if err != nil {
            return nil, status.Errorf(codes.Internal, "cannot generate a new laptop ID: %v", err)
        }
        laptop.Id = id.String()
    }
}
```

## Реализуем хранилище для сохранения ноутбуков в памяти
Итак, обычно после создания мы должны сохранить ноутбук в базе данных. Но 
этот курс посвящен gRPC и я хочу сосредоточиться на нём. Поэтому для простоты
я буду использовать хранилище, записывающее данные в оперативную память. Оно
также пригодится нам в дальнейшем для модульного тестирования. Давайте добавим
`LaptopStore` в структуру `LaptopServer`.

```go
type LaptopServer struct {
    Store LaptopStore
    pb.UnimplementedLaptopServiceServer
}
```

Затем создайте новый файл `laptop_store.go` внутри папки `service`. Поскольку
мы можем использовать разные типы хранилищ, я определю `LaptopStore` как 
интерфейс. Он будет содержать функцию `Save` для сохранения ноутбука в 
хранилище. Затем мы создадим `InMemoryLaptopStore` для реализации этого 
интерфейса. Позже, если мы захотим сохранить ноутбук в базе данных, то всегда 
сможем реализовать хранилище `DBLaptopStore` для этого. Итак, вернемся к нашему
`InMemoryLaptopStore`. Мы будем использовать отображение (словарь, хеш-таблицу)
для хранения данных, где ключом будет идентификатор ноутбука, а значением — 
объект-ноутбук. Может возникнуть ситуация, когда несколько одновременных 
запросов попытаются сохранить ноутбуки в нашем хранилище, поэтому нам 
понадобится мьютекс на чтение-запись для решения этой проблемы.

```go
// LaptopStore интерфейс для хранения ноутбуков
type LaptopStore interface {
    // Save сохраняет ноутбук в хранилище
    Save(laptop *pb.Laptop) error
}

// InMemoryLaptopStore сохраняет ноутбук в памяти
type InMemoryLaptopStore struct {
    mutex sync.RWMutex
    data  map[string]*pb.Laptop
}
```

Теперь давайте объявим функцию, возвращающую новую структуру `InMemoryLaptopStore`
и инициализируем отображение с данными внутри неё.

```go
// NewInMemoryLaptopStore возвращает новый InMemoryLaptopStore
func NewInMemoryLaptopStore() *InMemoryLaptopStore {
    return &InMemoryLaptopStore{
        data: make(map[string]*pb.Laptop),
    }
}
```

Затем реализуем функцию `Save` для сохранения ноутбука как того требует 
интерфейс. Сначала нам нужно захватить блокировку на запись, прежде чем 
добавлять новые объекты. Не забудьте вызвать команду разблокировки с помощью 
`defer`. Затем проверьте, существует ли в отображении ключ с таким 
идентификатором ноутбука. Если да, просто верните сообщение об ошибке тому, кто
вызвал функцию.

```go
// Save сохраняет ноутбук в хранилище
func (store *InMemoryLaptopStore) Save(laptop *pb.Laptop) error {
    store.mutex.Lock()
    defer store.mutex.Unlock()
    
    if store.data[laptop.Id] != nil {
        return ErrAlreadyExists
    }
}
```

Я определю ошибку в виде экспортируемой переменной, чтобы её можно было 
использовать вне пакета `service`.

```go
// ErrAlreadyExists is returned when a record with the same ID already exists in the store
var ErrAlreadyExists = errors.New("record already exists")
```

Теперь если ноутбука не существует, то мы можем сохранить его в хранилище. 
Чтобы не изменить исходный объект, я создам глубокую копию объекта-ноутбука. 
Давайте откроем браузер и поищем `golang copier`. Перейдите по адресу 
`https://github.com/jinzhu/copier`, скопируйте путь к пакету и выполните 
команду `go get` в терминале, чтобы установить его.

```shell
go get github.com/jinzhu/copier
```

Теперь создайте новый объект-ноутбук с названием "other" и вызовите метод 
`copier.Copy`, чтобы осуществить глубокое копирование объекта "laptop" в 
"other". Если возникла ошибка, оберните её и верните. В противном случае 
сохраните объект "other" в хранилище.

```go
func (store *InMemoryLaptopStore) Save(laptop *pb.Laptop) error {
    // ...
    
    // глубокая копия
    other := &pb.Laptop{}
    err := copier.Copy(other, laptop)
    if err != nil {
        return fmt.Errorf("cannot copy laptop data: %w", err)
    }
    
    store.data[other.Id] = other
    return nil
}
```

Давайте вернемся к нашему серверу. Мы можем вызвать `server.Store.Save`, 
чтобы сохранить ноутбук, переданный в запросе, в хранилище. Если возникла 
ошибка, верните код `codes.Internal` с ошибкой клиенту. Мы можем указать 
клиенту из-за чего возникла ошибка, проверив связана ли она с тем, что такая 
запись уже существует в хранилище. Просто вызовите функцию `errors.Is()`. Если
она вернет `true`, замените ошибку на `AlreadyExists` вместо `Internal`.

```go
func (server *LaptopServer) CreateLaptop(
    ctx context.Context,
    req *pb.CreateLaptopRequest,
) (*pb.CreateLaptopResponse, error) {
    // ...
    
    // сохраняем ноутбук в хранилище
    err := server.Store.Save(laptop)
    if err != nil {
        code := codes.Internal
        if errors.Is(err, ErrAlreadyExists) {
            code = codes.AlreadyExists
        }
        return nil, status.Errorf(code, "cannot save laptop to the store: %v", err)
    }
}
```

Наконец, если ошибок не возникло, можно добавить запись в лог о том, что 
ноутбук был успешно сохранен с таким-то идентификатором. Мы создадим новый 
ответ от сервера с идентификатором ноутбука и вернем его вызывающей стороне. 
На этом можно завершить разработку сервера.

```go
func (server *LaptopServer) CreateLaptop(
    ctx context.Context,
    req *pb.CreateLaptopRequest,
) (*pb.CreateLaptopResponse, error) {
    // ...
    
    log.Printf("saved laptop with id: %s", laptop.Id)
    
    res := &pb.CreateLaptopResponse{
        Id: laptop.Id,
    }
    return res, nil
}
```

## Тестируем обработчик унарного RPC
Теперь я покажу как протестировать его. Давайте создадим файл 
`laptop_server_test.go` в папке `service`. Зададим имя пакета `service_test`.
Создайте функцию `TestServerCreateLaptop()` и пусть она будет запускаться 
параллельно.

```go
package service_test

import "testing"

func TestServerCreateLaptop(t *testing.T) {
    t.Parallel()

}
```

Я хочу протестировать несколько различных случаев, поэтому давайте использовать
табличные тесты. Сначала я объявлю все тестируемые случаи. Они будут состоять 
из имени, объекта-ноутбука, поступающего в качестве входного параметра, 
хранилища ноутбуков и ожидаемого кода состояния. Первый случай — это успешный
вызов, когда идентификатор ноутбука был сгенерирован клиентом. Таким образом,
ноутбук можно создать с помощью `sample.NewLaptop()`, хранилище — это просто
новое `InMemoryLaptopStore` и ожидаемый код - `OK`. Второй случай — также 
успешный вызов, но без идентификатора ноутбука. В этот раз сервер должен 
сгенерировать случайный идентификатор за нас. Давайте создадим объект 
`laptopNoID`, используя функцию для определения ноутбука со случайными 
параметрами, и приравняем его идентификатор к пустой строке. Третий случай — 
это неудачный вызов из-за неправильного UUID. Давайте определим объект 
`laptopInvalidID`, сгенерировав его с помощью `sample.NewLaptop()`, и присвоим
его идентификатору значение "invalid-uuid". В этом случае мы ожидаем, что 
кодом состояния будет `InvalidArgument`.

```go
func TestServerCreateLaptop(t *testing.T) {
    // ...
    laptopNoID := sample.NewLaptop()
    laptopNoID.Id = ""
    
    laptopInvalidID := sample.NewLaptop()
    laptopInvalidID.Id = "invalid-uuid"
}
```

Последний случай - это неудачный вызов из-за уже существующего в хранилище 
идентификатора. Сначала мы создадим ноутбук и сохраним его в хранилище, затем
вызовем функцию `CreateLaptop` с этим же идентификатором ноутбука. В этом 
случае мы ожидаем, что код состояния будет равен `AlreadyExists`. Итак, 
давайте создадим `laptopDuplicateID` в качестве тестируемого ноутбука. 
Сохраните ноутбук в хранилище и убедитесь, что при этом не возникло ошибок.

```go
func TestServerCreateLaptop(t *testing.T) {
    // ...
	
    laptopDuplicateID := sample.NewLaptop()
    storeDuplicateID := service.NewInMemoryLaptopStore()
    err := storeDuplicateID.Save(laptopDuplicateID)
    require.Nil(t, err)
}
```

Отлично, все тестовые случаи заданы.

```go
func TestServerCreateLaptop(t *testing.T) {
    // ...
	
    testCases := []struct{
        name string
        laptop *pb.Laptop
        store service.LaptopStore
        code codes.Code
    } {
        {
            name: "success_with_id",
            laptop: sample.NewLaptop(),
            store: service.NewInMemoryLaptopStore(),
            code: codes.OK,
        },
        {
            name: "success_no_id",
            laptop: laptopNoID,
            store: service.NewInMemoryLaptopStore(),
            code: codes.OK,
        },
        {
            name: "failure_invalid_id",
            laptop: laptopInvalidID,
            store: service.NewInMemoryLaptopStore(),
            code: codes.InvalidArgument,
        },
        {
            name: "failure_duplicate_id",
            laptop: laptopDuplicateID,
            store: storeDuplicateID,
            code: codes.AlreadyExists,
        },
    }
}
```

Теперь мы перебираем их с помощью простого цикла `for`. Сохраните текущий
тестируемый случай в локальную переменную. Это важно, чтобы избежать проблем с 
параллелизмом, поскольку мы хотим создать несколько параллельных подтестов. 
Чтобы создать подтест, мы вызываем `t.Run()` и используем `tc.name` как 
название подтеста. Вызовите `t.Parallel()`, чтобы запустить его параллельно с 
другими тестами. Затем создайте новый запрос `CreateLaptopRequest` со значением
`tc.loptop`. Мы определим новый `LaptopServer` с хранилищем в памяти. Но 
кажется я забыл добавить хранилище в функцию `NewLaptopServer`. Поэтому давайте
добавим его.

```go
// NewLaptopServer возвращает новый LaptopServer
func NewLaptopServer(store LaptopStore) *LaptopServer {
    return &LaptopServer{
        Store: store,
    }
}
```

Теперь мы передаём `tc.store` в эту функцию, чтобы создать новый 
`LaptopServer`. Затем просто вызовите функцию `server.CreateLaptop()` с 
фоновым контекстом и объектом-запросом. Случай считается успешным, если 
`tc.code` равен `OK`. В этом случае мы должны проверить нет ли ошибок. Ответ
должен быть не равен `nil`. Возвращаемый идентификатор не должен быть пустым.
И если у ноутбука, который был передан в качестве входного параметра, был 
идентификатор, то возвращаемый идентификатор должен быть равен ему. Для 
неудачного случая `tc.code` не равен `OK`. Мы должны проверить, что произошла 
ошибка и ответ должен быть `nil`. После этого мы исследуем код состояния. 
Давайте вызовем `status.FromError`, чтобы получить объект состояния. Убедитесь,
что переменная `ok` равна `true`, а `st.Code()` - `tc.code`. Функция для 
тестирования работы сервера готова.

```go
func TestServerCreateLaptop(t *testing.T) {
	// ...
    
	for i := range testCases {
        tc := testCases[i]
    
        t.Run(tc.name, func (t *testing.T) {
            t.Parallel()
            
            req := &pb.CreateLaptopRequest{
                Laptop: tc.laptop,
            }
            
            server := service.NewLaptopServer(tc.store)
            res, err := server.CreateLaptop(context.Background(), req)
            if tc.code == codes.OK {
                require.NoError(t, err)
                require.NotNil(t, res)
                require.NotEmpty(t, res.Id)
                if len(tc.laptop.Id) > 0 {
                    require.Equal(t, tc.laptop.Id, res.Id)
                }
            } else {
                require.Error(t, err)
                require.Nil(t, res)
                st, ok := status.FromError(err)
                require.True(t, ok)
                require.Equal(t, tc.code, st.Code())
            }
        })
    }
}
```

Давайте запустим тесты. Отлично! Они успешно пройдены. Также запустите тест 
всего пакета и проверьте покрытие кода.

```shell
go test -cover
```

93.5% - это очень большой процент покрытия. Тем не менее, в написанных нами 
тестах не использовались какие-либо сетевые вызовы. По сути в них просто 
непосредственно вызывались функции на стороне сервера. 

## Тестируем обработчик унарного RPC, используя настоящее соединение
Теперь я покажу вам как протестировать RPC запрос со стороны клиента.
Давайте создадим файл `laptop_client_test.go` в папке `service`. Опять же будем
использовать `service_test` для названия пакета, но имя функции в этот раз 
равно `TestClientCreateLaptop`. Я также укажу, что её следует запускать 
параллельно.

```go
package service_test

import "testing"

func TestClientCreateLaptop(t *testing.T) {
    t.Parallel()
	
}
```

Сначала нам нужно запустить gRPC сервер. Я напишу для этого отдельную функцию.
В качестве аргумента она будет принимать `testing.T` и возвращать объект 
`LaptopServer` вместе со строкой, представляющей сетевой адрес сервера. В этой 
функции мы создадим новый сервер для работы с ноутбуками, использующий
хранилище в памяти.

```go
func startTestLaptopServer(t *testing.T) (*service.LaptopServer, string) {
    laptopServer := service.NewLaptopServer(service.NewInMemoryLaptopStore())
}
```

Создайте gRPC сервер, вызвав функцию `grpc.NewServer()`. Затем зарегистрируйте
сервер-сервис для работы с ноутбуками на этом gRPC сервере. Мы создадим новый 
обработчик, который будет прослушивать TCP соединение. Число 0 здесь означает, 
что мы хотим, чтобы ему был назначен любой доступный случайный порт. Теперь 
мы просто вызываем `grpc.Server.Serve`, чтобы начать прослушивание запросов.
Это блокирующий вызов, поэтому нам нужно запустить его в отдельной горутине. 
Теперь можно вернуть сервер и строку с адресом этого обработчика.

```go
func startTestLaptopServer(t *testing.T) (*service.LaptopServer, string) {
    // ...
    
    grpcServer := grpc.NewServer()
    pb.RegisterLaptopServiceServer(grpcServer, laptopServer)
    
    listener, err := net.Listen("tcp", ":0") // доступный случайный порт
    require.NoError(t, err)
    
    go grpcServer.Serve(listener)

    return laptopServer, listener.Addr().String()
}
```

В тесте мы вызываем эту функцию, чтобы получить сервер и его адрес. Затем мы
создаём другую функцию, возвращающую новый клиент для работы с ноутбуками, 
который можно использовать для тестирования.

```go
func TestClientCreateLaptop(t *testing.T) {
    // ...
    
    laptopServer, serverAddress := startTestLaptopServer(t)
    laptopClient := newTestLaptopClient(t, serverAddress)
}
```

Эта функция принимает объект `testing.T` и адрес сервера в качестве аргументов 
и возвращает `pb.LaptopServiceClient`. Сначала мы пытаемся соединиться с 
сервером с помощью `grpc.Dial()`. Поскольку это просто тесты, то мы можем 
использовать незащищенное соединение. Убедитесь, что не возникло ошибки и 
верните новый сервис-клиент для работы с ноутбуками с созданным подключением.

```go
func newTestLaptopClient(t *testing.T, serverAddress string) pb.LaptopServiceClient {
    conn, err := grpc.Dial(serverAddress, grpc.WithInsecure())
    require.NoError(t, err)
    return pb.NewLaptopServiceClient(conn)
}
```

Теперь мы создадим новый тестовый ноутбук. Сохраните его ID в переменной для
последующего сравнения. Задайте новый объект-запрос, использующий этот 
ноутбук. В этот раз мы будем использовать объект `laptopClient` для вызова 
функции `CreateLaptop()`. Проверим, что не возникло ошибок и ответ не равен
`nil`. Возвращенный идентификатор должен совпадать с тем, который мы сохранили
ранее.

```go
func TestClientCreateLaptop(t *testing.T) {
    // ...
    
    laptop := sample.NewLaptop()
    expectedID := laptop.Id
    req := &pb.CreateLaptopRequest{
        Laptop: laptop,
    }
    
    res, err := laptopClient.CreateLaptop(context.Background(), req)
    require.NoError(t, err)
    require.NotNil(t, res)
    require.Equal(t, expectedID, res.Id)

}
```

Теперь мы хотим убедиться, что ноутбук действительно сохранился на сервере.
Для этого нам нужно добавить ещё одну функцию в хранилище ноутбуков. Это 
функция `Find()`, которая будет искать ноутбук по его идентификатору. Она 
принимает идентификатор в виде строки в качестве входных данных и возвращает
объект-ноутбук с ошибкой.

```go
type LaptopStore interface {
    // Save сохраняет ноутбук в хранилище
    Save(laptop *pb.Laptop) error
    // Find ищет ноутбук по идентификатору
    Find(id string) (*pb.Laptop, error)
}
```

В этой функции мы сначала вызываем `mutex.RLock()`, чтобы захватить блокировку
на чтение. Не забудьте вызвать разблокировку с помощью команды `defer`. Теперь
мы можем найти ноутбук в отношении `store.data` по его идентификатору. Если он
не найден, возвращаем `nil`. В противном случае мы должны создать его глубокую
копию с помощью `copier.Copy()` как мы это делали раньше. Если возникнет 
ошибка, то возвращаем её. Иначе (всё прошло нормально), возвращаем 
скопированный объект.

```go
func (store *InMemoryLaptopStore) Find(id string) (*pb.Laptop, error) {
    store.mutex.RLock()
    defer store.mutex.RUnlock()
    
    laptop := store.data[id]
    if laptop == nil {
        return nil, nil
    }
    
    // глубокая копия
    other := &pb.Laptop{}
    err := copier.Copy(other, laptop)
    if err != nil {
        return nil, fmt.Errorf("cannot copy laptop data: %w", err)
    }
    
    return other, nil
}
```

Теперь вернемся к тесту нашего клиента. Мы вызываем 
`laptopServer.Store.Find()`, чтобы найти ноутбук по идентификатору. Убедитесь,
что не возникло ошибок и ноутбук должен быть найден, значит, не может быть 
равен `nil`.

```go
func TestClientCreateLaptop(t *testing.T) {
    // ...
	
    // проверяем, что ноутбук сохранился в хранилище
    other, err := laptopServer.Store.Find(res.Id)
    require.NoError(t, err)
    require.NotNil(t, other)
}
```

Наконец, мы хотим проверить, что сохраненный ноутбук не отличается от того, 
который мы отправили.

```go
func TestClientCreateLaptop(t *testing.T) {
    // ...
    
    // проверяем, что сохраненный ноутбук не отличается от отправленного
    requireSameLaptop(t, laptop, other)
}
```

Я напишу отдельную функцию для этого. Она будет принимать на вход: объект 
`testing.T` и два объекта-ноутбука. Теперь, если мы просто воспользуемся 
функцией `require.Equal` для этих двух объектов и запустим тест, то он не будет
пройден.

```go
func requireSameLaptop(t *testing.T, laptop1 *pb.Laptop, laptop2 *pb.Laptop) {
    require.Equal(t, laptop1, laptop2)
}
```

Это связано с тем, что в структуре `Laptop` есть некоторые особые поля, которые
используются внутри gRPC для сериализации объектов. Поэтому, чтобы правильно 
сравнить два ноутбука, мы должны игнорировать эти особые поля. Одним из 
возможных решений является сериализация объектов в JSON и сравнение двух
получившихся строк, что я и сделал. 

```go
func requireSameLaptop(t *testing.T, laptop1 *pb.Laptop, laptop2 *pb.Laptop) {
    json1, err := serializer.ProtobufToJSON(laptop1)
    require.NoError(t, err)
    
    json2, err := serializer.ProtobufToJSON(laptop2)
    require.NoError(t, err)
    
    require.Equal(t, json1, json2)
}
```

Теперь если мы снова запустим тест, то он будет успешно пройден. Отлично!

## Создаём main файлы для сервера и клиента
Теперь нам нужно реализовать настоящий сервер и клиент. Сначала я удалю этот
неиспользуемый файл `main.go`. Затем создам новую папку `"cmd"`, а в ней
один каталог для сервера, а другой — для клиента. У сервера будет свой 
собственный файл `main.go`. Давайте пока что вставим сюда простую программу, 
выводящую "Hello world".

```go
package main

import "fmt"

func main() {
    fmt.Println("Hello world")
}
```

Аналогично поступим для клиента.

После этого я открою Makefile и изменю команду `"run"` на две команды: команду
`"server"` для запуска main файла сервера и команду `"client"` для запуска main
файла клиента.

```makefile
server:
    go run cmd/server/main.go
client:
    go run cmd/client/main.go
```

Давайте немного изменим текст приветственного сообщения, чтобы оно отличалось
у сервера и клиента. Например, "hello world from server" и "hello world from 
client".

`cmd/server/main.go`
```go
package main

import "fmt"

func main() {
    fmt.Println("Hello world from server")
}
```

`cmd/server/client.go`
```go
package main

import "fmt"

func main() {
    fmt.Println("Hello world from client")
}
```

Давайте запустим их! Выполните команду `make server`, а затем `make client` в
терминале. Программы выполнились без ошибок и вывели нужные строки. Отлично!

Теперь давайте реализуем настоящий сервер. Нам нужен порт для сервера, поэтому
я буду использовать `flag.Int`, чтобы получить его из аргументов командной 
строки. Считанное значение порта выведем в лог. 

```go
package main

import (
    "flag"
    "log"
)

func main() {
    port := flag.Int("port", 0, "the server port")
    flag.Parse()
    log.Printf("start server on port %d", *port)
}
```

По аналогии с тем, что мы писали в модульных тестах, создадим новый сервер для 
работы с ноутбуками, использующий хранилище в памяти. Затем определим новый
gRPC сервер. Зарегистрируем сервер для работы с ноутбуками в gRPC сервере. 
После этого определим адресную строку с портом, который мы получили ранее. Мы
будем прослушивать TCP соединения на этом адресе сервера. Вызовите 
`grpcServer.Serve()`, чтобы запустить сервер. Если возникнет какая-либо ошибка,
запишите её в лог и завершите работу программы. На этом всё, код для сервера 
написан.

```go
func main() {
    // ...
    
    laptopServer := service.NewLaptopServer(service.NewInMemoryLaptopStore())
    grpcServer := grpc.NewServer()
    pb.RegisterLaptopServiceServer(grpcServer, laptopServer)
    
    address := fmt.Sprintf("0.0.0.0:%d", *port)
    listener, err := net.Listen("tcp", address)
    if err != nil {
        log.Fatal("cannot start server: ", err)
    }
    
    err = grpcServer.Serve(listener)
    if err != nil {
        log.Fatal("cannot start server: ", err)
    }
}
```

Теперь нам нужно обновить make-файл, чтобы передать аргумент `port` в 
программу для сервера. Я буду использовать порт 8080.

```makefile
server:
    go run cmd/server/main.go -port 8080
```

Давайте протестируем это в терминале:

```shell
make server
```

Сервер будет запущен на порту 8080.

Теперь займемся клиентом. Сначала мы получим адрес сервера из аргументов 
командной строки и выведем простой лог, сообщающий о том, что мы пытаемся 
соединиться с этим адресом сервера. Мы вызываем функцию `grpc.Dial()` с 
введенным адресом и пока просто создаём незащищенное соединение. Если произошла
ошибка, записываем её в лог и выходим из программы. В противном случае создадим
новый объект-клиент для работы с ноутбуками, использующий данное соединение.
Затем сгенерируем новый ноутбук, новый объект-запрос и просто вызовем функцию
`laptopClient.Createlaptop()`, где в качестве входных параметров передается
этот запрос и фоновый контекст. По аналогии с модульным тестом если возникает
ошибка мы преобразовываем её в объект состояния, чтобы мы могли проверить 
возвращаемый код состояния. Если код равен `Already Exists`, то просто 
записываем ошибку в лог. В противном случае сохраняем ошибку в лог и завершаем
программу. При наличии любых ошибок программа завершается здесь. Если всё 
нормально, мы просто пишем в лог сообщение о том, что был создан ноутбук с 
таким-то идентификатором. Давайте запустим клиент в терминале.

```go
func main() {
    serverAddress := flag.String("address", "", "the server address")
    flag.Parse()
    log.Printf("dial server %s", *serverAddress)
    
    conn, err := grpc.Dial(*serverAddress, grpc.WithInsecure())
    if err != nil {
        log.Fatal("cannot dial server: ", err)
    }
    
    laptopClient := pb.NewLaptopServiceClient(conn)
    
    laptop := sample.NewLaptop()
    req := &pb.CreateLaptopRequest{
        Laptop: laptop,
    }
    
    res, err := laptopClient.CreateLaptop(context.Background(), req)
    if err != nil {
        st, ok := status.FromError(err)
        if ok && st.Code() == codes.AlreadyExists {
            // просто пишем ошибку в лог
            log.Print("laptop already exists")
        } else {
            log.Fatal("cannot create laptop: ", err)
        }
        return
    }
    
    log.Printf("created laptop with id: %s", res.Id)
}
```

Сервер уже запущен. Откройте новую вкладку и выполните команду `make client`.
Возникнет ошибка из-за того, что не удалось соединиться с сервером, поскольку 
не был указан его адрес. Я забыл обновить `Makefile`. Давайте откроем make-файл
и добавим аргумент с адресом к команде `client`. 

```makefile
client:
	go run cmd/client/main.go -address 0.0.0.0:8080
```

Теперь вернитесь в терминал и снова выполните `make client`. В этот раз ноутбук
успешно создан! В терминале, где запущен сервер, мы увидим два лога:

```shell
2021/03/31 19:34:47 receive a create-laptop request with id: a6a4e0ba-d26b-4a9e-a71e-dab31f6e2d70
2021/03/31 19:34:47 saved laptop with id: a6a4e0ba-d26b-4a9e-a71e-dab31f6e2d70
```

В первом указано, что был получен запрос с идентификатором (a6a4e0ba-d26b-4a9e-a71e-dab31f6e2d70),
а второй сообщает, что был сохранен ноутбук с таким же идентификатором. Что 
будет, если клиент не отправит идентификатор? В main файле клиента я присвою
`laptop.Id` пустой строке.

`cmd/client/main.go`
```go
func main() {
    // ...	
    
    laptop := sample.NewLaptop()
    laptop.Id = ""
    req := &pb.CreateLaptopRequest{
        Laptop: laptop,
    }
    
    // ...
}
```

И перезапущу клиент. Ноутбук всё равно создался с некоторым идентификатором.
В терминале на стороне сервера, мы также увидим два лога.

```shell
2021/03/31 19:39:41 receive a create-laptop request with id: 
2021/03/31 19:39:41 saved laptop with id: e332aea5-5ad0-4096-b859-025fb8012d41
```

Но в этот раз, идентификатор в первом логе пустой. Это означает, что сервер
сгенерировал новый идентификатор для ноутбука. Давайте попытаемся отправить 
уже существующий идентификатор и посмотрим что произойдёт. Я скопирую 
идентификатор `a6a4e0ba-d26b-4a9e-a71e-dab31f6e2d70` из первого лога сервера и
вставлю его в код клиента.

`cmd/client/main.go`
```go
func main() {
    // ...	
    
    laptop := sample.NewLaptop()
    laptop.Id = "a6a4e0ba-d26b-4a9e-a71e-dab31f6e2d70"
    req := &pb.CreateLaptopRequest{
        Laptop: laptop,
    }
    
    // ...
}
```

Перезапустите клиент. В этот раз выводится сообщение о том, что ноутбук с таким
идентификатором уже существует.

```shell
2021/03/31 19:44:57 laptop already exists
```

На стороне сервера вывелся только один лог о получении запроса.

```shell
2021/03/31 19:44:57 receive a create-laptop request with id: a6a4e0ba-d26b-4a9e-a71e-dab31f6e2d70
```

Давайте попробуем вызвать клиент ещё раз с неправильным UUID. Я изменю 
`laptop.Id` в клиенте на `"invalid"` и снова запущу клиент. 

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

В этот раз мы получим сообщение о фатальной ошибке с кодом состояния 
`InvalidArgument`. Отлично!  

```shell
2021/03/31 19:49:00 cannot create laptop: rpc error: code = InvalidArgument desc = laptop ID is not a valid UUID: invalid UUID length: 7
exit status 1
```

Теперь я покажу вам как установить таймаут для запроса. В Go мы будем 
использовать для этого контекст. На стороне клиента вместо 
`context.Background()` я вызову `context.WithTimeout()` и передам в него 
фоновый контекст вместе с промежутком времени после которого произойдёт 
таймаут, скажем, 5 секунд. Функция возвращает контекст и объект `cancel`. 
Контекст используется в функции `CreateLaptop`, а вызов `cancel()` мы 
откладываем до момента выхода из функции `main`.

`cmd/client/main.go`
```go
func main() {
    // ...
	
    // устанавливаем таймаут
    ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
    defer cancel()
    
    res, err := laptopClient.CreateLaptop(ctx, req)
    // ...
}
```
`service/laptop_server.go`
```go
func (server *LaptopServer) CreateLaptop(
    ctx context.Context,
    req *pb.CreateLaptopRequest,
) (*pb.CreateLaptopResponse, error) {
    // ...

    // имитируем сложные вычисления
    time.Sleep(6 * time.Second)

    // сохраняем ноутбук в хранилище
}
```

На стороне сервера пусть выполняются какие-то сложные вычисления, которые 
занимают 6 секунд. Теперь давайте перезапустим сервер и клиент. После 5 секунд
на стороне клиента мы получаем ошибку с кодом `DeadlineExceeded`.

```shell
2021/03/31 20:04:41 cannot create laptop: rpc error: code = DeadlineExceeded desc = context deadline exceeded
exit status 1
```

Но на стороне сервера ноутбук всё равно создаётся и сохраняется.

```shell
2021/03/31 20:04:42 saved laptop with id: b6acadec-3f62-4712-b1ba-3bd450f5f2b3
```

Скорее всего это не то поведение, на которое мы рассчитывали. Если запрос 
отменяется прежде чем ноутбук сохранился в хранилище, то мы хотим, чтобы сервер
не сохранял его. Для этого проверим не возникла ли ошибка контекста на сервере.
Если произошла ошибка `DeadlineExceeded`, мы пишем её в лог и возвращаем код 
состояния ошибки `DeadlineExceeded` клиенту.

`service/laptop_server.go`
```go
func (server *LaptopServer) CreateLaptop(
    ctx context.Context,
    req *pb.CreateLaptopRequest,
) (*pb.CreateLaptopResponse, error) {
    // ...

    // имитируем сложные вычисления
    time.Sleep(6 * time.Second)

    if ctx.Err() == context.DeadlineExceeded {
        log.Print("deadline is exceeded")
        return nil, status.Error(codes.DeadlineExceeded, "deadline is exceeded")
    }
    // сохраняем ноутбук в хранилище
}
```

Давайте перезапустим сервер и клиент. Клиент по-прежнему получает ошибку
`DeadlineExceeded`.

```shell
2021/03/31 20:17:25 cannot create laptop: rpc error: code = DeadlineExceeded desc = context deadline exceeded
exit status 1
```

Но в этот раз сервер также выводит лог о том превышено максимальное время 
выполнения.

```shell
2021/03/31 20:17:26 deadline is exceeded
```

и он больше не сохраняет ноутбук в хранилище. Теперь посмотрим что произойдёт 
если мы отменим запрос, экстренно завершив программу-клиент. Давайте запустим
клиент и через одну секунду, нажмём `Ctrl+C`, чтобы остановить его выполнение. 
На стороне сервера мы видим, что ноутбук сохранился. Скорее всего, это тоже 
нежелательное поведение сервера, поскольку клиент отменил запрос. Чтобы 
исправить логику работы, откроем код сервера и добавим ещё одну проверку перед
сохранением ноутбука. Если произошла контекстная ошибка `context.Canceled`, то
мы просто пишем её в лог и возвращаем клиенту ошибку с кодом состояния 
`Canceled`.

`service/laptop_server.go`
```go
func (server *LaptopServer) CreateLaptop(
    ctx context.Context,
    req *pb.CreateLaptopRequest,
) (*pb.CreateLaptopResponse, error) {
    // ...
    
    // имитируем сложные вычисления
    time.Sleep(6 * time.Second)

    if ctx.Err() == context.Canceled {
        log.Print("request is canceled")
        return nil, status.Error(codes.Canceled, "deadline is canceled")
    }
    
    if ctx.Err() == context.DeadlineExceeded {
        log.Print("deadline is exceeded")
        return nil, status.Error(codes.DeadlineExceeded, "deadline is exceeded")
    }
    // сохраняем ноутбук в хранилище
}
```

Теперь, если мы перезапустим сервер и клиент, прервём клиент с помощью 
`Ctrl+C`, то в этот раз на серверной стороне мы увидим лог, сообщающий о том, 
что контекст был отменен.

```shell
2021/03/31 20:26:30 request is canceled
```

И ноутбук не сохранился в хранилище. Именно так, как мы и хотели! На этом всё. 
Мы многое узнали о том как реализовать и протестировать унарный gRPC запрос с
помощью Go. На следующей лекции мы узнаем как сделать то же самое на Java.
А пока желаю вам успехов в написании программ и до новых встреч!