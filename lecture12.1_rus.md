# Загружаем файл по частям с помощью клиентского потокового gRPC - Golang
Рад снова всех вас приветствовать! На этой лекции мы узнаем как использовать
клиентский потоковый RPC для загрузки файла изображения на сервер по частям.
В первой части лекции реализовывать эту задачу будем на языке Golang.

## Определяем клиентский потоковый RPC в proto файле
Итак, давайте начнём! Сначала мы определим RPC в файле `laptop_service.proto`.
Нам нужно будет сообщение `UploadImageRequest`. Идея заключается в том, чтобы
разделить файл изображения на несколько частей и посылать их по очереди серверу
в каждом сообщении-запросе. Я буду использовать здесь поле `oneof`, поскольку
первый запрос будет содержать только метаданные или некоторую базовую 
информацию об изображении, а следующие запросы будет содержать фрагменты данных
изображения. Сообщение `ImageInfo` будет иметь два поля: идентификатор ноутбука
и тип изображения, например, ".jpg" или ".png".

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

Затем мы определим сообщение `UploadImageResponse`, которое будет возвращено 
клиенту как только сервер получит все фрагменты изображения. Оно будет состоять
из идентификатора изображения, сгенерированного сервером, и общего размера 
загруженного изображения в байтах. Теперь мы зададим `UploadImage` RPC в
`LaptopService`. Он принимает на вход поток `UploadImageRequest` и возвращает 
один, единственный ответ от сервера `UploadImageResponse`.

```protobuf
message UploadImageResponse {
  string id = 1;
  string size = 2;
}
service LaptopService {
  rpc CreateLaptop(CreateLaptopRequest) returns (CreateLaptopResponse) {};
  rpc SearchLaptop(SearchLaptopRequest) returns (stream SearchLaptopResponse) {};
  rpc UploadImage(stream UploadImageRequest) returns (UploadImageResponse) {};
}
```

Теперь давайте выполним команду `make gen`, чтобы сгенерировать код.

```shell
make gen
```

Код успешно сгенерирован и если мы закомментируем строку 
`pb.UnimplementedLaptopServiceServer` в файле `laptop_server.go`,

```go
type LaptopServer struct {
    Store LaptopStore
    pb.UnimplementedLaptopServiceServer
}
```

то увидим ошибку в строке 
`pb.RegisterLaptopServiceServer(grpcServer, laptopServer)`, поскольку в 
`LaptopServer` не реализован метод `UploadImage`, который нужен, чтобы 
структура удовлетворяла интерфейсу `LaptopServiceServer`.

## Реализуем сервер
Итак, давайте откроем файл `laptop_Server.go` и добавим метод `UploadImage()`
в структуру `LaptopServer`. Мы можем легко сигнатуру этого метода внутри 
сгенерированного файла `laptop_service_grpc.pb.go`. Просто скопируйте и 
вставьте её сюда. Пусть пока она возвращает `nil`.

```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
	return nil
}
```

После этого появившаяся ошибка пропадёт. Мы вернемся к этому методу и напишем 
код для его чуть позже.

### Реализуем хранилище изображений
Сначала нам нужно реализовать новое хранилище изображений. Интерфейс
`ImageStore` будет содержать единственный метод для сохранения изображения
ноутбука. Он будет принимать три входных параметра: идентификатор ноутбука, 
тип изображения и данные изображения и возвращать идентификатор сохраненного
изображения или ошибку.

```go
type ImageStore interface {
    // Метод Save сохраняет новое изображение ноутбука в хранилище
    Save(laptopID string, imageType string, imageData bytes.Buffer) (string, error)
}
```

Затем мы реализуем хранилище `DiskImageStore`, которое будет сохранять файл 
изображения на диск, а информацию о нём в память. Как и для хранилища ноутбуков
нам понадобится мьютекс для защиты доступа к ресурсу в многопоточном 
приложении. Затем нам нужен будет путь к папке для сохранения изображений 
ноутбука и, наконец, карта, где ключом будет идентификатор изображения, а 
значением — некоторая информация об изображении.

```go
type DiskImageStore struct {
    mutex       sync.RWMutex
    imageFolder string
    images      map[string]*ImageInfo
}
```

Структура `ImageInfo` будет состоять из трёх полей: идентификатор ноутбука, 
тип изображения (или расширения файла) и пути к файлу изображения на диске.

```go
type ImageInfo struct {
    LaptopID string
    Type     string
    Path     string
}
```

Давайте напишем метод для создания нового `DiskImageStore`. На вход он
принимает только один входной аргумент — папку с изображениями - а внутри мы
просто инициализируем карту.

```go
func NewDiskImageStore(imageFolder string) *DiskImageStore {
    return &DiskImageStore{
        imageFolder: imageFolder,
        images:      make(map[string]*ImageInfo),
    }
}
```

Теперь нам нужно реализовать метод `Save`, чтобы структура удовлетворяла 
интерфейсу `ImageStore`. Сначала нам нужно сгенерировать новый случайный UUID
для изображения. Если произошла ошибка, то оборачиваем её и возвращаем. В 
противном случае создаём путь для хранения изображений, склеив папку для 
изображений, идентификатор и тип изображения. Затем мы вызываем `os.Create` 
для создания файла. Если произошла ошибка, то оборачиваем её и возвращаем. 
Иначе записываем данные изображения в созданный файл. Оборачиваем ошибку и 
возвращаем её, если она возникла. Если файл успешно записан, нам нужно 
сохранить информацию о нём в карту. Таким образом, нужно осуществить блокировку
на запись хранилища. После этого мы сохраняем информацию об изображении в 
карту, где ключом будет идентификатор изображения, а значением структура, 
состоящая из идентификатора ноутбука, типа изображения и пути к файлу. Наконец,
мы возвращаем идентификатор изображения и `nil`, что означает отсутствие 
ошибок. На этом реализация хранилища изображений завершена.

```go
func (store *DiskImageStore) Save(
    laptopID string,
    imageType string,
    imageData bytes.Buffer,
) (string, error) {
    imageID, err := uuid.NewRandom()
    if err != nil {
        return "", fmt.Errorf("cannot generate image id: %w", err)
    }

    imagePath := fmt.Sprintf("%s/%s%s", store.imageFolder, imageID, imageType)

    file, err := os.Create(imagePath)
    if err != nil {
        return "", fmt.Errorf("cannot create image file: %w", err)
    }

    _, err = imageData.WriteTo(file)
    if err != nil {
        return "", fmt.Errorf("cannot write image to file: %w", err)
    }

    store.mutex.Lock()
    defer store.mutex.Unlock()
    
    store.images[imageID.String()] = &ImageInfo{
        LaptopID: laptopID,
        Type: imageType,
        Path: imagePath,
    }

    return imageID.String(), nil
}
```

Теперь вернемся нереализованному методу на сервере.

### Реализуем UploadImage RPC
Нам нужно добавить новое хранилище изображений в структуру `LaptopServer`, 
поэтому я изменю название поля для хранилища ноутбуков на `laptopStore` и 
добавлю `imageStore` в качестве второго параметра метода `NewLaptopServer`.

```go
type LaptopServer struct {
    laptopStore LaptopStore
    imageStore ImageStore
    pb.UnimplementedLaptopServiceServer
}

// NewLaptopServer возвращает новый LaptopServer
func NewLaptopServer(laptopStore LaptopStore, imageStore ImageStore) *LaptopServer {
    return &LaptopServer{
        laptopStore: laptopStore,
        imageStore: imageStore,
    }
}
```

Теперь из-за этого изменения появились новые ошибки. Давайте откроем файл
`laptop_client_test.go`. Во-первых, публичное поле `Store` было изменено на
`laptopStore`. Поэтому давайте вынесем это новое хранилище ноутбуков в памяти
в отдельную переменную и будем вызывать метод `Find` с помощью неё. Теперь нам
больше не понадобится объект `laptopServer`, поэтому давайте удалим его из 
функции `startTestLaptopServer` и добавим `imageStore` в качестве входного 
параметра. Теперь мы можем передать два хранилища в функцию 
`NewLaptopServer()`. В конечном счёте `startTestLaptopServer` возвращает только
адрес сервера. Поэтому в тесте `TestClientCreateLaptop` мы просто передаём 
`nil` вместо хранилища изображений, поскольку в нём оно не используется.

```go
func TestClientCreateLaptop(t *testing.T) {
    // ...
    
    laptopStore := service.NewInMemoryLaptopStore()
    serverAddress := startTestLaptopServer(t, laptopStore, nil)
    // ...

    other, err := laptopStore.Find(res.Id)
}

func startTestLaptopServer(t *testing.T, laptopStore service.LaptopStore, imageStore service.ImageStore) string {
    laptopServer := service.NewLaptopServer(laptopStore, imageStore)
    
    // ...
    
    return listener.Addr().String()
}
```

По аналогии осуществим замены для теста поиска ноутбука. В этом файле больше
не должно быть ошибок.

```go
func TestClientSearchLaptop(t *testing.T) {
    // ...
    
    laptopStore := service.NewInMemoryLaptopStore()
    expectedIDs := make(map[string]bool)
    
    for i := 0; i < 6; i++ {
        // ...
    
        err := laptopStore.Save(laptop)
        require.NoError(t, err)
    }
    
    serverAddress := startTestLaptopServer(t, laptopStore, nil)
    // ...
}
```

Осуществим аналогичные действия для файла `laptop_server_test.go`. 

```go
func TestServerCreateLaptop(t *testing.T) {
    // ...
	
    server := service.NewLaptopServer(tc.store, nil)
    res, err := server.CreateLaptop(context.Background(), req)
	// ...
}
```

Наконец, в файле `laptop_server.go` мы просто изменим вызов с `Store` на 
`laptopStore` и все ошибки пропадут.

```go
func (server *LaptopServer) CreateLaptop(
	ctx context.Context,
	req *pb.CreateLaptopRequest,
) (*pb.CreateLaptopResponse, error) {
    // ...
	
    // сохраняем ноутбук в хранилище
    err := server.laptopStore.Save(laptop)
    // ...
}
```

Чтобы убедиться в этом, я запущу unit тесты для этого пакета.

```shell
cd service
go test
```

Тесты успешно пройдены, т. е. ничего не сломалось. Теперь в файле `main.go` 
сервера нам также нужно передавать два хранилища в метод `NewLaptopServer`. 
Сначала хранилище ноутбуков, а затем — хранилище изображений. Я создам новую
папку "img" для сохранения загруженных изображений.

```go
func main() {
    // ...
	
    laptopStore := service.NewInMemoryLaptopStore()
    imageStore := service.NewDiskImageStore("img")
    
    laptopServer := service.NewLaptopServer(laptopStore, imageStore)
    // ...
}
```

Вроде бы в файле ошибок больше нет. Давайте реализуем метод `UploadImage` на 
сервере. Сначала вызовем `stream.Recv()`, чтобы получить первый запрос, в 
котором содержится информация об изображении. Если произошла ошибка, мы пишем 
её в лог и возвращаем клиенту код состояния `Unknown`.

```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
    req, err := stream.Recv()
    if err != nil {
        log.Print("cannot receive image info", err)
        return status.Errorf(codes.Unknown, "cannot receive image info")
    }
    
    return nil
}
```

Этот кусок кода достаточно длинный и часто повторяется, поэтому я вынесу его в 
отдельную функцию `logError()`, которая будет писать ошибку в лог и возвращать 
её. Функция добавляет ошибку в лог только в случае, если она не равна `nil` и
всегда возвращает ошибку вызвавшему.

```go
func logError(err error) error {
    if err != nil {
        log.Print(err)
    }
    return err
}
```

Теперь используя эту функцию, мы можем упростить блок обработки ошибок 
следующим образом.

```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
    // ...
	
    if err != nil {
        return logError(status.Errorf(codes.Unknown, "cannot receive image info"))
    }
    
    // ...
}
```

Если ошибок не возникло, мы можем получить идентификатор ноутбука, а также
тип изображения из запроса. Давайте добавим сообщение в лог о том, что мы 
получили запрос upload-image с соответствующим идентификатором ноутбука и типом
изображения.

```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
    // ...
	
    laptopID := req.GetInfo().GetLaptopId()
    imageType := req.GetInfo().GetImageType()
    log.Printf("receive an upload-image request for laptop %s with image type %s", laptopID, imageType)
    // ...
}
```

Затем мы должны убедиться, что существует ноутбук с таким идентификатором. 
Поэтому мы вызываем `server.laptopStore.Find()`, чтобы найти ноутбук по его 
идентификатору. Если мы получили ошибку, просто выведем её в лог и вернем с 
кодом состояния `Internal`. Если значение переменной `laptop` равно `nil`, т. 
е. ноутбука с таким идентификатор в хранилище нет, то мы пишем ошибку в лог и 
возвращаем код состояния ошибки `InvalidArgument`. Также вы можете использовать
код `NotFound`, если хотите.

```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
    // ...	
    
    laptop, err := server.laptopStore.Find(laptopID)
    if err != nil {
        return logError(status.Errorf(codes.Internal, "cannot find laptop: %v", err))
    }
    if laptop == nil {
        return logError(status.Errorf(codes.InvalidArgument, "laptop %s doesn't exists", laptopID))
    }
    // ...
}
```

Теперь, если до этого всё прошло без ошибок и ноутбук найден, мы можем
начать получать данные о фрагментах изображения. Давайте создадим новый байтовый
буфер для их хранения, а также переменную для отслеживания общего размера 
изображения. Поскольку мы собираемся получать множество сообщений из потока,
я буду использовать здесь цикл `for`. Внутри него давайте добавим сообщение в
лог о том, что мы ожидаем фрагмент с данными. Как и раньше, мы вызываем 
`stream.Recv()`, чтобы получить запрос. Но в этот раз мы сначала проверяем 
произошла или нет ошибка `EOF`. Если да, то это означает, что данные больше не
будут отправляться, и мы можем не опасаясь потери данных выйти из цикла. Если 
возникла ошибка и она не равна `nil`, то мы возвращаем её клиенту с кодом
состояния `Unknown`. Если ошибки нет, мы можем получить фрагмент данных из 
запроса и его размер, используя функцию `len()`. Затем добавим этот размер к 
общему размеру изображения.

```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
    // ...

    imageData := bytes.Buffer{}
    imageSize := 0
    
    for {
        log.Print("waiting to receive more data")
    
        req, err := stream.Recv()
        if err == io.EOF {
            log.Print("no more data")
            break
        }
        if err != nil {
            return logError(status.Errorf(codes.Unknown, "cannot receive data: %v", err))
        }
    
        chunk := req.GetChunkData()
        size := len(chunk)

        imageSize += size
    }

    // ... 
}
```

Допустим, что мы не хотим, чтобы клиент отправлял слишком большое изображение,
поэтому мы проверим, что размер изображения не превышает максимальный. Я 
определю константу для максимального размера изображения равной 1 мегабайту.

```go
// максимум в 1 мегабайт
const maxImageSize = 1 << 20
```

Теперь при превышении максимального размера, мы можем вернуть ошибку с кодом 
состояния `InvalidArgument` и сообщение о том, что изображение слишком 
большое. В противном случае мы можем добавить фрагмент к данным изображения
с помощью функции `Write()`. Также добавим сообщение в лог и вернём код статуса
`Internal`, если возникла ошибка при записи.

```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
	// ...
	
    for {
        // ...
    	
        if imageSize > maxImageSize {
            return logError(status.Errorf(codes.InvalidArgument, "image is too large %d > %d", imageSize, maxImageSize))
        }
        
        _, err = imageData.Write(chunk)
        if err != nil {
            return logError(status.Errorf(codes.Internal, "cannot write chunk data: %v", err))
        }
    }
    // ...
}
```

После цикла `for` мы собрали все данные изображения в буфере. Теперь мы можем
вызвать метод `imageStore.Save`, чтобы сохранить данные изображения в хранилище
и получить идентификатор изображения. Если возникла ошибка, мы добавляем её в 
лог и возвращаем её с кодом состояния `Internal`. Если изображение успешно
сохранено, мы создаём объект-ответ от сервера с идентификатором и размером 
изображения. Затем вызываем `stream.SendAndClose()`, чтобы послать ответ 
клиенту. При возникновении ошибки выдаём её с кодом состояния `Unknown`. И, 
наконец, если всё прошло без ошибок, мы можем записать сообщение в лог о том, 
что изображение успешно сохранено с соответствующим идентификатором и размером.
Реализация логики работы сервера на этом завершена.

```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
	// ...
	
    imageID, err := server.imageStore.Save(laptopID, imageType, imageData)
    if err != nil {
        return logError(status.Errorf(codes.Internal, "cannot save image to the store: %v", err))
    }
    
    res := &pb.UploadImageResponse{
        Id: imageID,
        Size: uint32(imageSize),
    }
    
    err = stream.SendAndClose(res)
    if err != nil {
        return logError(status.Errorf(codes.Unknown, "cannot send response: %v", err))
    }
    
    log.Printf("saved image with id: %s, size: %d", imageID, imageSize)
    // ...
}
```

Теперь давайте реализуем логику работы клиента.

## Реализуем клиент
Сначала я немного реорганизую код. Давайте сделаем ноутбук параметром функции 
`createLaptop`

`cmd/client/main.go`
```go
func createLaptop(laptopClient pb.LaptopServiceClient, laptop *pb.Laptop) {
    laptop.Id = ""
    req := &pb.CreateLaptopRequest{
        Laptop: laptop,
    }
    
    // ...
}
```

и будем отправлять в неё ноутбук, как показано в этом цикле `for`.

`cmd/client/main.go`
```go
func main() {
	// ...
    for i := 0; i < 10; i++ {
        createLaptop(laptopClient, sample.NewLaptop())
    }
    
    // ...
}
```

Затем я создам отдельную функцию для тестирования RPC, предназначенного для 
поиска ноутбука, который мы написали на прошлой лекции. Давайте скопируем этот
фрагмент кода

```go
for i := 0; i < 10; i++ {
    createLaptop(laptopClient, sample.NewLaptop())
}

filter := &pb.Filter{
    MaxPriceUsd: 3000,
    MinCpuCores: 4,
    MinCpuGhz:   2.5,
    MinRam:      &pb.Memory{Value: 8, Unit: pb.Memory_GIGABYTE},
}

searchLaptop(laptopClient, filter)
```

и вставим его в функцию.

```go
func testSearchLaptop(laptopClient pb.LaptopServiceClient) {
    for i := 0; i < 10; i++ {
        createLaptop(laptopClient, sample.NewLaptop())
    }
    
    filter := &pb.Filter{
        MaxPriceUsd: 3000,
        MinCpuCores: 4,
        MinCpuGhz:   2.5,
        MinRam:      &pb.Memory{Value: 8, Unit: pb.Memory_GIGABYTE},
    }
    
    searchLaptop(laptopClient, filter)
}
```

Давайте добавим ещё одну функцию для тестирования RPC, предназначенного для 
создания ноутбука. 

```go
func testCreateLaptop(laptopClient pb.LaptopServiceClient) {
    createLaptop(laptopClient, sample.NewLaptop())
}
```

Теперь мы напишем новую функцию для тестирования RPC, предназначенного для
загрузки изображений и вызовем его из функции `main`.

```go
func testUploadImage(laptopClient pb.LaptopServiceClient) {

}
func main() {
    // ...
    
    laptopClient := pb.NewLaptopServiceClient(conn)
    testUploadImage(laptopClient)
}
```

В этой функции `testUploadImage()` мы сначала генерируем случайный ноутбук и 
вызываем `createLaptop()`, чтобы создать его на сервере. Затем мы напишем новую
функцию `uploadImage()` для загрузки изображения этого ноутбука на сервер.

```go
func testUploadImage(laptopClient pb.LaptopServiceClient) {
    laptop := sample.NewLaptop()
    createLaptop(laptopClient, laptop)
    uploadImage(laptopClient, laptop.GetId(), "tmp/laptop.jpg")
}
```

Эта функция будет иметь три входных параметра: клиент, идентификатор ноутбука 
и путь к изображению ноутбука. Сначала мы вызываем `os.Open()`, чтобы открыть
файл изображения. Если возникла ошибка, мы пишем её в лог и аварийно завершаем
работу. В противном случае мы используем ключевое слово `defer`, чтобы закрыть 
файл после завершения функции `main`. Затем мы создаём контекст с таймаутом в
5 секунд и вызываем метод `laptopClient.UploadImage()` с этим контекстом. Он 
возвращает объект-поток и ошибку. Если ошибка не равна `nil`, мы пишем её в лог
и аварийно завершаем работу. Иначе создаём первый запрос, в котором отправляем
на сервер определенную информацию об изображении, а именно, идентификатор 
ноутбука и тип изображения или расширение файла изображения.

```go
func uploadImage(laptopClient pb.LaptopServiceClient, laptopID string, imagePath string) {
    file, err := os.Open(imagePath)
    if err != nil {
        log.Fatal("cannot open image file: ", err)
    }
    defer file.Close()
    
    ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
    defer cancel()
    
    stream, err := laptopClient.UploadImage(ctx)
    if err != nil {
        log.Fatal("cannot upload image", err)
    }
    
    req := &pb.UploadImageRequest{
        Data: &pb.UploadImageRequest_Info{
            Info: &pb.ImageInfo{
                LaptopId:  laptopID,
                ImageType: filepath.Ext(imagePath),
            },
        },
    }
}
```

Отлично, теперь мы вызываем `stream.Send()`, чтобы послать первый запрос на
сервер. Если мы получим ошибку, пишем её в лог и аварийно завершаем работу. 
Иначе создаём буфер для считывания содержимого файла изображения по частям. 
Пусть размер каждой части будет равен 1 килобайту или 1024 байт. Считаем 
фрагменты данных изображения в цикле `for`. Для того, чтобы добавить данные в 
буфер достаточно вызвать `reader.Read()`. Метод вернёт число прочитанных байтов 
и ошибку. Если ошибка равна EOF, то был достигнут конец файла и мы можем выйти 
из цикла. Если ошибка не равна `nil`, пишем её в лог и аварийно завершаем 
работу. В противном случае создаём новый запрос с данными фрагмента. Важно, 
чтобы фрагмент содержал только первые `n` байтов буфера. Затем опять вызываем 
`stream.Send()`, чтобы послать запрос на сервер. Опять пишем сообщение в лог и 
аварийно завершаем работу, если произойдёт ошибка.

```go
func uploadImage(laptopClient pb.LaptopServiceClient, laptopID string, imagePath string) {
    // ...
	
    err = stream.Send(req)
    if err != nil {
        log.Fatal("cannot send image info: ", err)
    }
    
    reader := bufio.NewReader(file)
    buffer := make([]byte, 1024)
    
    for {
        n, err := reader.Read(buffer)
        if err == io.EOF {
            break
        }
        if err != nil {
            log.Fatal("cannot read chunk to buffer: ", err)
        }
    
        req := &pb.UploadImageRequest{
            Data: &pb.UploadImageRequest_ChunkData{
                ChunkData: buffer[:n],
            },
        }
        
        err = stream.Send(req)
        if err != nil {
            log.Fatal("cannot send chunk to server: ", err)
        }
    }
}
```

Наконец, после цикла `for` мы вызываем метод `stream.CloseAndRecv()` для 
получения ответа от сервера. Если произошла ошибка, пишем её в лог и аварийно 
завершаем работу. В противном случае пишем сообщение в лог о том, что 
изображение успешно загружено с соответствующим идентификатором и размером.
На этом всё.

```go
func uploadImage(laptopClient pb.LaptopServiceClient, laptopID string, imagePath string) {
    res, err := stream.CloseAndRecv()
    if err != nil {
        log.Fatal("cannot receive response: ", err)
    }
    
    log.Printf("image uploaded with id: %s, size: %d", res.GetId(), res.GetSize())
}
```

Клиент готов к работе.

Теперь давайте запустим сервер

```shell
make server
```

и клиент.
 
```shell
make client
```

Возникла ошибка: cannot open image file `laptop.jpg` (не удается открыть файл 
изображения `laptop.jpg`). Она произошла, потому что я забыл добавить файл в 
папку `tmp`. Давайте сделаем это. У меня уже скачан файл с изображением 
ноутбука в папке `Dowload`. Я перетащу его в папку `tmp`. После того как файл
будет успешно скопирован, давайте перезапустим клиент. Мы получили другую 
ошибку: 

```shell
2021/04/09 19:05:00 cannot send chunk to server: EOF
```

От этого сообщение об ошибке мало пользы, поскольку не ясна причина, из-за 
которой фрагмент не может быть послан на сервер. Итак, давайте посмотрим на код
клиента. Мы знаем, что сообщение об ошибке выводится благодаря этой строчке 
кода `log.Fatal("cannot send chunk to server: ", err)`.

```go
func uploadImage(laptopClient pb.LaptopServiceClient, laptopID string, imagePath string) {
    // ...	

    for {
        // ...
        
        err = stream.Send(req)
        if err != nil {
            log.Fatal("cannot send chunk to server: ", err)
        }
    }

    // ...
}
```

Ошибка равна EOF, поскольку когда она возникает, сервер закрывает поток и, 
таким образом, клиент не сможет отправить ему больше данных. Чтобы получить 
ошибку с gRPC код состояния, мы должны вызвать `stream.RecvMsg()` с параметром
`nil`. Теперь мы можем также вывести и эту ошибку.

```go
func uploadImage(laptopClient pb.LaptopServiceClient, laptopID string, imagePath string) {
    // ...	

    for {
        // ...
        
        err = stream.Send(req)
        if err != nil {
            err2 := stream.RecvMsg(nil)
            log.Fatal("cannot send chunk to server: ", err, err2)
        }
    }

    // ...
}
```

Перезапустите клиент и посмотрите что произойдёт.

```shell
2021/04/09 19:14:24 cannot send chunk to server: EOF rpc error: code = InvalidArgument desc = laptop  doesn't exists
```

Теперь мы видим, что возникла на самом деле ошибка `InvalidArgument, laptop 
doesn't exists`. Причина в том, что идентификатор ноутбука равен пустой строке.
Теперь давайте немного изменим сообщение об ошибке и будем писать в лог сразу
`stream.RecvMsg(nil)`.

```go
func uploadImage(laptopClient pb.LaptopServiceClient, laptopID string, imagePath string) {
    // ...
    
    err = stream.Send(req)
    if err != nil {
        log.Fatal("cannot send image info: ", err, stream.RecvMsg(nil))
    }
    
    // ...
    for {
        // ...
        
        err = stream.Send(req)
        if err != nil {
        err2 := stream.RecvMsg(nil)
            log.Fatal("cannot send chunk to server: ", err, err2)
        }
    }
        
    // ...
}
```

Идентификатор ноутбука пуст, потому что он так задан в функции 
`createLaptop()`. Поэтому давайте удалим эту строку

```go
func createLaptop(laptopClient pb.LaptopServiceClient, laptop *pb.Laptop) {
    laptop.Id = "" // удалите эту строку
    req := &pb.CreateLaptopRequest{
        Laptop: laptop,
    }
    
    // ...
}
```

и перезапустим клиент. На этот раз все работает.

```shell
2021/04/09 19:26:13 image uploaded with id: f3b2a396-2765-475f-9d28-f5e8d9614093, size: 87635
```

Изображение успешно загружено. На стороне сервера мы видим множество сообщений 
типа: waiting to receive more data (ожидаю получения следующих данных). Чего 
именно ожидает сервер не сразу понятно, поэтому давайте добавим здесь ещё одно
сообщение в лог о том, что мы получили новый фрагмент с данными и укажем их
размер.

`service/laptop_server.go`
```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
    // ...
    
    for {
        // ...
        
        chunk := req.GetChunkData()
        size := len(chunk)
    
        log.Printf("received a chunk with size: %d", size)
        
        imageSize += size
        if imageSize > maxImageSize {
            return logError(status.Errorf(codes.InvalidArgument, "image is too large %d > %d", imageSize, maxImageSize))
        }
        
        // ...
    }
    
    // ...
}
```

Отлично, теперь если мы откроем папку `img`, то увидим, что изображение 
ноутбука было сохранено в ней. Превосходно! Теперь давайте посмотрим, что 
случится, если произойдет таймаут. Предположим, что по какой-то причине сервер
очень медленно записывает данные. Чтобы смоделировать это поведение, я добавлю 
задержку в 1 секунду перед записью фрагмента в буфер.

```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
    // ...
    
    for {
        // ...
        
        imageSize += size
        if imageSize > maxImageSize {
            return logError(status.Errorf(codes.InvalidArgument, "image is too large %d > %d", imageSize, maxImageSize))
        }

        // имитируем медленную запись
        time.Sleep(time.Second)
        
        // ...
    }
    
    // ...
}
```

Давайте попробуем запустить сервер и клиент. Через 5 секунд, мы видим сообщение
об ошибке в логах на сервере.

```shell
2021/04/09 19:39:52 rpc error: code = Unknown desc = cannot receive data: rpc error: code = DeadlineExceeded desc = context deadline exceeded
```

Но код состояния равен `Unknown` и кроме того он содержит другую ошибку 
`DeadlineExceeded`, что может сбивать с толку. Поэтому давайте исправим это,
проверив ошибку контекста перед тем как получить данные из потока. Я вырежу
фрагмент кода, где происходит проверка контекста из RPC `CreateLaptop` и помещу
его в отдельную функцию. Давайте воспользуемся здесь оператором switch-case,
чтобы сократить размер кода и упростить его чтение. Если ошибка контекста 
равна `Cancelled`, мы пишем её в лог и возвращаем ошибку. Для случая 
`DeadlineExceeded`, делаем то же самое. В случае по умолчанию просто 
возвращаем nil.

```go
func (server *LaptopServer) CreateLaptop(
    ctx context.Context,
    req *pb.CreateLaptopRequest,
) (*pb.CreateLaptopResponse, error) {
	// ...
	
    // имитируем сложные вычисления
    // time.Sleep(6 * time.Second)
    
    if err := contextError(ctx); err != nil {
        return nil, err
    }
    
    // ...
}

func contextError(ctx context.Context) error {
    switch ctx.Err() {
    case context.Canceled:
        return logError(status.Error(codes.Canceled, "request is cancelled"))
    case context.DeadlineExceeded:
        return logError(status.Error(codes.DeadlineExceeded, "deadline is exceeded"))
    default:
        return nil
    }
}
```

Теперь вернемся к нашему циклу `for`. Здесь мы вызываем функцию 
`contextError()`, передавая ей контекст потока. Если ошибка не равна `nil`, мы
сразу же возвращаем её.

```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
    // ...
    
    for {
        // check context error
        if err := contextError(stream.Context()); err != nil {
            return err
        }
        
        // ...
    }
    
    // ...
}
```

Давайте запустим север и клиент. Теперь на стороне сервера мы увидим более 
понятное сообщение в логе с кодом состояния `DeadlineExceeded`.

```shell
2021/04/09 20:13:37 rpc error: code = DeadlineExceeded desc = deadline is exceeded
```

Отлично! Давайте рассмотрим случай, когда размер загружаемого изображения 
больше максимально допустимого. Я изменю значение константы с 1 мегабайта на 
1 килобайт.

```go
// максимум в 1 килобайт
const maxImageSize = 1 << 10
```

Затем перезапустите сервер и клиент. В этот раз мы получили ошибку 
`InvalidArgument`: image is too large (изображение слишком большое). На стороне
сервера он получил только 2 фрагмента данных прежде чем выдал ту же ошибку.
Таким образом, ограничение на размер загружаемого изображения работает! Я 
отменю внесенные изменения и оставлю максимальный размер изображения равным 1 
мегабайту. Также закомментируйте эту строку `time.Sleep(time.Second)`.

```go
func (server *LaptopServer) UploadImage(stream pb.LaptopService_UploadImageServer) error {
    // ...
    
    for {
        // ...
        
        imageSize += size
        if imageSize > maxImageSize {
            return logError(status.Errorf(codes.InvalidArgument, "image is too large %d > %d", imageSize, maxImageSize))
        }

        // имитируем медленную запись
        // time.Sleep(time.Second)
        
        // ...
    }
    
    // ...
}
```

Теперь давайте узнаем как написать тест для этого клиентского потокового RPC.

## Пишем unit тест
Назовём функцию `TestClientUploadImage`. Для этого теста я буду использовать
папку `tmp` в качестве папки с изображениями. Прежде всего нам нужно создать
новое хранилище в памяти для ноутбуков, а затем новое хранилище изображений на 
диске, используя папку `tmp`. Мы сгенерируем тестовый ноутбук и сохраним его в 
хранилище для ноутбуков.

```go
func TestClientUploadImage(t *testing.T) {
    t.Parallel()
    
    testImageFolder := "../tmp"
    
    laptopStore := service.NewInMemoryLaptopStore()
    imageStore := service.NewDiskImageStore(testImageFolder)
    
    laptop := sample.NewLaptop()
    err := laptopStore.Save(laptop)
    require.NoError(t, err)
}
```

Затем мы запускаем тестовый сервер и создаём новый клиент. Изображение, которое
мы будем загружать - это файл `laptop.jpg` в папке `tmp`. Итак, давайте 
откроем файл, проверим что при этом не возникло ошибок и с помощью ключевого
слова `defer` закроем файл после выполнения функции. Затем мы вызываем
`laptopClient.UploadImage`, чтобы получить поток. После этого мы получаем тип 
изображения из расширения файла.

```go
func TestClientUploadImage(t *testing.T) {
    // ...
    
    serverAddress := startTestLaptopServer(t, laptopStore, imageStore)
    laptopClient := newTestLaptopClient(t, serverAddress)
    
    imagePath := fmt.Sprintf("%s/laptop.jpg", testImageFolder)
    file, err := os.Open(imagePath)
    require.NoError(t, err)
    defer file.Close()
    
    stream, err := laptopClient.UploadImage(context.Background())
    require.NoError(t, err)
    
    imageType := filepath.Ext(imagePath)
}
```

Оставшаяся часть теста очень похожа на то, что мы делали в файле `main.go` 
клиента. Поэтому я просто скопирую и вставлю код, чтобы сэкономить время. 
`laptopID` нужно заменить на `laptop.GetId()`, а тип изображения взять из 
переменной `imageType`. Мы заменяем блок проверки ошибок на 
`require.NoError()`. Аналогичную замену осуществим для ошибки в цикле. Мы 
также хотим отслеживать общий размер изображения, поэтому давайте определим 
здесь переменную `size` и будем прибавлять значение `n` к `size` в цикле. 
Заменим оставшиеся блоки проверки ошибок в функции на `require.NoError()`.

```go
func TestClientUploadImage(t *testing.T) {
    // ...
    
    imageType := filepath.Ext(imagePath)
    req := &pb.UploadImageRequest{
        Data: &pb.UploadImageRequest_Info{
            Info: &pb.ImageInfo{
                LaptopId: laptop.GetId(),
                ImageType: imageType,
            },
        },
    }
    
    err = stream.Send(req)
    require.NoError(t, err)
    
    reader := bufio.NewReader(file)
    buffer := make([]byte, 1024)
    size := 0
    
    for {
        n, err := reader.Read(buffer)
        if err == io.EOF {
            break
        }
        require.NoError(t, err)
        size += n
    
        req := &pb.UploadImageRequest{
            Data: &pb.UploadImageRequest_ChunkData{
                ChunkData: buffer[:n],
            },
        }
    
        err = stream.Send(req)
        require.NoError(t, err)
    }
    
    res, err := stream.CloseAndRecv()
    require.NoError(t, err)
}
```

Теперь проверим, что возвращаемый идентификатор не равен нулю и изображение, 
сохраненное в переменную res, имеет тот же размер, что и переменная `size`.
Мы также хотим убедиться, что изображение сохраняется в правильную папку на 
сервере. Оно должно находиться в папке `tmp`. Причем имя файла должно быть 
равно идентификатору изображения, а расширение — типу изображения. Мы можем
использовать `require.FileExists()` для проверки. И, наконец, нам нужно удалить
файл в конце теста.

```go
func TestClientUploadImage(t *testing.T) {
    // ...
	
    res, err := stream.CloseAndRecv()
    require.NoError(t, err)
    require.NotZero(t, res.GetId())
    require.EqualValues(t, size, res.GetSize())
    
    savedImagePath := fmt.Sprintf("%s/%s%s", testImageFolder, res.GetId(), imageType)
    require.FileExists(t, savedImagePath)
    require.NoError(t, os.Remove(savedImagePath))
}
```

Итак, давайте запустим его. Тест пройден! Давайте запустим все тесты в пакете.

```shell
make test
```

Превосходно! Все тесты успешно пройдены!

На этом закончим сегодняшнюю лекцию о клиентском потоковом RPC. На следующей
лекции мы узнаем как реализовать его на Java. Спасибо за потраченное на чтение
время и до новых встреч!