package com.recapgrid.config;

import com.google.cloud.tasks.v2.CloudTasksClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.IOException;

@Configuration
public class CloudTasksConfig {
  @Bean
  public CloudTasksClient cloudTasksClient() throws IOException {
    return CloudTasksClient.create();
  }
}
