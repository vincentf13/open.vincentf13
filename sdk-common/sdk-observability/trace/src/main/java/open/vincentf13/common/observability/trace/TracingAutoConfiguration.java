package open.vincentf13.common.observability.trace;

import java.util.UUID;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

/**
 * Minimal tracing filter that injects a fallback trace id into the request context.
 */
@AutoConfiguration
public class TracingAutoConfiguration {

    @Bean
    public FilterRegistrationBean<Filter> traceIdFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
                if (request instanceof HttpServletRequest servletRequest) {
                    servletRequest.setAttribute("traceId", UUID.randomUUID().toString());
                }
                chain.doFilter(request, response);
            }
        });
        registration.setOrder(Integer.MIN_VALUE);
        return registration;
    }
}
