# Безопасное gRPC подключение с помощью SSL/TLS - Golang
На предыдущей лекции мы узнали как использовать gRPC перехватчики для 
аутентификации пользователей. Тем не менее, API, которое мы применяли для
входа пользователей в систему было не безопасным, то есть имя пользователя и
пароль посылались в виде открытого текста и могли быть считаны кем угодно, кто
прослушивает канал обмена данными между клиентом и сервером. Итак, сегодня мы
узнаем как защитить gRPC подключение с помощью TLS. Если вы не читали [мою
лекцию лекцию о SSL/TLS](SSL_TLS_lecture_rus.md), я настоятельно рекомендую 
вам сначала прочитать её, чтобы лучше понимать TLS, прежде чем продолжить.

## Типы gRPC подключений
Существует три типа gRPC подключений. Первый — это небезопасное подключение,
которое мы используем с самого начала этого курса. В нём все данные, 
передаваемые между клиентом и сервером, не шифруются. Поэтому, пожалуйста, 
не применяйте его для продакшена! Второй тип — соединение, защищенное TLS на
стороне сервера. В этом случае все данные зашифрованы, но только сервер должен 
предоставить свой сертификат TLS клиенту. Вы можете использовать этот тип 
соединения, если серверу неважно какой клиент вызывает его API. Третий и самый 
надежный тип — это подключение, защищенное двухсторонним TLS. Мы применяем 
его, когда серверу также необходимо проверить, кто вызывает его сервисы. В 
этом случае и клиент, и сервер должны предоставить свои TLS сертификаты друг
другу. На этой лекции мы узнаем как реализовать TLS на стороне сервера, так и 
двухсторонний TLS, используя Golang. Итак, начнём!

## Генерируем TLS сертификаты
Для начала нам понадобится скрипт для генерации TLS сертификатов. Создайте
файлы `gen.sh` и `server-ext.cnf` в папке `cert`. Я рекомендую вам прочитать
лекцию о том как [создать и подписать TLS сертификат](create_SSL_TLS_certificates_rus.md),
чтобы понять, как работает этот скрипт.

`cert/gen.sh`
```shell
rm *.pem

# 1. Генерируем приватный ключ CA и самоподписанный сертификат
openssl req -x509 -newkey rsa:4096 -days 365 -nodes -keyout ca-key.pem -out ca-cert.pem -subj "/C=FR/ST=Occitanie/L=Toulouse/O=Tech School/OU=Education/CN=*.techschool.guru/emailAddress=techschool.guru@gmail.com"

echo "CA's self-signed certificate"
openssl x509 -in ca-cert.pem -noout -text

# 2. Генерируем приватный ключ веб-сервера и запрос на подпись сертификата (CSR)
openssl req -newkey rsa:4096 -nodes -keyout server-key.pem -out server-req.pem -subj "/C=FR/ST=Ile de France/L=Paris/O=PC Book/OU=Computer/CN=*.pcbook.com/emailAddress=pcbook@gmail.com"

# 3. Используем приватный ключ CA, чтобы подписать CSR веб-сервера и получить обратно подписанный сертификат 
openssl x509 -req -in server-req.pem -days 60 -CA ca-cert.pem -CAkey ca-key.pem -CAcreateserial -out server-cert.pem -extfile server-ext.cnf

echo "Server's signed certificate"
openssl x509 -in server-cert.pem -noout -text
```
`cert/server.ext.cnf`
```
subjectAltName=DNS:*.pcbook.com,DNS:*.pcbook.org,IP:0.0.0.0
```

По сути этот скрипт состоит из трёх частей: во-первых, он генерирует приватный 
ключ CA и его самоподписанный сертификат, во-вторых, создаём приватный ключ
веб-сервера и CSR, и, в-третьих, использует приватный ключ для подписи CSR
веб-сервера и получения его сертификата. На этой лекции мы будем работать со 
следующими файлами: сертификат CA, приватный ключ CA, сертификат сервера и 
приватный ключ сервера. Я добавлю новую команду в `Makefile` для запуска 
скрипта генерации сертификата. Всё очень просто! Мы заходим в папку `cert`,
выполняем `gen.sh`, затем выходим из этой папки. Допишем эту команду `cert` в
список PHONY.

```makefile
# ...
cert:
	cd cert; ./gen.sh; cd ..

.PHONY: gen clean server client test cert
```

Теперь давайте попробуем запустить её из терминала. Выполните

```shell
make cert
```

Превосходно! Все файлы успешно созданы. Далее я покажу вам как защитить наше
gRPC подключение, используя TLS на стороне сервера.

## Реализуем TLS на стороне сервера
Давайте откроем файл `cmd/server/main.go`. Я добавлю функцию для загрузки TLS 
данных. Она будет возвращать объект `TransportCredentials` или
ошибку. Для TLS на стороне сервера нам понадобится сертификат сервера и
приватный ключ, поэтому мы используем `tls.LoadX509KeyPair` функцию для 
загрузки файлов `server-cert.pem` и `server-key.perm` из папки `cert`. Если
произошла ошибка, просто вернём её. В противном случае мы создаём из них 
идентификационные данные для передачи информации. Определим объект 
`tls.Config`, используя сертификат сервера, и мы присвоим полю `ClientAuth` 
значение `NoClientCert`, поскольку мы применяем TLS только на стороне 
сервера. Наконец, обращаемся к `credentials.NewTLS()`, передав этот `config` и 
возвращаем результат вызывающей стороне.

`cmd/server/main.go`
```go
func loadTLSCredentials() (credentials.TransportCredentials, error) {
    // Загружаем сертификат сервера и приватный ключ
    serverCert, err := tls.LoadX509KeyPair("cert/server-cert.pem", "cert/server-key.pem")
    if err != nil {
        return nil, err
    }
    
    // Создаём идентификационные данные и возвращаем их
    config := &tls.Config{
        Certificates: []tls.Certificate{serverCert},
        ClientAuth: tls.NoClientCert,
    }
    
    return credentials.NewTLS(config), nil
}
```

Функция `loadTLSCredentials()` реализована. В функции `main` мы вызываем 
эту функцию, чтобы получить TLS объект c идентификационными данными. Если 
возникает ошибка, мы просто добавляем сообщение в лог и аварийно завершаем 
работу программы. Иначе мы добавляем идентификационные данные TLS в gRPC
сервер, используя параметр `grpc.Creds()`. Это всё, что нужно модифицировать
на сервере.

`cmd/server/main.go`
```go
func main() {
    // ...
    
    tlsCredentials, err := loadTLSCredentials()
    if err != nil {
        log.Fatal("cannot load TLS credentials: ", err)
    }
    
    interceptor := service.NewAuthInterceptor(jwtManager, accessibleRoles())
    grpcServer := grpc.NewServer(
        grpc.Creds(tlsCredentials),
        grpc.UnaryInterceptor(interceptor.Unary()),
        grpc.StreamInterceptor(interceptor.Stream()),
    )
    
    // ...
}
```

Давайте запустим его в терминале.

```shell
make server
```

Сервер стартовал. Теперь если попробовать подключиться к нему из клиента, то

```shell
2021/05/05 19:45:00 cannot create auth interceptor: rpc error: code = Unavailable desc = connection closed
```

возникнет ошибка, поскольку мы ещё не включили TLS на стороне клиента. Давайте
сделаем это! По аналогии с тем, что мы делали на сервере, я также добавлю 
функцию для загрузки TLS данных из файлов. Но в этот раз нам нужно загрузить
только сертификат CA, подписавшего сертификат сервера. Дело в том, что клиенту
необходимо проверить подлинность сертификата, который он получает от сервера.
Таким образом, он может убедиться, что это действительно тот сервер, с которым
следует взаимодействовать. Итак, здесь мы загружаем файл `ca-cert.pem`, затем
создаём новый пул сертификатов x509 и добавляем PEM файл CA в этот пул. 
Наконец, мы создаём идентификационные данные и возвращаем их. Обратите 
внимание, что мы инициализируем только поле `RootCAs`, которое содержит
сертификат доверенного CA.

`cmd/client/main.go`
```go
func loadTLSCredentials() (credentials.TransportCredentials, error) {
    // Загружаем сертификат CA, подписавшего сертификат сервера
    pemServerCA, err := ioutil.ReadFile("cert/ca-cert.pem")
    if err != nil {
        return nil, err
    }
    
    certPool := x509.NewCertPool()
    if !certPool.AppendCertsFromPEM(pemServerCA) {
        return nil, fmt.Errorf("failed to add server CA's certificate")
    }
    
    // Создаём идентификационные данные и возвращаем их
    config := &tls.Config{
        RootCAs: certPool,
    }
    
    return credentials.NewTLS(config), nil
}
```

Теперь в функции `main` существует два соединения, которые всё ещё не 
безопасны. Нам нужно заменить их на безопасный TLS. Давайте вызовем 
`loadTLSCredentials()`, чтобы получить объект с идентификационными данными,
затем изменим вызов `grpc.WithInsecure()` на 
`grpc.WithTransportCredentials()` и передадим в него этот объект.

`cmd/client/main.go`
```go
func main() {
    // ...
    
    tlsCredentials, err := loadTLSCredentials()
    if err != nil {
        log.Fatal("cannot load TLS credentials: ", err)
    }
    
    cc1, err := grpc.Dial(*serverAddress, grpc.WithTransportCredentials(tlsCredentials))
    
    // ...
}
```

Аналогично и для этого подключения. Изменения, необходимые для работы клиента,
реализованы.

`cmd/client/main.go`
```go
func main() {
    // ...
	
	cc2, err := grpc.Dial(
        *serverAddress,
        grpc.WithTransportCredentials(tlsCredentials),
        grpc.WithUnaryInterceptor(interceptor.Unary()),
        grpc.WithStreamInterceptor(interceptor.Stream()),
    )
	
	// ...
}
```

Давайте протестируем их! В этот раз запросы должны успешно отправиться на 
сервер. Отлично!

## Альтернативные имена субъектов (Subject Alternative Name, SAN)
Хочу показать вам ещё кое-что. Помните, что при разработке на localhost важно
добавить к сертификату `IP:0.0.0.0` в качестве расширения альтернативного 
имени субъекта (SAN). Посмотрим что произойдёт, если мы удалим его из
файла конфигурации, затем заново сгенерируем сертификаты и перезапустим сервер
и клиент.

```shell
2021/05/05 20:37:43 cannot create auth interceptor: rpc error: code = Unavailable desc = connection error: desc = "transport: authentication handshake failed: x509: cannot validate certificate for 0.0.0.0 because it doesn't contain any IP SANs"
```

Как видите, произошла ошибка, указывающая, что TLS рукопожатия не произошло,
так как невозможно проверить сертификат для 0.0.0.0, поскольку SAN не содержит
этот IP адрес. На продакшене такого не будет, поскольку мы там используем 
доменные имена. Итак, теперь вы знаете как включить TLS на стороне сервера для 
вашего gRPC подключения. Давайте узнаем как создать двухсторонний TLS!

## Реализация двухстороннего TLS
На данный момент сервер уже поделился своим сертификатом с клиентом. Для 
двухстороннего TLS, клиент также должен отправить свой сертификат серверу. 
Итак, теперь давайте обновим этот скрипт, чтобы создать и подписать сертификат 
для клиента. Давайте для этого курса будем использовать один и тот же CA для 
подписи сертификатов сервера и клиента. На самом деле может существовать 
множество клиентов с различными сертификатами, подписанными разными CA.

`cert/client-ext.cnf`
```
subjectAltName=DNS:*.pcclient.com,IP:0.0.0.0
```

`cert/gen.sh`
```sh
# ...

# 4. Генерируем приватный ключ клиента и запрос на подпись сертификата (CSR)
openssl req -newkey rsa:4096 -nodes -keyout client-key.pem -out client-req.pem -subj "/C=FR/ST=Alsace/L=Strasbourg/O=PC Client/OU=Computer/CN=*.pcclient.com/emailAddress=pcclient@gmail.com"

# 5. Используем приватный ключ CA для подписи CSR клиента и получения обратно подписанного сертификата
openssl x509 -req -in client-req.pem -days 60 -CA ca-cert.pem -CAkey ca-key.pem -CAcreateserial -out client-cert.pem -extfile client-ext.cnf

echo "Client's signed certificate"
openssl x509 -in client-cert.pem -noout -text
```

Давайте выполним

```shell
make cert
```

чтобы повторно сгенерировать сертификаты. После этого в папке `cert` появится
сертификат клиента и приватный ключ. Для включения двухстороннего TLS на 
стороне сервера нам нужно также изменить значение поля `ClientAuth` на
`RequireAndVerifyClientCert`. Также нужно предоставить список сертификатов 
доверенного CA, который подписывает сертификаты наших клиентов.

`cmd/server/main.go`
```go
func loadTLSCredentials() (credentials.TransportCredentials, error) {
    // ...
    
    // Создаём идентификационные данные и возвращаем их
    config := &tls.Config{
        Certificates: []tls.Certificate{serverCert},
        ClientAuth: tls.RequireAndVerifyClientCert,
        ClientCAs: certPool,
    }
    
    // ...
}
```

В нашем случае у нас есть только один CA, который подписывает сертификаты как 
сервера, так и клиента. Таким образом, мы можем просто скопировать код, который
написали для клиента, чтобы загрузить сертификат CA и создать новый пул 
сертификатов. Затем достаточно изменить названия переменных и сообщения об 
ошибках, чтобы отразить тот факт, что это должен быть CA, который подписывает
сертификат клиента. Это все изменения, которые надо внести в сервер.

`cmd/server/main.go`
```go
func loadTLSCredentials() (credentials.TransportCredentials, error) {
    // Загружаем сертификат CA, подписавшего сертификат клиента
    pemClientCA, err := ioutil.ReadFile("cert/ca-cert.pem")
    if err != nil {
        return nil, err
    }
    
    certPool := x509.NewCertPool()
    if !certPool.AppendCertsFromPEM(pemClientCA) {
        return nil, fmt.Errorf("failed to add client CA's certificate")
    }
    
    // Загружаем сертификат сервера и приватный ключ
    serverCert, err := tls.LoadX509KeyPair("cert/server-cert.pem", "cert/server-key.pem")
    if err != nil {
        return nil, err
    }

    // ...
}
```

Давайте запустим его в терминале.

```shell
make server
```

Теперь если мы подключим текущий клиент к этому новому серверу, то возникнет
ошибка, поскольку сервер теперь также требует, чтобы клиент отправил свой
сертификат.

```shell
2021/05/06 19:32:07 cannot create auth interceptor: rpc error: code = Unavailable desc
```

Давайте перейдём в код клиента, чтобы исправить это. Я просто скопирую код 
загрузки сертификата на стороне сервера и заменю названия файлов на 
`client-cert.pem` и `client-key.pem`. Затем мы должны добавить сертификат 
клиента в эту конфигурацию TLS, установив поле `Certificates`, точно так же,
как мы сделали на стороне сервера.

`cmd/client/main.go`
```go
func loadTLSCredentials() (credentials.TransportCredentials, error) {
    // ...

    // Load client's certificate and private key
    clientCert, err := tls.LoadX509KeyPair("cert/client-cert.pem", "cert/client-key.pem")
    if err != nil {
        return nil, err
    }
    // Create the credentials and return it
    config := &tls.Config{
        Certificates: []tls.Certificate{clientCert},
        RootCAs: certPool,
    }
    
    // ...
}
```

Теперь, если мы повторно запустим клиент, все запросы успешно выполняться.
Превосходно!

## Шифрование закрытого ключа
И последнее о чём хотел бы сказать, прежде чем мы закончим: используемые 
приватные ключи клиента и сервера не зашифрованы. Это связано с тем, что при 
их создании был указан ключ `-nodes`. Если мы удалим его и выполним 

```sh
# ...

# 2. Генерируем приватный ключ веб-сервера и запрос на подпись сертификата (CSR)
openssl req -newkey rsa:4096 -keyout server-key.pem -out server-req.pem -subj "/C=FR/ST=Ile de France/L=Paris/O=PC Book/OU=Computer/CN=*.pcbook.com/emailAddress=pcbook@example.com"

# ...
```

```shell
make cert
```

то необходимо будет ввести пароль для шифрования приватного ключа сервера, а
сгенерированный ключ будет зашифрован как видно из названия.

`cert/server-key.pem`
```
-----BEGIN ENCRYPTED PRIVATE KEY-----
```

Если мы попытаемся запустить сервер с этим ключом, то получим ошибку:

```shell
2021/05/06 20:13:08 cannot load TLS credentials: tls: failed to parse private key
```

Она возникла, поскольку ключ зашифрован. Мы можем добавить необходимый код для
расшифровки, но на мой взгляд, в конце концов, нам всё равно нужно будет 
защищать пароль, храня его в безопасном месте. Таким образом, всегда можно
поместить туда же наш незашифрованный приватный ключ. Например, если вы 
используете веб-сервис Amazon, то можете хранить ваш приватный ключ или любую
другую секретную информацию в зашифрованном виде с помощью AWS Secrets Manager
или воспользуйтесь HashiCorp's Vault для той же цели.

Это всё, о чём я хотел вам рассказать на этой лекции. Надеюсь, что 
предоставленная информация была полезной для вас. Большое спасибо за время, 
потраченное на чтение, и до скорой встречи на следующей лекции!