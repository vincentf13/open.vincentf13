package test.open.vincentf13.common.core.test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;


// 單一共用的 Spring Boot 測試主程式；其他測試若需額外 bean，請使用 @TestConfiguration 避免產生多個 @SpringBootConfiguration
@SpringBootConfiguration
@ComponentScan({"open.vincentf13", "test.open.vincentf13"}) // 掃描正式與測試用元件
@EnableAutoConfiguration
public class TestBoot {
}
