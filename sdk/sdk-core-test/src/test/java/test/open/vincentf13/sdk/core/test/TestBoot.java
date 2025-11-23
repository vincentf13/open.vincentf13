package test.open.vincentf13.sdk.core.test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import open.vincentf13.sdk.core.OpenConstant;


// 單一共用的 Spring Boot 測試主程式；其他測試若需額外 bean，請使用 @TestConfiguration 避免產生多個 @SpringBootConfiguration
@SpringBootConfiguration
@ComponentScan({OpenConstant.Package.BASE.value(), OpenConstant.Package.TEST.value()}) // 掃描正式與測試用元件
@EnableAutoConfiguration
public class TestBoot {
}
