package open.vincentf13.sdk.auth.jwt.token.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import open.vincentf13.sdk.auth.jwt.session.JwtSessionService;
import open.vincentf13.sdk.auth.jwt.session.JwtSessionStore;
import open.vincentf13.sdk.auth.jwt.session.JwtSessionStoreInMemory;
import open.vincentf13.sdk.auth.jwt.session.JwtSessionStoreRedis;
import open.vincentf13.sdk.auth.jwt.token.OpenJwtService;
import open.vincentf13.sdk.infra.redis.config.RedisAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@AutoConfiguration(after = RedisAutoConfiguration.class)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtAutoConfig {

  @Bean
  @ConditionalOnMissingBean
  public OpenJwtService openJwtToken(
      JwtProperties properties,
      ObjectProvider<JwtEncoder> encoderProvider,
      ObjectProvider<JwtDecoder> decoderProvider) {
    return new OpenJwtService(properties, encoderProvider, decoderProvider);
  }

  @Bean
  @ConditionalOnMissingBean
  public JwtEncoder jwtEncoder(JwtProperties properties) {
    SecretKey secretKey = new SecretKeySpec(properties.getSecret().getBytes(), "HMACSHA256");
    JWKSource<SecurityContext> jwkSource =
        new ImmutableJWKSet<>(new JWKSet(new OctetSequenceKey.Builder(secretKey).build()));
    return new NimbusJwtEncoder(jwkSource);
  }

  @Bean
  @ConditionalOnMissingBean
  public JwtDecoder jwtDecoder(JwtProperties properties) {
    SecretKey secretKey = new SecretKeySpec(properties.getSecret().getBytes(), "HMACSHA256");
    return NimbusJwtDecoder.withSecretKey(secretKey).build();
  }

  @Bean
  @ConditionalOnBean(RedisTemplate.class)
  @Primary
  public JwtSessionStore redisJwtSessionStore(
      RedisTemplate<String, Object> redisTemplate, JwtProperties properties) {
    return new JwtSessionStoreRedis(redisTemplate, properties);
  }

  @Bean
  @ConditionalOnMissingBean
  public JwtSessionService jwtSessionService(JwtSessionStore sessionStore) {
    return new JwtSessionService(sessionStore);
  }

  @Bean
  @ConditionalOnMissingBean(JwtSessionStore.class)
  public JwtSessionStore inMemoryJwtSessionStore() {
    return new JwtSessionStoreInMemory();
  }
}
