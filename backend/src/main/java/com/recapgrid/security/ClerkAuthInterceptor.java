package com.recapgrid.security;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.clerk.backend_api.helpers.jwks.AuthenticateRequest;
import com.clerk.backend_api.helpers.jwks.AuthenticateRequestOptions;
import com.clerk.backend_api.helpers.jwks.RequestState;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ClerkAuthInterceptor implements HandlerInterceptor {
    public static final String SECRET_KEY = System.getenv("CLERK_SECRET_KEY");
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws IOException{
        String auth = req.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header");
            return false;
        }
        Map<String, List<String>> headers = Collections.list(req.getHeaderNames())
            .stream()
            .collect(Collectors.toMap(
                name -> name, 
                name -> Collections.list(req.getHeaders(name))
        ));
        RequestState state = AuthenticateRequest.authenticateRequest(
            headers,
            AuthenticateRequestOptions
                .secretKey(SECRET_KEY)
                .build()
        );
        if(!state.isSignedIn()){
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid Clerk token");
            return false;
        }
        String clerkUserId = state.claims().map(
            claims -> claims.getSubject())
            .orElseThrow(() -> new IllegalStateException("No sub claim"));
        String userIdParam = req.getParameter("userId");
        if (userIdParam != null && !userIdParam.equals(clerkUserId)) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "User ID does not match");
            return false;
        }
        return true;
    }
}
