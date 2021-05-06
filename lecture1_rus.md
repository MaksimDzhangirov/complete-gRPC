# Полный курс по gRPC
В этом курсе мы изучим [gRPC](https://ru.wikipedia.org/wiki/GRPC) и как его
использовать вместе с [Protocol Buffers](https://ru.wikipedia.org/wiki/Protocol_Buffers)
для разработки приложения на Go и Java.
## Мотивация для изучения
Зачем нужно изучать gRPC или какие проблемы gRPC пытается решить?
Ответ прост — взаимодействие между различными приложениями,
написанными на разных языках программирования. Например,
бекэнд может быть написан на Go, тогда как фронтэнд на Java
(Android приложение) или на Swift (iOS приложение).
Таким образом, нужен способ, с помощью которого приложения
могли бы обмениваться информацией.
В настоящее время популярна микросервисная архитектура,
поэтому даже внутри неё на бекэнде разные сервисы могут быть написаны на
разных языках, таких как Go, Python или Rust, в зависимости от требований
бизнеса и технических ограничений. Итак, для взаимодействия такие сервисы
должны подчиняться определенному набору правил:
* знать и уметь реализовывать канал связи, используемый другими сервисами
  (REST, SOAP, очереди сообщений).
* знать и уметь реализовывать механизм аутентификации для обмена
  информацией с другими сервисами: Basic, OAuth, JWT.
* знать формат данных других сервисов и уметь пребразовывать данные
  в этот формат/из этого формата: JSON, XML, бинарный.
* знать модель данных, которая используется другими сервисами.
* знать и уметь обрабатывать ошибки, генерируемые другими сервисами.

Необходимость учета всех этих правил усложняет процесс проектирования API.
Кроме того мы хотим, чтобы механизм взаимодействия был эффективным
(т. е. быстрым и упрощенным). Часто число передаваемых сообщений от
одного микросервиса другому велико, поэтому чем быстрее происходит
обмен, тем лучше.
В особых случаях, например, для мобильных приложений, где часто скорость
и пропускная способность сети ограничены, очень важно иметь упрощенный протокол
обмена сообщениями для взаимодействия с сервером.

Наконец, взаимодействие должно быть простым с точки зрения реализации.
Представим себе систему, состоящую из сотен или даже тысяч микросервисов.
Согласитесь, что не хотелось бы потратить основную часть времени на написание
кода, позволяющего им взаимодействовать друг с другом.
Хотелось бы иметь какого-либо рода фреймворк, который позволял бы разработчикам
сосредотачиваться на реализации основной логики их сервисов, оставляя всю
рутинную работу фреймворку. И таким фреймворком как раз и является gRPC.

Теперь вы имеете некоторое представление о тех проблемах, которые gRPC пытается
решить.

На следующей лекции мы изучим чем конкретно является gRPC, и каким образом он
достигает поставленной выше цели.