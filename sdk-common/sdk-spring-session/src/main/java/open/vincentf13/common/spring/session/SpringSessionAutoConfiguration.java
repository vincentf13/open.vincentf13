package open.vincentf13.common.spring.session;

import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.util.StringUtils;

@AutoConfiguration
@ConditionalOnClass({RedisIndexedSessionRepository.class, CookieSerializer.class})
@EnableConfigurationProperties(OpenSessionProperties.class)
public class SpringSessionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    CookieSerializer sessionCookieSerializer(OpenSessionProperties properties,
                                             ObjectProvider<ServerProperties> serverProperties,
                                             Environment environment) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        OpenSessionProperties.Cookie cookie = properties.getCookie();
        serializer.setCookieName(cookie.getName());
        if (StringUtils.hasText(cookie.getDomain())) {
            serializer.setDomainName(cookie.getDomain());
        }
        if (StringUtils.hasText(cookie.getPath())) {
            serializer.setCookiePath(cookie.getPath());
        }
        serializer.setUseHttpOnlyCookie(cookie.isHttpOnly());
        serializer.setUseSecureCookie(resolveSecure(cookie, serverProperties, environment));
        serializer.setUseBase64Encoding(cookie.isUseBase64Encoding());

        Duration maxAge = cookie.getMaxAge();
        if (maxAge != null) {
            long seconds = maxAge.getSeconds();
            if (seconds < 0) {
                seconds = -1;
            }
            if (seconds > Integer.MAX_VALUE) {
                seconds = Integer.MAX_VALUE;
            }
            serializer.setCookieMaxAge((int) seconds);
        }

        OpenSessionProperties.Cookie.SameSite sameSite = cookie.getSameSite();
        if (sameSite != null && sameSite.getAttributeValue() != null) {
            serializer.setSameSite(sameSite.getAttributeValue());
        }

        String rememberMeAttribute = cookie.getRememberMeRequestAttribute();
        if (StringUtils.hasText(rememberMeAttribute)) {
            serializer.setRememberMeRequestAttribute(rememberMeAttribute);
        }

        return serializer;
    }

    @Bean
    @ConditionalOnMissingBean
    SessionRepositoryCustomizer<RedisIndexedSessionRepository> redisSessionRepositoryCustomizer(OpenSessionProperties properties) {
        return (repository) -> {
            long timeoutSeconds = properties.getMaxInactiveInterval().getSeconds();
            if (timeoutSeconds > Integer.MAX_VALUE) {
                timeoutSeconds = Integer.MAX_VALUE;
            }
            repository.setDefaultMaxInactiveInterval((int) timeoutSeconds);
            repository.setRedisKeyNamespace(properties.getNamespace());
            repository.setFlushMode(properties.getFlushMode());
            repository.setSaveMode(properties.getSaveMode());
        };
    }

    private boolean resolveSecure(OpenSessionProperties.Cookie cookie,
                                   ObjectProvider<ServerProperties> serverProperties,
                                   Environment environment) {
        return switch (cookie.getSecure()) {
            case ALWAYS -> true;
            case NEVER -> false;
            case AUTO -> {
                Boolean configured = resolveServerCookieSecure(serverProperties.getIfAvailable());
                if (configured != null) {
                    yield configured;
                }
                Boolean sslEnabled = environment.getProperty("server.ssl.enabled", Boolean.class);
                yield Boolean.TRUE.equals(sslEnabled);
            }
        };
    }

    private Boolean resolveServerCookieSecure(ServerProperties serverProperties) {
        if (serverProperties == null || serverProperties.getServlet() == null) {
            return null;
        }
        ServerProperties.Servlet servlet = serverProperties.getServlet();
        if (servlet.getSession() == null || servlet.getSession().getCookie() == null) {
            return null;
        }
        return servlet.getSession().getCookie().getSecure();
    }
}
