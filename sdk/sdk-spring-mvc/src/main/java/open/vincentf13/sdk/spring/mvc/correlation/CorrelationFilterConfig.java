package open.vincentf13.sdk.spring.mvc.correlation;

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
public class CorrelationFilterConfig {
    
    
    /**
     建立 RequestCorrelationFilter，負責補足 traceId/requestId 與同步至 MDC。
     */
    @Bean
    public RequestCorrelationFilter requestCorrelationFilter(MvcProperties properties) {
        return new RequestCorrelationFilter(properties.getRequest());
    }
    
    /**
     將 RequestCorrelationFilter 以指定順序註冊到 Servlet Filter Chain。
     */
    @Bean
    public FilterRegistrationBean<RequestCorrelationFilter> requestCorrelationFilterRegistration(RequestCorrelationFilter filter,
                                                                                                 MvcProperties properties) {
        FilterRegistrationBean<RequestCorrelationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(properties.getRequest().getFilterOrder());
        registration.addUrlPatterns("/*");
        return registration;
    }
}
