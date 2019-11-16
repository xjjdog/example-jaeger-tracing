package com.sayhiai.example.jaeger.totorial03.controller;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Controller
public class LoveYouController {

    @Autowired
    Tracer tracer;

    @PostMapping("/hello")
    @ResponseBody
    public String hello(@RequestBody String name,
                        HttpServletRequest request) {

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
        span.setTag("name", name);

        System.out.println("Hello " + name);

        span.log("Love service say hello to " + name);
        span.finish();

        return "hello " + name;
    }
}
