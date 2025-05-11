package com.recapgrid.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.recapgrid.security.ClerkWebhookInterceptor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class WebhookConfig implements WebMvcConfigurer {
    @Bean
    public OncePerRequestFilter requestWrapperFilter(){
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
            ) throws ServletException, IOException {
                filterChain.doFilter(
                    new ContentCachingRequestWrapper(request), 
                    response
                );
            }
        };
    }
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ClerkWebhookInterceptor())
            .addPathPatterns("/api/clerk-user", "/api/delete-user");
    }
}
