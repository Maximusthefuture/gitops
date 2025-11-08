package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HelloController {

    @Value("${spring.application.name:demo}")
    private String appName;

    @Value("${server.port:8080}")
    private String port;

    @GetMapping("/")
    public Map<String, String> hello() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Hello from GitOps Demo!");
        response.put("application", appName);
        response.put("port", port);
        response.put("status", "running");
        return response;
    }

    @GetMapping("/api/hello")
    public Map<String, String> apiHello() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Hello from API endpoint!");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return response;
    }
}

