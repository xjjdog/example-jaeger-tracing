
# [jaeger] 二、客户端简单实用 (Java版本)

> 友情提示，jaeger的standalone部署方式，可以快速开启试用。

本文通过两个简单的示例，说明如何使用java的api构建一个简单的调用链。相关代码见github，更多连载请关注《小姐姐味道》。


由于jaeger是基于OpenTracing的，所以只要你的应用只要支持OpenTracing协议，就可以和Jaeger集成起来。

OpenTracing的地址是
```
https://opentracing.io/
```

# 编写一个HelloWorld

## 一、maven依赖
首先，创建一个普通maven工程。然后加入依赖：

- opentracing-util 0.32.0
- jaeger-client 0.35.0
- logback-classic 1.2.3

我们主要用到了opentracing相关的jar包，而且用到了jaeger的java客户端实现。

## 二、一段简单的代码

我们创建一个简单的`loveyou`类，里面有一个简单的方法`hello`。本部分之与OpenTracing有关，与Jaeger无关。

在`hello`方法体的前后，加入几行简单的代码，主要是根据OpenTracing规范定义的api进行一些内容的添加。

```java
public class LoveYou {

    Tracer tracer;

    public LoveYou() {
        tracer = JaegerTracerHelper.initTracer("loveYouService");
    }

    public void hello(String name) {
        Span span = tracer.buildSpan("hello").start();
        span.setTag("name", name);

        System.out.println("Hello " + name);

        span.log("Love service say hello to " + name);
        span.finish();
    }

    public static void main(String[] args) {
        new LoveYou().hello("小姐姐味道");
    }
}
```

代码主要加入了以下几个重要的信息。
- 构建了一个新的`span`，每个span有三个id：rootid、parentid、id。它们构成了树状调用链的每个具体节点。
- 给新加的span添加了一个`tag`信息，用来进行一些自定义标识。tag有一些标准的清单，但也可以自定义。
- 给新加的span添加了log。log信息会`附着`在信息span上，一块被收集起来，仅定义一些比较重要的信息，包括异常栈等。一些不重要的信息不建议使用log，它会占用大量存储空间。

执行代码后，可以在jaeger的ui端看到这次的调用信息。如下：
![](media/15565956452459/15571294000739.jpg)

## 三、构建jaeger实现

```
public class JaegerTracerHelper {
    public static JaegerTracer initTracer(String service) {

        final String endPoint = "http://10.30.94.8:14268/api/traces";

        final CompositeReporter compositeReporter = new CompositeReporter(
                new RemoteReporter.Builder()
                        .withSender(new HttpSender.Builder(endPoint).build())
                        .build(),
                new LoggingReporter()
        );

        final Metrics metrics = new Metrics(new NoopMetricsFactory());

        JaegerTracer.Builder builder = new JaegerTracer.Builder(service)
                .withReporter(compositeReporter)
                .withMetrics(metrics)
                .withExpandExceptionLogs()
                .withSampler(new ConstSampler(true));

        return builder.build();
    }
}
```

我们的OpenTracing数据是如何构建，并发送到Jaeger的server端呢？就是上面的代码完成的。

JaegerTracer的参数很多，本篇不做详细介绍。要完成示例构建需要以下简单步骤：

- 构建Reporter，指发送到server的方式，代码中构建了一个http endpoint，越过jaeger-agent直接发送到jaeger-collector
- 构建一个Sampler，指定要收集的信息，由于本次要收集所有的信息。所以使用默认的ConstSampler

哦对了，为了便于调试和发现，代码还加入了一个LoggingReporter，用于将span输出到控制台。效果如下：
![](media/15565956452459/15571299556629.jpg)

到此为止，一个简单的示例java示例九完成了。以上代码，见github:


# 实现一个2层深度的链

以上代码，仅产生了一个span，也就是一个方法调用。接下来，我们看一下如何完成一个多层的调用链条。

接下俩还是要修改我们的LoveYou类。我们把调用方法hello拆解一下，拆成dispatch和hello两个方法，并在hello方法里sleep一秒钟。

**dispatch**

```
public void dispatch(String cmd, String content) {
    Span span = tracer.buildSpan("dispatch").start();
    tracer.activateSpan(span);


    if (cmd.equals("hello")) {
        this.hello(content);
    }


    if (null != span) {
        span.setTag("cmd", cmd);
        span.finish();
    }
}
```

**hello**
```
public void hello(String name) {
    Span span = tracer.buildSpan("hello").start();
    tracer.activateSpan(span);


    System.out.println("Hello " + name);
    try {
        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
    } catch (InterruptedException e) {
        e.printStackTrace();
    }

    span.setTag("name", name);
    span.log("Love service say hello to " + name);
    span.finish();
}
```

与示例一不同的是，每次生成span之后，我们还要将其激活一下
```
tracer.activateSpan(span);
```

它的目的主要是让span实例在当前的ctx里面保持活跃（比如一个线程）。这样，如果新的span判断当前有活跃的span，则将其设置成它的parent。这样，链条就串起来了。

以下是程序运行后的效果。

![](media/15565956452459/15571305700974.jpg)


# End

通过OpenTracing的Api，可以很容易的实现调用链功能。但可以看到，由于存在各种各样的客户端，主要工作量就集中在对这些客户端的兼容上。比如线程池、SpringCloud、MQ、数据库连接池等等等等。

使用Aop可以省去一些编码和侵入，可控制性会更弱一些。

接下来，我们给一个简单的OkHttp+SpringBoot调用，添加trace功能。
