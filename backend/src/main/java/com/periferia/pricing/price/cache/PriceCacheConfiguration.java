package com.periferia.pricing.price.cache;

import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PriceCacheProperties.class)
public class PriceCacheConfiguration {

    @Bean
    public Ticker cacheTicker() {
        return Ticker.systemTicker();
    }
}
