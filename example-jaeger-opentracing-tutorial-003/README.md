# [jaeger] 三、实现一个分布式调用（OkHttp+SpringBoot)

很多情况，`trace`是分布在不同的应用中的，最常用的远程调用方式就是`Http`。

在这种情况下，我们通常通过增加额外的`Http Header`传递Trace信息，然后将其组织起来。

本部分通过构建一个目前最火的`SpringBoot`服务端，然后通过`OkHttp3`进行调用，来展示分布式调用链的组织方式。

更多连载关注小姐姐味道，本文相关代码见：
```
https://github.com/sayhiai/example-jaeger-opentracing-tutorial-003
```

**需要的知识：**

- 创建一个简单的SpringBoot应用
- 使用OkHttp3发起一个Post请求
- 了解OpenTracing的inject和extract函数

# inject & extract函数

这是两个为了跨进程追踪而生的两个函数，力求寻找一种通用的trace传输方式。这是两个强大的函数，它进行了一系列抽象，使得OpenTracing协议不用和特定的实现进行耦合。

- **Carrier** 携带trace信息的载体，下文中将自定义一个
- **inject** 将额外的信息`注入`到相应的载体中
- **extract** 将额外的信息从载体中`提取`出来

其实，这个载体大多数都是用一个Map（具体是text map)来实现；或者是其他二进制方式实现。


在本文中，我们就是用了text map，载体的底层就是http头信息（也可以通过request params进行传递）。


## 创建一个Server端

## maven依赖

首先，通过bom方式import进spring boot的相关配置。

- spring-boot-dependencies 2.1.3.RELEASE

然后，引入其他依赖

- opentracing-util 0.32.0
- jaeger-client 0.35.0
- logback-classic 1.2.3
- spring-boot-starter-web 2.1.3.RELEASE
- okhttp 3.14.1

## SpringBoot应用

创建一个SpringBoot应用，端口指定为8888，并初始化默认的`Tracer`。

```java
@SpringBootApplication
@EnableAutoConfiguration
@ComponentScan(basePackages = {  "com.sayhiai.example.jaeger.totorial03.controller",
})
public class App extends SpringBootServletInitializer {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
    @Bean
    public JaegerTracer getJaegerTracer() {
        return JaegerTracerHelper.initTracer("LoveYou");
    }
}
```

在controller目录下创建一个简单的服务`/hello`，通过request body传递参数。

关键代码如下：

```
@PostMapping("/hello")
@ResponseBody
public String hello(@RequestBody String name,HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String header = headerNames.nextElement();
            headers.put(header, request.getHeader(header));
        }

        System.out.println(headers);

        Tracer.SpanBuilder builder = null;
        SpanContext parentSpanContext = tracer.extract(Format.Builtin.HTTP_HEADERS, new TextMapAdapter(headers));
        if (null == parentSpanContext) {
            builder = tracer.buildSpan("hello");
        } else {
            builder = tracer.buildSpan("hello").asChildOf(parentSpanContext);
        }

        Span span = builder.start();
```

首先拿到头信息，并进行`extract`，如果得到的`SpanContext`不为空，则代表当前的请求是另外一个应用发起的。在这种情况下，我们把请求的来源，作为当前请求的`parent`。

使用Curl进行调用，确保服务能正常运行。
```
curl -XPOST http://localhost:8888/hello  -H "Content-Type:text/plain;charset=utf-8"   -d "小姐姐味道"
```

# 创建OkHttp3客户端调用

## 创建载体

OkHttp3是一个非常轻量级的类库，它的header信息可以通过以下代码设置。
```
Request.Builder builder;
builder.addHeader(key, value);
```

我们在上面提到，将要创建一个自定义的`Carrier`，这里通过继承`TextMap`，来实现一个。

```java
public class RequestBuilderCarrier implements io.opentracing.propagation.TextMap {
    private final Request.Builder builder;

    RequestBuilderCarrier(Request.Builder builder) {
        this.builder = builder;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        throw new UnsupportedOperationException("carrier is write-only");
    }

    @Override
    public void put(String key, String value) {
        builder.addHeader(key, value);
    }
}
```

## 发起调用

使用OkHttp3发起一个简单的Post请求即可。

```
public static void main(String[] args) {
    Tracer tracer = JaegerTracerHelper.initTracer("Main");

    String url = "http://localhost:8888/hello";
    OkHttpClient client = new OkHttpClient();
    Request.Builder request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(MediaType.parse("text/plain;charset=utf-8"), "小姐姐味道"));


    Span span = tracer.buildSpan("okHttpMainCall").start();
    Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
    Tags.HTTP_METHOD.set(span, "POST");
    Tags.HTTP_URL.set(span, url);
    tracer.activateSpan(span);

    tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new RequestBuilderCarrier(request));

    client.newCall(request.build()).enqueue(new Callback() {
        @Override
        public void onFailure(Call call, IOException e) {
            e.printStackTrace();
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            System.out.println(response.body().string());
        }
    });

    span.finish();
}
```

注意，在方法中间，我们使用inject函数，将trace信息附着在`RequestBuilderCarrier`上进行传递。

这两个函数，使用的就是jaeger的实现。见：
```
io.jaegertracing.internal.propagation.TextMapCodec
```

运行Main方法，查看Jaeger的后台，可以看到，我们的分布式Trace已经生成了。

![](media/15572190944469/15572985388918.jpg)


# End

本文展示了创建分布式调用链的一般方式。类比此法，可以很容易的写出基于`HttpClient`组件的客户端组件。

接下来，我们将使用Spring的拿手锏Aop，来封装通过Feign接口调用的SpringCloud服务。你会发现，实现一个类似Sleuth的客户端收集器，还是蛮简单的。

