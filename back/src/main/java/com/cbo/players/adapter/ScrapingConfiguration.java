package com.cbo.players.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class ScrapingConfiguration {
    @Bean
    ScrapingPort scrapingPort(ObjectMapper mapper,
            @Value("${app.scraper.base-url:http://localhost:3000}") String baseUrl,
            @Value("${app.scraper.api-key:}") String apiKey,
            @Value("${app.scraper.connect-timeout-ms:3000}") int connectTimeout,
            @Value("${app.scraper.read-timeout-ms:90000}") int readTimeout) {
        if (connectTimeout <= 0 || readTimeout <= 0) {
            throw new IllegalArgumentException("Scraper timeouts must be positive");
        }
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new ScrapingRestAdapter(RestClient.builder().baseUrl(baseUrl)
                .requestFactory(factory).build(), mapper, apiKey);
    }
}
