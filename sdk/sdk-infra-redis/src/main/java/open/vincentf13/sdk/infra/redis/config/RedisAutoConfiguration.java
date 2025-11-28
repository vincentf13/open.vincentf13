package open.vincentf13.sdk.infra.redis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import open.vincentf13.sdk.infra.redis.OpenRedisString;
import open.vincentf13.sdk.infra.redis.OpenRedissonLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@AutoConfiguration(after = org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class)
@ConditionalOnClass(RedisTemplate.class)
@ConditionalOnBean(RedisConnectionFactory.class)
public class RedisAutoConfiguration {


    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "stringObjectRedisTemplate")
    public RedisTemplate<String, Object> stringObjectRedisTemplate(
            RedisConnectionFactory redisConnectionFactory,
            RedisSerializer<Object> redisValueSerializer
                                                                  ) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);

        /*
  注意：此模板的泛型為 RedisTemplate<String, Object>，API 回傳值編譯期型別僅有 Object。
  - 使用 GenericJackson2JsonRedisSerializer 等通用序列化時，物件會還原為 Map/List/值類型而非原類別。
  - 若改用 Jackson2JsonRedisSerializer<Object> 並開啟 default typing，可寫入類別資訊以嘗試還原原型，但需考慮安全與相容性。
  - 需要強型別時，優先建立針對目標類的 RedisTemplate (配置繁瑣)  或在讀取後再用 ObjectMapper.convertValue(..., Target.class) 轉型。
  例如：
  Human human = objectMapper.convertValue(redisTemplate.opsForValue().get("human:1"), Human.class);
  Human h2 = objectMapper.convertValue(redisTemplate.opsForHash().get("human:hash", "h1"), Human.class);
 */
        template.setValueSerializer(redisValueSerializer);
        template.setHashValueSerializer(redisValueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @ConditionalOnMissingBean(name = "redisValueSerializer")
    public RedisSerializer<Object> redisValueSerializer(ObjectMapper objectMapper) {

        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }


    @Bean
    @ConditionalOnBean({RedisTemplate.class, StringRedisTemplate.class})
    @ConditionalOnMissingBean
    public OpenRedisString openRedisString(RedisTemplate<String, Object> redisTemplate,
                                           StringRedisTemplate stringRedisTemplate) {
        OpenRedisString.register(redisTemplate, stringRedisTemplate);
        return OpenRedisString.getInstance();
    }

    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean
    public OpenRedissonLock openRedissonLock(RedissonClient redissonClient) {
        OpenRedissonLock.register(redissonClient);
        return OpenRedissonLock.getInstance();
    }

}
