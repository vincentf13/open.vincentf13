package test.open.vincentf13;


import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootConfiguration
@ComponentScan("open.vincentf13")  // 指定主程式路徑
public class TestConfig {
//    @Bean
//    LettuceConnectionFactory lettuceConnectionFactory() {
//        // 以容器暴露的 host/port 建立專屬測試連線工廠
//        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
//        configuration.setHostName(REDIS.getHost());
//        configuration.setPort(REDIS.getMappedPort(6379));
//        LettuceConnectionFactory factory = new LettuceConnectionFactory(configuration);
//        factory.afterPropertiesSet();
//        return factory;
//    }
//
//    @Bean
//    StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
//        return new StringRedisTemplate(connectionFactory);
//    }
}
