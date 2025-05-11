package com.recapgrid.config;

import org.springframework.context.annotation.Bean;

import com.clerk.backend_api.Clerk;

public class ClerkConfig {
    @Bean
    public Clerk clerkClient(){
        String serviceRoleKey = System.getenv("CLERK_SECRET_KEY");
        return Clerk.builder()
                .bearerAuth(serviceRoleKey)
                .build();
    }
}
