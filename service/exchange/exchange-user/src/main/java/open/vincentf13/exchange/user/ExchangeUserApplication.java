package open.vincentf13.exchange.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan(basePackages = "open.vincentf13.exchange.user.infra.mybatis.mapper")
public class ExchangeUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExchangeUserApplication.class, args);
    }
}
