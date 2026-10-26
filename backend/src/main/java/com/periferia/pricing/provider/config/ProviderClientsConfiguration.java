package com.periferia.pricing.provider.config;

import com.periferia.pricing.provider.client.ProviderAClient;
import com.periferia.pricing.provider.client.ProviderBClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProviderProperties.class)
public class ProviderClientsConfiguration {

    @Bean
    public ProviderAClient providerAClient(RestClient.Builder builder, ProviderProperties properties) {
        return new ProviderAClient(buildClient(builder, properties.a()));
    }

    @Bean
    public ProviderBClient providerBClient(RestClient.Builder builder, ProviderProperties properties) {
        return new ProviderBClient(buildClient(builder, properties.b()));
    }

    private RestClient buildClient(RestClient.Builder builder, ProviderProperties.Endpoint endpoint) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(endpoint.connectTimeoutMs());
        factory.setReadTimeout(endpoint.readTimeoutMs());
        return builder.clone().baseUrl(endpoint.baseUrl().toString()).requestFactory(factory).build();
    }
}
