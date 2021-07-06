# Реализация двунаправленного потокового gRPC - Golang
Привет всем. Сегодня мы узнаем как реализовать последний тип gRPC, то есть 
двунаправленную потоковую передачу. Эта потоковая передача позволяет клиенту 
и серверу одновременно отправлять несколько запросов и ответов друг другу. Мы
напишем API, где клиент будет отправлять в виде потока оценки ноутбуков в 
диапазоне от 1 до 10, а сервер отвечать потоком из усредненных значений оценок 
для каждого из ноутбуков. Итак, давайте начнём!

## Определяем двунаправленный потоковый gRPC в protobuf
Во-первых, нам нужно определить новый двунаправленный потоковый RPC в файле
`laptop_service.proto`. Мы задаём `RateLaptopRequest` с двумя полями: 
идентификатором ноутбука и оценкой.

```protobuf
message RateLaptopRequest {
  string laptop_id = 1;
  double score = 2;
}

message RateLaptopResponse {
  string laptop_id = 1;
  uint32 rated_count = 2;
  double average_score = 3;
}
```

Затем `RateLaptopResponse` с тремя полями: идентификатором ноутбука, числом,
указывающим сколько раз ноутбук оценивался, и средней оценкой. Теперь мы 
зададим `RateLaptop` RPC, где на вход будет поступать поток из 
`RateLaptopRequest`, а выдаваться будет поток из `RateLaptopResponse`.

```protobuf
service LaptopService {
  rpc CreateLaptop(CreateLaptopRequest) returns (CreateLaptopResponse) {};
  rpc SearchLaptop(SearchLaptopRequest) returns (stream SearchLaptopResponse) {};
  rpc UploadImage(stream UploadImageRequest) returns (UploadImageResponse) {};
  rpc RateLaptop(stream RateLaptopRequest) returns (stream RateLaptopResponse) {};
}
```

Теперь давайте выполним

```shell
make gen
```

чтобы сгенерировать код. После того как код сгенерирован, закомментируйте 
строку `pb.UnimplementedLaptopServiceServer` в файле `service/laptop_server.go`
и мы увидим ошибку в файле сервера `main.go`. Она связана с тем, что интерфейс
`LaptopServiceServer` теперь требует наличие ещё одного метода: `RateLaptop`. 
Мы можем найти сигнатуру этого метода внутри файла `laptop_service_grpc.pb.go`.
Поэтому давайте скопируем её и вставим в файл `laptop_server.go`. Пока 
достаточно вернуть `nil` и заменить входной параметр 
`LaptopService_RateLaptopServer` на `pb.LaptopService_RateLaptopServer`. После
этого раскомментируйте ранее закомментированную строку и ошибка пропадёт.

`service/laptop_server.go`
```go
// RateLaptop - это RPC с двунаправленной потоковой передачей, которая позволяет 
// клиенту оценивать ноутбуки и возвращает поток из усредненных оценок для 
// каждого из них 
func (server *LaptopServer) RateLaptop(stream pb.LaptopService_RateLaptopServer) error {
    return nil
}
```

Мы реализуем этот метод чуть позже.

## Реализуем хранилище рейтингов
Теперь нам нужно создать новое хранилище для сохранения рейтингов ноутбуков.
Я определю интерфейс `RatingStore`. Он будет иметь один метод `Add`, который 
принимает на вход идентификатор ноутбука и оценку и возвращает обновленный 
рейтинг ноутбука или ошибку. Рейтинг будет состоять из двух полей: количества,
указывающего сколько раз ноутбук оценивался, и суммы всех оценок.

```go
package service

// RatingStore - это интерфейс для хранения рейтингов ноутбуков
type RatingStore interface {
    // Add добавляет новую оценку ноутбука в хранилище и возвращает его рейтинг
    Add(laptopID string, score float64) (*Rating, error)
}

// Rating содержит информацию об оценках ноутбука
type Rating struct {
    Count uint32
    Sum float64
}
```

Затем мы напишем хранилище рейтинга в памяти, реализующее интерфейс. Подобно 
хранилищу ноутбуков в памяти, здесь нам понадобится мьютекс для защиты доступа 
к ресурсу в многопоточном приложении, и мы будем использовать карту `rating`,
где ключ — это идентификатор ноутбука, а значение — объект-рейтинг. Затем мы
зададим метод для создания нового хранилища рейтинга в памяти. В нём нам нужно
просто инициализировать карту `rating`.

```go
// InMemoryRatingStore хранит рейтинги ноутбуков в памяти
type InMemoryRatingStore struct {
    mutex sync.RWMutex
    rating map[string]*Rating
}
// NewInMemoryRatingStore возвращает новое InMemoryRatingStore
func NewInMemoryRatingStore() *InMemoryRatingStore {
    return &InMemoryRatingStore{
        rating: make(map[string]*Rating),
    }
}
```

Отлично, теперь давайте реализуем метод `Add`. Поскольку мы собираемся 
изменить данные хранилища, здесь нам нужно установить блокировку. Затем мы 
получаем рейтинг из карты с помощью идентификатора ноутбука. Если рейтинг не 
найден, то мы создаём новый объект со значением `Count` равным 1 и `Sum` 
равной входной оценке. В противном случае мы увеличиваем `Count` на единицу и 
прибавляем оценку к `Sum`. Наконец, мы записываем обновленный рейтинг обратно 
в карту и возвращаем его вызывающему. На этом реализация алгоритма работы 
хранилища завершена.

```go
// Add добавляет новую оценку ноутбука в хранилище и возвращает его рейтинг
func (store *InMemoryRatingStore) Add(laptopID string, score float64) (*Rating, error) {
    store.mutex.Lock()
    defer store.mutex.Unlock()
    
    rating := store.rating[laptopID]
    if rating == nil {
        rating = &Rating{
            Count: 1,
            Sum: score,
        }
    } else {
        rating.Count++
        rating.Sum += score
    }
    
    store.rating[laptopID] = rating
    return rating, nil
}
```

Теперь давайте реализуем алгоритм работы сервера.

## Реализуем двунаправленный потоковый gRPC сервер
Мы добавим новое хранилище рейтингов в структуру `LaptopServer`, а также в 
метод `NewLaptopServer`.

`service/laptop_server.go`
```go
// LaptopServer - это сервер, предоставляющий различные сервисы по работе с ноутбуком
type LaptopServer struct {
    // ...
    ratingStore RatingStore
    // ...
}

// NewLaptopServer возвращает новый экземпляр LaptopServer
func NewLaptopServer(laptopStore LaptopStore, imageStore ImageStore, ratingStore RatingStore) *LaptopServer {
    return &LaptopServer{
        laptopStore: laptopStore,
        imageStore:  imageStore,
        ratingStore: ratingStore,
    }
}
```

Из-за этих изменений, возникнут некоторые ошибки, поэтому давайте исправим их.
В файле `laptop_client_test.go` мы добавим новый параметр хранилище рейтинга в 
метод `startTestLaptopServer` и передадим его при создании нового сервера.

```go
func startTestLaptopServer(t *testing.T, laptopStore service.LaptopStore, imageStore service.ImageStore, ratingStore service.RatingStore) string {
    laptopServer := service.NewLaptopServer(laptopStore, imageStore, ratingStore)
    
    // ...
}
```

Поскольку это хранилище с рейтингами не используется во всех текущих unit 
тестах, мы можем просто установить его равным `nil` здесь.

```go
func TestClientUploadImage(t *testing.T) {
    // ...
	
    serverAddress := startTestLaptopServer(t, laptopStore, imageStore, nil)
    
    // ...
}
```

здесь

```go
func TestClientSearchLaptop(t *testing.T) {
    // ...
	
    serverAddress := startTestLaptopServer(t, laptopStore, nil, nil)
    
    // ...
}
```

и здесь.

```go
func TestClientCreateLaptop(t *testing.T) {
    // ...
    
    serverAddress := startTestLaptopServer(t, laptopStore, nil, nil)
    
    // ...
}
```

В файле `laptop_server_test.go` мы также можем передать `nil` в метод
`service.NewLaptopServer`. Все ошибки в тестовых файлах исчезли.

```go
func TestServerCreateLaptop(t *testing.T) {
    // ...
    
    server := service.NewLaptopServer(tc.store, nil, nil)
    
    // ...
}
```

Теперь осталась только одна ошибка в файле сервера `main.go`. В нём мы должны
создать новое хранилище рейтинга в памяти и передать его в метод для создания
нового сервера. После этого ошибок не должно остаться.

```go
func main() {
    // ...
    ratingStore := service.NewInMemoryRatingStore()
    laptopServer := service.NewLaptopServer(laptopStore, imageStore, ratingStore)
    
    // ...
}
```

Теперь давайте вернемся к реализации метода `RateLaptop`. Поскольку мы будем
получать несколько запросов из потока, мы должны использовать здесь цикл 
`for`. Подобно тому, что мы делали с клиентским потоковым RPC, прежде чем 
что-либо делать, давайте проверим не возникла ли ошибка контекста. Возможно 
он уже отменен или превышено максимальное время выполнения. Затем мы вызываем
`stream.Recv()`, чтобы получить запрос из потока. Если произошла ошибка — конец
файла, это означает, что данных больше нет и можно спокойно выйти из цикла.
Если ошибка не равна `nil`, пишем её в лог и возвращаем ошибку с кодом 
состояния `Unknown` клиенту. В противном случае мы можем извлечь 
идентификатор ноутбука и оценку из запроса. Давайте запишем здесь сообщение в 
лог о том, что мы получили запрос на изменение рейтинга ноутбука, добавив 
информацию об идентификаторе ноутбука и оценке.

```go
func (server *LaptopServer) RateLaptop(stream pb.LaptopService_RateLaptopServer) error {
    for {
        err := contextError(stream.Context())
        if err != nil {
            return err
        }
    
        req, err := stream.Recv()
        if err == io.EOF {
            log.Print("no more data")
            break
        }
        if err != nil {
            return logError(status.Errorf(codes.Unknown, "cannot receive stream request: %v", err))
        }
    
        laptopID := req.GetLaptopId()
        score := req.GetScore()
    
        log.Printf("received a rate-laptop request: id = %s, score = %.2f", laptopID, score)
    }
    
    return nil
}
```

Теперь мы должны проверить, что такой идентификатор ноутбука существует в 
хранилище ноутбуков с помощью метода `laptopStore.Find()`. Если возникнет 
ошибка, то мы возвращаем её с кодом состояния `Internal`. Если ноутбук не 
найден, то выдаём клиенту ошибку с кодом состояния `NotFound`.

```go
func (server *LaptopServer) RateLaptop(stream pb.LaptopService_RateLaptopServer) error {
    for {
        // ...
    
        found, err := server.laptopStore.Find(laptopID)
        if err != nil {
            return logError(status.Errorf(codes.Internal, "cannot find laptop: %v", err))
        }
        if found == nil {
            return logError(status.Errorf(codes.NotFound, "laptopID %s is not found", laptopID))
        }
    }
    
    return nil
}
```

Если ошибок не возникло, мы вызываем `ratingStore.Add`, чтобы добавить новую
оценку в хранилище и вернуть обновленный объект-рейтинг. Если при этом 
произошла ошибка, возвращаем её с кодом состояния `Internal`. В противном 
случае создаём `RateLaptopResponse` с `laptopID` равным идентификатору 
ноутбука из запроса. Значение для поля `RatedCount` берется из 
объекта-рейтинга, а средняя оценка вычисляется, используя поля `Sum` and 
`Count`. Мы вызываем `stream.Send()`, чтобы отправить ответ клиенту. Если 
ошибка не равна `nil`, пишем её в лог и возвращаем код состояния `Unknown`.
На этом всё! Мы реализовали логику работы сервера.

```go
func (server *LaptopServer) RateLaptop(stream pb.LaptopService_RateLaptopServer) error {
	for {
        // ...
		
        rating, err := server.ratingStore.Add(laptopID, score)
        if err != nil {
            return logError(status.Errorf(codes.Internal, "cannot and rating to the scores: %v", err))
        }
        
        res := &pb.RateLaptopResponse{
            LaptopId: laptopID,
            RatedCount: rating.Count,
            AverageScore: rating.Sum / float64(rating.Count),
        }
        
        err = stream.Send(res)
        if err != nil {
            return logError(status.Errorf(codes.Unknown, "cannot send stream response: %v", err))
        }
    }

    return nil
}
```

## Реализуем двунаправленный потоковый gRPC клиент
Теперь прежде чем перейти к клиенту я добавлю новую функцию для генерации
случайной оценки ноутбука в пакете `sample`. Для простоты предположим, что это 
будет случайное число от 1 до 10. 

`sample/generator.go`
```go
// RandomLaptopScore возвращает сгенерированную случайным образом оценку ноутбука
func RandomLaptopScore() float64 {
    return float64(randomInt(1, 10))
}
```

Итак, теперь давайте реализуем клиент. Сначала мы определим функцию 
`rateLaptop` с тремя входными параметрами: клиентом, списком идентификаторов
ноутбуков и соответствующие им оценки. В этой функции мы создаём новый контекст
с таймаутом через 5 секунд. Затем вызываем метод `laptopClient.RateLaptop()` с 
этим контекстом. Результатом выполнения является поток или ошибка. Если ошибка
не равна `nil`, мы просто возвращаем её.

```go
func rateLaptop(laptopClient pb.LaptopServiceClient, laptopIDs []string, scores []float64) error {
    ctx, cancel := context.WithTimeout(context.Background(), 5 * time.Second)
    defer cancel()
    
    stream, err := laptopClient.RateLaptop(ctx)
    if err != nil {
        return fmt.Errorf("cannot rate laptop: %v", err)
    }
}
```

В противном случае нам нужно создать канал, ожидающий ответов от сервера. 
Обратите внимание, что запросы и ответы отправляются одновременно, поэтому 
нам нужно запустить новую горутину для получения ответов. Таким образом, в 
канал `waitResponse` запишется сообщение об ошибке в случае её возникновения,
или `nil`, если все ответы будут успешно обработаны. В горутине мы используем
цикл `for` и вызываем `stream.Recv()`, чтобы получить ответ от сервера. Если
ошибка равна EOF, это означает, что ответов больше поступать не будет, поэтому
мы посылаем `nil` в канал `waitResponse` и выходим из метода. Если ошибка
не равна `nil`, мы посылаем ошибку в канал `waitResponse` и также выходим из 
метода. Если ошибок не произошло, пишем ответ от сервера в лог.

```go
func rateLaptop(laptopClient pb.LaptopServiceClient, laptopIDs []string, scores []float64) error {
    // ...

    waitResponse := make(chan error)
    // горутина для получения ответов
    go func() {
        for {
            res, err := stream.Recv()
            if err == io.EOF {
                log.Print("no more responses")
                waitResponse <- nil
                return
            }
            if err != nil {
                waitResponse <- fmt.Errorf("cannot receive stream response: %v", err)
                return
            }
            
            log.Print("received response: ", res)
        }
    }()
}
```

После этой горутины, мы можем начать отправлять запросы на сервер. Давайте
в цикле пройдём по списку ноутбуков и создадим новый запрос для каждого из них
с `laptopID` и соответствующим ему оценкой. Затем вызовем `stream.Send()`,
чтобы отправить запрос на сервер. Если мы получим ошибку, просто вернём её.
Обратите внимание, что мы вызываем `stream.RecvMsg()`, чтобы получить настоящую
ошибку, как мы это делали на предыдущей лекции для клиентского потокового RPC.
Если ошибок нет, добавляем сообщение в лог, что запрос отправлен вместе с 
содержимым запроса.

```go
func rateLaptop(laptopClient pb.LaptopServiceClient, laptopIDs []string, scores []float64) error {
    // ...
	
    // посылаем запросы
    for i, laptopID := range laptopIDs {
        req := &pb.RateLaptopRequest{
            LaptopId: laptopID,
            Score:    scores[i],
        }
    
        err := stream.Send(req)
        if err != nil {
            return fmt.Errorf("cannot send stream request: %v - %v", err, stream.RecvMsg(nil))
        }
        
        log.Print("sent request: ", req)
    }
}
```

После того как все запросы отправлены, необходимо не забыть вызвать 
`stream.CloseSend()`. Это сообщит серверу о том, что мы не будем больше
передавать данные. Наконец, считайте значение из канала `waitResponse` и 
верните полученную ошибку. Логика работы функции `rateLaptop()` реализована. 

```go
func rateLaptop(laptopClient pb.LaptopServiceClient, laptopIDs []string, scores []float64) error {
    // ...
	
    err = stream.CloseSend()
    if err != nil {
        return fmt.Errorf("cannot close send: %v", err)
    }
    
    err = <-waitResponse
    return err
}
```

Теперь мы напишем функцию `testRatelaptop()`, которая будет вызывать
`rateLaptop()`. Пусть мы хотим оценить 3 ноутбука. Итак, мы объявляем срез для
хранения идентификаторов ноутбуков и используем цикл `for` для создания 
ноутбука со случайными характеристиками, добавляем его идентификатор в срез и 
вызываем функцию `createLaptop()` для сохранения на сервере.

```go
func testRateLaptop(laptopClient pb.LaptopServiceClient) {
    n := 3
    laptopIDs := make([]string, n)
    
    for i := 0; i < n; i++ {
        laptop := sample.NewLaptop()
        laptopIDs[i] = laptop.GetId()
        createLaptop(laptopClient, laptop)
    }
}
```

Затем мы также инициализируем срез, в котором будут храниться оценки. Я хочу 
оценить эти 3 ноутбука несколько раз, поэтому воспользуюсь здесь циклом `for` 
и буду выводить в консоль вопрос о том нужно ли провести ещё одну итерацию или 
нет. Если ответ - "нет", мы прерываем цикл, иначе генерируем новый набор 
оценок для ноутбуков и вызываем функцию `rateLaptop` для обновления рейтинга,
используя эти сгенерированные оценки. Если произошла ошибка, мы пишем её в лог
и аварийно завершаем работу. На этом всё.

```go
func testRateLaptop(laptopClient pb.LaptopServiceClient) {
    // ...
    
    scores := make([]float64, n)
    for {
        fmt.Print("rate laptop (y/n)?")
        var answer string
        fmt.Scan(&answer)
    
        if strings.ToLower(answer) != "y" {
            break
        }
    
        for i := 0; i < n; i++ {
            scores[i] = sample.RandomLaptopScore()
        }
    
        err := rateLaptop(laptopClient, laptopIDs, scores)
        if err != nil {
            log.Fatal(err)
        }
    }
}
```

Теперь в функции `main` достаточно вызвать `testRateLaptop`, чтобы 
протестировать работу функции.

`cmd/client/main.go`
```go
func main() {
    // ...
    testRateLaptop(laptopClient)
}
```

## Запускаем двунаправленный gRPC сервер и клиент
Давайте запустим сервер и клиент. Создано 3 ноутбука.

```shell
2021/04/13 19:48:58 created laptop with id: 7087f361-bd46-4d08-87b2-66e92dad6b0d
2021/04/13 19:48:58 created laptop with id: c209924a-ac73-48f1-88e7-91f7f62bd0b6
2021/04/13 19:48:58 created laptop with id: f2ceae64-1938-4a16-b7de-654cbbaa7fb8
rate laptop (y/n)?
```

Оценить ноутбук? Нажимаем y(Yes).

```shell
2021/04/13 20:09:54 sent request: laptop_id:"03572d1f-c64e-4e71-ba6f-7985bfa9dd9c" score:10
2021/04/13 20:09:54 sent request: laptop_id:"ad198e92-ef54-48f7-a36d-3a2c1435c820" score:7
2021/04/13 20:09:54 sent request: laptop_id:"788a4976-172f-43ee-b50d-13e2563fbff7" score:10
2021/04/13 20:09:54 received response: laptop_id:"03572d1f-c64e-4e71-ba6f-7985bfa9dd9c" rated_count:1 average_score:10
2021/04/13 20:09:54 received response: laptop_id:"ad198e92-ef54-48f7-a36d-3a2c1435c820" rated_count:1 average_score:7
2021/04/13 20:09:54 received response: laptop_id:"788a4976-172f-43ee-b50d-13e2563fbff7" rated_count:1 average_score:10
2021/04/13 20:09:54 no more responses
rate laptop (y/n)?
```

Как видите, мы отправили 3 запроса с оценками 10, 7 и 10 и получили 3 ответа
с rated count (число, указывающее сколько раз ноутбук оценивался) равным 1 и 
средними оценками в 10, 7 и 10 соответственно. Отлично! Теперь давайте ещё
раз оценим ноутбуки.

```shell
2021/04/13 20:16:38 sent request: laptop_id:"03572d1f-c64e-4e71-ba6f-7985bfa9dd9c" score:4
2021/04/13 20:16:38 sent request: laptop_id:"ad198e92-ef54-48f7-a36d-3a2c1435c820" score:6
2021/04/13 20:16:38 sent request: laptop_id:"788a4976-172f-43ee-b50d-13e2563fbff7" score:2
2021/04/13 20:16:38 received response: laptop_id:"03572d1f-c64e-4e71-ba6f-7985bfa9dd9c" rated_count:2 average_score:7
2021/04/13 20:16:38 received response: laptop_id:"ad198e92-ef54-48f7-a36d-3a2c1435c820" rated_count:2 average_score:6.5
2021/04/13 20:16:38 received response: laptop_id:"788a4976-172f-43ee-b50d-13e2563fbff7" rated_count:2 average_score:6
2021/04/13 20:16:38 no more responses
```

В этот раз оценки равны 4, 6 и 2. Ответы от сервера имеют rated count равный 2
и средние оценки 7, 6,5 и 6. Результаты верны и их можно легко проверить, 
используя формулу для вычисления усредненного значения. Осуществим оценку ещё 
раз.

```shell
2021/04/13 20:19:15 sent request: laptop_id:"03572d1f-c64e-4e71-ba6f-7985bfa9dd9c" score:2
2021/04/13 20:19:15 sent request: laptop_id:"ad198e92-ef54-48f7-a36d-3a2c1435c820" score:5
2021/04/13 20:19:15 sent request: laptop_id:"788a4976-172f-43ee-b50d-13e2563fbff7" score:8
2021/04/13 20:19:15 received response: laptop_id:"03572d1f-c64e-4e71-ba6f-7985bfa9dd9c" rated_count:3 average_score:5.333333333333333
2021/04/13 20:19:15 received response: laptop_id:"ad198e92-ef54-48f7-a36d-3a2c1435c820" rated_count:3 average_score:6
2021/04/13 20:19:15 received response: laptop_id:"788a4976-172f-43ee-b50d-13e2563fbff7" rated_count:3 average_score:6.666666666666667
2021/04/13 20:19:15 no more responses
```

Всё работает, как и ожидалось. Здорово! 

## Тестируем двунаправленный gRPC
Теперь я покажу вам как протестировать этот RPC с двунаправленной потоковой 
передачей. Давайте перейдём в файл `laptop_client_test.go`. Сам тест будет 
очень похож на тот, который мы написали для загрузки изображений. Поэтому я
просто скопирую и вставлю его. Изменим название теста на 
`TestClientRateLaptop`, удалим `testImageFolder`, поменяем `imageStore` на
`ratingStore` и передадим его в функцию `startTestLaptopServer` вместо
`imageStore`.

```go
func TestClientRateLaptop(t *testing.T) {
    t.Parallel()
    
    laptopStore := service.NewInMemoryLaptopStore()
    ratingStore := service.NewInMemoryRatingStore()
    
    laptop := sample.NewLaptop()
    err := laptopStore.Save(laptop)
    require.NoError(t, err)
    
    serverAddress := startTestLaptopServer(t, laptopStore, nil, ratingStore)
    laptopClient := newTestLaptopClient(t, serverAddress)
}
```

Теперь мы вызываем `laptopClient.RateLaptop()` с фоновым контекстом, чтобы 
получить поток. При этом не должно возникнуть ошибок. Для простоты будем 
оценивать только один ноутбук, но мы сделаем это три раза, используя значения
8, 7,5 и 10 соответственно. Таким образом, усредненное значение рейтинга после
каждого вызова будет равно 8, 7,75 и 8,5. Зададим переменную `n`, которая
будет определять сколько раз ноутбук оценивался, и воспользуемся циклом `for`,
чтобы отправить несколько запросов. Каждый раз мы будем создавать новый запрос
с одинаковым значением идентификатора ноутбука, но измененным значением оценки.
Вызовем `stream.Send()` для отправки запроса на сервер. При этом также не 
должно возникнуть ошибок.

```go
func TestClientRateLaptop(t *testing.T) {
    // ...

    stream, err := laptopClient.RateLaptop(context.Background())
    require.NoError(t, err)
    
    scores := []float64{8, 7.5, 10}
    averages := []float64{8, 7.75, 8.5}
    
    n := len(scores)
    for i := 0; i < n; i++ {
        req := &pb.RateLaptopRequest{
            LaptopId: laptop.GetId(),
            Score: scores[i],
        }
        
        err := stream.Send(req)
        require.NoError(t, err)
    }
}
```

Чтобы не усложнять тест, я не буду создавать отдельную горутину для получения
ответов. Здесь я воспользуюсь циклом `for` и переменной `idx`, чтобы посчитать 
сколько ответов было принято. Внутри цикла мы вызываем `stream.Recv()`, чтобы
получить новый ответ. Если ошибка равна EOF, то это означает конец потока,
поэтому нужно убедиться, что количество принятых ответов равно `n`, т. е. 
количеству посланных запросов и можно выходить из цикла. В противном случае
ошибок быть не должно. Идентификатор ноутбука в ответе должен быть равен 
идентификатору сгенерированного ноутбука. Число, указывающее сколько раз 
ноутбук оценивался, должно быть равно `idx + 1`, а средняя оценка должна быть
равна ожидаемому значению из массива `averages`.

```go
func TestClientRateLaptop(t *testing.T) {
    // ...
    
    for idx := 0; ; idx++ {
        res, err := stream.Recv()
        if err == io.EOF {
            require.Equal(t, n, idx)
            return
        }
    
        require.NoError(t, err)
        require.Equal(t, laptop.GetId(), res.GetLaptopId())
        require.Equal(t, uint32(idx+1), res.GetRatedCount())
        require.Equal(t, averages[idx], res.GetAverageScore())
    }
}
```

Теперь запустим тест. Он завершается с ошибкой через 30 секунд из-за того, что
я забыл закрыть поток. Давайте вызовем `stream.CloseSend()` здесь.

```go
func TestClientRateLaptop(t *testing.T) {
    // ...
	
	err = stream.CloseSend()
	require.NoError(t, err)
    
    for idx := 0; ; idx++ {
        // ...
    }
}
```

При вызове не должно возникнуть ошибок. Повторно запустим тест. Теперь он 
успешно пройден. Запустим тесты всего пакета. Все тесты успешно пройдены. 
Отлично! На этом мы завершим сегодняшнюю лекцию, посвященную реализации RPC
с двунаправленной потоковой передачей в Go. На следующей лекции, мы узнаем
как это сделать в Java. Спасибо за время, потраченное на чтение. Желаю вам 
успехов в написании программ, увидимся на следующей лекции.