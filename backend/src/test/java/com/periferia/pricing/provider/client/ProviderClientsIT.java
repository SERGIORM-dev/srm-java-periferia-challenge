package com.periferia.pricing.provider.client;

import com.periferia.pricing.provider.config.ProviderClientsConfiguration;
import com.periferia.pricing.provider.config.ProviderProperties;
import com.periferia.pricing.provider.exception.ProviderException;
import com.periferia.pricing.provider.exception.ProviderException.Reason;
import com.periferia.pricing.provider.model.ProviderId;
import com.periferia.pricing.provider.model.ProviderPrice;
import com.periferia.pricing.support.WireMockTestContainers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.periferia.pricing.support.WireMockTestContainers.baseUrl;

@Testcontainers
class ProviderClientsIT {

    @Container
    static final GenericContainer<?> A = WireMockTestContainers.provider("provider-a");

    @Container
    static final GenericContainer<?> B = WireMockTestContainers.provider("provider-b");

    @ParameterizedTest
    @EnumSource(ProviderId.class)
    void readsTheFourDemoProductsOverRealHttp(ProviderId provider) {
        PriceProviderClient client = client(provider, RestClient.builder()
                .defaultHeader("X-Demo-Freshness", "fresh"), 250, 1000);
        Map<String, String> prices = provider == ProviderId.PROVIDER_A
                ? Map.of("GAS-CORRIENTE", "100", "GAS-EXTRA", "120",
                         "ADIT-OCTANAJE", "25", "ADIT-LIMPIADOR", "15")
                : Map.of("GAS-CORRIENTE", "102", "GAS-EXTRA", "123",
                         "ADIT-OCTANAJE", "26", "ADIT-LIMPIADOR", "16");

        prices.forEach((sku, expected) -> {
            Instant before = Instant.now();
            ProviderPrice price = client.fetchPrice(sku);

            assertThat(price.sku()).isEqualTo(sku);
            assertThat(price.basePrice()).isEqualByComparingTo(expected);
            assertThat(price.currency()).isEqualTo("USD");
            assertThat(price.provider()).isEqualTo(provider);
            assertThat(price.lastUpdated()).isBetween(before.minusSeconds(1), Instant.now());
        });
    }

    @Test
    void preservesTheStaleTimestampProvidedByA() {
        PriceProviderClient client = client(ProviderId.PROVIDER_A, RestClient.builder()
                .defaultHeader("X-Demo-Freshness", "stale"), 250, 1000);

        ProviderPrice price = client.fetchPrice("GAS-CORRIENTE");

        assertThat(Duration.between(price.lastUpdated(), Instant.now()).toSeconds())
                .isBetween(1199L, 1202L);
    }

    @ParameterizedTest
    @EnumSource(ProviderId.class)
    void translatesRealProviderFailures(ProviderId provider) {
        PriceProviderClient client = client(provider, RestClient.builder()
                .defaultHeader("X-Demo-Status", "503"), 250, 1000);

        assertThatThrownBy(() -> client.fetchPrice("GAS-CORRIENTE"))
                .isInstanceOfSatisfying(ProviderException.class, exception -> {
                    assertThat(exception.getProvider()).isEqualTo(provider);
                    assertThat(exception.getReason()).isEqualTo(Reason.HTTP_ERROR);
                    assertThat(exception.getHttpStatus()).isEqualTo(503);
                });
    }

    @ParameterizedTest
    @EnumSource(ProviderId.class)
    void translatesAnUnknownSku(ProviderId provider) {
        PriceProviderClient client = client(provider, RestClient.builder(), 250, 1000);

        assertThatThrownBy(() -> client.fetchPrice("NO-EXISTE"))
                .isInstanceOfSatisfying(ProviderException.class, exception ->
                        assertThat(exception.getReason()).isEqualTo(Reason.NOT_FOUND));
    }

    @ParameterizedTest
    @EnumSource(ProviderId.class)
    void enforcesTheConfiguredReadTimeout(ProviderId provider) {
        String baseUrl = baseUrl(provider == ProviderId.PROVIDER_A ? A : B);
        SimpleClientHttpRequestFactory adminFactory = new SimpleClientHttpRequestFactory();
        adminFactory.setConnectTimeout(1000);
        adminFactory.setReadTimeout(5000);
        RestClient.builder().baseUrl(baseUrl).requestFactory(adminFactory).build()
                .post().uri("/__admin/mappings").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("priority", 1,
                        "request", Map.of("method", "GET", "url", "/api/prices?sku=TIMEOUT"),
                        "response", Map.of("status", 200, "fixedDelayMilliseconds", 1000,
                                "jsonBody", Map.of("sku", "TIMEOUT"))))
                .retrieve().toBodilessEntity();
        PriceProviderClient client = client(provider, RestClient.builder(), 50, 50);

        assertThatThrownBy(() -> client.fetchPrice("TIMEOUT"))
                .isInstanceOfSatisfying(ProviderException.class, exception -> {
                    assertThat(exception.getProvider()).isEqualTo(provider);
                    assertThat(exception.getReason()).isEqualTo(Reason.TIMEOUT);
                });
    }

    private static PriceProviderClient client(ProviderId provider, RestClient.Builder builder,
                                              int readTimeoutA, int readTimeoutB) {
        ProviderProperties properties = new ProviderProperties(
                new ProviderProperties.Endpoint(URI.create(baseUrl(A)), 200, readTimeoutA),
                new ProviderProperties.Endpoint(URI.create(baseUrl(B)), 200, readTimeoutB));
        ProviderClientsConfiguration configuration = new ProviderClientsConfiguration();
        return provider == ProviderId.PROVIDER_A
                ? configuration.providerAClient(builder, properties)
                : configuration.providerBClient(builder, properties);
    }

}
