package com.sayhiai.example.jaeger.totorial04.controller;

import io.opentracing.Tracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class LoveYouController {

    @Autowired
    Tracer tracer;

    @PostMapping("/hello")
    @ResponseBody
    public String hello(@RequestBody String name) {

        System.out.println("Hello " + name);

        return "hello " + name;
    }
}
