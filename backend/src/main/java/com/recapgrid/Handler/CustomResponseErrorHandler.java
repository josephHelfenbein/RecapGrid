package com.recapgrid.Handler;

import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.util.StreamUtils;

public class CustomResponseErrorHandler extends DefaultResponseErrorHandler {
    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        System.err.println("Response error: " + response.getStatusCode() + " " + response.getStatusText());
        String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        System.err.println("Response body: " + body);
        super.handleError(response);
    }
}
