# Сериализация protobuf сообщения - Golang
Здравствуйте и добро пожаловать на gRPC курсе. На предыдущих лекциях мы узнали 
как создать protobuf сообщения и сгенерировать Go и Java код из них. Сегодня 
мы начнём использовать эти сгенерированные коды для сериализации объекта в 
двоичный код и JSON. На первой половине лекции мы будем писать код на Go. А 
именно мы запишем protobuf сообщение в двоичный файл. Затем считаем содержимое
этого файла и сохраним в другом сообщении. Мы также запишем сообщение в JSON 
файл, а затем сравним его размер с двоичным файлом, чтобы посмотреть какой из
них меньше. На второй половине лекции мы реализуем то же самое, но будем писать
код на Java. Запишем protobuf сообщение в двоичный файл, считаем его, затем 
запишем в JSON файл. Но в этот раз мы также попробуем прочитать двоичный файл, 
который был создан программой на Go и запишем его в другой JSON файл, чтобы
сравнить с файлом, сгенерированным программой на Java. Итак, приступим!

## Создаём protobuf сообщения, используя генератор случайных чисел
Я открою `pcbook` проект на Go, над которым мы работали на предыдущих лекциях.
Прежде всего нам нужно выполнить команду `go mod init` для инициализации нашего
пакета. Я назову его `gitlab.com/techschool/pcbook`.

```shell
go mod init gitlab.com/techschool/pcbook
```

После выполнения этой команды в папке с проектом будет создан `go.mod` файл. 
Теперь давайте создадим пакет `sample`, чтобы сгенерировать тестовые данные, 
касающихся характеристик ноутбука. Я люблю генерировать случайные данные, 
потому что их удобно использовать при написании модульных тестов, так как 
будут выдаваться различные значения при каждом вызове и данные выглядят 
естественно и близки к реальным.

### Генерируем клавиатуру со случайными параметрами
Итак, для начала нам нужна клавиатура, поэтому я определю функцию 
`NewKeyboard`, которая возвращает указатель на объект `pb.Keybord`. Хорошо, что
Visual Studio Code автоматически импортирует для нас правильный пакет. Visual
Studio Code выдаёт предупреждение, поскольку все экспортируемые функции 
(начинающиеся с большой буквы) в Go должны иметь комментарии. Задайте 
комментарий и определите клавиатуру следующим образом:

`sample/generator.go`
```go
package sample

// NewKeyboard returns a new sample keyboard
func NewKeyboard() *pb.Keyboard {
    keyboard := &pb.Keyboard{
        Layout:  randomKeyboardLayout(),
        Backlit: randomBool(),
    }
    return keyboard
}
```

Клавиатура имеет раскладку, поэтому я напишу функцию для генерации случайной
раскладки клавиатуры, а также функцию, генерирующую случайное логическое 
значение для поля, отвечающего за подсветку. Давайте создадим новый файл 
`random.go`. Она находится в том же пакете `sample`. Я создам в нём две функции
только объявив их, пока без содержимого.

`sample/random.go`
```go
package sample

func randomBool() bool {
    
}

func randomKeyboardLayout() pb.Keyboard_Layout {
    
}
```

Теперь давайте реализуем сначала функцию `randomBool`, поскольку она проще. 
Логической переменной можно присвоить только одно из двух значений: `true` или
`false`, поэтому я буду использовать функцию `rand.Intn` из пакета `math/rand` 
с `n` равным 2. Она возвращает случайное целое число: 0 или 1. Таким образом, 
будем возвращать `true`, если значение равно 1, и `false` - в противном случае.
Функция `randomKeyboardLayout` возвращает одно из трех возможных значений, 
поэтому будем использовать `rand.Intn(3)`. Если значение равно 1, то вернем 
`QWERTY`, если 2 - `QWERTZ`, иначе - `AZERTY`.

`sample/random.go`
```go
package sample

func randomBool() bool {
    return rand.Intn(2) == 1
}

func randomKeyboardLayout() pb.Keyboard_Layout {
    switch rand.Intn(3) {
    case 1:
        return pb.Keyboard_QWERTY
    case 2:
        return pb.Keyboard_QWERTZ
    default:
        return pb.Keyboard_AZERTY
    }
}
```

### Генерируем ЦПУ со случайными параметрами
Затем создадим функцию для генерации ЦПУ со случайными параметрами. Сначала я 
создам пустой объект ЦПУ и верну его.

`sample/generator.go`
```go
func NewCPU() *pb.CPU {
    cpu := &pb.CPU{
        
    }
    
    return cpu
}
```

Если навести на название структуры, то увидим множество полей, которые 
необходимо заполнить. Нам нужна функция, которая вернет случайную строку с 
фирмой производителем ЦПУ. Давайте перейдём в файл `random.go` и определим её.
Один из простейших способов сделать это — выбрать случайное значение из 
заранее определенного множества фирм производителей, например, "Intel" и 
"AMD".

`sample/random.go`
```go
func randomCPUBrand() string {
    return randomStringFromSet("Intel", "AMD")
}

func randomStringFromSet(a ...string) string {
    n := len(a)
    if n == 0 {
        return ""
    }
    return a[rand.Intn(n)]
}
```

Здесь я определил функцию `randomStringFromSet`, которая принимает на вход 
произвольное число строк и возвращает одну случайную строку из них. Её очень
просто написать, используйте функцию `rand.Intn()`, как мы делали до этого. 
Теперь мы можем заполнить поле с фирмой производителем для ЦПУ. Затем мы 
сгенерируем случайное название ЦПУ, используя название фирмы производителя в 
этой функции. Поскольку существует только две фирмы производителя, то простого 
`if` будет достаточно. Чтобы не вводить все возможные названия, я ограничился
некоторым заранее определенным множеством. Теперь мы можем задать значение для
поля с названием ЦПУ.

`sample/random.go`
```go
func randomCPUName(brand string) string {
    if brand == "Intel" {
        return randomStringFromSet(
            "Xeon E-2286M",
            "Core i9-9980HK",
            "Core i7-9750H",
            "Core i5-9400F",
            "Core i3-1005G1",
        )
    }

    return randomStringFromSet(
        "Ryzen 7 PRO 2700U",
        "Ryzen 5 PRO 3500U",
        "Ryzen 3 PRO 3200GE",
    )
}
```

Следующее поле, которое мы должны заполнить — это число ядер. Допустим, что
это число должно быть в диапазоне от 2 до 8. Таким образом, нам понадобится 
функция `randomInt()`, для создания случайного целого числа между `min` и 
`max`.

`sample/random.go`
```go
func randomInt(min, max int) int {
    return min + rand.Int(max-min+1)
}
```

Здесь используется формула `min + rand.Intn(max-min+1)`. В этой формуле функция
`rand.Intn` возвращает целое число в диапазоне от `0` до `max-min`. Поэтому
если мы прибавим `min` к этому числу, то получим значение от `min` до `max`. 
Ничего сложного, правда?
Итак, теперь мы можем задать значение для поля `numberCores`. Оно ожидает 
значение типа `unsigned int32`, поэтому здесь нам необходимо осуществить 
преобразование типа. По аналогии для числа потоков мы будем использовать 
случайное целое число в диапазоне от числа ядер до 12. Следующее поле - 
`minGhz` с типом `float64`. Я хочу, чтобы процессор имел минимальную частоту в 
диапазоне от 2.0 до 3.5, поэтому нам нужна функция, создающая `float64` в 
промежутке от `min` до `max`.

`sample/random.go`
```go
func randomFloat64(min, max float64) float64 {
    return min + rand.Float64()*(max-min)
}
```

Она немного отличается от функции `randomInt`. Поскольку функция 
`rand.Float64()` возвращает случайное число с плавающей запятой в диапазоне от
`0` до `1`, мы должны умножить его на `(max-min)`, чтобы получить значение в 
промежутке от `0` до `max-min`. Когда мы прибавляем `min` к этому значению, мы
получаем число в диапазоне от `min` до `max`. Надеюсь вы поняли. Вернемся к 
нашему генератору. Для максимальной частоты будем использовать случайное 
значение типа `float64` в диапазоне от `min` частоты до `5.0` ГГц. Теперь мы 
можем задать значения для полей `minGhz` и `maxGhz` ЦПУ.

`sample/generator.go`
```go
func NewCPU() *pb.CPU {
    brand := randomCPUBrand()
    name := randomCPUName(brand)
    
    numberCores := randomInt(2, 8)
    numberThreads := randomInt(numberCores, 12)
    
    minGhz := randomFloat64(2.0, 3.5)
    maxGhz := randomFloat64(minGhz, 5.0)
    
    cpu := &pb.CPU{
        Brand:         brand,
        Name:          name,
        NumberCores:   uint32(numberCores),
        NumberThreads: uint32(numberThreads),
        MinGhz:        minGhz,
        MaxGhz:        maxGhz,
    }
    
    return cpu
}
```

### Генерируем GPU со случайными параметрами
Подобным образом можно реализовать функцию `NewGPU`. Мы создадим функцию, 
возвращающую случайную строку с фирмой производителем GPU, которая может быть 
равна NVIDIA или AMD. Затем мы сгенерируем случайное название GPU, используя 
название фирмы производителя.

`sample/random.go`
```go
func randomGPUBrand() string {
    return randomStringFromSet("NVIDIA", "AMD")
}

func randomGPUName(brand string) string {
    if brand == "NVIDIA" {
        return randomStringFromSet(
            "RTX 2060",
            "RTX 2070",
            "GTX 1660-Ti",
            "GTX 1070",
        )
    }
    
    return randomStringFromSet(
        "RX 590",
        "RX 580",
        "RX 5700-XT",
        "RX Vega-56",
    )
}
```

Как и для ЦПУ, чтобы не вводить все возможные названия, я ограничился
некоторым заранее определенным множеством. Значения для полей `minGhz` и 
`maxGhz` генерируются, используя функцию `randomFloat64`, которую мы определили
ранее. Пусть минимальная частота может меняться в диапазоне от `1.0` до `1.5`, 
а максимальная меняется от этого `min` до `2.0` ГГц. Осталось заполнить одно
поле: память. Для этого нам нужно создать новый объект `Memory`. Допустим мы 
хотим, чтобы она была в диапазоне от 2 до 6 гигабайт. Таким образом, нам нужно
использовать функцию `randomInt` с преобразованием типа в `unsigned int64`.
В качестве единиц измерения используйте значение `Memory_GIGABYTE` из 
перечисления, сгенерированного `protoc` за нас. Мы оценим удобство от 
использования этого перечисления в дальнейшем. На этом мы завершим работу с 
функцией для GPU.

`sample/generator.go`
```go
func NewGPU() *pb.GPU {
    brand := randomGPUBrand()
    name := randomGPUName(brand)
    
    minGhz := randomFloat64(1.0, 1.5)
    maxGhz := randomFloat64(minGhz, 2.0)
    
    memory := &pb.Memory{
        Value: uint64(randomInt(2, 6)),
        Unit:  pb.Memory_GIGABYTE,
    }
    
    gpu := &pb.GPU{
        Brand: brand,
        Name: name,
        MinGhz: minGhz,
        MaxGhz: maxGhz,
        Memory: memory,
    }
    
    return gpu
}
```

### Генерируем ОЗУ со случайными параметрами
Определить функцию для генерации ОЗУ очень просто, поскольку она практически 
ничем не отличается от памяти GPU.

`sample/generator.go`
```go
func NewRAM() *pb.Memory {
    ram := &pb.Memory{
        Value: uint64(randomInt(4, 64)),
        Unit:  pb.Memory_GIGABYTE,
    }
    
    return ram
}
```

### Генерируем накопитель со случайными параметрами
Теперь займемся накопителями. Мы создадим две функции: одну для SSD, а другую 
для HDD. Для SSD в качестве драйвера будем использовать значение `Storage_SSD`,
а объём накопителя будет в пределах от `128` до `1024` гигабайт. Я скопирую 
функцию `NewSSD()` и изменю название на HDD. Здесь в качестве драйвера мы 
должны использовать `Storage_HDD`, а объём зададим в диапазоне от 1 до 6 
терабайт. Отлично!

`sample/generator.go`
```go
func NewSSD() *pb.Storage {
    ssd := &pb.Storage{
        Driver: pb.Storage_SSD,
        Memory: &pb.Memory{
            Value: uint64(randomInt(128, 1024)),
            Unit: pb.Memory_GIGABYTE,
        },
    }
    
    return ssd
}

func NewHDD() *pb.Storage {
    hdd := &pb.Storage{
        Driver: pb.Storage_HDD,
        Memory: &pb.Memory{
            Value: uint64(randomInt(1, 6)),
            Unit: pb.Memory_TERABYTE,
        },
    }
    
    return hdd
}
```

### Генерируем экран со случайными параметрами
Теперь займемся функцией для экрана. Размер экрана может меняться в диапазоне 
от 13 до 17 дюймов. Это значение имеет тип `float32`, поэтому определим 
функцию `randomFloat32`. Она будет отличаться от `randomFloat64` только тем, 
что использует значения с типом `float32`.

`sample/random.go`
```go
func randomFloat32(min float32, max float32) float32 {
    return min + rand.Float32()*(max-min)
}
```

Займемся разрешением экрана. Для высоты будем использовать случайное целое
число в диапазоне от 1080 до 4320, а ширину вычислять по высоте, используя 
соотношение сторон 16 на 9.

`sample/random.go`
```go
func randomScreenResolution() *pb.Screen_Resolution {
    height := randomInt(1080, 4320)
    width := height * 16 / 9
    
    resolution := &pb.Screen_Resolution{
        Height: uint32(height),
        Width:  uint32(width),
    }
    
    return resolution
}
```

Здесь мы должны выполнить преобразование типа, поскольку ширина и высота должны
иметь тип `unsigned int32`. Теперь зададим типа матрицы экрана. Напишем 
отдельную функцию, возвращающую случайную строку. В нашем приложении только два
типа матриц: `IPS` или `OLED`. Таким образом, достаточно использовать функцию 
`rand.Intn(2)` и оператор `if`.

`sample/random.go`
```go
func randomScreenPanel() pb.Screen_Panel {
    if rand.Intn(2) == 1 {
        return pb.Screen_IPS
    }
    return pb.Screen_OLED
}
```

Последнее поле, которое мы должны задать — мультитач — это просто случайное
логическое значение.

`sample/generator.go`
```go
func NewScreen() *pb.Screen {
    screen := &pb.Screen{
        SizeInch: randomFloat32(13, 17),
        Resolution: randomScreenResolution(),
        Panel: randomScreenPanel(),
        Multitouch: randomBool(),
    }
    
    return screen
}
```

### Генерируем ноутбук со случайными параметрами
Итак, все компоненты созданы. Теперь мы можем сгенерировать новый ноутбук.
Ему нужен уникальный случайный идентификатор. Для этого давайте создадим 
функцию `randomID()`. Я буду использовать пакет Google UUID. Мы можем найти его
через поисковик браузера, скопировать команду `go get` и запустить её в 
терминале, чтобы установить пакет.

```shell
go get github.com/google/uuid
```

Теперь вернемся к нашему коду. Мы можем вызвать функцию `uuid.New()`, чтобы 
получить случайный идентификатор и преобразовать его в строку.

`sample/random.go`
```go
func randomID() string {
    return uuid.New().String()
}
```

Затем мы сгенерируем фирму производителя ноутбука и название по аналогии с ЦПУ
и GPU. Я опять буду использовать значения из предопределенного множества, чтобы
не перечислять все возможные значения.

`sample/random.go`
```go
func randomLaptopBrand() string {
    return randomStringFromSet("Apple", "Dell", "Lenovo")
}

func randomLaptopName(brand string) string {
    switch brand {
    case "Apple":
        return randomStringFromSet("Macbook Air", "Macbook Pro")
    case "Dell":
        return randomStringFromSet("Lalitude", "Vostro", "XPS", "Alienware")
    default:
        return randomStringFromSet("Thinkpad X1", "Thinkpad P1", "Thinkpad P53")
    }
}
```

Будем создавать ноутбуки фирм `Apple`, `Dell` и `Lenovo`. Мы используем
оператор `switch-case`, чтобы сгенерировать правильное название ноутбука в 
зависимости от фирмы производителя. Затем зададим ЦПУ и ОЗУ, вызвав 
соответствующие функции-генераторы. Поле для GPU является списком, поэтому в
качестве значения я определю список. Пусть у всех ноутбуков пока будет по одну
GPU. Аналогично создадим список для накопителей, но в этот раз я добавлю в него
два элемента: один для SSD, а другой для HDD. Поля для экрана и клавиатуры 
заполнить несложно — просто используйте генераторы. Далее следует поле 
`oneof`: `Weight` (Вес). Мы можем указать вес в килограммах или фунтах. 
`Protoc` создал для нас две структуры. Я собираюсь использовать здесь 
килограммы. В структуре `pb.Laptop_WeightKg` есть поле `WeightKg`. Зададим 
для него значение в диапазоне от одного до трёх килограмм. Цена — это случайное
число в диапазоне от 1500 до 3000. Год выпуска зададим от 2015 до 2019. И,
наконец, для временной метки `updateAt` мы можем использовать функцию `Now()`,
предоставляемую пакетом `google.golang.org/protobuf/types/known/timestamppb`.
Мы закончили!

`sample/generator.go`
```go
func NewLaptop() *pb.Laptop {
    brand := randomLaptopBrand()
    name := randomLaptopName(brand)
    
    laptop := &pb.Laptop{
        Id: randomID(),
        Brand: brand,
        Name: name,
        Cpu: NewCPU(),
        Ram: NewRAM(),
        Gpus: []*pb.GPU{NewGPU()},
        Storages: []*pb.Storage{NewSSD(), NewHDD()},
        Screen: NewScreen(),
        Keyboard: NewKeyboard(),
        Weight: &pb.Laptop_WeightKg{
            WeightKg: randomFloat64(1.0, 3.0),
        },
        PriceUsd: randomFloat64(1500, 3000),
        ReleaseYear: uint32(randomInt(2015, 2019)),
        UpdatedAt: timestamppb.Now(),
    }
    
    return laptop
}
```

## Сериализация protobuf сообщений
Теперь создадим новый пакет `serializer` и реализуем в нём несколько функций 
для сериализации объектов-ноутбуков в файлы. Итак, давайте создадим файл 
`file.go` в папке `serializer`.

### Записываем protobuf сообщение в двоичный файл
Первая функция будет записывать protobuf сообщение в файл в двоичном формате. 
В нашем случае сообщением будет объект-ноутбук. Мы можем использовать интерфейс
`proto.Message`, чтобы функция могла принимать любые сообщения в качестве 
входного аргумента. В этой функции нам нужно сначала вызвать `proto.Marshal`,
чтобы сериализовать сообщение в двоичную форму. Если возникает ошибка мы просто
обертываем её и возвращаем вызывающему. В противном случае мы используем 
функцию `ioutil.WriteFile()` для сохранения данных в файле с указанным именем.
Опять обертываем и возвращаем любую ошибку, возникающую во время этого 
процесса. Если всё прошло удачно, мы просто возвращаем `nil`, что означает 
отсутствие ошибок. Функция готова.

```go
package serializer

import (
    "fmt"
    "io/ioutil"
    
    "github.com/golang/protobuf/proto"
)

// WriteProtobufToBinaryFile writes protocol buffer message to binary file
func WriteProtobufToBinaryFile(message proto.Message, filename string) error {
    data, err := proto.Marshal(message)
    if err != nil {
        return fmt.Errorf(
            "cannot marshal proto message to binary: %w",
            err,
        )
    }
    
    err = ioutil.WriteFile(filename, data, 0644)
    if err != nil {
        return fmt.Errorf("cannot write binary data to file: %w", err)
    }
    
    return nil
}
```

Я покажу как написать модульный текст для неё. Давайте создадим файл 
`file_test.go` в папке `serializer`. Обратите внимание, что суффикс `_test`
в названии файла является обязательным, иначе Go не сможет понять, что это
тестовый файл. Кроме того принято определенным образом называть функции 
модульного теста. Они должны начинаться с префикса Test и принимать в качестве 
входных данных указатель на объект `testing.T`. Я обычно вызываю `t.Parallel()`
для почти всех моих модульных тестов, чтобы они запускались параллельно и можно
было легко обнаружить любое [состояние гонки](https://en.wikipedia.org/wiki/Race_condition). 

```go
func TestFileSerializer(t *testing.T) {
    t.Parallel()
}
```

Допустим мы хотим сериализовать объект в файл `laptop.bin` внутри папки `tmp`. 
Итак, сначала нам надо создать папку `tmp`. Затем, используя функцию 
`NewLaptop()` сгенерировать новый `laptop1` и вызвать функцию 
`WriteProtobufToBinaryFile()`, чтобы сохранить его в файл `laptop.bin`. 
Поскольку эта функция может возвращать ошибку, мы должны убедиться, что ошибка
равна `nil`. Это означает, что файл успешно записан. Для подобных проверок, я 
часто использую пакет `testify`. Откройте его страницу на github, чтобы 
скопировать `go get` команду и запустить её в вашем терминале.

```shell
go get github.com/stretchr/testify
```

Теперь мы можем просто вызвать функцию `require.NoError()` с параметрами `t`
и `err`.

```go
package serializer

import (
    "testing"
    
    "github.com/MaksimDzhangirov/complete-gRPC/code/lecture9.1/sample"
    "github.com/MaksimDzhangirov/complete-gRPC/code/lecture9.1/serializer"
    "github.com/stretchr/testify/require"
)

func TestFileSerializer(t *testing.T) {
    t.Parallel()
    
    binaryFile := "../tmp/laptop.bin"
    
    laptop1 := sample.NewLaptop()
    err := serializer.WriteProtobufToBinaryFile(laptop1, binaryFile)
    require.NoError(t, err)
}
```

В Visual Studio Code мы можем нажать на ссылку "Run test", расположенной на 
строчку выше самого теста, чтобы запустить его. После запуска произошла ошибка:
`import cycle not allowed in test`. Она связана с тем, что мы находимся в 
пакете `serializer` и пытаемся импортировать его. Просто добавьте `_test` к 
имени нашего пакета, чтобы изменить его название, а также указать Go, что это
тестовый пакет. Теперь если мы повторно запустим тест, то он будет успешно 
пройден. Отлично! Как мы видим, файл `laptop.bin` записался в папку `tmp`.

### Считываем protobuf сообщение из двоичного файла
Теперь мы напишем другую функцию для считывания этого бинарного файла в объект
protobuf сообщения. Я назову эту функцию `ReadProtobufFromBinaryFile()`. 
Сначала нам нужно использовать `ioutil.ReadFile()` для чтения двоичных данных 
из файла. Затем мы вызываем `proto.Unmarchal()`, чтобы десериализовать двоичные
данные в protobuf сообщение.

`serializer/file.go`
```go
// ...
func ReadProtobufFromBinaryFile(filename string, message proto.Message) error {
    data, err := ioutil.ReadFile(filename)
    if err != nil {
        return fmt.Errorf("cannot read binary data from file: %w", err)
    }
    
    err = proto.Unmarshal(data, message)
    if err != nil {
        return fmt.Errorf(
            "canot unmarshal binary to proto message: %w",
            err,
        )
    }
    
    return nil
}
```

Давайте протестируем его. В нашем модульном тесте я определю новый объект 
`laptop2` и вызову `ReadProtobufFromBinaryFile()`, чтобы считать данные файла
в этот объект. Мы проверим нет ли ошибок и мы также хотим убедиться, что 
`laptop2` содержит те же данные, что и `laptop1`. Для этого мы можем 
использовать функцию `proto.Equal` из пакета `golang/protobuf`. Эта функция
должна возвращать `true`, поэтому здесь мы осуществляем проверку 
`require.True()`. Теперь запустим тест. Он был успешно пройден!

```go
// ...

func TestFileSerializer(t *testing.T) {
    // ...
    
    laptop2 := &pb.Laptop{}
    err = serializer.ReadProtobufFromBinaryFile(binaryFile, laptop2)
    require.NoError(t, err)
    require.True(t, proto.Equal(laptop1, laptop2))
}
```

### Записываем protobuf сообщение в JSON файл
Теперь поскольку данные записаны в двоичном формате, мы не можем просмотреть 
их. Давайте напишем другую функцию для их сериализации в `JSON` формат. В этой
функции мы должны сначала преобразовать protobuf сообщение в `JSON` строку. 
Для этого я создам новую функцию с именем `ProtobufToJSon`. Её код поместите в
отдельный файл `json.go` в тот же пакет `serializer`. Итак, теперь, чтобы 
преобразовать protobuf сообщение в `JSON`, мы можем использовать структуру
`jsonb.Marshaler`. По сути, нам нужно просто вызвать функцию 
`marshaler.MarshalToString()`.

`serializer/json.go`
```go
package serializer

import (
    "github.com/golang/protobuf/jsonpb"
    "github.com/golang/protobuf/proto"
)

func ProtobufToJSON(message proto.Message) (string, error) {
    marshaler := jsonpb.Marshaler{
        EnumsAsInts:  false,
        EmitDefaults: true,
        Indent:       " ",
        OrigName:     true,
    }
    return marshaler.MarshalToString(message)
}
```

Здесь мы можем настроить несколько параметров, например, записывать 
перечисления в виде целых чисел или строк (`EnumsAsInts`), записывать поля со
значениями по умолчанию или нет (`EmitDefaults`), какой отступ мы хотим 
использовать (`Indent`), хотим ли мы использовать исходные названия полей, 
определенные в proto файле (`OrigName`). Давайте пока будем использовать 
настройки, приведенные выше, а другие значения для них зададим позже. Теперь
вернемся к нашей функции. После вызова `ProtobufToJSON`, мы получили `JSON`
строку. Всё, что нам осталось сделать, это записать эту строку в файл.

`serializer/file.go`
```go
func WriteProtobufToJSONFile(message proto.Message, filename string) error {
    data, err := ProtobufToJSON(message)
    if err != nil {
        return fmt.Errorf(
            "cannot marshal proto message to JSON: %w",
            err,
        )
    }
    
    err = ioutil.WriteFile(filename, []byte(data), 0644)
    if err != nil {
        return fmt.Errorf("cannot write JSON data to file: %w", err)
    }
    
    return nil
}
```

Теперь давайте вызовем эту функцию в нашем модельном тесте.

```go
// ...

func TestFileSerializer(t *testing.T) {
    // ...
    err = serializer.WriteProtobufToJSONFile(laptop1, jsonFile)
    require.NoError(t, err)
}
```

Добавим проверку на отсутствие ошибок после вызова этой функции и запустим 
тест. Вуаля, был успешно создан файл `laptop.json`. Как видите, названия полей 
точно такие же как мы определили в наших proto файлах, то есть маленькими 
буквами в snake case стиле.
Теперь, если мы изменим параметр `OrigName` на `false`, и повторно запустим 
тест, то названия полей изменятся на сamel сase стиль, где все слова начинаются
с маленькой буквы. Сейчас все значения, задаваемые в полях-перечислениях, 
записываются в виде строк, например, `IPS` для типа матрицы экрана. Если мы 
изменим значение параметра `EnumsAsInts` на `true` и повторно запустим тест,
то значение типа матрицы изменится на целое число 1.
Теперь я хочу убедиться, что сгенерированные ноутбуки будут иметь различные
характеристики каждый раз при запуске тестов. Давайте выполним команду `go
test ./...` несколько раз и посмотрим что произойдёт.  

```shell
go test ./...
```

`...` означает, что мы хотим запустить модульные тесты для всех пакетов, 
находящихся в подкаталогах текущего каталога. Похоже, что в JSON файле 
меняется только уникальный идентификатор, а остальные значения не меняются. 
Это происходит из-за того, что по умолчанию пакет `rand` использует 
фиксированное начальное значение. Мы можем указать ему использовать различные
значения при каждом запуске. Для этого давайте создадим `init()` функцию в
файле `random.go`. Это специальная функция, которая будет вызываться единожды 
перед выполнением любого другого кода в пакете. В этой функции мы укажем 
`rand`, что нужно использовать текущее значение времени в наносекундах в 
качестве начального значения.

`sample/random.go`
```go
func init() {
    rand.Seed(time.Now().UnixNano())
}
```

Теперь давайте запустим тест несколько раз. Мы видим, что характеристики 
ноутбука меняются. Превосходно!
Мы также можем выполнить команду `go test ./serializer/file_test.go`, чтобы
прогнать тесты только из этого файла. Теперь давайте зададим команду 
`make test` в make-файле для запуска модульных тестов. Мы можем использовать 
флаг `-cover`, чтобы измерить покрытие кода нашими тестами и флаг `-race` для 
обнаружения любых состояний гонки в нашем коде.
 
```makefile
# ...
test:
    go test -cover -race ./...
# ...
```

Теперь запустите команду `make test` в терминале.

```shell
make test
```

Как видите, в пакете `serializer` тестами покрыто 73.9% кода. Мы можем также 
перейти в файл с тестами и нажать на "run package tests" в самом верху. Это 
запустит все тесты в пакете и сообщит о покрытии кода. Затем мы можем открыть 
файлы и просмотреть какая часть нашего кода покрыта тестами, а какая — нет. 
Покрытый код выделен синим, а не покрытый — красным. Всегда нужно стараться 
писать такой набор тестов, который будет покрывать различные ветки выполнения 
кода.

### Сравниваем размер двоичного и JSON файла
Я хочу показать ещё кое-что прежде, чем мы перейдём к проекту на Java. Давайте
откроем терминал, зайдём в папку `tmp` и выполним команду `ls -l`. Мы увидим, 
что размер json-файла примерно в 5 раз больше, чем двоичного. Таким образом, 
мы сэкономим много трафика, если будем использовать gRPC вместо обычного JSON 
API. Кроме того, так как размер сообщения меньше, то его можно быстрее 
передать. Это большое преимущество двоичного протокола. Увидимся в разделе, 
который посвящен проекту на Java!