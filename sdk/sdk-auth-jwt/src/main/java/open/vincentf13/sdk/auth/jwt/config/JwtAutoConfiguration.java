package open.vincentf13.sdk.auth.jwt.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import open.vincentf13.sdk.auth.jwt.filter.JwtFilter;
import open.vincentf13.sdk.auth.jwt.session.JwtSessionService;
import open.vincentf13.sdk.auth.jwt.session.JwtSessionStore;
import open.vincentf13.sdk.auth.jwt.session.JwtSessionStoreInMemory;
import open.vincentf13.sdk.auth.jwt.session.JwtSessionStoreRedis;
import open.vincentf13.sdk.auth.jwt.token.JwtProperties;
import open.vincentf13.sdk.auth.jwt.token.OpenJwt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtAutoConfiguration {



    @Bean
    @ConditionalOnMissingBean
    public OpenJwt openJwtToken(JwtProperties properties, ObjectProvider<JwtEncoder> encoderProvider,
                                ObjectProvider<JwtDecoder> decoderProvider) {
        return new OpenJwt(properties, encoderProvider, decoderProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        SecretKey secretKey = new SecretKeySpec(properties.getSecret().getBytes(), "HMACSHA256");
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(new OctetSequenceKey.Builder(secretKey).build()));
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        SecretKey secretKey = new SecretKeySpec(properties.getSecret().getBytes(), "HMACSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKey).build();
    }

    @Bean
    @ConditionalOnMissingBean(JwtSessionStore.class)
    public JwtSessionStore inMemoryJwtSessionStore() {
        return new JwtSessionStoreInMemory();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtSessionService jwtSessionService(JwtSessionStore sessionStore) {
        return new JwtSessionService(sessionStore);
    }

    @Configuration
    @ConditionalOnClass(RedisTemplate.class)
    @ConditionalOnBean(RedisTemplate.class)
    static class RedisJwtConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "redisJwtSessionStore")
        public JwtSessionStore redisJwtSessionStore(RedisTemplate<String, Object> redisTemplate,
                                                    JwtProperties properties) {
            return new JwtSessionStoreRedis(redisTemplate, properties);
        }
    }

    @Configuration
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class ServletJwtConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public JwtFilter jwtAuthenticationFilter(OpenJwt openJwt,
                                                 ObjectProvider<JwtSessionService> sessionServiceProvider,
                                                 JwtProperties properties) {
            return new JwtFilter(openJwt, sessionServiceProvider, properties);
        }

        @Bean
        @ConditionalOnMissingBean
        public JwtConfigurer jwtSecurityConfigurer(ObjectProvider<JwtFilter> provider) {
            return new JwtConfigurer(provider);
        }
    }
}
