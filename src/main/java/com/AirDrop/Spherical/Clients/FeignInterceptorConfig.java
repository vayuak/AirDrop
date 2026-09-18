package com.AirDrop.Spherical.Clients;

import feign.RequestInterceptor;
import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignInterceptorConfig {

    // 🟢 SECURE PRACTICE: Pulling both keys from the environment
    @Value("${ghost.shield.key}")
    private String shieldSecret;

    @Value("${ghost.gateway.secret}")
    private String gatewaySecret;

    @Bean
    public RequestInterceptor gatewayShieldInterceptor() {
        return requestTemplate -> {
            // 🟢 CORRECT HEADERS: Injects both signatures so target services allow access
            requestTemplate.header("X-Ghost-Shield-Key", shieldSecret);
            requestTemplate.header("X-Gateway-Secret", gatewaySecret);
        };
    }

    @Bean
    public Encoder feignFormEncoder(ObjectFactory<HttpMessageConverters> messageConverters) {
        // Enables multipart/form-data encoding for cross-service file transfers
        return new SpringFormEncoder(new SpringEncoder(messageConverters));
    }
}