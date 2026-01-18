package open.vincentf13.servicetest;

import io.micrometer.core.annotation.Timed;
import open.vincentf13.sdk.core.OpenConstant;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(scanBasePackages = OpenConstant.Package.Names.BASE_PACKAGE)
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
    return "Hello from 微服務版 ServiceTest!"
        + "<br/>image.tag = "
        + buildProperties.get("image.tag")
        + "<br/>Build Time: "
        + buildProperties.get("build.timestamp");
  }

  @GetMapping("/burn")
  @Timed(value = "servicetest.burn")
  public String burn(@RequestParam(defaultValue = "200") int ms) {
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() - start < ms) {
      // busy wait for the requested duration
    }
    return "service-test burn " + ms + "ms";
  }
}
