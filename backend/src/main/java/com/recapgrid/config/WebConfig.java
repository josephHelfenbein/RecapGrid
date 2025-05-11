package com.recapgrid.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.recapgrid.security.ClerkAuthInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer{
    @Autowired
    private ClerkAuthInterceptor clerkAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry){
        registry.addInterceptor(clerkAuthInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns("/api/clerk-user")
            .excludePathPatterns("/api/delete-user");
    }
}