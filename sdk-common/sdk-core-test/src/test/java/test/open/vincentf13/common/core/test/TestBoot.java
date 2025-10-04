package test.open.vincentf13.common.core.test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;


@SpringBootConfiguration
@ComponentScan("open.vincentf13")  // 指定主程式路徑
@EnableAutoConfiguration
public class TestBoot{
}
