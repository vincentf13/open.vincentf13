package open.vincentf13.sdk.spring.mvc.log;

import open.vincentf13.sdk.spring.mvc.web.MvcProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RequestLoggingFilterConfig {

    @Bean
    public RequestLoggingFilter requestLoggingFilter(MvcLogService mvcLogService) {
        return new RequestLoggingFilter(mvcLogService);
    }

    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilterRegistration(RequestLoggingFilter filter,
                                                                                        MvcProperties properties) {
        FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>(filter);
        int order = properties.getRequest().getFilterOrder();
        // stay slightly after correlation filters if enabled while remaining early in the chain
        registration.setOrder(order == Integer.MIN_VALUE ? order + 10 : order + 1);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
