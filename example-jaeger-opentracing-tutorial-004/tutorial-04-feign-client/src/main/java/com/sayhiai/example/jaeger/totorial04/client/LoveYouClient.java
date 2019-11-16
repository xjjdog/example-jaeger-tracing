package com.sayhiai.example.jaeger.totorial04.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@FeignClient("love-you-application")
public interface LoveYouClient {
    @PostMapping("/hello")
    @ResponseBody
    public String hello(@RequestBody String name);
}
