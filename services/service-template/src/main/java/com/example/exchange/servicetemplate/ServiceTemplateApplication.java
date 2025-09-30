package com.example.exchange.servicetemplate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.example.exchange")
public class ServiceTemplateApplication {
    private final BuildProperties buildProperties;

    public ServiceaApplication(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    public static void main(String[] args) {
        SpringApplication.run(ServiceTemplateApplication.class, args);
    }

        @GetMapping("/")
    public String hello() {
        return "Hello from 微服務版  Service A!" +
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
