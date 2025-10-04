package test.open.vincentf13.common.core.test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@ComponentScan("open.vincentf13")  // 指定主程式路徑
@EnableAutoConfiguration
@Import( {KafkaTest.TestConfig.class})
public class TestBoot {
}
