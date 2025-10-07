package open.vincentf13.common.spring.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.session.FlushMode;
import org.springframework.session.SaveMode;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.test.util.ReflectionTestUtils;

class SpringSessionAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SpringSessionAutoConfiguration.class));

    @Test
    void providesCookieSerializerWithRecommendedDefaults() {
        this.contextRunner.run((context) -> {
            DefaultCookieSerializer serializer = context.getBean(DefaultCookieSerializer.class);
            assertThat(ReflectionTestUtils.getField(serializer, "cookieName")).isEqualTo("OPEN_SESSION");
            assertThat(ReflectionTestUtils.getField(serializer, "cookiePath")).isEqualTo("/");
            assertThat(ReflectionTestUtils.getField(serializer, "useHttpOnlyCookie")).isEqualTo(true);
            assertThat(ReflectionTestUtils.getField(serializer, "useSecureCookie")).isEqualTo(Boolean.FALSE);
            assertThat(ReflectionTestUtils.getField(serializer, "useBase64Encoding")).isEqualTo(false);
            assertThat(ReflectionTestUtils.getField(serializer, "sameSite")).isEqualTo("Lax");
        });
    }

    @Test
    void customPropertiesAreApplied() {
        this.contextRunner
                .withPropertyValues(
                        "open.vincentf13.session.max-inactive-interval=10m",
                        "open.vincentf13.session.namespace=test-session",
                        "open.vincentf13.session.flush-mode=immediate",
                        "open.vincentf13.session.save-mode=on-get-attribute",
                        "open.vincentf13.session.cookie.same-site=none",
                        "open.vincentf13.session.cookie.use-base64-encoding=true",
                        "open.vincentf13.session.cookie.secure=always",
                        "open.vincentf13.session.cookie.max-age=15m",
                        "open.vincentf13.session.cookie.remember-me-request-attribute=remember-me-token")
                .run((context) -> {
                    DefaultCookieSerializer serializer = context.getBean(DefaultCookieSerializer.class);
                    assertThat(ReflectionTestUtils.getField(serializer, "sameSite")).isEqualTo("None");
                    assertThat(ReflectionTestUtils.getField(serializer, "useSecureCookie")).isEqualTo(Boolean.TRUE);
                    assertThat(ReflectionTestUtils.getField(serializer, "useBase64Encoding")).isEqualTo(true);
                    assertThat(ReflectionTestUtils.getField(serializer, "cookieMaxAge")).isEqualTo((int) Duration.ofMinutes(15).getSeconds());
                    assertThat(serializer.getRememberMeRequestAttribute()).isEqualTo("remember-me-token");

                    SessionRepositoryCustomizer<RedisIndexedSessionRepository> customizer =
                            context.getBean(SessionRepositoryCustomizer.class);
                    RedisIndexedSessionRepository repository =
                            new RedisIndexedSessionRepository(mockRedisOperations());
                    customizer.customize(repository);

                    Duration maxInactiveInterval = (Duration) ReflectionTestUtils.getField(repository, "defaultMaxInactiveInterval");
                    assertThat(maxInactiveInterval).isEqualTo(Duration.ofMinutes(10));
                    assertThat(ReflectionTestUtils.getField(repository, "namespace")).isEqualTo("test-session:");
                    assertThat(ReflectionTestUtils.getField(repository, "flushMode")).isEqualTo(FlushMode.IMMEDIATE);
                    assertThat(ReflectionTestUtils.getField(repository, "saveMode")).isEqualTo(SaveMode.ON_GET_ATTRIBUTE);
                });
    }

    private static org.springframework.data.redis.core.RedisOperations<String, Object> mockRedisOperations() {
        return Mockito.mock(org.springframework.data.redis.core.RedisOperations.class, Answers.RETURNS_DEEP_STUBS);
    }
}
