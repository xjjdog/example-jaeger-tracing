package com.sayhiai.example.jaeger.totorial02;

import io.opentracing.Span;
import io.opentracing.Tracer;

import java.util.concurrent.TimeUnit;

public class LoveYou {

    Tracer tracer;

    public LoveYou() {
        tracer = JaegerTracerHelper.initTracer("loveYouService");
    }

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

    public static void main(String[] args) {
        new LoveYou().dispatch("hello", "小姐姐味道");
    }
}
