package open.vincentf13.sdk.spring.mvc.config;

import open.vincentf13.sdk.spring.mvc.log.RequestLoggingFilter;
import open.vincentf13.sdk.spring.mvc.log.MvcLogService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ConfigFilter {


    /*
  讓 PUT、PATCH、DELETE 等非 POST 請求
  且 Content-Type = application/x-www-form-urlencoded
  能透過 request.getParameter() 讀取表單欄位
 */
//    @Bean
//    FormContentFilter formContentFilter() {
//        return new FormContentFilter();
//    }
//
//    /**
//     * 建立 RequestCorrelationFilter，負責補足 traceId/requestId 與同步至 MDC。
//     */
//    @Bean
//    public RequestCorrelationFilter requestCorrelationFilter(MvcProperties properties) {
//        return new RequestCorrelationFilter(properties.getRequest());
//    }
//
//    /**
//     * 將 RequestCorrelationFilter 以指定順序註冊到 Servlet Filter Chain。
//     */
//    @Bean
//    public FilterRegistrationBean<RequestCorrelationFilter> requestCorrelationFilterRegistration(RequestCorrelationFilter filter,
//                                                                                                 MvcProperties properties) {
//        FilterRegistrationBean<RequestCorrelationFilter> registration = new FilterRegistrationBean<>(filter);
//        registration.setOrder(properties.getRequest().getFilterOrder());
//        registration.addUrlPatterns("/*");
//        return registration;
//    }

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
