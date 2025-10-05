package open.vincentf13.common.spring.mvc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import open.vincentf13.common.spring.mvc.advice.ApiResponseBodyAdvice;
import open.vincentf13.common.spring.mvc.filter.RequestCorrelationFilter;
import open.vincentf13.common.spring.mvc.interceptor.RequestLoggingInterceptor;
import open.vincentf13.common.spring.mvc.exception.RestExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Spring MVC 自動配置，集中常見最佳實踐（i18n、CORS、Filter、Validator 等）。
 */
@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(MvcProperties.class)
public class SpringWebMvcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "messageSource")
    public MessageSource mvcMessageSource(MvcProperties properties) {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setBasename(properties.getI18n().getMessageBasename());
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }

    @Bean
    @ConditionalOnMissingBean(LocaleResolver.class)
    public LocaleResolver localeResolver(MvcProperties properties) {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(properties.getI18n().defaultLocale());
        resolver.setSupportedLocales(properties.getI18n().supportedLocales());
        return resolver;
    }

    @Bean
    @ConditionalOnProperty(prefix = "open.vincentf13.mvc.i18n", name = "enable-locale-change-interceptor", havingValue = "true", matchIfMissing = true)
    public LocaleChangeInterceptor localeChangeInterceptor(MvcProperties properties) {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName(properties.getI18n().getLocaleParamName());
        return interceptor;
    }

    @Bean
    public RequestLoggingInterceptor requestLoggingInterceptor() {
        return new RequestLoggingInterceptor();
    }

    @Bean
    public RequestCorrelationFilter requestCorrelationFilter(MvcProperties properties) {
        return new RequestCorrelationFilter(properties.getRequest());
    }

    @Bean
    public FilterRegistrationBean<RequestCorrelationFilter> requestCorrelationFilterRegistration(RequestCorrelationFilter filter,
                                                                                                MvcProperties properties) {
        FilterRegistrationBean<RequestCorrelationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(properties.getRequest().getFilterOrder());
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean(Validator.class)
    public LocalValidatorFactoryBean mvcValidator(MessageSource messageSource, MvcProperties properties) {
        LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
        factoryBean.setValidationMessageSource(messageSource);
        Properties validationProperties = new Properties();
        validationProperties.setProperty("hibernate.validator.fail_fast", Boolean.toString(properties.getValidation().isFailFast()));
        factoryBean.setValidationProperties(validationProperties);
        return factoryBean;
    }

    @Bean
    @ConditionalOnMissingBean
    public MethodValidationPostProcessor methodValidationPostProcessor(Validator validator) {
        MethodValidationPostProcessor processor = new MethodValidationPostProcessor();
        processor.setValidator(validator);
        return processor;
    }


    @Bean
    @ConditionalOnMissingBean
    public RestExceptionHandler restExceptionHandler() {
        return new RestExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiResponseBodyAdvice apiResponseBodyAdvice(ObjectMapper objectMapper, MvcProperties properties) {
        return new ApiResponseBodyAdvice(objectMapper, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public WebMvcConfigurer baseWebMvcConfigurer(ObjectMapper objectMapper,
                                                 MvcProperties properties,
                                                 ObjectProvider<LocaleChangeInterceptor> localeChangeInterceptorProvider,
                                                 RequestLoggingInterceptor requestLoggingInterceptor,
                                                 Validator validator) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(@NonNull InterceptorRegistry registry) {
                localeChangeInterceptorProvider.orderedStream().forEach(registry::addInterceptor);
                registry.addInterceptor(requestLoggingInterceptor);
            }

            @Override
            public void addCorsMappings(@NonNull CorsRegistry registry) {
                if (!properties.getCors().isEnabled()) {
                    return;
                }
                var cors = properties.getCors();
                registry.addMapping(cors.getPathPattern())
                        .allowedOrigins(cors.getAllowedOrigins().toArray(String[]::new))
                        .allowedMethods(cors.getAllowedMethods().toArray(String[]::new))
                        .allowedHeaders(cors.getAllowedHeaders().toArray(String[]::new))
                        .exposedHeaders(cors.getExposedHeaders().toArray(String[]::new))
                        .allowCredentials(cors.isAllowCredentials())
                        .maxAge(cors.getMaxAge().toSeconds());
            }

            @Override
            public void addFormatters(@NonNull FormatterRegistry registry) {
                registry.addConverter(String.class, String.class, source -> {
                    if (!StringUtils.hasText(source)) {
                        return null;
                    }
                    return source.trim();
                });
            }

            @Override
            public void extendMessageConverters(@NonNull java.util.List<HttpMessageConverter<?>> converters) {
                converters.stream()
                        .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                        .map(MappingJackson2HttpMessageConverter.class::cast)
                        .findFirst()
                        .ifPresent(converter -> {
                            converter.setObjectMapper(objectMapper);
                            converter.setDefaultCharset(StandardCharsets.UTF_8);
                        });

                converters.stream()
                        .filter(StringHttpMessageConverter.class::isInstance)
                        .map(StringHttpMessageConverter.class::cast)
                        .findFirst()
                        .ifPresent(converter -> converter.setDefaultCharset(StandardCharsets.UTF_8));
            }

            @Override
            public Validator getValidator() {
                return validator;
            }
        };
    }
}
