# Реализация серверного потокового gRPC API - Golang
Всем привет, сегодня мы узнаем как реализовать серверный потоковый RPC в Go. 
Сначала мы определим новый RPC в proto файле для поиска ноутбуков с некоторыми
конкретными параметрами. Затем мы реализуем сервер, клиент и напишем unit
тест для этого RPC.

## Добавляем описание серверного потокового RPC в Protobuf
Итак, давайте начнём! Я открою golang проект `pcbook`, над которым мы работали.
Наш новый RPC позволит нам искать ноутбуки, удовлетворяющие некоторым 
требованиям к конфигурации. Итак, я создам файл `filter_message.proto`. В этом
сообщении определим ноутбук с какими характеристиками мы хотим найти. 
Например, максимальную цену, которую мы готовы заплатить за ноутбук, 
минимальное количество ядер в процессоре ноутбука, минимальная частота 
процессора и минимальный размер оперативной памяти.

```protobuf
syntax = "proto3";

package techschool_pcbook;

option go_package = ".;pb";
option java_package = "com.github.techschool.pcbook.pb";
option java_multiple_files = true;

import "memory_message.proto";

message Filter {
  double max_price_usd = 1;
  uint32 min_cpu_cores = 2;
  double min_cpu_ghz = 3;
  Memory min_ram = 4;
}
```

После этого мы определим новый серверный потоковый RPC в файле
`laptop_service.proto`. Сначала мы зададим сообщение `SearchLaptopRequest` с 
единственным полем типа `Filter`, а затем `SearchLaptopResponse`, состоящее 
из одного поля `Laptop`.

`proto/laptop_service.proto`
```protobuf
// ...
import "filter_message.proto";

//...

message SearchLaptopRequest { Filter filter = 1; }

message SearchLaptopResponse { Laptop laptop = 1; }
```

Сам серверный потоковый RPC определяется аналогично унарному RPC. Начните с 
ключевого слова `rpc`, затем названия RPC - `SearchLaptop`, на вход поступает
`SearchLaptopRequest`, а результатом работы будет поток из 
`SearchLaptopResponse`. Вот и всё. Достаточно просто.

```protobuf
// ...

service LaptopService {
  rpc CreateLaptop(CreateLaptopRequest) returns (CreateLaptopResponse) {};
  rpc SearchLaptop(SearchLaptopRequest) returns (stream SearchLaptopResponse) {};
}
```

Теперь давайте сгенерируем код. В файл `laptop_service.pb.go` было добавлено 
несколько новых фрагментов кода. Появилась структура `SearchLaptopRequest` и
`SearchLaptopResponse`

```go
type SearchLaptopRequest struct {
    state         protoimpl.MessageState
    sizeCache     protoimpl.SizeCache
    unknownFields protoimpl.UnknownFields
    
    Filter *Filter `protobuf:"bytes,1,opt,name=filter,proto3" json:"filter,omitempty"`
}

// ...

type SearchLaptopResponse struct {
    state         protoimpl.MessageState
    sizeCache     protoimpl.SizeCache
    unknownFields protoimpl.UnknownFields
    
    Laptop *Laptop `protobuf:"bytes,1,opt,name=laptop,proto3" json:"laptop,omitempty"`
}
```

а также новым метод `SearchLaptop` в интерфейсе `LaptopServiceClient`.

```go
type LaptopServiceClient interface {
    CreateLaptop(ctx context.Context, in *CreateLaptopRequest, opts ...grpc.CallOption) (*CreateLaptopResponse, error)
    SearchLaptop(ctx context.Context, in *SearchLaptopRequest, opts ...grpc.CallOption) (LaptopService_SearchLaptopClient, error)
}
```

Кроме того добавился новый метод `SearchLaptop` в интерфейсе 
`LaptopServiceServer`.

```go
type LaptopServiceServer interface {
    CreateLaptop(context.Context, *CreateLaptopRequest) (*CreateLaptopResponse, error)
    SearchLaptop(*SearchLaptopRequest, LaptopService_SearchLaptopServer) error
    mustEmbedUnimplementedLaptopServiceServer()
}
```

## Добавляем функцию поиска в хранилище в памяти
Сначала реализуем серверную часть. Давайте добавим метод `Search()` в интерфейс
`LaptopStore`. Он принимает на вход фильтр, а также функцию обратного вызова,
сообщающую о результатах поиска ноутбука, и возвращает ошибку.

```go
// ...

type LaptopStore interface {
    // ...
    // Метод Search ищет ноутбуки, используя заданный фильтр, и возвращает их поочереди с помощью функции found
    Search(filter *pb.Filter, found func(laptop *pb.Laptop) error) error
}

// ...
```

Теперь давайте реализуем этот метод для `InMemoryLaptopStore`. Поскольку мы 
считываем данные нужно установить блокировку по чтению. Не забудьте снять её 
после завершения работы. 

```go
// ...

// Метод Search ищет ноутбуки, используя заданный фильтр, и возвращает их поочереди с помощью функции found
func (store *InMemoryLaptopStore) Search(
    filter *pb.Filter,
    found func(laptop *pb.Laptop) error,
) error {
    store.mutex.RLock()
    defer store.mutex.RUnlock()
}
```

Мы в цикле перебираем все ноутбуки в хранилище и проверяем какой из них 
подходит под условия в фильтре. Функция `isQualified()` принимает на вход 
фильтр и ноутбук и возвращает `true`, если характеристики ноутбука 
удовлетворяют фильтру. Если цена ноутбука больше, чем максимальная цена в 
фильтре, то возвращается `false`. Если количество ядер процессора ноутбука 
меньше минимального количества ядер в фильтре, то возвращается `false`. Если
минимальная частота процессора ноутбука меньше, чем та, что задана в фильтре,
возвращается `false`. Теперь нам нужно сравнить ОЗУ. Поскольку объём памяти 
можно задать с помощью различных единиц измерения, для правильного сравнения 
нам нужно написать функцию, которая преобразует память к наименьшей единице 
измерения: биту (`BIT`). Если размер ОЗУ меньше, чем тот, что указан в фильтре,
то возвращаем `false`, в противном случае - `true`.

```go
// ...
func isQualified(filter *pb.Filter, laptop *pb.Laptop) bool {
    if laptop.GetPriceUsd() > filter.GetMaxPriceUsd() {
        return false
    }
    
    if laptop.GetCpu().GetNumberCores() < filter.GetMinCpuCores() {
        return false
    }
    
    if laptop.GetCpu().GetMinGhz() < filter.GetMinCpuGhz() {
        return false
    }
    
    if toBit(laptop.GetRam()) < toBit(filter.GetMinRam()) {
        return false
    }
    
    return true
}
```

Теперь давайте реализуем функцию `toBit()`. Сначала мы получим числовое 
значение памяти. Затем воспользуемся оператором `switch-case`, преобразуя
значение в зависимости от единицы измерения. Если она равна `BIT`, то мы просто 
возвращаем значение. Если `BYTE`, то мы должны умножить значение на 8, поскольку
`1 байт = 8 бит`. И поскольку 8 = 2 в 3 степени, мы можем использовать здесь
побитовый оператор сдвига влево вместо умножения. Если `KILOBYTE`, то мы должны
умножить значение на 1024 и 8, поскольку `1 кБ = 1024 байт`. И поскольку 1024 *
8 = 2^13, мы можем просто сдвинуть значение влево на 13. По аналогии, если 
объём памяти указан в мегабайтах, то мы возвращаем значение сдвинутое на 23, 
для гигабайтов — на 33 и, наконец, для терабайтов — на 43. Для случая по 
умолчанию просто вернём 0. 

```go
// ...

func toBit(memory *pb.Memory) uint64 {
    value := memory.GetValue()

    switch memory.GetUnit() {
        case pb.Memory_BIT:
            return value
        case pb.Memory_BYTE:
            return value << 3 // 8 = 2^3
        case pb.Memory_KILOBYTE:
            return value << 13 // 1024 * 8 = 2^10 * 2^3 = 2^13
        case pb.Memory_MEGABYTE:
            return value << 23
        case pb.Memory_GIGABYTE:
            return value << 33
        case pb.Memory_TERABYTE:
            return value << 43
        default:
            return 0
    }
}
```

Теперь вернемся к нашей функции `SearchLaptop()`. Когда ноутбук найден нам 
нужно создать его глубокую копию прежде чем вызвать функцию обратного вызова.
Поскольку глубокое копирование используется во многих местах я напишу
отдельный метод для него. Просто скопируйте и вставьте блок кода из метода
`Find` в этот метод `deepCopy`.

```go
func deepCopy(laptop *pb.Laptop) (*pb.Laptop, error) {
    // deep copy
    other := &pb.Laptop{}
    err := copier.Copy(other, laptop)
    if err != nil {
        return nil, fmt.Errorf("cannot copy laptop data: %w", err)
    }
    
    return other, nil
}
```

Затем в этом методе `Find()`, мы просто вернём `deepCopy(laptop)`. Метод 
`Save()` также может быть упрощен как показано ниже. 

```go
func (store *InMemoryLaptopStore) Find(id string) (*pb.Laptop, error) {
    store.mutex.RLock()
    defer store.mutex.RUnlock()
    
    laptop := store.data[id]
    if laptop == nil {
        return nil, nil
    }
    
    return deepCopy(laptop)
}

// Метод Save сохраняет ноутбук в хранилище
func (store *InMemoryLaptopStore) Save(laptop *pb.Laptop) error {
    store.mutex.Lock()
    defer store.mutex.Unlock()
    
    if store.data[laptop.Id] != nil {
        return ErrAlreadyExists
    }
    
    // глубокое копирование
    other, err := deepCopy(laptop)
    if err != nil {
        return err
    }
    
    store.data[other.Id] = other
    return nil
}
```

В методе `Search()` мы глубоко копируем найденный ноутбук и передаём его в 
функцию `found()`, чтобы отправить его вызвавшему. В случае возникновения 
ошибки возвращаем её. В противном случае возвращаем `nil` в конце метода. 
Отлично, метод `Search` реализован для хранилища в памяти.

```go
// Метод Search ищет ноутбуки, используя заданный фильтр, и возвращает их поочереди с помощью функции found
func (store *InMemoryLaptopStore) Search(
    filter *pb.Filter,
    found func(laptop *pb.Laptop) error,
) error {
    // ...

    for _, laptop := range store.data {
        if isQualified(filter, laptop) {
            other, err := deepCopy(laptop)
            if err != nil {
                return err
            }
    
            err = found(other)
            if err != nil {
                return err
            }
        }
    }
    
    return nil
}
```

## Реализуем сервер
Теперь давайте реализуем сервер. Нам нужно будет реализовать метод
`SearchLaptop` интерфейса `LaptopServiceServer`. Я скопирую сигнатуру этого 
метода и вставлю её в файл `laptop_server.go`. Функция имеет два аргумента: 
входной запрос и выходной потоковый ответ. Прежде всего нам нужно получить 
значение фильтра из запроса. Затем мы запишем сообщение в лог о том, что 
поступил запрос search-laptop со следующими значениями фильтра, и вызовем 
`server.Store.Search`, передав внутрь фильтр и функцию обратного вызова. Если
возникла ошибка, мы возвращаем её с кодом `Internal status code`, в противном
случае возвращаем `nil`. Затем в функции обратного вызова когда мы находим
ноутбук мы создаём новый объект — ответ от сервера с этим ноутбуком и 
отправляем его клиенту, вызывая `stream.Send()`. Если возникает ошибка, просто
возвращаем её. В противном случае мы просто пишем в лог, сообщая о том, что мы
отправили ноутбук с определенным идентификатором и затем возвращаем `nil`. На
этом реализация работы сервера завершена.

```go
// SearchLaptop - это серверный потоковый RPC для поиска ноутбуков
func (server *LaptopServer) SearchLaptop(
    req *pb.SearchLaptopRequest,
    stream pb.LaptopService_SearchLaptopServer,
) error {
    filter := req.GetFilter()
    log.Printf("receive a search-laptop request with filter: %v", filter)
    
    err := server.Store.Search(
        filter,
        func (laptop *pb.Laptop) error {
            res := &pb.SearchLaptopResponse{Laptop: laptop}
    
            err := stream.Send(res)
            if err != nil {
                return err
            }
    
            log.Printf("sent laptop with id: %s", laptop.GetId())
            return nil
        },
    )
    
    if err != nil {
        return status.Errorf(codes.Internal, "unexpected error: %v", err)
    }
    
    return nil
}
```

## Реализуем клиент
Теперь давайте реализуем клиент. Сначала я реализую отдельную функцию для 
создания случайного ноутбука. Скопируем фрагмент кода из функции `main` файла
`cmd/client/main.go` и вставляем его в функцию `createLaptop()`. Теперь в 
функции `main` мы будем использовать цикл `for` для создания 10 случайных 
ноутбуков.

`cmd/client/main.go`
```go
func createLaptop(laptopClient pb.LaptopServiceClient)  {
    laptop := sample.NewLaptop()
    laptop.Id = ""
    req := &pb.CreateLaptopRequest{
        Laptop: laptop,
    }
    
    // устанавливаем таймаут
    ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
    defer cancel()
    
    res, err := laptopClient.CreateLaptop(ctx, req)
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
func main() {
    // ...
    
    laptopClient := pb.NewLaptopServiceClient(conn)
    for i := 0; i < 10; i++ {
        createLaptop(laptopClient)
    }
}
```

Затем мы создадим новый поисковый фильтр. Я хочу найти ноутбуки с максимальной 
ценой в 3000 и минимум 4 ядрами, минимальной частотой процессора в 2,5 ГГц и 
минимум 8 гигабайтами оперативной памяти. Теперь мы вызываем `searchLaptop`, 
передавая клиент и фильтр.

```go
func main() {
    // ...
    filter := &pb.Filter{
        MaxPriceUsd: 3000,
        MinCpuCores: 4,
        MinCpuGhz:   2.5,
        MinRam:      &pb.Memory{Value: 8, Unit: pb.Memory_GIGABYTE},
    }

    searchLaptop(laptopClient, filter)
}
```

Давайте напишем эту функцию. Сначала мы пишем сообщение в лог, чтобы показать 
чему равны значения фильтра. Затем создадим контекст с таймаутом в 5 секунд. 
Инициализируем объект `laptopClient.SearchLaptop()`, передав в него фильтр. 
После этого вызываем `laptopClient.SearchLaptop()`, чтобы получить поток. Если
произошла ошибка, пишем её в лог и аварийно завершаем работу. В противном 
случае используем цикл `for` для получения серии ответов из потока. Если поток
возвращает ошибку конца файла (EOF), то это означает его конец. Поэтому мы 
просто выходим из функции. В противном случае, если ошибка не равна `nil`, мы 
пишем её в лог и аварийно завершаем работу. Если ошибок не возникло, мы сможем
получить ноутбук из потока. Я выведу только несколько характеристик ноутбука,
чтобы было проще воспринять полученную информацию: идентификатор ноутбука, 
фирму-производитель, название, количество ядер ЦПУ, минимальную частоту ЦПУ, 
ОЗУ и, наконец, цену.  

`cmd/client/main.go`
```go
func searchLaptop(laptopClient pb.LaptopServiceClient, filter *pb.Filter) {
	log.Print("search filter: ", filter)

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	req := &pb.SearchLaptopRequest{Filter: filter}
	stream, err := laptopClient.SearchLaptop(ctx, req)
	if err != nil {
		log.Fatal("cannot search laptop: ", err)
	}

	for {
		res, err := stream.Recv()
		if err == io.EOF {
			return
		}
		if err != nil {
			log.Fatal("cannot receive response: ", err)
		}

		laptop := res.GetLaptop()
		log.Print("- found: ", laptop.GetId())
		log.Print(" + brand: ", laptop.GetBrand())
		log.Print(" + name: ", laptop.GetName())
		log.Print(" + cpu cores: ", laptop.GetCpu().GetNumberCores())
		log.Print(" + cpu min ghz: ", laptop.GetCpu().GetMinGhz())
		log.Print(" + ram: ", laptop.GetRam().GetValue(), laptop.GetRam().GetUnit())
		log.Print(" + price: ", laptop.GetPriceUsd(), "usd")
	}
}
```

Итак, давайте запустим клиент и сервер.

```shell
2021/04/02 19:30:12 cannot create laptop: rpc error: code = DeadlineExceeded desc = context deadline exceeded
exit status 1
```

При создании ноутбуков произошла ошибка, связанная с превышением максимального 
времени выполнения. Она возникла из-за того, что на предыдущей лекции мы 
добавили задержку в 6 секунд на стороне сервера. Давайте её закомментируем

`service/laptop_server.go`
```go
func (server *LaptopServer) CreateLaptop(
    ctx context.Context,
    req *pb.CreateLaptopRequest,
) (*pb.CreateLaptopResponse, error) {
    // ...
	
    // имитируем сложные вычисления
    // time.Sleep(6 * time.Second) 
    
    //...
}
```

и перезапустим сервер с клиентом. В этот раз мы успешно создали 10 ноутбуков и
нашли 3 ноутбука соответствующие фильтру.

```shell
2021/04/02 19:35:35 created laptop with id: 0b3b3be6-1341-4a8b-820e-b48cc63e7a4f
2021/04/02 19:35:35 created laptop with id: 2d4d9bae-16ca-4e96-9588-0a8ebcfb12d8
2021/04/02 19:35:35 created laptop with id: 724e7fe8-5a70-4b70-97c1-8435fbfa01f9
2021/04/02 19:35:35 created laptop with id: c6d58719-6153-4db2-af54-664d0083bcfb
2021/04/02 19:35:35 created laptop with id: 11ea4983-ab3a-492f-829a-4237839226b0
2021/04/02 19:35:35 created laptop with id: fc2a5fef-298e-49b5-a974-fc09695c15ff
2021/04/02 19:35:35 created laptop with id: 74f7643b-5c80-4dee-aa65-7e3a5c4ebd37
2021/04/02 19:35:35 created laptop with id: 2b052e26-2758-4382-bcfd-aa783f5d6831
2021/04/02 19:35:35 created laptop with id: f86d0fbc-50f2-4f23-93af-34d5353226eb
2021/04/02 19:35:35 created laptop with id: a4498ed8-eccc-4907-b8e3-68ab0d7ebc70
2021/04/02 19:35:35 search filter: max_price_usd:3000 min_cpu_cores:4 min_cpu_ghz:2.5 min_ram:{value:8 unit:GIGABYTE}
2021/04/02 19:35:35 - found: 724e7fe8-5a70-4b70-97c1-8435fbfa01f9
2021/04/02 19:35:35  + brand: Dell
2021/04/02 19:35:35  + name: Alienware
2021/04/02 19:35:35  + cpu cores: 6
2021/04/02 19:35:35  + cpu min ghz: 3.1019312916606565
2021/04/02 19:35:35  + ram: 22 GIGABYTE
2021/04/02 19:35:35  + price: 2242.9127272631004usd
2021/04/02 19:35:35 - found: 74f7643b-5c80-4dee-aa65-7e3a5c4ebd37
2021/04/02 19:35:35  + brand: Apple
2021/04/02 19:35:35  + name: Macbook Air
2021/04/02 19:35:35  + cpu cores: 5
2021/04/02 19:35:35  + cpu min ghz: 3.4362480622854794
2021/04/02 19:35:35  + ram: 10 GIGABYTE
2021/04/02 19:35:35  + price: 2761.2363802318578usd
2021/04/02 19:35:35 - found: 0b3b3be6-1341-4a8b-820e-b48cc63e7a4f
2021/04/02 19:35:35  + brand: Apple
2021/04/02 19:35:35  + name: Macbook Air
2021/04/02 19:35:35  + cpu cores: 8
2021/04/02 19:35:35  + cpu min ghz: 2.6970044221301923
2021/04/02 19:35:35  + ram: 13 GIGABYTE
2021/04/02 19:35:35  + price: 2086.775426243169usd
```

Давайте запустим клиент повторно, чтобы создать ещё 10 ноутбуков. В этот раз мы
нашли 7 подходящих под параметры фильтра ноутбуков. Посмотрим логи сервера. 
Из них видно, что он получает запрос search-laptop и отправляет клиенту 3 
ноутбука. Превосходно!

```shell
2021/04/02 19:35:35 receive a search-laptop request with filter: max_price_usd:3000  min_cpu_cores:4  min_cpu_ghz:2.5  min_ram:{value:8  unit:GIGABYTE}
2021/04/02 19:35:35 sent laptop with id: 724e7fe8-5a70-4b70-97c1-8435fbfa01f9
2021/04/02 19:35:35 sent laptop with id: 74f7643b-5c80-4dee-aa65-7e3a5c4ebd37
2021/04/02 19:35:35 sent laptop with id: 0b3b3be6-1341-4a8b-820e-b48cc63e7a4f
```

Теперь давайте смоделируем случай превышения максимального времени выполнения.
Пусть функция поиска в хранилище `Search` выполняется очень медленно, так
что каждая итерация занимает 1 секунду. Будем писать здесь сообщение в лог, 
чтобы мы могли отслеживать прогресс.

```go
func (store *InMemoryLaptopStore) Search(
    filter *pb.Filter,
    found func(laptop *pb.Laptop) error,
) error {
    // ...
    for _, laptop := range store.data {
        // сложные вычисления
		time.Sleep(time.Second)
		log.Print("checking laptop id: ", laptop.GetId())
        if isQualified(filter, laptop) {
            other, err := deepCopy(laptop)
            if err != nil {
                return err
            }
    
            err = found(other)
            if err != nil {
                return err
            }
        }
    }
    
    return nil
}
```

Итак, перезапустим сервер и клиент. Через несколько секунд, мы получим ошибку 
превышения максимального времени выполнения. Давайте запустим клиент ещё раз, 
чтобы у сервера было больше ноутбуков среди которых нужно осуществить поиск.
Ошибка, связанная с превышением максимального времени выполнения всё равно 
возникает, но сервер, как видно из логов, всё равно продолжает осуществлять 
поиск. Продолжать смысла нет, поскольку клиент уже отменил запрос, поэтому 
давайте скорректируем работу сервера. В цикле `for` перед проверкой 
соответствует ли ноутбук параметрам фильтра или нет, определим состояние 
контекста. Для этого мы должны добавить контекст запроса в качестве параметра
функции `Search`.

```go
type LaptopStore interface {
    // ...
    // Метод Search ищет ноутбуки, используя заданный фильтр, и возвращает их поочереди с помощью функции found
    Search(ctx context.Context, filter *pb.Filter, found func(laptop *pb.Laptop) error) error
}

// ...

func (store *InMemoryLaptopStore) Search(
    ctx context.Context,
    filter *pb.Filter,
    found func(laptop *pb.Laptop) error,
) error {
    // ...
}
```

Итак, теперь мы проверим равна ли ошибка контекста `Cancelled` или 
`DeadlineExceeded`. Если да, то пишем сообщение в лог и возвращаем ошибку о
том, что контекст был отменен.

```go
func (store *InMemoryLaptopStore) Search(
    filter *pb.Filter,
    found func(laptop *pb.Laptop) error,
) error {
    // ...
    for _, laptop := range store.data {
    // сложные вычисления
    time.Sleep(time.Second)
    log.Print("checking laptop id: ", laptop.GetId())
    
    if ctx.Err() == context.Canceled || ctx.Err() == context.DeadlineExceeded {
        log.Print("context is cancelled")
        return errors.New("context is cancelled")
    }

    // ...
}
```

На стороне сервера, нам нужно получить контекст из потока и передать его в
функцию `Search`. Вот и всё.

```go
func (server *LaptopServer) SearchLaptop(
    req *pb.SearchLaptopRequest,
    stream pb.LaptopService_SearchLaptopServer,
) error {
    // ...
    
    err := server.Store.Search(
        stream.Context(),
        filter,
        func (laptop *pb.Laptop) error {
            // ...
        }
    )
    
    // ...
}
```

Теперь давайте перезапустим сервер и клиент. В этот раз на стороне сервера в 
логах мы видим сообщение "context is cancelled".

```shell
2021/04/02 21:06:54 context is cancelled
```

и после него он перестаёт осуществлять поиск. Таким образом, всё работает как 
мы и планировали.

## Пишем unit тест
Теперь я покажу вам как написать unit тесты для серверной потоковой RPC.
Это можно сделать двумя способами. Первый способ — имитировать этот потоковый 
интерфейс, реализовать функцию `Send` для перехвата ответов, но нам также нужно 
будет добавить какие-то заглушки для оставшихся функций в интерфейсе
`grpc.ServerStreaming`. Их 6, что достаточно много. Поэтому я буду использовать
второй способ, который заключается в использовании клиента для вызова RPC на 
тестовом сервере. Я скопирую этот блок

```go
    t.Parallel()
    
    laptopServer, serverAddress := startTestLaptopServer(t)
    laptopClient := newTestLaptopClient(t, serverAddress)
```

и вставлю его в `TestClientSearchLaptop`, создав новый unit тест.

```go
func TestClientSearchLaptop(t *testing.T) {
	t.Parallel()

	laptopServer, serverAddress := startTestLaptopServer(t)
	laptopClient := newTestLaptopClient(t, serverAddress)
}
```

В первую очередь я инициализирую поисковый фильтр. Пусть мы ищем ноутбуки с 
максимальной ценой равной 2000, минимальным количеством ядер - 4, минимальной 
частотой процессора - 2,2 ГГц и минимальным объёмом ОЗУ - 8 гигабайт.

```go
func TestClientSearchLaptop(t *testing.T) {
    // ...
    
    filter := &pb.Filter{
        MaxPriceUsd: 2000,
        MinCpuCores: 4,
        MinCpuGhz:   2.2,
        MinRam:      &pb.Memory{Value: 8, Unit: pb.Memory_GIGABYTE},
    }

    // ...
}
```

Затем я создам новое хранилище в памяти и добавлю в него несколько ноутбуков 
для поиска. Создадим карту expectedIDs, где будем хранить все идентификаторы 
ноутбуков, которые, как мы ожидаем, найдёт сервер. Отлично, теперь 
воспользуемся циклом `for` для создания 6 ноутбуков. Первый ноутбук не будет 
подходить под параметры фильтра из-за слишком высокой цены. Второй ноутбук не
подойдёт, поскольку имеет только два ядра. Третий ноутбук будет иметь слишком
маленькую минимальную частоту. У четвертого ноутбука будет только 4 Гб ОЗУ. 
Пятый ноутбук будет подходить под все параметры фильтра. Его стоимость будет 
равна 1999$. У него 4 ядра, минимальная частота 2,5 ГГц, максимальная частота
4,5 ГГц и 16 Гб ОЗУ. Мы добавим идентификатор этого ноутбука в карту 
expectedIDs. Последний ноутбук также будет подходить по параметрам. Так что я 
просто скопирую фрагмент кода из предыдущей конструкции case и немного его 
изменю. 

```go
func TestClientSearchLaptop(t *testing.T) {
    // ...
    
    store := service.NewInMemoryLaptopStore()
    expectedIDs := make(map[string]bool)
    
    for i := 0; i < 6; i++ {
        laptop := sample.NewLaptop()
    
        switch i {
        case 0:
            laptop.PriceUsd = 2500
        case 1:
            laptop.Cpu.NumberCores = 2
        case 2:
            laptop.Cpu.MinGhz = 2.0
        case 3:
            laptop.Ram = &pb.Memory{Value: 4096, Unit: pb.Memory_GIGABYTE}
        case 4:
            laptop.PriceUsd = 1999
            laptop.Cpu.NumberCores = 4
            laptop.Cpu.MinGhz = 2.5
            laptop.Cpu.MinGhz = 4.5
            laptop.Ram = &pb.Memory{Value: 16, Unit: pb.Memory_GIGABYTE}
            expectedIDs[laptop.Id] = true
        case 5:
            laptop.PriceUsd = 2000
            laptop.Cpu.NumberCores = 6
            laptop.Cpu.MinGhz = 2.8
            laptop.Cpu.MinGhz = 5.0
            laptop.Ram = &pb.Memory{Value: 64, Unit: pb.Memory_GIGABYTE}
            expectedIDs[laptop.Id] = true
        }
    }

    // ...
}
```

Итак, теперь мы можем вызвать `Store.Save`, чтобы сохранить ноутбук в 
хранилище. При этом не должно возникнуть ошибок.

```go
func TestClientSearchLaptop(t *testing.T) {
    // ...
    
    for i := 0; i < 6; i++ {
    	// ...

        err := store.Save(laptop)
        require.NoError(t, err)
    }

    // ...
}
```

Затем нам нужно добавить это хранилище в тестовый сервер. Я добавлю ещё один 
параметр `store` к функции `startTestLaptopServer`.

```go
func TestClientSearchLaptop(t *testing.T) {
    // ...

    laptopServer, serverAddress := startTestLaptopServer(t, store)
    laptopClient := newTestLaptopClient(t, serverAddress)
}

func startTestLaptopServer(t *testing.T, store service.LaptopStore) (*service.LaptopServer, string) {
    laptopServer := service.NewLaptopServer(store)
    // ...
}	
```

Затем обновите тест `TestClientCreateLaptop`, передав в него новое хранилище 
в памяти.

```go
func TestClientCreateLaptop(t *testing.T) {
    // ...
    	
    laptopServer, serverAddress := startTestLaptopServer(t, service.NewInMemoryLaptopStore())
    
    // ...
}
```

Теперь вернемся к нашему тесту `TestClientSearchLaptop`. Здесь мы не будем
использовать объект `laptopServer`, поэтому я удалю его.

```go
func TestClientSearchLaptop(t *testing.T) {
    // ...

    _, serverAddress := startTestLaptopServer(t, store)
    laptopClient := newTestLaptopClient(t, serverAddress)
}
```

Теперь мы создаём новый `SearchLaptopRequest`, используя значение фильтра. 
Затем мы вызываем `laptopClient.SearchLaptop` с созданным запросом. Чтобы тест
прошел успешно, функция не должна возвращать ошибок. Затем я буду 
использовать переменную `found`, чтобы отслеживать количество найденных 
ноутбуков. В цикле `for` мы обрабатываем ответы от сервера. Если мы получили 
ошибку конца файла, то выходим из цикла. В противном случае проверяем, что 
ошибки нет и что идентификатор ноутбука содержится в карте expectedIDs. После
этого можно увеличить количество найденных ноутбуков. Наконец, нужно убедиться,
что число найденных ноутбуков равно длине карты expectedIDs.

```go
func TestClientSearchLaptop(t *testing.T) {
    // ...

    req := &pb.SearchLaptopRequest{Filter: filter}
    stream, err := laptopClient.SearchLaptop(context.Background(), req)
    require.NoError(t, err)
    
    found := 0
    for {
        res, err := stream.Recv()
        if err == io.EOF {
            break
        }
    
        require.NoError(t, err)
        require.Contains(t, expectedIDs, res.GetLaptop().GetId())
        
        found += 1
    }
    
    require.Equal(t, len(expectedIDs), found)
}
```

Отлично, теперь давайте запустим этот unit тест. Он успешно пройден.

```shell
--- PASS: TestClientSearchLaptop (6.00s)
```

Но он выполнялся 6 секунд. Это из-за того, что мы забыли закомментировать 
`time.Sleep` в функции поиска. Давайте сделаем это. 

```go
func (store *InMemoryLaptopStore) Search(
    filter *pb.Filter,
    found func(laptop *pb.Laptop) error,
) error {
    // ...
    for _, laptop := range store.data {
        // сложные вычисления
        // time.Sleep(time.Second)
        // log.Print("checking laptop id: ", laptop.GetId())
        
    	// ...
    }
    
    return nil
}
```

Заново запустим тест. Он отработал намного быстрее. Запустим тест всего пакета.

```shell
go test -cover
```

Все тесты успешно пройдены и покрытие составляет 75,8%. Неплохо!

На этом закончим сегодняшнюю лекцию. Мы узнали как реализовать и протестировать
серверный потоковый RPC в Go. На следующей лекции мы узнаем как это сделать в 
Java. Спасибо за потраченное время и до новых встреч!
