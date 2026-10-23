package com.enterprise.billing.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final QuotaEnforcementInterceptor quotaEnforcementInterceptor;

    public WebMvcConfig(QuotaEnforcementInterceptor quotaEnforcementInterceptor) {
        this.quotaEnforcementInterceptor = quotaEnforcementInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(quotaEnforcementInterceptor)
                .addPathPatterns("/api/**");
    }
}
