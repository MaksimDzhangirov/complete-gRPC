# Балансировка нагрузки gRPC сервиса с помощью NGINX
До сих пор мы многое узнали о том как разрабатывать бэкенд веб-сервисы с 
помощью gRPC. Когда дело доходит до развертывания, мы должны учитывать одну 
важную вещь — балансировку нагрузки. При крупномасштабном gRPC развертывании 
используется несколько идентичных бэкенд серверов и множество клиентов. 
Балансировка нагрузки используется для оптимального распределения нагрузки от 
клиентов между доступными серверами.

## Типы балансировки нагрузки
Существует два основных варианта gRPC балансировки нагрузки: на стороне сервера
и на стороне клиента. Решение, какой из них использовать, является основным 
архитектурным выбором.

### Балансировка нагрузки на стороне сервера
При балансировке нагрузки на стороне сервера клиент отправляет RPC на
балансировщик нагрузки или прокси, например Nginx или Envoy. Балансировщик 
нагрузки распределяет RPC вызов на один из доступных бэкенд серверов. Он также 
отслеживает нагрузку на каждый сервер и реализует алгоритмы справедливого 
распределения нагрузки. Сами клиенты не знают о бэкенд серверах.

![Балансировка-нагрузки-на-стороне-сервера](images/lecture17/server_side_load_balancing_rus.png)

**Рисунок 1** - Балансировка нагрузки на стороне сервера.

### Балансировка нагрузки на стороне клиента
При балансировке нагрузки на стороне клиента клиент знает о множестве
бэкенд серверов и выбирает один из них для каждого RPC. Обычно бэкенд сервера
регистрируются в инфраструктуре обнаружения сервисов, например Consul или 
Etcd. Затем клиент связывается с этой инфраструктурой, чтобы узнать адреса 
серверов. "Толстый" клиент сам реализует алгоритмы балансировки нагрузки. 
Например, при простой конфигурации, где нагрузка на сервер не учитывается, 
клиент может просто циклически переключаться между доступными серверами. Другой
подход заключается в использовании независимого внешнего балансировщика 
нагрузки, при котором "умные" алгоритмы распределения реализованы в нём. 
Клиенты посылают запросы на внешний балансировщик нагрузки, а он возвращает 
лучший(ие) сервер(а) для использования. Вся рутинная работа по отслеживанию 
состояния сервера, обнаружению сервисов и реализации алгоритма распределения 
нагрузки выполняется внешним балансировщиком. Давайте рассмотрим некоторые 
преимущества и недостатки этих подходов.

![Балансировка-нагрузки-на-стороне-клиента](images/lecture17/client_side_load_balancing_rus.png)

**Рисунок 2** - Балансировка нагрузки на стороне клиента.

### Преимущества и недостатки
Одним из преимуществ балансировки нагрузки на стороне сервера является простота 
реализации клиента. Все, что нужно знать клиенту, - это адрес прокси, писать
код не нужно. Этот подход работает даже для клиентов, к которым нет доверия, 
то есть gRPC служба может быть доступна всем, у кого есть выход в Интернет. 
Однако, недостатком является необходимость добавления ещё одного посредника 
перед вызовом. Все RPC должны пройти через прокси, прежде чем попасть на
бэкенд сервер. Таким образом, это приводит к увеличению сетевой задержки. 
Следовательно, такая балансировка нагрузки на стороне сервера подходит для
случаев, когда существует множество клиентов, возможно ненадёжных, имеющих
доступ в Интернет, которые хотят подключиться к нашим gRPC серверам в центре
обработки данных.

Балансировка нагрузки на стороне клиента, напротив, не добавляет никаких 
посредников перед вызовом, и, следовательно, приводит к более высокой 
производительности в целом. Тем не менее, реализация клиента теперь становится
сложной, особенно для случая с "толстым" клиентом. Такой подход следует 
использовать только для доверенных клиентов или нам понадобится внешний 
балансировщик нагрузки, защищающий доступ к сети, которой мы доверяем.
Из-за этого балансировка нагрузки на стороне клиента часто используется в 
системе с очень большим трафиком и в микросервисной архитектуре.

На этой лекции мы узнаем как настроить балансировку нагрузки на стороне 
сервера для наших gRPC сервисов, используя Nginx.

## Рефакторинг кода
Поскольку я собираюсь рассмотреть различные конфигурации Nginx, в которых TLS
может быть включен или отключен на сервере и клиенте, давайте обновим наш код,
чтобы он мог принимать и обрабатывать новый аргумент из командной строки.

### Обновляем сервер
Аргумент `tls` будет булевым флагом, который укажет: хотим ли мы включить TLS 
на нашем gRPC сервере или нет. Значение по умолчанию равно `false`. Давайте
также добавим его в лог, чтобы мы могли видеть его значение при запуске 
сервера. 

```go
func main() {
    port := flag.Int("port", 0, "the server port")
    enableTLS := flag.Bool("tls", false, "enable SSL/TLS")
    flag.Parse()
    log.Printf("start server on port %d, TLS = %t", *port, *enableTLS)
    
    // ...
}
```

Затем я перенесу перехватчики в отдельную переменную `serverOptions`. Теперь
мы проверим TLS флаг. Только в случае если он включен, то мы загружаем TLS 
данные и добавляем их в срез `serverOptions`. Наконец, мы просто передаём
`serverOptions` в вызов функции `grpc.NewServer()`. Это все, что касается 
сервера.

```go
func main() {
    // ...
	
    interceptor := service.NewAuthInterceptor(jwtManager, accessibleRoles())
    serverOptions := []grpc.ServerOption{
        grpc.UnaryInterceptor(interceptor.Unary()),
        grpc.StreamInterceptor(interceptor.Stream()),
    }
    
    if *enableTLS {
        tlsCredentials, err := loadTLSCredentials()
        if err != nil {
            log.Fatal("cannot load TLS credentials: ", err)
        }
    
        serverOptions = append(serverOptions, grpc.Creds(tlsCredentials))
    }
    
    grpcServer := grpc.NewServer(serverOptions...)
    
    // ...
}
```

Теперь сделаем то же самое для клиента.

### Обновляем клиент
Сначала мы добавим TLS флаг к аргументу командной строки. Обновим сообщение, 
записываемое в лог, включив в него это значение. Затем мы определим переменную
`transportOption` со значением по умолчанию `grpc.WithInsecure()`. Только когда
значение TLS флага равно `true` мы загружаем TLS данные из PEM файлов и меняем
`transportOption` на `grpc` с этими идентификационными данными для передачи 
информации. Наконец, мы передаём `transportOption` в gRPC подключения. Клиент
готов.

```go
func main() {
    serverAddress := flag.String("address", "", "the server address")
    enableTLS := flag.Bool("tls", false, "enable SSL/TLS")
    flag.Parse()
    log.Printf("dial server %s, TLS = %t", *serverAddress, *enableTLS)
    
    transportOption := grpc.WithInsecure()
    
    if *enableTLS {
        tlsCredentials, err := loadTLSCredentials()
        if err != nil {
            log.Fatal("cannot load TLS credentials: ", err)
        }
        
        transportOption = grpc.WithTransportCredentials(tlsCredentials)
    }
    
    cc1, err := grpc.Dial(*serverAddress, transportOption)
    
    // ...
    
    cc2, err := grpc.Dial(
        *serverAddress,
        transportOption,
        grpc.WithUnaryInterceptor(interceptor.Unary()),
        grpc.WithStreamInterceptor(interceptor.Stream()),
    )
    
    // ...
}
```

### Тестируем новый флаг
Нам нужно будет немного обновить Makefile, чтобы передать TLS флаг. Но сначала
попробуем запустить сервис без TLS. Пока что если мы выполним

```shell
make server
```

то увидим, что сервер работает с отключенным TLS.

```shell
2021/05/07 19:50:59 start server on port 8080, TLS = false
```

А если запустим

```shell
make client
```

Он также заработает без TLS и все вызовы успешно выполнены. Теперь, если мы
добавим этот `-tls` флаг к серверу и перезапустим его, TLS будет включен.

```makefile
# ...
server:
    go run cmd/server/main.go -port 8080 -tls
# ...
```

```shell
2021/05/07 19:54:33 start server on port 8080, TLS = true
```

Если мы выполним теперь

```shell
make client
```

то запросы не пройдут.

```shell
2021/05/07 19:55:30 cannot create auth interceptor: rpc error: code = Unavailable desc = connection closed
```

Мы должны также включить TLS на стороне клиента, добавив флаг `-tls` к команде
`make cleint`. И мы видим, что запросы вновь успешно отправляются. 

## Обновляем Makefile
Отлично, теперь TLS флаг работает так, как мы хотели.

```makefile
# ...
client:
    go run cmd/client/main.go -address 0.0.0.0:8080 -tls
# ...
```

Пришло время заняться балансировкой нагрузки на нашем сервере. Начнём с 
небезопасного gRPC подключения. Итак, я удалю эти флаги `-tls`, чтобы команды
`make server` и `make client` работали без TLS. Добавим ещё две команды `make`
для запуска двух экземпляров сервера на разных портах. Допустим, первый сервер
будет работать на порту 50051, а второй — на 50052.

```makefile
# ...
server1:
	go run cmd/server/main.go -port 50051
server2:
	go run cmd/server/main.go -port 50052
server:
	go run cmd/server/main.go -port 8080
client:
	go run cmd/client/main.go -address 0.0.0.0:8080
# ...
```

## Устанавливаем Nginx
Теперь нам нужно установить Nginx. Поскольку у меня ОС Mac, то я могу 
воспользоваться Homebrew:

```shell
brew install nginx
```

Вы также можете запустить Nginx, используя docker-compose файл.

```shell
docker-compose up -d
```

После установки Nginx, мы перейдём в папку `usr/local/etc/nginx`, чтобы 
настроить его. Давайте откроем файл `nginx.conf` с помощью Visual Studio Code.

```shell
cd /usr/local/etc/nginx
code nginx.conf
```

Настройки по умолчанию следующие:

```
#user  nobody;
worker_processes  1;

#error_log  logs/error.log;
#error_log  logs/error.log  notice;
#error_log  logs/error.log  info;

#pid        logs/nginx.pid;


events {
    worker_connections  1024;
}


http {
    include       mime.types;
    default_type  application/octet-stream;

    #log_format  main  '$remote_addr - $remote_user [$time_local] "$request" '
    #                  '$status $body_bytes_sent "$http_referer" '
    #                  '"$http_user_agent" "$http_x_forwarded_for"';

    #access_log  logs/access.log  main;

    sendfile        on;
    #tcp_nopush     on;

    #keepalive_timeout  0;
    keepalive_timeout  65;

    #gzip  on;

    server {
        listen       8080;
        server_name  localhost;

        #charset koi8-r;

        #access_log  logs/host.access.log  main;

        location / {
            root   html;
            index  index.html index.htm;
        }

        #error_page  404              /404.html;

        # redirect server error pages to the static page /50x.html
        #
        error_page   500 502 503 504  /50x.html;
        location = /50x.html {
            root   html;
        }

        # proxy the PHP scripts to Apache listening on 127.0.0.1:80
        #
        #location ~ \.php$ {
        #    proxy_pass   http://127.0.0.1;
        #}

        # pass the PHP scripts to FastCGI server listening on 127.0.0.1:9000
        #
        #location ~ \.php$ {
        #    root           html;
        #    fastcgi_pass   127.0.0.1:9000;
        #    fastcgi_index  index.php;
        #    fastcgi_param  SCRIPT_FILENAME  /scripts$fastcgi_script_name;
        #    include        fastcgi_params;
        #}

        # deny access to .htaccess files, if Apache's document root
        # concurs with nginx's one
        #
        #location ~ /\.ht {
        #    deny  all;
        #}
    }

    include servers/*;
}
```

Некоторые параметры в настройках для нас не важны в этом курсе, поэтому 
давайте обновим этот файл конфигурации.

## Настраиваем Nginx для небезопасного gRPC подключения
Во-первых, давайте удалим `#user  nobody;`, раскомментируем лог ведения ошибок,
удалим настройки, отвечающие за типы сообщений в логах и pid и пусть на данный
момент нам нужно всего 10 рабочих подключений.

Важно правильно настроить место для хранения файлов с логами ошибок и доступа.
В моём случае Homebrew уже создал папку `log` для Nginx 
(`/usr/local/var/log/nginx`), поэтому я просто укажу её как путь к логу
ошибок/доступа в настройках.

```
worker_processes  1;

error_log  /usr/local/var/log/nginx/error.log;

events {
    worker_connections  10;
}

http {
    access_log  /usr/local/var/log/nginx/access.log;

    server {
        listen       8080 http2;

        location / {
        }
    }
}
```

Теперь в блоке `server` записана команда `listen` для прослушивания входящих
запросов от клиента на порту `8080`. Это настройка по умолчанию для обычного
HTTP-сервера. Поскольку gRPC использует `HTTP/2`, мы должны добавить `http2` в
конце этой команды. Давайте удалим `server_name` и `charset`, поскольку нам 
они сейчас не нужны. Избавимся также от `access_log`, поскольку определили его
выше. Давайте удалим настройку для корневого HTML-файла по умолчанию и всего, 
что находится после блока `location`, так как сейчас они нас не интересуют.

Отлично, теперь мы хотим сбалансировать нагрузку от входящих запросов на наши
два сервера. Таким образом, мы должны определить `upstream` для них. Я назову
его `upstream pcbook_services`. Внутри этого блока мы используем ключевое слово
`server` для объявления экземпляра сервера. Первый работает на `localhost` 
порту `50051`, а второй — на порту `50052`. Чтобы направить все RPC вызовы в
`upstream`, в блоке `location` мы используем ключевое слово `grpc_pass`, за 
которым следует схема `grpc://` и название `upstream` - `pcbook_services`.

```
worker_processes  1;

error_log  /usr/local/var/log/nginx/error.log;

events {
    worker_connections  10;
}

http {
    access_log  /usr/local/var/log/nginx/access.log;

    upstream pcbook_services {
        server 0.0.0.0:50051;
        server 0.0.0.0:50052;
    }

    server {
        listen       8080 http2;

        location / {
            grpc_pass grpc://pcbook_services;
        }
    }
}
```

Вот и всё! Балансировка нагрузки для нашего небезопасного gRPC сервера
реализована.

Напишем команду `nginx` в терминале, чтобы запустить его. Мы можем проверить
работает он или нет, используя `ps` и `grep`.

```shell
ps aux | grep nginx
```

Давайте посмотрим что находится в папке `log`:

```shell
cd /usr/local/var/log/nginx
ls -l
```

Там должны быть два файла с логами: `access.log` и `error.log`. На данный 
момент они пустые, поскольку мы еще не отправили ни одного запроса.

Теперь давайте выполним `make server1`, чтобы запустить первый сервер на порту
`50051` с `TLS = false`. Затем в другой вкладке `make server2`, чтобы стартовал
второй - на порту `50052` с также отключенным TLS. Наконец, запустим 
`make client`.

Прекрасно. Все RPC запросы успешно выполнены.

```shell
2021/05/12 19:48:13 token refreshed: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MjA4MDU2OTMsInVzZXJuYW1lIjoiYWRtaW4xIiwicm9sZSI6ImFkbWluIn0.Tc6w-sU9-hUvB84MUYDSnydea4D2YuItZ6gc5HrNH0I
2021/05/12 19:48:13 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/12 19:48:13 created laptop with id: 437cd13f-312a-4cec-b014-7a66141d7681
2021/05/12 19:48:13 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/12 19:48:13 created laptop with id: 871432d9-8f65-4838-821b-8f2a3a6f2a4e
2021/05/12 19:48:13 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/12 19:48:13 created laptop with id: 58ec02c4-59d4-4873-8c13-3f858e99a108
```

Давайте проверим логи на наших серверах.

```shell
server1       | 2021/05/12 19:48:13 --> unary interceptor:  /techschool_pcbook.AuthService/Login
server2       | 2021/05/12 19:48:13 --> unary interceptor:  /techschool_pcbook.LaptopService/CreateLaptop
server2       | 2021/05/12 19:48:13 receive a create-laptop request with id: 437cd13f-312a-4cec-b014-7a66141d7681
server2       | 2021/05/12 19:48:13 saved laptop with id: 437cd13f-312a-4cec-b014-7a66141d7681
server1       | 2021/05/12 19:48:13 --> unary interceptor:  /techschool_pcbook.LaptopService/CreateLaptop
server1       | 2021/05/12 19:48:13 receive a create-laptop request with id: 871432d9-8f65-4838-821b-8f2a3a6f2a4e
server1       | 2021/05/12 19:48:13 saved laptop with id: 871432d9-8f65-4838-821b-8f2a3a6f2a4e
server2       | 2021/05/12 19:48:13 --> unary interceptor:  /techschool_pcbook.LaptopService/CreateLaptop
server2       | 2021/05/12 19:48:13 receive a create-laptop request with id: 58ec02c4-59d4-4873-8c13-3f858e99a108
server2       | 2021/05/12 19:48:13 saved laptop with id: 58ec02c4-59d4-4873-8c13-3f858e99a108
```

`server2` получил 2 запроса на создание ноутбука, а `server1` получил 1 запрос
на вход в систему и один запрос на создание ноутбука. Превосходно!

И через некоторое время, на этот сервер поступает ещё один запрос на вход в 
систему. Это происходит потому, что наш клиент всё ещё работает и периодически
вызывает Login RPC для обновления токена.

Я надеюсь, что вы помните код, который мы написали на [лекции, посвященной gRPC
перехватчикам](lecture15_rus.md).

Теперь давайте посмотрим на файл логов доступа nginx.

```
nginx  | 192.168.48.1 - - [12/May/2021:19:48:13 +0000] "POST /techschool_pcbook.AuthService/Login HTTP/2.0" 200 160 "-" "grpc-go/1.37.0"
nginx  | 192.168.48.1 - - [12/May/2021:19:48:13 +0000] "POST /techschool_pcbook.LaptopService/CreateLaptop HTTP/2.0" 200 43 "-" "grpc-go/1.37.0"
nginx  | 192.168.48.1 - - [12/May/2021:19:48:13 +0000] "POST /techschool_pcbook.LaptopService/CreateLaptop HTTP/2.0" 200 43 "-" "grpc-go/1.37.0"
nginx  | 192.168.48.1 - - [12/May/2021:19:48:13 +0000] "POST /techschool_pcbook.LaptopService/CreateLaptop HTTP/2.0" 200 43 "-" "grpc-go/1.37.0"
nginx  | 192.168.48.1 - - [12/May/2021:19:48:43 +0000] "POST /techschool_pcbook.AuthService/Login HTTP/2.0" 200 160 "-" "grpc-go/1.37.0"
```

Как видите сначала происходит вызов для входа в систему, затем три вызова на 
создание ноутбука и, наконец, ещё один вход в систему. Так что все работает 
именно так, как мы ожидали.

Далее я покажу вам как включить `SSL/TLS` для `Nginx`.

## Настраиваем Nginx для gRPC с использованием TLS
Обычно при развертывании, gRPC сервера уже работают внутри доверенной сети и 
только подсистема балансировки нагрузки (в данном случае Nginx) доступна через 
Интернет. Таким образом, наши gRPC серверы могут работать без `TLS`, как и 
раньше и нужно добавить `TLS` только к `Nginx`.

### Включаем TLS на Nginx, а для gRPC серверов используем незащищенное подключение
Для этого нам нужно скопировать 3 PEM файла в папку с конфигурацией nginx:

* Сертификат сервера `server-cert.pem`
* Приватный ключ сервера `server-key.pem`
* И сертификат CA `ca-cert.pem`, который подписал сертификат клиента, если мы 
  используем двухсторонний TLS.

После этого я перейду (`cd`) в папку `/usr/local/etc/nginx` и создам новый 
каталог `cert`. Затем я скопирую эти три 3 PEM файла из нашего проекта 
`pcbook` туда.

```shell
cd /usr/local/etc/nginx
mkdir cert
cp ~/go/src/github.com/MaksimDzhangirov/complete-gRPC/code/lecture17/cert/server-cert.pem cert
cp ~/go/src/github.com/MaksimDzhangirov/complete-gRPC/code/lecture17/cert/server-key.pem cert
cp ~/go/src/github.com/MaksimDzhangirov/complete-gRPC/code/lecture17/cert/ca-cert.pem cert
```

Отлично, теперь можно использовать файлы сертификатов и приватного ключа для
настройки.

Давайте добавим ещё одну команду `client-tls` в `Makefile`, чтобы запустить
клиент с TLS.

```makefile
# ...
client-tls:
	go run cmd/client/main.go -address 0.0.0.0:8080 -tls
# ...
```

Вернёмся к нашему конфигурационному файлу nginx.

Чтобы включить TLS, нам нужно сначала добавить `ssl` к команде `listen`. Затем 
укажем `Nginx` путь к файлу сертификата сервера с помощью команды 
`ssl_certificate` и к приватному ключу сервера — команда 
`ssl_certificate_key`.

```
...

    server {
        listen       8080 ssl http2;

        ssl_certificate cert/server-cert.pem;
        ssl_certificate_key cert/server-key.pem;

        ssl_client_certificate cert/ca-cert.pem;
        ssl_verify_client on;

        location / {
            grpc_pass grpc://pcbook_services;
        }
    }

...
```

Поскольку мы используем двухсторонний TLS, нам также понадобится команда
`ssl_client_certificate`, чтобы сообщить Nginx путь к файлу сертификата CA 
клиента. И, наконец, мы устанавливаем `ssl_verify_client` равным `on`, чтобы
Nginx проверял подлинность сертификата, который отправит клиент. На этом всё.

Давайте перезапустим Nginx. Введём `nginx -s stop`, чтобы остановить его. 
Затем запустим командой `nginx`.

```shell
nginx -s stop
nginx
```

Если мы просто выполним `make client`, он будет работать без TLS, поэтому 
запрос не будет выполнен, так как `Nginx` теперь работает с включенным TLS.

```shell
2021/05/13 00:28:07 cannot create auth interceptor: rpc error: code = Unavailable desc = connection closed
```

Теперь давайте запустим `make client-tls`.

```shell
2021/05/13 00:29:37 token refreshed: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MjA4MjI1NzcsInVzZXJuYW1lIjoiYWRtaW4xIiwicm9sZSI6ImFkbWluIn0.xHvRFcHwoAEzXZMn6rfxUN6SL0W2QFgSyxyM83jPLMo
2021/05/13 00:29:37 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/13 00:29:37 created laptop with id: 40573d0a-7a86-4acc-8388-7773772c660e
2021/05/13 00:29:37 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/13 00:29:37 created laptop with id: c3f65296-8983-42af-9a4b-e1b1c32c5aa3
2021/05/13 00:29:37 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/13 00:29:37 created laptop with id: ec526761-a01a-4774-af03-26491b7f3be2
```

В этот раз клиент работает с TLS и все запросы успешно выполнены. Имейте в 
виду, что наши серверы по-прежнему работают без TLS. Таким образом, только 
подключение между клиентом и `Nginx` безопасно, а `Nginx` взаимодействует с 
нашим бэкенд серверам через другое небезопасное соединение.

Как только `Nginx` получает зашифрованные данные от клиента, он расшифровывает
их перед отправкой бэкенд серверам. Следовательно, вы можете использовать 
этот метод только если `Nginx` и бэкенд сервера находятся в одной и той же
доверенной сети.

Но что делать, если это условие не выполняется? В этом случае у нас нет
другого выбора, кроме как включить TLS на наших бэкенд серверах и настроить
`Nginx` для работы с ними.

## Включаем TLS на Nginx и gRPC серверах
Давайте добавим ещё две команды `make` для запуска двух экземпляров сервера с
TLS. Команда `server1-tls` стартует TLS сервер на порту 50051, а `server2-tls`
- на порту 50052.

```makefile
# ...
server1-tls:
	go run cmd/server/main.go -port 50051 -tls
server2-tls:
	go run cmd/server/main.go -port 50052 -tls
# ...
```

Отлично, давайте остановим текущий `server1` и запустим `server1` с `TLS`.
Точно так же поступим с `server2`. Теперь, если мы сразу же вызовем
`make client-tls`, запрос не будет выполнен.

```shell
2021/05/13 00:45:35 cannot create auth interceptor: rpc error: code = Unavailable desc = Bad Gateway: HTTP status code 502; transport: received the unexpected content-type "text/html"
```

Дело в том, что хотя TLS рукопожатие между клиентом и `Nginx` прошло успешно,
`Nginx` подключиться к нашим бэкенд серверам не удалось, так как теперь они 
требуют безопасного TLS соединения, в то время как `Nginx` всё ещё использует 
небезопасное для взаимодействия с ними. Как видите из логов ошибок, сбой 
произошёл, когда `Nginx` попытался обратиться к `upstream` серверам.

```shell
nginx  | 2021/05/13 00:45:35 [error] 31#31: *1 upstream prematurely closed connection while reading response header from upstream, client: 192.168.48.1, server: , request: "POST /techschool_pcbook.AuthService/Login HTTP/2.0", upstream: "grpc://192.168.48.3:50051", host: "0.0.0.0:8080"
```

Чтобы включить безопасное TLS подключение между `Nginx` и `upstream`, в файле
`nginx.conf` мы должны изменить схему `grpc` на `grpcs`.

```
...

    server {
        ...

        location / {
            grpc_pass grpcs://pcbook_services;
        }
    }

...
```

Этого должно быть достаточно, если вы используете TLS на стороне сервера.
Но так как мы применяем двухсторонний TLS, то если мы перезапустим `Nginx`
и `make client-tls`, запрос всё равно завершиться с ошибкой. Это связано с 
тем, что `Nginx` пока что не настроен на отправку своего сертификата 
`upstream` серверам.

```shell
nginx  | 2021/05/13 01:09:24 [error] 31#31: *1 SSL_do_handshake() failed (SSL: error:14094412:SSL routines:ssl3_read_bytes:sslv3 alert bad certificate:SSL alert number 42) while SSL handshaking to upstream, client: 192.168.48.1, server: , request: "POST /techschool_pcbook.AuthService/Login HTTP/2.0", upstream: "grpcs://192.168.48.3:50051", host: "0.0.0.0:8080"
nginx  | 2021/05/13 01:09:24 [error] 31#31: *1 SSL_do_handshake() failed (SSL: error:14094412:SSL routines:ssl3_read_bytes:sslv3 alert bad certificate:SSL alert number 42) while SSL handshaking to upstream, client: 192.168.48.1, server: , request: "POST /techschool_pcbook.AuthService/Login HTTP/2.0", upstream: "grpcs://192.168.48.2:50052", host: "0.0.0.0:8080"
```

Как видите мы получили ошибку `bad certificate` в логах ошибок.

Давайте посмотрим, что произойдёт, если мы откроем файл `cmd/server/main.go`
и изменим значения поля `ClientAuth` с `tls.RequireAndVerifyClientCert` на
`NoClientCert`, то есть мы будем использовать TLS на стороне сервера.

```go
func loadTLSCredentials() (credentials.TransportCredentials, error) {
	// ...

	// Создаём идентификационные данные и возвращаем их
	config := &tls.Config{
		Certificates: []tls.Certificate{serverCert},
		ClientAuth:   tls.NoClientCert,
		ClientCAs:    certPool,
	}

	return credentials.NewTLS(config), nil
}
```

Затем перезапустите `server1-tls`, `server2-tls` и `make client-tls`.

```shell
2021/05/13 01:20:35 token refreshed: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MjA4MjU2MzUsInVzZXJuYW1lIjoiYWRtaW4xIiwicm9sZSI6ImFkbWluIn0.gzA0FnWa5fBi2huYWQGMivbsSLNPfcZp_VUgcZDTuno
2021/05/13 01:20:35 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/13 01:20:35 created laptop with id: 5eadb784-1cf5-4b1d-99b8-9f251eaffd01
2021/05/13 01:20:35 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/13 01:20:35 created laptop with id: 79aac545-60c3-401a-bf80-ae8af3d6df1a
2021/05/13 01:20:35 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/13 01:20:35 created laptop with id: 8fe4f365-8705-423e-a27a-3d186a2bd84d
```

На этот раз все запросы выполнены успешно, как и ожидалось.

Прекрасно, но что если нам действительно нужен двухсторонний TLS между
`Nginx` и `upstream`?

Давайте изменим значение поля `ClientAuth` обратно на 
`tls.RequireAndVerifyClientCert`, перезапустим два TLS бэкенд сервера и добавим 
необходимые настройки в файл `nginx.conf`.

Мы должны указать `Nginx` выполнять двухсторонний TLS с бэкенд серверами,
задав путь к сертификату и приватному ключу. Воспользуемся ключевым
словом `grpc_ssl_certificate` для сертификата и `grpc_ssl_certificate_key` -
для приватного ключа.

Если хотите, вы можете сгенерировать другую пару сертификатов и приватного 
ключа для `Nginx`. Я же буду использовать тот же сертификат и приватный ключ, 
что и серверы.

```
worker_processes  1;

error_log  /usr/local/var/log/nginx/error.log;

events {
    worker_connections  10;
}

http {
    access_log  /usr/local/var/log/nginx/access.log;

    upstream pcbook_services {
        server 0.0.0.0:50051;
        server 0.0.0.0:50052;
    }

    server {
        listen       8080 ssl http2;

        # Mutual TLS between gRPC client and nginx
        ssl_certificate cert/server-cert.pem;
        ssl_certificate_key cert/server-key.pem;

        ssl_client_certificate cert/ca-cert.pem;
        ssl_verify_client on;

        location / {
            grpc_pass grpcs://pcbook_services;

            # Mutual TLS between nginx and gRPC server
            grpc_ssl_certificate cert/server-cert.pem;
            grpc_ssl_certificate_key cert/server-key.pem;
        }
    }
}
```

Давайте попробуем теперь отправить запросы. Сначала остановите текущий процесс
`Nginx` и запустите новый. Также ещё раз выполните в терминале 
`make client-tls`. 

```shell
2021/05/13 01:31:33 token refreshed: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MjA4MjYyOTMsInVzZXJuYW1lIjoiYWRtaW4xIiwicm9sZSI6ImFkbWluIn0.Ej9eBFdXJ9vBTdwOix0OrNvRahpxd1p31tHWDpuxIJs
2021/05/13 01:31:33 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/13 01:31:33 created laptop with id: 1c5c430c-7eb0-4900-b6ae-6fdc3d13f1b5
2021/05/13 01:31:33 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/13 01:31:33 created laptop with id: f8d9561c-6a1b-46a6-82af-ffd1386ac5c1
2021/05/13 01:31:33 --> unary interceptor: /techschool_pcbook.LaptopService/CreateLaptop
2021/05/13 01:31:33 created laptop with id: 1782b1b9-d4a9-49e2-932b-de9cd214bcda
```

На этот раз все запросы успешно выполнены. Превосходно!

## Несколько location в файле конфигурации NGINX для разделения маршрутизации
Прежде чем мы закончим, я хотел бы показать вам ещё кое-что. Как вы уже видели
запросы на вход в систему и создание ноутбуков сейчас равномерно распределяются
между нашими двумя бэкенд серверами. Но иногда мы хотим разделить сервис для 
аутентификации и бизнес-логики. Например, предположим, что необходимо, чтобы 
все запросы на вход в систему направлялись на сервер 1, а все остальные 
запросы — на сервер 2. В этом случае мы также можем указать `Nginx` 
осуществлять маршрутизацию запросов в зависимости от вызываемого сервиса. 

```
worker_processes  1;

error_log  /usr/local/var/log/nginx/error.log;

events {
    worker_connections  10;
}

http {
    access_log  /usr/local/var/log/nginx/access.log;

    upstream auth_services {
        server 0.0.0.0:50051;
    }

    upstream laptop_services {
        server 0.0.0.0:50052;
    }

    server {
        listen       8080 ssl http2;

        # Mutual TLS between gRPC client and nginx
        ssl_certificate cert/server-cert.pem;
        ssl_certificate_key cert/server-key.pem;

        ssl_client_certificate cert/ca-cert.pem;
        ssl_verify_client on;

        location /techschool_pcbook.AuthService {
            grpc_pass grpcs://auth_services;

            # Mutual TLS between nginx and gRPC server
            grpc_ssl_certificate cert/server-cert.pem;
            grpc_ssl_certificate_key cert/server-key.pem;
        }

        location /techschool_pcbook.LaptopService {
            grpc_pass grpcs://laptop_services;

            # Mutual TLS between nginx and gRPC server
            grpc_ssl_certificate cert/server-cert.pem;
            grpc_ssl_certificate_key cert/server-key.pem;
        }
    }
}
```

Здесь я скопирую путь `/techschool_pcbook.AuthService` сервиса `AuthService` и
вставлю его в `location`. Затем изменю имя `upstream` на `auth_services`.
Подключение должно происходить только к серверу `server1` через порт `50051`.

Я добавлю другой `upstream` для службы, работающей с ноутбуками. Сделаем так,
чтобы он работал только с `server2` на порту `50052`. Затем продублируйте 
блок `location`, измените название `upstream` на `laptop_services` и обновите
путь на `/techschool_pcbook.LaptopService`.


Давайте попробуем отправить запросы. Нам просто нужно перезапустить `Nginx` и
`make client-tls`.

```shell
server1       | 2021/05/13 01:47:38 --> unary interceptor:  /techschool_pcbook.AuthService/Login
server2       | 2021/05/13 01:47:39 --> unary interceptor:  /techschool_pcbook.LaptopService/CreateLaptop
server2       | 2021/05/13 01:47:39 receive a create-laptop request with id: cfd3aee1-c258-4949-89b3-2449860ca1e0
server2       | 2021/05/13 01:47:39 saved laptop with id: cfd3aee1-c258-4949-89b3-2449860ca1e0
server2       | 2021/05/13 01:47:39 --> unary interceptor:  /techschool_pcbook.LaptopService/CreateLaptop
server2       | 2021/05/13 01:47:39 receive a create-laptop request with id: e3bcd979-9d3e-4b81-ad9f-0a8bf0a54d43
server2       | 2021/05/13 01:47:39 saved laptop with id: e3bcd979-9d3e-4b81-ad9f-0a8bf0a54d43
server2       | 2021/05/13 01:47:39 --> unary interceptor:  /techschool_pcbook.LaptopService/CreateLaptop
server2       | 2021/05/13 01:47:39 receive a create-laptop request with id: ca181f5b-b331-48d7-834a-c56a8b73d06a
server2       | 2021/05/13 01:47:39 saved laptop with id: ca181f5b-b331-48d7-834a-c56a8b73d06a
```

Теперь мы видим, что запросы на вход в систему поступают только на `server1`.

А все остальные запросы на создание ноутбуков отправляются на `server2`. Даже
если мы запустим `make client-tls` несколько раз. Итак, всё работает!

На этом мы завершим нашу лекцию о балансировке нагрузки gRPC с помощью 
`Nginx`. Я запушу файл конфигурации `Nginx` в репозиторий `pcbook`, чтобы вы
могли скачать и изменить настройки по своему желанию.

Большое спасибо за время, потраченное на чтение, и что не забросили курс. 
Желаю вам успехов в написании программ и до встречи на следующей лекции.
