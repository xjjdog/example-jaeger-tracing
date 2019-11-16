package com.sayhiai.example.jaeger.totorial02;

import io.opentracing.Span;
import io.opentracing.Tracer;

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
