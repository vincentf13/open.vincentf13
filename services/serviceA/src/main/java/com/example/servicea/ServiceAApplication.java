package com.example.servicea;

import io.micrometer.core.annotation.Timed;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class ServiceAApplication {

    private final BuildProperties buildProperties;

    public ServiceAApplication(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    public static void main(String[] args) {
        SpringApplication.run(ServiceAApplication.class, args);
    }

    @GetMapping("/")
    public String hello() {
        return "Hello from Service A!" +
                "<br/>image.tag=" + buildProperties.get("image.tag") +
                "<br/>Build Time: " + buildProperties.get("build.timestamp");
    }

    @GetMapping("/burn")
    @Timed(value = "servicea.burn")
    public String burn(@RequestParam(defaultValue = "200") int ms) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < ms) {
            // busy wait for the requested duration
        }
        return "serviceA burn " + ms + "ms";
    }
}
