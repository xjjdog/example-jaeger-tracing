package com.sayhiai.example.jaeger.totorial04.controller;

import com.sayhiai.example.jaeger.totorial04.client.LoveYouClient;
import io.opentracing.Tracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class TestController {

    @Autowired
    Tracer tracer;

    @Autowired
    LoveYouClient loveYouClient;

    @GetMapping("/test")
    @ResponseBody
    public String hello() {
        String rs = loveYouClient.hello("小姐姐味道");
        return rs;
    }
}
