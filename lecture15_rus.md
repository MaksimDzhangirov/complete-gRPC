# Реализуем gRPC перехватчик + JWT аутентификацию в Go
Привет всем, рад снова вас приветствовать на курсе по gRPC! Сегодня мы узнаем
как использовать gRPC перехватчики для аутентификации и авторизации 
пользователей.

## Что такое gRPC перехватчик?
Ну, по сути, он представляет собой связующую, промежуточную функцию, которую 
можно добавить как на стороне сервера, так и на стороне клиента. Серверный 
перехватчик — это функция, вызываемая gRPC сервером, перед тем как будет 
выполнен RPC метод. Её можно использовать для разных целей, например, 
логирования, трассировки, ограничения частоты запросов, аутентификации и 
авторизации. Точно также клиентский перехватчик - это функция, которая будет 
вызываться gRPC клиентом перед выполнением фактического RPC.

![gRPC перехватчик](images/lecture15/grpc_interceptor_rus.png)

**Рисунок 1** - gRPC перехватчик.

На этой лекции мы сначала реализуем серверный перехватчик для авторизации 
доступа к нашим gRPC API с помощью JSON веб-токена (JWT). Используя этот 
перехватчик, мы будем уверены, что некоторые API на нашем сервере смогут 
вызывать только пользователи с определенными ролями. Затем мы реализуем 
клиентский перехватчик для входа пользователя в систему и добавим JWT к 
запросу перед вызовом gRPC API.

## Простой серверный перехватчик
Итак, давайте начнём с добавления простого перехватчика к нашему gRPC серверу.
Перехватчики существуют двух типов: первые — для унарных RPC, а вторые — для 
потоковых RPC.

### Унарный перехватчик
Давайте передадим новый параметр `grpc.UnaryInterceptor()` в эту функцию 
`grpc.NewServer()`.

`cmd/sever/main.go`
```go
func main() {
    // ...
    
    grpcServer := grpc.NewServer(
        grpc.UnaryInterceptor(),
    )
    // ...
}
```

Она ожидает на вход серверный унарный перехватчик. Мы можем нажать Command в
macOS (Ctrl в Windows и Linux) и щелкнуть по имени функции, чтобы перейти к её
определению. Давайте откроем таким способом определение 
`UnaryServerInterceptor`, скопируем сигнатуру этой функции и скопируем её в 
наш файл `main.go`. Я назову её `unaryInterceptor()`. У неё есть несколько 
входных параметров, таких как контекст, запрос, информация об унарном сервере
и фактический унарный обработчик. Она возвращает ответ и ошибку. Теперь мы 
можем передать эту функцию в качестве параметра унарному перехватчику. Здесь 
нам нужно не забыть добавить название пакета `grpc` к структуре 
`UnaryServerInfo` и функции `UnaryHandler`.

```go
func unaryInterceptor(
    ctx context.Context,
    req interface{},
    info *grpc.UnaryServerInfo,
    handler grpc.UnaryHandler,
) (interface{}, error) {

}

func main() {
    // ...
    
    grpcServer := grpc.NewServer(
        grpc.UnaryInterceptor(unaryInterceptor),
    )
    // ...
}
```

Отлично, теперь давайте просто добавим простое сообщение в лог о том, что 
был вызван унарный перехватчик, добавив сюда полное название метода 
запрашиваемого RPC. Затем мы просто вызываем фактический обработчик с 
исходным контекстом и запросом и возвращаем его результат. 

```go
func unaryInterceptor(
    ctx context.Context,
    req interface{},
    info *grpc.UnaryServerInfo,
    handler grpc.UnaryHandler,
) (interface{}, error) {
    log.Println("--> unary interceptor: ", info.FullMethod)
    return handler(ctx, req)
}
```

### Потоковый перехватчик
Мы можем сделать то же самое для потокового перехватчика. Добавьте параметр
`grpc.StreamInterceptor()`. Найдите определение функции, чтобы получить её 
сигнатуру. Скопируйте и вставьте её в файл `main.go`. Передайте эту функцию
в качестве параметра потоковому перехватчику. Добавим сообщение в лог с полным
названием RPC метода. В этот раз мы вызываем обработчик с параметрами `srv` и
`stream`.

```go
func streamInterceptor(
    srv interface{},
    stream grpc.ServerStream,
    info *grpc.StreamServerInfo,
    handler grpc.StreamHandler,
) error {
    log.Println("--> stream interceptor: ", info.FullMethod)
    return handler(srv, stream)
}

func main() {
    // ...
        
    grpcServer := grpc.NewServer(
        grpc.UnaryInterceptor(unaryInterceptor),
        grpc.StreamInterceptor(streamInterceptor),
    )
    // ...
}
```

## Запускаем клиент и сервер
Давайте посмотрим как это работает! Сначала я стартую сервер, затем открою новую
вкладку терминала и запущу клиент. Как видно из логов сервера, унарный 
перехватчик вызывается 3 раза для `CreateLaptop` RPC. Далее выводится полное 
название этого RPC: `/techschool_pcbook.LaptopService/CreateLaptop`, которое 
включает имя пакета `techschool_pcbook` и сервиса `LaptopService`.

```shell
2021/04/15 19:38:55 --> unary interceptor:  /techschool_pcbook.LaptopService/CreateLaptop
2021/04/15 19:38:55 receive a create-laptop request with id: 2b6bc3c0-22fb-4eec-a991-98336ee1090d
2021/04/15 19:38:55 saved laptop with id: 2b6bc3c0-22fb-4eec-a991-98336ee1090d
2021/04/15 19:38:55 --> unary interceptor:  /techschool_pcbook.LaptopService/CreateLaptop
2021/04/15 19:38:55 receive a create-laptop request with id: 968c098c-13b9-4ff3-aa3f-f74ed22f1e6f
2021/04/15 19:38:55 saved laptop with id: 968c098c-13b9-4ff3-aa3f-f74ed22f1e6f
2021/04/15 19:38:55 --> unary interceptor:  /techschool_pcbook.LaptopService/CreateLaptop
2021/04/15 19:38:55 receive a create-laptop request with id: ab034232-f0aa-48c4-a20e-108c6766286a
2021/04/15 19:38:55 saved laptop with id: ab034232-f0aa-48c4-a20e-108c6766286a
```

Теперь введите "y", чтобы оценить 3 созданных ноутбука. Потоковый перехватчик
вызывается единожды.

```shell
2021/04/15 19:42:27 --> stream interceptor:  /techschool_pcbook.LaptopService/RateLaptop
```

Отлично.

Итак, теперь у вас есть некоторое представление о том как работает серверный
перехватчик. Мы расширим его функциональные возможности, чтобы использовать для
аутентификации и авторизации запросов от пользователя. Для этого нам нужно 
добавить пользователей в нашу систему, а также сервис для входа пользователей 
в систему и возврата JWT токена доступа.

## Структура User
Итак, давайте создадим файл `user.go` и определим новую структуру `User`. Она
будет состоять из имени пользователя, хэшированного пароля и роли. Мы добавим 
метод `NewUser()` для создания нового пользователя, который принимает на вход
имя пользователя, пароль и роль и возвращает объект `User` и ошибку.

```go
package service

// User содержит информацию о пользователе
type User struct {
    Username       string
    HashedPassword string
    Role           string
}

// NewUser возвращает нового пользователя
func NewUser(username string, password string, role string) (*User, error) {

}
```

Мы никогда не должны хранить пароль в открытом виде в системе, поэтому здесь 
будем использовать `bcrypt`, чтобы сначала захэшировать пароль. Пока что 
будем передавать `bcrypt.DefaultCost` во второй аргумент функции 
`GenerateFromPassword`. Если возникает ошибка, просто обернём и вернём её. В 
противном случае мы создаём новый объект пользователя с именем, хэшированным
паролем и ролью и возвращаем его. Затем мы определяем метод для проверки 
правильности пароля введенного пользователем. Для этого вызовем функцию
`bcrypt.CompareHashAndPassword()` и передадим ей захэшированный и введенный 
пользователем пароль. Она вернёт `true`, если ошибка равна `nil`. Я добавлю
сюда ещё один метод для клонирования пользователя. Эта функция понадобится нам
позже, поскольку мы собираемся хранить пользователей в памяти.

```go
// NewUser возвращает нового пользователя
func NewUser(username string, password string, role string) (*User, error) {
    hashedPassword, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
    if err != nil {
        return nil, fmt.Errorf("cannot hash password: %w", err)
    }
    
    user := &User{
        Username:       username,
        HashedPassword: string(hashedPassword),
        Role:           role,
    }
    
    return user, nil
}

// IsCorrectPassword проверяет правильный ли пароль был введён
func (user *User) IsCorrectPassword(password string) bool {
    err := bcrypt.CompareHashAndPassword([]byte(user.HashedPassword), []byte(password))
    return err == nil
}

// Clone возвращает объект-клон данного пользователя
func (user *User) Clone() *User {
    return &User{
        Username:       user.Username,
        HashedPassword: user.HashedPassword,
        Role:           user.Role,
    }
}
```

## Хранилище для пользователей
Отлично, теперь давайте создадим файл `user_store.go` и определим в нём
интерфейс как мы это делали для хранилища ноутбуков и рейтингов в предыдущих
лекциях. В нём определим два метода: первый — для сохранения пользователя в 
хранилище, а второй — для поиска пользователя в хранилище по имени. Теперь 
давайте реализуем этот интерфейс с помощью хранилища пользователей в памяти.
Оно содержит мьютекс для управления доступом к ресурсу в многопоточном 
приложении и карту `users`, где ключом будет `username`. Я напишу функцию для
создания нового хранилища пользователей в памяти и инициализирую карту `users`
в ней.

```go
package service

import "sync"

// UserStore - это интерфейс для хранения пользователей
type UserStore interface {
    // Save сохраняет пользователя в хранилище
    Save(user *User) error
    // Find находит пользователя по имени
    Find(username string) (*User, error)
}

// InMemoryUserStore хранит пользователей в памяти
type InMemoryUserStore struct {
    mutex sync.RWMutex
    users map[string]*User
}

// NewInMemoryUserStore возвращает новое хранилище пользователей в памяти
func NewInMemoryUserStore() *InMemoryUserStore {
    return &InMemoryUserStore{
        users: make(map[string]*User),
    }
}
```

Затем нам нужно реализовать методы `Save` и `Find` интерфейса. В методе `Save`
мы сначала захватываем блокировку по записи, затем проверяем существует ли
пользователь с таким именем в хранилище. Если "да" - возвращаем ошибку. В 
противном случае мы клонируем поступивший на вход объект `user` и помещаем его
в карту.

```go
// Save сохраняет пользователя в хранилище
func (store *InMemoryUserStore) Save(user *User) error {
    store.mutex.Lock()
    defer store.mutex.Unlock()
    
    if store.users[user.Username] != nil {
        return ErrAlreadyExists
    }
    
    store.users[user.Username] = user.Clone()
    return nil
}
```

Для метода `Find` захватываем блокировку по чтению, затем получаем пользователя
по имени из карты. Если результат равен `nil`, просто возвращаем `nil, nil`.
В противном случае — объект-клон этого пользователя.

```go
// Find finds a user by username
func (store *InMemoryUserStore) Find(username string) (*User, error) {
    store.mutex.RLock()
    defer store.mutex.RUnlock()
    
    user := store.users[username]
    if user == nil {
        return nil, nil
    }
    
    return user.Clone(), nil
}
```

На этом всё. Хранилище для пользователей готово.

## JWT менеджер
Теперь давайте реализуем JWT менеджер, генерирующий и проверяющий токена 
доступа пользователей. Структура `JWTManager` состоит из двух полей: секретный 
ключ для подписи и проверки токена доступа и допустимый срок действия токена. 
Эти два поля должны быть переданы в качестве входных параметров в функцию
`NewJWTManager()`.

```go
package service

import "time"

// JWTManager - это менеджер JSON веб-токенов
type JWTManager struct {
    secretKey     string
    tokenDuration time.Duration
}

// NewJWTManager возвращает новый JWT менеджер
func NewJWTManager(secretKey string, tokenDuration time.Duration) *JWTManager {
    return &JWTManager{secretKey: secretKey}
}
```

Чтобы работать с JWT, я воспользуюсь библиотекой `jwt-go`. Давайте поищем её в 
браузере. Скопируйте ссылку библиотеки на Github 
`https://github.com/dgrijalva/jwt-go` и выполните в терминале

```shell
go get github.com/dgrijalva/jwt-go
```

чтобы установить её. Отлично, теперь вернемся к нашему JWT менеджеру. JSON 
веб-токен должен содержать claims-объект, в котором находится некая полезная 
информация о пользователя, владеющим им. Таким образом, я объявлю свой,
пользовательский `UserClaims`. Он будет включать в себя стандартный JWT 
claims-объект в виде анонимного поля. Этот стандартный claims-объект включает 
в себя некоторую полезную информацию и каждое из его полей мы могли бы 
проинициализировать. Но для демонстрационных целей я буду использовать только 
одно поле - `expiresAt` - срок действия токена. Как вы возможно заметили,
claims-объект содержит метод `Valid()`, который автоматически проверяет, истек 
ли срок действия токена за нас. Он будет использоваться когда мы позже вызовем
метод `ParseWithClaims`. Хорошо, теперь кроме этого стандартного 
claims-объекта, давайте добавим поле с именем пользователя и его ролью, 
поскольку мы хотим выполнять авторизацию на основе ролей.

```go
// UserClaims - это нестандартный JWT claims-объект, содержащий некоторую 
// дополнительную информацию о пользователе
type UserClaims struct {
    jwt.StandardClaims
    Username string `json:"username"`
    Role     string `json:"role"`
}
```

## Генерируем и подписываем токен доступа
Затем давайте напишем метод, генерирующий и подписывающий новый токен доступа
для конкретного пользователя. В этом методе мы сначала создаём новый объект
`UserClaims`. Из стандартных claims, я проинициализирую только поле 
`expiresAt`. Мы должны добавить время действия токена к текущему времени и 
преобразовать его в метку времени Unix. Затем мы инициализируем значения для 
полей `Username` и `Role` и вызываем функцию `jwt.NewWithClaims()`, чтобы 
создать объект-токен. Для простоты здесь я подписываю токен, используя HMAC 
алгоритм, а именно `HS256`. Для продакшена вам следует применять более стойкие 
методы, например, RSA или алгоритмы цифровой подписи на эллиптических кривых.
Последний и самый важный шаг — подписать сгенерированный токен своим секретным
ключом. Это гарантирует, что никто не сможет подделать токен доступа, поскольку
у них не будет вашего секретного ключа.

```go
// Generate генерирует и подписывает новый токен для пользователя
func (manager *JWTManager) Generate(user *User) (string, error) {
    claims := UserClaims{
        StandardClaims: jwt.StandardClaims{
            ExpiresAt: time.Now().Add(manager.tokenDuration).Unix(),
        },
        Username: user.Username,
        Role: user.Role,
    }
    
    token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
    return token.SignedString([]byte(manager.secretKey))
}
```

## Разбираем и проверяем токен доступа
Здорово, теперь давайте добавим другой метод для проверки токена доступа и
выдачи `UserClaims`, если токен действителен. Нам просто нужно вызвать
`jwt.ParseWithClaims` передать в него токен доступа, пустой объект `UserClaims`
и анонимную функцию, возвращающую ключ. В этой функции нужно прежде всего 
проверить, что метод, которым подписан токен, совпадает с алгоритмом, 
применяемым на сервере, в нашем случае `HMAC`. Если произошло совпадение, то
мы просто возвращаем секретный ключ, который использовался для подписи токена.
Функция `jwt.ParseWithClaims` вернет объект-токен и ошибку. Если ошибка не
равна `nil`, то выдаём сообщение о некорректном токене. В противном случае
получаем claims из токена и преобразуем их в объект `UserClaims`. Если 
преобразование осуществить не удалось, возвращаем ошибку о некорректных claims
токена. Иначе просто возвращаем `UserClaims`. На этом всё!

```go
// Verify проверяет строку с токеном доступа и возвращает UserClaims, если токен действителен
func (manager *JWTManager) Verify(accessToken string) (*UserClaims, error) {
    token, err := jwt.ParseWithClaims(
        accessToken,
        &UserClaims{},
        func (token *jwt.Token) (interface{}, error) {
            _, ok := token.Method.(*jwt.SigningMethodHMAC)
            if !ok {
                return nil, fmt.Errorf("unexpected token signing method")
            }
    
            return []byte(manager.secretKey), nil
        },
    )
    
    if err != nil {
        return nil, fmt.Errorf("invalid token: %w", err)
    }
    
    claims, ok := token.Claims.(*UserClaims)
    if !ok {
        return nil, fmt.Errorf("invalid token claims")
    }
    
    return claims, nil
}
```

## Реализуем сервер службы аутентификации
Следующее, что нам нужно сделать — это предоставить клиенту новый сервис для
входа в систему и получения токена доступа. Давайте создадим новый файл
`auth_service.proto`. Название пакета и остальные настройки будут такими же, 
как и в других proto файлах. Мы зададим сообщение `LoginRequest` с двумя 
полями: `username` типа `string` и `password` типа `string` и сообщение 
`LoginResponse` с единственным полем: токеном доступа. Определим новый сервис
`AuthService`. Он будет содержать только одну унарную RPC: `Login`, которая 
принимает на вход `LoginRequest` и возвращает `LoginResponse`.

```protobuf
syntax = "proto3";

package techschool_pcbook;

option go_package = ".;pb";
option java_package = "com.github.techschool.pcbook.pb";
option java_multiple_files = true;

message LoginRequest {
  string username = 1;
  string password = 2;
}

message LoginResponse { string access_token = 1; }

service AuthService {
  rpc Login(LoginRequest) returns (LoginResponse) {};
}
```

Отлично, теперь давайте выполним команду `make gen`, чтобы сгенерировать код
для этого сервиса. Как видите, были созданы файлы `auth_service.pb.go` и 
`auth_service_grpc.pb.go`. Как и в случае с сервером для ноутбуков в предыдущих
лекциях, я создам новый файл `auth_server.go` для реализации этого нового 
сервиса. Структура `AuthServer` будет содержать `userStore` и `JWTManager`.
Давайте добавим функцию для создания и выдачи нового объекта `AuthServer`.

```go
package service

// AuthServer - это сервер для аутентификации
type AuthServer struct {
    userStore UserStore
    jwtManager *JWTManager
    pb.AuthServiceServer
}

// NewAuthServer возвращает новый сервер для аутентификации
func NewAuthServer(userStore UserStore, jwtManager *JWTManager) *AuthServer {
    return &AuthServer{userStore: userStore, jwtManager: jwtManager}
}
```

Затем перейдём в файл `auth_service_grpc.pb.go` и скопируем сигнатуру функции
`Login`. Вставим её в наш файл `auth_server.go` и сделаем методом структуры
`AuthServer`. Этот метод очень легко реализовать. Сначала мы вызываем 
`userStore.Find()`, чтобы найти пользователя по имени. Если возникла ошибка,
просто возвращаем её с кодом состояния `Internal`, иначе, если пользователь
не найден или введён неверный пароль, выдаём ошибку с кодом состояния 
`NotFound` и сообщением о неверном имени пользователя или пароле. Если 
пользователь найден и пароль верен, то вызываем `jwtManager.Generate()`, чтобы
сгенерировать новый токен доступа. Если произошла ошибка, возвращаем её с кодом
состояния `Internal`. В противном случае создаём новый объект 
`LoginResponse`, используя сгенерированный токен доступа, и отправляем его 
клиенту.

```go
// Login - это унарный RPC для входа пользователя в систему
func (server *AuthServer) Login(ctx context.Context, req *pb.LoginRequest) (*pb.LoginResponse, error) {
    user, err := server.userStore.Find(req.GetUsername())
    if err != nil {
        return nil, status.Errorf(codes.Internal, "cannot find user: %v", err)
    }
    
    if user == nil || !user.IsCorrectPassword(req.GetPassword()) {
        return nil, status.Errorf(codes.NotFound, "incorrect username/password")
    }
    
    token, err := server.jwtManager.Generate(user)
    if err != nil {
        return nil, status.Errorf(codes.Internal, "cannot generate access token")
    }
    
    res := &pb.LoginResponse{AccessToken: token}
    return res, nil
}
```

## Добавляем сервис аутентификации к gRPC серверу
Давайте добавим эту новый сервис аутентификации к gRPC серверу. Сначала нам
нужно создать новое хранилище пользователей в памяти. Затем инициализируем
новый JWT менеджер с секретным ключом и временем действия токена.

```go
func main() {
    // ...
    
    userStore := service.NewInMemoryUserStore()
    jwtManager := service.NewJWTManager(secretKey, tokenDuration)
    
    // ...
}
```

Для простоты я задам их здесь в виде констант.

```go
const (
    secretKey = "secret"
    tokenDuration = 15 * time.Minute
)
```

На самом деле мы должны загружать их из переменных окружения или из файла 
конфигурации. Для демонстрационных целей установим время действия токена равным
15 минутам.

Затем нам нужно создать новый сервер аутентификации, используя `userStore` и
`jwtManager`. Мы вызываем `pb.RegisterAuthServiceServer`, чтобы добавить его
к gRPC серверу.

```go
func main() {
    // ...
    jwtManager := service.NewJWTManager(secretKey, tokenDuration)
    authServer := service.NewAuthServer(userStore, jwtManager)
    
    // ...
    
    grpcServer := grpc.NewServer(
        grpc.UnaryInterceptor(unaryInterceptor),
        grpc.StreamInterceptor(streamInterceptor),
    )
    
    pb.RegisterAuthServiceServer(grpcServer, authServer)
    // ...
}
```

## Генерируем пользователей
Чтобы протестировать новое API для входа в систему, мы должны добавить в неё
нескольких пользователей. Давайте сделаем это. Я напишу функцию, которая 
создаст пользователя по заданному имени, паролю и роли и сохранит его в 
хранилище.

```go
func createUser(userStore service.UserStore, username, password, role string) error {
    user, err := service.NewUser(username, password, role)
    if err != nil {
        return err
    }
    
    return userStore.Save(user)
}
```

Затем в этой функции `seedUsers()` я вызову `createUser()` 2 раза, чтобы 
создать одного пользователя с правами администратора и одного обычного 
пользователя. Пусть у них будет один и тот же пароль - `secret`.

```go
func seedUsers(userStore service.UserStore) error {
    err := createUser(userStore, "admin1", "secret", "admin")
    if err != nil {
        return err
    }
    return createUser(userStore, "user1", "secret", "user")
}
```

Теперь в функции `main` мы вызовем `seedUsers()` сразу после создания 
`userStore`.

```go
func main() {
    // ...
    
    userStore := service.NewInMemoryUserStore()
    err := seedUsers(userStore)
    if err != nil {
        log.Fatal("cannot seed users")
    }
    jwtManager := service.NewJWTManager(secretKey, tokenDuration)
    // ...
}
```

## Тестируем сервис аутентификации с помощью Evans CLI
Давайте протестируем его! Я запущу gRPC сервер. Затем используйте Evans для 
подключения к нему.

```shell
evans -r -p 8080
```

Когда мы вызываем `show service`, мы видим новый `AuthService` с `Login` RPC. 
Давайте выберем `AuthService`

```shell
service AuthService
```

и запустим `Login` RPC

```shell
call Login
```

Вводим имя пользователя `admin1`. Попробуем отправить неверный пароль

```shell
username (TYPE_STRING) => admin1
password (TYPE_STRING) => 1
command call: rpc error: code = NotFound desc = incorrect username/password
```

Сервер возвращает неверное имя пользователя/пароль, как и ожидалось. Давайте
снова вызовем `Login`, но в этот раз введём правильный пароль для `admin1`.

```shell
username (TYPE_STRING) => admin1
password (TYPE_STRING) => secret
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MTg0ODkyMjMsInVzZXJuYW1lIjoiYWRtaW4xIiwicm9sZSI6ImFkbWluIn0.Bxxo2193YtuxWXg4Id2r4YDZmRzYgzdkgj5NOxaB8Kw"
}
```

Запрос выполнен успешно и мы получили токен доступа. Скопируем этот токен и 
перейдём на сайт `jwt.io` в браузере. Вставьте токен в текстовое поле 
`Encoded`. Мы видим в поле `Decoded`, что заголовок содержит алгоритм `HS256`
и тип токена JWT. Полезная нагрузка содержит claims пользователя с временем 
действия токена, именем пользователя и ролью. Итак, RPC для входа пользователя
в систему работает отлично.

Тем не менее, мы ещё не написали соответствующий перехватчик для проверки 
токена. Итак, вернемся к коду и сделаем это.

## Реализуем серверный перехватчик аутентификации
Я создам новый файл `auth_interceptor.go` и определю внутри него новую 
структуру `AuthInterceptor`.

```go
package service

type AuthInterceptor struct {
    jwtManager *JWTManager
    accessibleRoles map[string][]string
}
```

Этот перехватчик будет содержать JWT менеджер и карту, определяющая 
для каждого RPC метода список ролей, которые могут получить к нему доступ. 
Таким образом, ключом карты будет полное название метода, а её значением — срез 
из названий ролей. Давайте напишем функцию `NewAuthInterceptor()` для создания
и возврата нового объекта-перехватчика аутентификации. `jwtManager` и карта
`accessibleRoles` будут входными параметрами этой функции.

```go
// NewAuthInterceptor returns a new auth interceptor
func NewAuthInterceptor(jwtManager *JWTManager, accessibleRoles map[string][]string) *AuthInterceptor {
    return &AuthInterceptor{jwtManager: jwtManager, accessibleRoles: accessibleRoles}
}
```

Теперь я добавлю новый метод `Unary()` к объекту-перехватчику аутентификации,
который создаст и вернет функцию для gRPC серверного унарного перехватчика.

```go
// Unary возвращает функцию серверного перехватчика для аутентификации и  
// авторизации унарной RPC
func (interceptor *AuthInterceptor) Unary() grpc.UnaryServerInterceptor {
	
}
```

Давайте откроем файл `main.go`, скопируем функцию `unaryInterceptor()`, 
которую мы написали ранее и вставим её в эту функцию `Unary()`. Эту
анонимную функцию нам и надо вернуть. 

```go
// Unary возвращает функцию серверного перехватчика для аутентификации и  
// авторизации унарной RPC
func (interceptor *AuthInterceptor) Unary() grpc.UnaryServerInterceptor {
    return func(
        ctx context.Context,
        req interface{},
        info *grpc.UnaryServerInfo,
        handler grpc.UnaryHandler,
    ) (interface{}, error) {
        log.Println("--> unary interceptor: ", info.FullMethod)
        return handler(ctx, req)
    }
}
```

Точно так же мы добавляем новый метод `Stream()`, который создаст и вернёт 
функцию для gRPC серверного потокового перехватчика. Скопируйте и вставьте 
функцию потокового перехватчика сюда из файла `main.go`.

```go
// Stream возвращает функцию серверного перехватчика для аутентификации и  
// авторизации потоковой RPC
func (interceptor *AuthInterceptor) Stream() grpc.StreamServerInterceptor {
    return func(
        srv interface{},
        stream grpc.ServerStream,
        info *grpc.StreamServerInfo,
        handler grpc.StreamHandler,
    ) error {
        log.Println("--> stream interceptor: ", info.FullMethod)
        return handler(srv, stream)
    }
}
```

Теперь в файле `main.go` мы должны создать новый объект-перехватчик, используя
`jwtManager` и карту `accessibleRoles`.

`cmd/server/main.go`
```go
func main() {
    // ...
    
    interceptor := service.NewAuthInterceptor(jwtManager, accessibleRoles())
    grpcServer := grpc.NewServer(
        grpc.UnaryInterceptor(interceptor.Unary()),
        grpc.StreamInterceptor(interceptor.Stream()),
    )
    // ...
}
```

Поскольку наш проект является учебным я просто напишу функцию, которая будет 
возвращать константную карту.

```go
func accessibleRoles() map[string][]string {
	
}
```

Затем в функцию `grpc.NewServer()` мы можем передать `interceptor.Unary()` и
`interceptor.Stream()`.

## Определяем доступные роли
Теперь для функции `accessibleRoles` нам нужно подготовить список RPC методов
и ролей, которые могут получить доступ к каждому из них. Чтобы получить полное
название RPC метода, давайте откроем терминал и выполним

```shell
make client
```

Тогда в логах сервера мы увидим полное название метода `CreateLaptop`
RPC: `/techschool_pcbook.LaptopService/CreateLaptop`. Давайте скопируем его.
Мы знаем, что все методы `LaptopService` будут начинаться одинаково, поэтому
я определю здесь константу. Затем мы создаём и возвращаем карту следующим 
образом.

```go
func accessibleRoles() map[string][]string {
    const laptopServicePath = "/techschool_pcbook.LaptopService/"
    
    return map[string][]string{
        laptopServicePath + "CreateLaptop": {"admin"},
        laptopServicePath + "UploadImage": {"admin"},
        laptopServicePath + "RateLaptop": {"admin", "user"},
    }
}
```

Первый метод — это `CreateLaptop`, который могут вызывать только пользователи
с правами `admin`. Метод `UploadImage` также доступен только для них. Метод
`RateLaptop` могут вызывать как пользователи с правами `admin`, так и `user`. 
Пусть `SearchLaptop` API доступна всем, даже не зарегистрированным 
пользователям. Идея такова: мы не добавляем в эту карту `SearchLaptop` или 
любые другие общедоступные RPC.

Итак, давайте вернемся к перехватчику аутентификации. Мы определим новую 
функцию `authorize()`, которая на вход будет принимать контекст и метод и 
возвращать ошибку, если запрос не авторизирован. 

```go
func (interceptor *AuthInterceptor) authorize(ctx context.Context, method string) error {

}
```

Затем в функции `Unary()` мы вызываем `interceptor.authorize()`, передавая
контекст и `info.FullMethod`. Если ошибка не равна `nil`, мы сразу выходим
из функции.

```go
// Unary возвращает функцию серверного перехватчика для аутентификации и  
// авторизации унарной RPC
func (interceptor *AuthInterceptor) Unary() grpc.UnaryServerInterceptor {
    return func(
        ctx context.Context,
        req interface{},
        info *grpc.UnaryServerInfo,
        handler grpc.UnaryHandler,
    ) (interface{}, error) {
        // ...
    
        err := interceptor.authorize(ctx, info.FullMethod)
        if err != nil {
            return nil, err
        }
    
        // ...
    }
}
```

Подобным образом, для функции `Stream()` мы вызываем `interceptor.authorize()`
с контекстом потока и `info.FullMethod` и сразу же выходим из неё, если 
возникла ошибка.

```go
// Stream возвращает функцию серверного перехватчика для аутентификации и  
// авторизации потоковой RPC
func (interceptor *AuthInterceptor) Stream() grpc.StreamServerInterceptor {
    return func(
        srv interface{},
        stream grpc.ServerStream,
        info *grpc.StreamServerInfo,
        handler grpc.StreamHandler,
    ) error {
        // ...
    
        err := interceptor.authorize(stream.Context(), info.FullMethod)
        if err != nil {
            return err
        }
    
        // ...
    }
}
```

## Реализуем функцию authorize
Теперь давайте реализуем функцию `authorize()`. Сначала мы получаем список 
ролей, которые могут получить доступ к конкретному RPC методу. Если его нет
в карту, это означает, что RPC публично доступен. Поэтому в этом случае мы 
просто возвращаем `nil`. В противном случае мы должны получить токен доступа
из контекста. Для этого мы используем пакет `grpc/metadata`. Мы вызываем
`metadata.FromIncomingContext`, чтобы получить метаданные запроса. Если их 
нет, то мы возвращаем ошибку с кодом состояния `Unauthenticated`. В противном
случае мы получаем значения из метаданных по ключу "authorization". Если он 
пуст, выдаём ошибку с кодом состояния `Unauthenticated`, потому что отсутствует 
токен. Иначе токен доступа должен храниться в первом элементе полученного 
массива. Затем вызываем `jwtManager.Verify`, чтобы проверить токен и извлечь
claims. Если возникла ошибка, это означает, что токен недействителен. Поэтому 
мы просто возвращаем клиенту код `Unauthenticated`. В противном случае мы 
перебираем в цикле `accessibleRoles` и проверяем может ли данный пользователь,
учитывая его роль, получить доступ к этой RPC. Если роль пользователя найдена
в списке, мы просто возвращаем `nil`. Если нет, мы возвращаем ошибку с кодом
состояния `PermissionDenied` и сообщение о том, что у пользователя нет прав
для доступа к этому RPC. На этом реализация серверного перехватчика 
авторизации завершена!

```go
func (interceptor *AuthInterceptor) authorize(ctx context.Context, method string) error {
    accessibleRoles, ok := interceptor.accessibleRoles[method]
    if !ok {
        // любой может получить доступ
        return nil
    }
    
    md, ok := metadata.FromIncomingContext(ctx)
    if !ok {
        return status.Errorf(codes.Unauthenticated, "metadata is not provided")
    }
    
    values := md["authorization"]
    if len(values) == 0 {
        return status.Errorf(codes.Unauthenticated, "authorization token is not provided")
    }
    
    accessToken := values[0]
    claims, err := interceptor.jwtManager.Verify(accessToken)
    if err != nil {
        return status.Errorf(codes.Unauthenticated, "access token is invalid: %v", err)
    }
    
    for _, role := range accessibleRoles {
        if role == claims.Role {
            return nil
        }
    }
    
    return status.Error(codes.PermissionDenied, "no permission to access this RPC")
}
```

Давайте протестируем его работу в терминале. Сначала запустите сервер

```shell
make server
```

и клиент.

```shell
make client
```

Мы получили ошибку с кодом `Unauthenticated`

```shell
2021/04/16 02:01:29 cannot create laptop: rpc error: code = Unauthenticated desc = authorization token is not provided
```

и текстом "authorization token is not provided". Это было ожидаемо, поскольку
мы ещё не вошли в систему и не добавили токен доступа к запросу на стороне 
клиента.

Итак, давайте сделаем это!

## Реализуем клиент сервиса аутентификации
Поскольку кода клиента становится всё больше, я создам отдельный пакет `client`
для него. Затем создам новый файл `auth_client.go` внутри этой папки. Давайте
определим структуру `AuthClient` для вызова сервиса аутентификации. Эта 
структура будет содержать поле `service` типа `AuthServiceClient`, который 
генерирует `protoc`, а также поля `username` и `password`, используемые для 
входа в систему и получения токена доступа.

```go
package client

// AuthClient - это клиент, вызывающий RPC для аутентификации
type AuthClient struct {
    service pb.AuthServiceClient
    username string
    password string
}
```

Как обычно, мы определяем функцию для создания и возврата нового объекта-клиента
для аутентификации. Она будет иметь три входных параметра: соединение с grpc
клиентом, имя пользователя и пароль. Сервис будет создан путём вызова функции
`pb.NewAuthServiceClient()` и передачи в неё соединения.

```go
// NewAuthClient возвращает новый клиент для аутентификации
func NewAuthClient(cc *grpc.ClientConn, username string, password string) *AuthClient {
    service := pb.NewAuthServiceClient(cc)
    return &AuthClient{service: service, username: username, password: password}
}
```

Затем мы пишем функцию `Login` для вызова `Login` RPC, чтобы получить токен 
доступа. Как и для других RPC, мы создаём контекст с таймаутом в 5 секунд,
вызываем `defer cancel()`. Затем инициализируем запрос на вход с 
предоставленным именем пользователя и паролем и вызываем `service.Login`, 
используя этот запрос. Если ошибка не равна `nil`, мы просто возвращаем её. В
противном случае мы выдаём полученный токен доступа вызывающей стороне. Клиент
для аутентификации готов.

```go
// Login осуществляет вход пользователя в систему и возвращает токен доступа
func (client *AuthClient) Login() (string, error) {
    ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
    defer cancel()
    
    req := &pb.LoginRequest{
        Username: client.username,
        Password: client.password,
    }
    
    res, err := client.service.Login(ctx, req)
    if err != nil {
        return "", err
    }
    
    return res.GetAccessToken(), nil
}
```

## Реализуем клиентский перехватчик
Теперь давайте научимся создавать клиентский перехватчик. Я создам новый файл
`auth_interceptor.go` в пакете `client`. Идея такова: мы будем перехватывать
все gRPC запросы и прикреплять к ним токен доступа (при необходимости) перед
вызовом сервера. По аналогии с тем, что мы делали на сервере я определю 
структуру `AuthInterceptor`. Она будет содержать объект-клиент для 
аутентификации, который будет использоваться для входа пользователя в систему,
карту, указывающую для какого метода требуется аутентификация, и последний 
полученный токен доступа. Что значит "последний"? На самом деле мы создадим
отдельную горутину, которая будет периодически вызывать API для входа в 
систему, чтобы получить новый токен доступа до истечения срока действия 
текущего.

```go
package client

// AuthInterceptor - это клиентский перехватчик для аутентификации
type AuthInterceptor struct {
    authClient *AuthClient
    authMethods map[string]bool
    accessToken string
}
```

Таким образом в функции `NewAuthInterceptor()`, помимо клиента и методов 
аутентификации, там также нужен параметр, определяющий время обновления токена.
Оно укажет насколько часто нам нужно будет вызывать API для входа в систему, 
чтобы получить новый токен. В этой функции сначала мы создаём новый 
объект-перехватчик. Затем обращаемся к `scheduleRefreshToken`, чтобы 
запланировать обновление токена доступа и передаём в неё время после которого 
необходимо осуществить обновление. Если возникает ошибка, просто верните её, 
иначе возвращаем перехватчик.

```go
// NewAuthInterceptor возвращает новый перехватчик для аутентификации
func NewAuthInterceptor(
    authClient *AuthClient,
    authMethods map[string]bool,
    refreshDuration time.Duration,
) (*AuthInterceptor, error) {
    interceptor := &AuthInterceptor{
        authClient: authClient,
        authMethods: authMethods,
    }
    
    err := interceptor.scheduleRefreshToken(refreshDuration)
    if err != nil {
        return nil, err
    }
    
    return interceptor, nil
}
```

## Настраиваем обновление токена по графику
Теперь давайте реализуем функцию `scheduleRefreshToken`. Но сначала нам нужна
функция для обновления токена без планирования. В этой функции, мы просто 
используем клиент аутентификации для входа в систему. После возврата токена мы 
просто сохраняем его в поле `interceptor.accessToken`. Давайте добавим сюда 
простой лог, чтобы было проще зафиксировать факт обновления токена, и затем 
вернём `nil`.

```go
func (interceptor *AuthInterceptor) refreshToken() error {
    accessToken, err := interceptor.authClient.Login()
    if err != nil {
        return err
    }
    
    interceptor.accessToken = accessToken
    log.Printf("token refreshed: %v", accessToken)
    
    return nil
}
```

Теперь в функции `scheduleRefreshToken()` мы должны убедиться, что первый вызов 
`refreshToken()` прошёл успешно, то есть правильный токен всегда доступен для
использования. После этого мы запускаем новую горутину. Здесь я использую
переменную `wait`, в которой сохраняю промежуток времени до следующего 
обновления токена. Затем в бесконечном цикле вызываем `time.Sleep`, чтобы 
организовать задержку, и, дождавшись необходимого момента времени, обращаемся к 
`interceptor.refreshToken()`. Если возникла ошибка, уменьшим период ожидания
до, скажем, 1 секунды, прежде чем повторить попытку. Если все прошло без 
ошибок, то нам определенно стоит дождаться следующего периода обновления. Мы 
реализовали функцию, осуществляющую периодическое обновление токена. 

```go
func (interceptor *AuthInterceptor) scheduleRefreshToken(refreshDuration time.Duration) error {
    err := interceptor.refreshToken()
    if err != nil {
        return err
    }
    
    go func() {
        wait := refreshDuration
        for {
            time.Sleep(wait)
            err := interceptor.refreshToken()
            if err != nil {
                wait = time.Second
            } else {
                wait = refreshDuration
            }
        }
    }()
    
    return nil
}
```

## Присоединяем токен к контексту запроса
Далее следует важная часть: добавление перехватчиков для присоединения токена
к контексту запроса. Как и на стороне сервера, на этот раз мы определяем 
функцию `Unary()`, которая возвращает gRPC клиентский унарный перехватчик. 
Давайте посмотрим на этот фрагмент с `grpc.UnaryClientInterceptor`, чтобы 
увидеть как должна выглядеть функция перехватчика.

```go
type UnaryClientInterceptor func(ctx context.Context, method string, req, reply interface{}, cc *ClientConn, invoker UnaryInvoker, opts ...CallOption) error
```

Я скопирую её сигнатуру и вставлю в файл `auth_interceptor.go`.

```go
func (interceptor *AuthInterceptor) Unary() grpc.UnaryClientInterceptor {
    type UnaryClientInterceptor func(ctx context.Context, method string, req, reply interface{}, cc *ClientConn, invoker UnaryInvoker, opts ...CallOption) error
}
```

Эта функция, которую мы должны вернуть. Я немного переформатирую её и добавлю 
отсутствующий пакет `grpc`.

```go
// Unary возвращает клиентский перехватчик для аутентификации унарного RPC
func (interceptor *AuthInterceptor) Unary() grpc.UnaryClientInterceptor {
    return func(
        ctx context.Context,
        method string,
        req, reply interface{},
        cc *grpc.ClientConn,
        invoker grpc.UnaryInvoker,
        opts ...grpc.CallOption,
    ) error {
    
    }
}
```

Теперь внутри этой функции-перехватчика давайте добавим простое сообщение в 
лог с названием вызывающего метода. Затем проверим нужна ли для этого метода
аутентификация. Если "да", то мы должны присоединить токен доступа к контексту 
перед выполнением фактического RPC.

```go
func (interceptor *AuthInterceptor) Unary() grpc.UnaryClientInterceptor {
    return func(
        ctx context.Context,
        method string,
        req, reply interface{},
        cc *grpc.ClientConn,
        invoker grpc.UnaryInvoker,
        opts ...grpc.CallOption,
    ) error {
        log.Printf("--> unary interceptor: %s", method)
        
        if interceptor.authMethods[method] {
            return invoker(interceptor.attachToken(ctx), method, req, reply, cc, opts...)
        }
    }
}
```

Итак, я определю новую функцию для присоединения токена к входному контексту и
возврата результата. В этой функции мы просто используем 
`metadata.AppendToOutgoingContext()`, передавая входной контекст вместе с 
ключом "authorization" и значением токена доступа. Убедитесь, что ключ-строка 
"authorization" совпадает с той, которую мы использовали на стороне сервера.

```go
func (interceptor *AuthInterceptor) attachToken(ctx context.Context) context.Context {
    return metadata.AppendToOutgoingContext(ctx, "authorization", interceptor.accessToken)
}
```

Теперь вернемся к нашему унарному перехватчику. Если метод не требует 
аутентификации, то ничего делать не нужно, просто выполняем RPC с 
первоначальным контекстом.

```go
// Unary возвращает клиентский перехватчик для аутентификации унарного RPC
func (interceptor *AuthInterceptor) Unary() grpc.UnaryClientInterceptor {
    return func(
        ctx context.Context,
        method string,
        req, reply interface{},
        cc *grpc.ClientConn,
        invoker grpc.UnaryInvoker,
        opts ...grpc.CallOption,
    ) error {
        // ...
        
        return invoker(ctx, method, req, reply, cc, opts...)
    }
}
```

Аналогичным образом можно реализовать потоковый перехватчик. Скопируйте 
сигнатуру функции `grpc.StreamClientInterceptor`, вставьте её в файл 
`auth_interceptor.go`, допишите `return`, отформатируйте код и добавьте 
отсутствующее название пакета `grpc`.

```go
// Stream возвращает клиентский перехватчик для аутентификации потокового RPC
func (interceptor *AuthInterceptor) Stream() grpc.StreamClientInterceptor {
    return func(
        ctx context.Context,
        desc *grpc.StreamDesc,
        cc *grpc.ClientConn,
        method string,
        streamer grpc.Streamer,
        opts ...grpc.CallOption,
    ) (grpc.ClientStream, error) {
        
    }
}
```

Затем пишем сообщение в лог, проверяем требует ли метод аутентификации. Если 
"да", вызываем `streamer` с контекстом, к которому был присоединен токен 
доступа или просто вызовите  `streamer` с первоначальным. На этом мы закончили
реализацию клиентского перехватчика для аутентификации.

```go
// Stream возвращает клиентский перехватчик для аутентификации потокового RPC
func (interceptor *AuthInterceptor) Stream() grpc.StreamClientInterceptor {
    return func(
        ctx context.Context,
        desc *grpc.StreamDesc,
        cc *grpc.ClientConn,
        method string,
        streamer grpc.Streamer,
        opts ...grpc.CallOption,
    ) (grpc.ClientStream, error) {
        log.Printf("--> stream interceptor: %s", method)
    
        if interceptor.authMethods[method] {
            return streamer(interceptor.attachToken(ctx), desc, cc, method, opts...)
        }
        
        return streamer(ctx, desc, cc, method, opts...)
    }
}
```

## Проводим рефакторинг gRPC клиента
Теперь откроем файл `cmd/client/main.go`. Прежде чем добавить перехватчики к 
клиенту я осуществлю небольшой рефакторинг. Давайте вынесем эту функции
`createLaptop`, `searchLaptop`, `uploadImage`, `rateLaptop` в отдельный файл
внутри нового пакета `client`. В этом файле давайте определим структуру
`LaptopClient`.

```go
// LaptopClient - это клиент для вызова RPC сервисов, осуществляющих операции с ноутбуками
type LaptopClient struct {
    service pb.LaptopServiceClient
}
```

Он будет состоять из объекта `LaptopServiceClient`. Затем, как обычно, мы 
определяем функцию для создания нового объекта-клиента. Он принимает на вход
клиентское соединение и используется его для создания нового клиента, 
осуществляющего операции с ноутбуками.

```go
// NewLaptopClient возвращает новый клиент для работы с ноутбуками
func NewLaptopClient(cc *grpc.ClientConn) *LaptopClient {
    service := pb.NewLaptopServiceClient(cc)
    return &LaptopClient{
        service: service,
    }
}
```

Теперь мы должны превратить функцию `createLaptop` в метод объекта 
`LaptopClient` и сделать его публичным, сделав первую букву `C` заглавной. 
Также добавьте `.service`, чтобы исправить ошибку.

```go
// Create Laptop вызывает RPC для создания ноутбука
func (laptopClient *LaptopClient) CreateLaptop(laptop *pb.Laptop) {
    // ...
    
    res, err := laptopClient.service.CreateLaptop(ctx, req)
}
```

Затем осуществляем аналогичные операции для функции `searchLaptop`, 
`uploadImage` и наконец `rateLaptop`.

```go
// SearchLaptop вызывает RPC для поиска ноутбуков
func (laptopClient *LaptopClient) SearchLaptop(filter *pb.Filter) {
    // ...
    stream, err := laptopClient.service.SearchLaptop(ctx, req)
}

// UploadImage вызывает RPC для загрузки изображений
func (laptopClient *LaptopClient) UploadImage(laptopID string, imagePath string) {
    // ...
    
    stream, err := laptopClient.service.UploadImage(ctx)
}

// RateLaptop вызывает RPC для подсчета рейтинга ноутбука
func (laptopClient *LaptopClient) RateLaptop(laptopIDs []string, scores []float64) error {
    // ...
    
    stream, err := laptopClient.service.RateLaptop(ctx)
}
```

Всё готово. Теперь давайте вернёмся к файлу `cmd/client/main.go`. Мы должны 
изменить `pb.LaptopServiceClient` на `client.LaptopClient`, затем вызвать
`laptopClient.CreateLaptop`. То же самое делаем для всех остальных функций 
тестирующих работу сервера.

```go
func testCreateLaptop(laptopClient *client.LaptopClient) {
    laptopClient.CreateLaptop(sample.NewLaptop())
}

func testUploadImage(laptopClient *client.LaptopClient) {
    laptop := sample.NewLaptop()
    laptopClient.CreateLaptop(laptop)
    laptopClient.UploadImage(laptop.GetId(), "tmp/laptop.jpg")
}

func testSearchLaptop(laptopClient *client.LaptopClient) {
    for i := 0; i < 10; i++ {
        laptopClient.CreateLaptop(sample.NewLaptop())
    }
    
    // ...
    
    laptopClient.SearchLaptop(filter)
}

func testRateLaptop(laptopClient *client.LaptopClient) {
    // ...
	
    for i := 0; i < n; i++ {
        // ...
        laptopClient.CreateLaptop(laptop)
    }

    for {
    	// ...
    	
        err := laptopClient.RateLaptop(laptopIDs, scores)
        
        // ...
    }
}
```

## Добавляем перехватчик аутентификации клиенту
Теперь в функции `main` мы должны вызвать `client.NewLaptopClient()` и 
передать в него gRPC соединение. Здесь не всё так просто — нам нужно 
отдельное соединение для клиента сервиса аутентификации `authClient`, так как 
он будет использоваться для создания перехватчика аутентификации 
`interceptor`. `interceptor` в свою очередь будет применяться для инициализации
другого соединения с клиентом, осуществляющим работу с ноутбуками. Поэтому я 
изменил название соединения на `cc1` и создал новый клиент аутентификации с 
помощью него.

```go
func main() {
    // ...
    
    cc1, err := grpc.Dial(*serverAddress, grpc.WithInsecure())
    // ...
    
    authClient := client.NewAuthClient(cc1, username, password)
    interceptor, err := client.NewAuthInterceptor(authClient, authMethods(), refreshDuration)
}
```

Для упрощения я задам username и password в виде констант.

```go
const (
    username = "admin1"
    password = "secret"
)
```

Затем давайте создадим новый перехватчик с клиентом сервиса аутентификации. 
Период обновления также будет константой. Пусть в нашем случае мы будем 
обновлять токен каждые 30 секунд.

```go
const (
    // ...
    refreshDuration = 30 * time.Second
)
```

Нам также нужна функция для определения списка методов, требующих 
аутентификации. Я просто скопирую и вставлю их из серверного перехватчика. 
Изменим `map[string][]string` на `map[string]bool`, а все значения на `true`.

```go
func authMethods() map[string]bool {
    const laptopServicePath = "/techschool_pcbook.LaptopService/"
    
    return map[string]bool{
        laptopServicePath + "CreateLaptop": true,
        laptopServicePath + "UploadImage":  true,
        laptopServicePath + "Ratelaptop":   true,
    }
}
```

Отлично, вернемся к функции `main`. Если во время вызова 
`client.NewAuthInterceptor` возникла ошибка, мы пишем её в лог и аварийно 
завершаем работу. Если нет, то пытаемся создать другое соединение, но в этот
раз с двумя дополнительными параметрами: унарным и потоковым перехватчиком. 
Затем мы меняем здесь `client.NewLaptopClient(cc2)` название этого соединения 
на `cc2`. Реализация клиента завершена.

```go
func main() {
    // ...
    if err != nil {
        log.Fatal("cannot create auth interceptor: ", err)
    }
    
    cc2, err := grpc.Dial(
        *serverAddress,
        grpc.WithInsecure(),
        grpc.WithUnaryInterceptor(interceptor.Unary()),
        grpc.WithStreamInterceptor(interceptor.Stream()),
    )
    if err != nil {
        log.Fatal("cannot dial server: ", err)
    }
    
    laptopClient := client.NewLaptopClient(cc2)
    testRateLaptop(laptopClient)
}
```

## Тестируем клиентский перехватчик аутентификации
Откроем терминал и протестируем работу. Сначала запустите сервер

```shell
make server
```

и клиент 

```shell
make client
```

Появилось странное сообщение: "client is up to date". Оно связано с тем, что
команда "make" спутала нашу папку `client` с командой `client`. Чтобы исправить
это, добавим её в список PHONY файла `Makefile`. Давайте я добавлю все эти
команды в список PHONY.

```makefile
gen:
	protoc --proto_path=proto --go_out=pb --go-grpc_out=pb proto/*.proto
clean:
	rm pb/*.go
server:
	go run cmd/server/main.go -port 8080
client:
	go run cmd/client/main.go -address 0.0.0.0:8080
test:
	go test -cover -race ./...

.PHONY: gen clean server client test
```

Вернитесь в терминал и снова выполните `make client`. На этот раз мы увидим
сообщения "token refreshed" и "unary interceptor" в логах, а также информацию
о том, что было успешно создано 3 ноутбука. 

```shell
2021/04/16 19:10:28 token refreshed: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MTg1NzUwMjgsInVzZXJuYW1lIjoiYWRtaW4xIiwicm9sZSI6ImFkbWluIn0.gQJ71LrEqYggkksDAXhpzpyMs7sjcOSrgft-H23T47w
2021/04/16 19:10:28 created laptop with id: 7f2d7032-4a62-4e0b-9f63-217bf53afc94
2021/04/16 19:10:28 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/04/16 19:10:28 created laptop with id: 26dc6266-cd4e-4dff-8f37-62ab2d20e58f
2021/04/16 19:10:28 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/04/16 19:10:28 created laptop with id: 7b1dcd35-5c32-4a1a-b565-3de4c87ef83f
```

Давайте введём "y", чтобы оценить их. Операция прошла успешно, и мы видим 
сообщение в логе от потокового перехватчика. 

```shell
2021/04/16 19:13:04 --> stream interceptor: /techschool_pcbook.LaptopService/RateLaptop
```

На стороне сервера также выводятся сообщения от унарных и потоковых 
перехватчиков.

```shell
2021/04/16 19:10:28 --> unary interceptor:  /techschool_pcbook.LaptopService/CreateLaptop
```
```shell
2021/04/16 19:13:04 --> stream interceptor:  /techschool_pcbook.LaptopService/RateLaptop
```

Через какое-то время на стороне клиента мы увидим, что токен обновился. И, 
наконец, давайте изменим имя пользователя на `user1`

And after a while, on the client side, we will see that the token is getting 
refreshed as well. Before we finish let's change this username to `user1`

```go
const (
    username = "user1"
    password = "secret"
)
```

и посмотрим, что произойдёт. Как и следовало ожидать, мы получили ошибку с 
отказом в доступе

```shell
2021/04/16 19:18:55 cannot create laptop: rpc error: code = PermissionDenied desc = no permission to access this RPC
```

так как только пользователь с правами администратора может вызывать 
`CreateLaptop` API. Таким образом, всё работает как надо. Превосходно! На этом
закончим сегодняшнюю лекцию. Надеюсь вам она понравилась. Спасибо за время, 
потраченное на чтение, и до новых встреч.