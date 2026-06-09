package com.acaboumony.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class HttpClientConfig {

    @Bean
    RestTemplate mpOAuthRestTemplate() {
        return new RestTemplate();
    }
}
