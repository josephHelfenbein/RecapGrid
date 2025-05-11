package com.recapgrid.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ClerkWebhookInterceptor implements HandlerInterceptor{
    private static final String SECRET_CLERK_USER   = System.getenv("CLERK_WEBHOOK_SECRET_CLERK_USER");
    private static final String SECRET_DELETE_USER  = System.getenv("CLERK_WEBHOOK_SECRET_DELETE_USER");
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws IOException{
        if(!(req instanceof ContentCachingRequestWrapper)){
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Request not wrapped");
            return false;
        }
        String path = req.getRequestURI();
        String secret;
        if (path.endsWith("/api/clerk-user")) {
          secret = SECRET_CLERK_USER;
        } else if (path.endsWith("/api/delete-user")) {
          secret = SECRET_DELETE_USER;
        } else {
          res.sendError(HttpServletResponse.SC_NOT_FOUND);
          return false;
        }
        ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) req;
        byte[] body = wrapper.getContentAsByteArray();
        byte[] expected;
        try{
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), 
                "HmacSHA256"
            ));
            expected = mac.doFinal(body);
        } catch(Exception e){
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error creating HMAC");
            return false;
        }
        String expectedHex = HexFormat.of().formatHex(expected);
        String signature = req.getHeader("Clerk-Signature");
        if(signature == null || !signature.equals(expectedHex)){
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid signature");
            return false;
        }
        return true;
    }
}
