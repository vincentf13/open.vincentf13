package com.example.demo;

import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@RestController
public class DemoApplication {

    @Value("${APP_IMAGE_TAG:unknown}")
    String tag;

    private final BuildProperties buildProperties;

    public DemoApplication(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @GetMapping("/")
    public String hello() {

        return "Hello Spring Boot in K8s!" +
                "<br/>image.tag=" +  buildProperties.get("image.tag") +
                "<br/>Build Time: " + buildProperties.get("build.timestamp");

    }

    @GetMapping("/burn")
    @Timed
    public String burn(@RequestParam(defaultValue = "200") int ms) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < ms) {
        }
        return "burn " + ms + "ms";
    }
}