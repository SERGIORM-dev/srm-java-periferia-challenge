package com.periferia.pricing.provider.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties("pricing.providers")
public record ProviderProperties(@Valid @NotNull Endpoint a, @Valid @NotNull Endpoint b) {

    public record Endpoint(@NotNull URI baseUrl, @Min(1) int connectTimeoutMs,
                           @Min(1) int readTimeoutMs) {

        @AssertTrue(message = "La URL debe usar HTTP o HTTPS, tener host y no incluir consulta ni fragmento.")
        public boolean isHttpBaseUrl() {
            return baseUrl == null || (("http".equals(baseUrl.getScheme())
                    || "https".equals(baseUrl.getScheme())) && baseUrl.getHost() != null
                    && baseUrl.getRawQuery() == null && baseUrl.getRawFragment() == null);
        }
    }
}
