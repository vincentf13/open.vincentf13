package com.example.demo;

import io.micrometer.core.annotation.Timed;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@RestController
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @GetMapping("/")
    public String hello() {
        return "Hello Spring Boot in K8s!";
    }

    @GetMapping("/burn")
    @Timed
    public String burn(@RequestParam(defaultValue = "200") int ms) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < ms) { }
        return "burn " + ms + "ms";
    }
}