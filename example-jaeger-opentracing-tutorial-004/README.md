# [jaeger] 四、微服务之调用链（Feign+SpringCloud）

终于到了我们的重点，微服务了。

与使用OkHttp3来实现的客户端类似，Feign接口本来也就是一个Http调用，依然可以使用Http头传值的方式，将`Trace`往下传。

本文更多的是关于`SpringCloud`的一些知识，你需要了解一些基本的`Spring`相关的知识。

# 安装Consul

SpringCloud的注册中心，我们选用Consul。

consul也是用golang开发的。从consul官网下载二进制包以后，解压。

```
./consul agent   -bind 127.0.0.1 -data-dir . -node my-register-center -bootstrap-expect 1 -ui -dev
```

使用以上脚本快速启动，即可使用。

访问
http://localhost:8500/ui/
可以看到Consul的web页面。

# 构建微服务服务端和客户端

## maven依赖

以bom方式引入springboot和springcloud的组件。

**spring-boot-dependencies** 2.1.3.RELEASE
**spring-cloud-dependencies** Greenwich.SR1

都是热乎乎的新鲜版本。

接下来下，引入其他必须的包
opentracing-util 0.32.0
jaeger-client 0.35.0
logback-classic 1.2.3
opentracing-spring-jaeger-cloud-starter 2.0.0

spring-boot-starter-web
spring-boot-starter-aop
spring-boot-starter-actuator
spring-cloud-starter-consul-discovery
spring-cloud-starter-openfeign


## 构建服务端

服务端App的端口是`8888`

```
@SpringBootApplication
@EnableAutoConfiguration
@EnableDiscoveryClient
@ComponentScan(basePackages = {
        "com.sayhiai.example.jaeger.totorial04.controller",
})
public class App extends SpringBootServletInitializer {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
```

在application.yml中，配置Consul作为配置中心。

```
 cloud:
    consul:
      host: 127.0.0.1
      port: 8500
      discovery:
        register: true
        tags: version=1.0,author=xjjdog
        healthCheckPath: /actuator/health
        healthCheckInterval: 5s
```

---

**创建Rest服务`/hello`**
```
@PostMapping("/hello")
@ResponseBody
public String hello(@RequestBody String name) {
        return "hello " + name;
}
```

## 构建Feign客户端

Feign客户端的App端口是`9999`，同样是一个SpringCloud服务。

**创建FeignClient**

```
@FeignClient("love-you-application")
public interface LoveYouClient {
    @PostMapping("/hello")
    @ResponseBody
    public String hello(@RequestBody String name);
}
```

**创建调用入口`/test`**
```
@GetMapping("/test")
@ResponseBody
public String hello() {
    String rs = loveYouClient.hello("小姐姐味道");
    return rs;
}
```

## 集成jaeger

目前，已经有相关SpringCloud的轮子了，我们就不重复制造了。

首先，我们看一下使用方法，然后，说明一下背后的原理。了解原理之后，你将很容易的给自己开发的中间件加入Trace功能。

---

轮子在这里，引入相应maven包即可使用：

https://github.com/opentracing-contrib/java-spring-jaeger

```
<dependency>
  <groupId>io.opentracing.contrib</groupId>
  <artifactId>opentracing-spring-jaeger-cloud-starter</artifactId>
</dependency>
```



**加入配置生效**

在`application.yml`中，加入以下配置，就可以得到调用链功能了。

配置指明了trace的存放地址，并将本地log打开。

```
opentracing.jaeger.http-sender.url: http://10.30.94.8:14268/api/traces
opentracing.jaeger.log-spans: true
```

访问 localhost:9999/test，会得到以下调用链。

![](media/15573041787805/15574590861187.jpg)

可以看到。Feign的整个调用过程都被记录下来了。

# 原理

## Feign的调用

### Feign通过Header传递参数

首先看下Feign的Request构造函数。

```java
public static Request create(
String method, 
String url, 
Map<String, Collection<String>> headers,
byte[] body, 
Charset charset) {
    return new Request(method, url, headers, body, charset);
}
```
如代码，完全可以通过在headers参数中追加我们需要的信息进行传递。

接着源代码往下找:
Client**->**
LoadBalancerFeignClient execute()**->**
executeWithLoadBalancer()**->**
IClient**->**


**再往下，IClient实现有**
OkHttpLoadBalancingClient
RibbonLoadBalancingHttpClient（基于apache的包）
等，它们都可以很容易的设置其Header

最终，我们的请求还是由这些底层的库函数发起，默认的是HttpURLConnection。

>读过Feign和Ribbon源码的人都知道，这部分代码不是一般的乱，但好在上层的Feign是一致的。

#### 使用委托包装Client

通过实现`feign.Client`接口，结合委托，可以重新封装`execute`方法，然后将信息`inject`进Feign的scope中。


#### 使用Aop自动拦截Feign调用

```
@Aspect
class TracingAspect {
  @Around("execution (* feign.Client.*(..)) && !within(is(FinalType))")
  public Object feignClientWasCalled(final ProceedingJoinPoint pjp) throws Throwable {
    Object bean = pjp.getTarget();
    if (!(bean instanceof TracingClient)) {
      Object[] args = pjp.getArgs();
      return new TracingClientBuilder((Client) bean, tracer)
          .withFeignSpanDecorators(spanDecorators)
          .build()
          .execute((Request) args[0], (Request.Options) args[1]);
    }
    return pjp.proceed();
  }
}
```

利用spring boot starter技术，我们不需要任何其他改动，就可以拥有trace功能了。

---

## Web端的发送和接收

了解spring的人都知道，最适合做http头信息添加和提取的地方，就是拦截器和过滤器。

**发送**

对于普通的http请求客户端来说，是通过添加一个
`ClientHttpRequestInterceptor`
拦截器来实现的。过程不再表诉，依然是使用inject等函数进行头信息设置。

**接收**

而对于接收，则使用的是Filter进行实现的。通过实现一个普通的servlet filter。可以通过`extract`函数将trace信息提取出来，然后将context作为Request的attribute进行传递。

相关代码片段如下。
```
if (servletRequest.getAttribute(SERVER_SPAN_CONTEXT) != null) {
    chain.doFilter(servletRequest, servletResponse);
} else {
    SpanContext extractedContext = tracer.extract(Format.Builtin.HTTP_HEADERS,
            new HttpServletRequestExtractAdapter(httpRequest));

    final Span span = tracer.buildSpan(httpRequest.getMethod())
            .asChildOf(extractedContext)
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
            .start();

httpRequest.setAttribute(SERVER_SPAN_CONTEXT, span.context());
```

就这样，整个链条就穿插起来啦。


