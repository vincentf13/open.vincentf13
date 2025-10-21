package open.vincentf13.service.test;

import io.micrometer.core.annotation.Timed;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import open.vincentf13.common.core.OpenConstant;

@SpringBootApplication(scanBasePackages = OpenConstant.BASE_PACKAGE)
@RestController
public class ServiceTestApplication {
    private final BuildProperties buildProperties;

    public ServiceTestApplication(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    public static void main(String[] args) {
        SpringApplication.run(ServiceTestApplication.class, args);
    }

    @GetMapping("/")
    public String hello() {
        return "Hello from 微服務版 Service Test !" +
                "<br/>image.tag =  " + buildProperties.get("image.tag") +
                "<br/>Build Tim e: " + buildProperties.get("build.timestamp");
    }

    @GetMapping("/burn")
    @Timed(value = "service-test.burn")
    public String burn(@RequestParam(defaultValue = "200") int ms) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < ms) {
            // busy wait for the requested duration
        }
        return "service-test burn " + ms + "ms";
    }
}
