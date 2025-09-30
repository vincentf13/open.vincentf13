package com.example.exchange.demo;

import io.micrometer.core.annotation.Timed;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@SpringBootApplication(scanBasePackages = "com.example.exchange")
public class DemoApplication {
    private final BuildProperties buildProperties;

    public DemoApplication(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @GetMapping("/")
    public String hello() {
        return "Hello from 微服務版 Demo!" +
                "<br/>image.tag = " + buildProperties.get("image.tag") +
                "<br/>Build Time: " + buildProperties.get("build.timestamp");
    }

    @GetMapping("/burn")
    @Timed(value = "demo.burn")
    public String burn(@RequestParam(defaultValue = "200") int ms) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < ms) {
            // busy wait for the requested duration
        }
        return "demo burn " + ms + "ms";
    }
}
