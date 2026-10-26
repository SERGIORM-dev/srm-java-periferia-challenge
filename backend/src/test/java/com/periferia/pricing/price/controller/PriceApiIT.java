package com.periferia.pricing.price.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.periferia.pricing.productrule.model.PreferredProvider;
import com.periferia.pricing.productrule.repository.ProductRuleRepository;
import com.periferia.pricing.support.MySqlTestContainers;
import com.periferia.pricing.support.WireMockTestContainers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.level.com.periferia.pricing.common.web.ApiExceptionHandler=OFF")
@Testcontainers
class PriceApiIT {

    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
    private static final String SKU = "GAS-CORRIENTE";
    private static final String PRICE_URL = "/api/v1/prices?sku=" + SKU;

    @Container static final MySQLContainer<?> MYSQL = MySqlTestContainers.pricingDatabase();
    @Container static final GenericContainer<?> A = WireMockTestContainers.provider("provider-a");
    @Container static final GenericContainer<?> B = WireMockTestContainers.provider("provider-b");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("pricing.providers.a.base-url", () -> WireMockTestContainers.baseUrl(A));
        registry.add("pricing.providers.b.base-url", () -> WireMockTestContainers.baseUrl(B));
    }

    @Autowired private TestRestTemplate http;
    @Autowired private ProductRuleRepository repository;
    @MockitoBean private Clock clock;

    @BeforeEach
    void resetScenario() {
        when(clock.instant()).thenReturn(NOW);
        var rule = repository.findBySku(SKU).orElseThrow();
        rule.updateDetails("Gasolina corriente", new BigDecimal("10.00"), PreferredProvider.AUTO, true);
        repository.saveAndFlush(rule);
        for (var provider : new GenericContainer<?>[]{A, B}) {
            admin(provider).delete().uri("/__admin/requests").retrieve().toBodilessEntity();
        }
        stub(A, 200, NOW);
        stub(B, 200, NOW);
    }

    @Test
    void returnsFreshAWithMarginAndDoesNotCallB() {
        JsonNode body = successfulPrice(PRICE_URL);

        assertThat(body.path("sku").asText()).isEqualTo(SKU);
        assertThat(body.path("productName").asText()).isEqualTo("Gasolina corriente");
        assertThat(body.path("providerPrice").decimalValue()).isEqualByComparingTo("100");
        assertThat(body.path("marginPercentage").decimalValue()).isEqualByComparingTo("10");
        assertThat(body.path("finalPrice").decimalValue()).isEqualByComparingTo("110");
        assertThat(body.path("currency").asText()).isEqualTo("USD");
        assertThat(body.path("source").asText()).isEqualTo("PROVIDER_A");
        assertThat(body.path("stale").asBoolean()).isFalse();
        assertThat(body.path("responseTimeMs").asLong()).isNotNegative();
        assertThat(calls(A)).isEqualTo(1);
        assertThat(calls(B)).isZero();
    }

    @Test
    void normalizesTheSkuBeforeLookingUpAndCallingProviders() {
        ResponseEntity<JsonNode> response = http.getForEntity("/api/v1/prices?sku={sku}",
                JsonNode.class, " gas-corriente ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("sku").asText()).isEqualTo(SKU);
        assertThat(calls(A)).isEqualTo(1);
    }

    @Test
    void usesTheConfiguredBPreference() {
        preferB();

        JsonNode body = successfulPrice(PRICE_URL);

        assertThat(body.path("source").asText()).isEqualTo("PROVIDER_B");
        assertThat(body.path("finalPrice").decimalValue()).isEqualByComparingTo("112.20");
        assertThat(calls(A)).isZero();
        assertThat(calls(B)).isEqualTo(1);
    }

    @Test
    void usesFreshBWhenAIsStale() {
        stub(A, 200, NOW.minusSeconds(1200));

        JsonNode body = successfulPrice(PRICE_URL);

        assertThat(body.path("source").asText()).isEqualTo("PROVIDER_B");
        assertThat(body.path("stale").asBoolean()).isFalse();
        assertThat(calls(A)).isEqualTo(1);
        assertThat(calls(B)).isEqualTo(1);
    }

    @Test
    void usesBWhenAReturns503() {
        stub(A, 503, NOW);

        assertThat(successfulPrice(PRICE_URL).path("source").asText()).isEqualTo("PROVIDER_B");
    }

    @Test
    void usesAWhenPreferredBReturns429() {
        preferB();
        stub(B, 429, NOW);

        assertThat(successfulPrice(PRICE_URL).path("source").asText()).isEqualTo("PROVIDER_A");
        assertThat(calls(A)).isEqualTo(1);
        assertThat(calls(B)).isEqualTo(1);
    }

    @Test
    void returnsStaleAWhenBIsUnavailable() {
        stub(A, 200, NOW.minusSeconds(1200));
        stub(B, 503, NOW);

        JsonNode body = successfulPrice(PRICE_URL);

        assertThat(body.path("source").asText()).isEqualTo("PROVIDER_A");
        assertThat(body.path("stale").asBoolean()).isTrue();
        assertThat(body.path("finalPrice").decimalValue()).isEqualByComparingTo("110");
    }

    @Test
    void prefersStaleBOverStaleA() {
        stub(A, 200, NOW.minusSeconds(1200));
        stub(B, 200, NOW.minusSeconds(1800));

        JsonNode body = successfulPrice(PRICE_URL);

        assertThat(body.path("source").asText()).isEqualTo("PROVIDER_B");
        assertThat(body.path("stale").asBoolean()).isTrue();
    }

    @Test
    void returnsControlled503WhenNeitherProviderHasAPrice() {
        stub(A, 503, NOW);
        stub(B, 503, NOW);

        ResponseEntity<String> response = http.getForEntity(PRICE_URL, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).contains("Precio no disponible").doesNotContain("private_details");
    }

    @Test
    void blocksUnknownAndInactiveProductsBeforeCallingProviders() {
        assertThat(http.getForEntity("/api/v1/prices?sku=NO-EXISTE", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.getForEntity("/api/v1/prices?sku=ADIT-LIMPIADOR", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(calls(A)).isZero();
        assertThat(calls(B)).isZero();
    }

    @Test
    void rejectsMissingAndBlankSkuBeforeCallingProviders() {
        assertThat(http.getForEntity("/api/v1/prices", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.getForEntity("/api/v1/prices?sku={sku}", JsonNode.class, " ").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(calls(A)).isZero();
        assertThat(calls(B)).isZero();
    }

    @Test
    void observesCrudChangesOnTheNextQuery() {
        assertThat(successfulPrice(PRICE_URL).path("finalPrice").decimalValue()).isEqualByComparingTo("110");
        Map<String, Object> update = Map.of("name", "Gasolina nueva", "marginPercentage", 20,
                "preferredProvider", "PROVIDER_B", "active", true);
        assertThat(http.exchange("/api/v1/product-rules/" + SKU, HttpMethod.PUT,
                new HttpEntity<>(update), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = successfulPrice(PRICE_URL);

        assertThat(body.path("productName").asText()).isEqualTo("Gasolina nueva");
        assertThat(body.path("marginPercentage").decimalValue()).isEqualByComparingTo("20");
        assertThat(body.path("source").asText()).isEqualTo("PROVIDER_B");
        assertThat(body.path("finalPrice").decimalValue()).isEqualByComparingTo("122.40");
    }

    private JsonNode successfulPrice(String url) {
        ResponseEntity<JsonNode> response = http.getForEntity(url, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private void preferB() {
        var rule = repository.findBySku(SKU).orElseThrow();
        rule.updateDetails(rule.getName(), rule.getMarginPercentage(), PreferredProvider.PROVIDER_B, true);
        repository.saveAndFlush(rule);
    }

    private static void stub(GenericContainer<?> provider, int status, Instant timestamp) {
        RestClient admin = admin(provider);
        admin.delete().uri("/__admin/mappings").retrieve().toBodilessEntity();
        Map<String, Object> body = status != 200 ? Map.of("error", "private_details")
                : provider == A
                ? Map.of("sku", SKU, "basePrice", new BigDecimal("100.00"), "currency", "USD",
                        "lastUpdated", timestamp.toString())
                : Map.of("itemCode", SKU, "cost", new BigDecimal("102.00"), "curr", "USD",
                        "timestamp", timestamp.getEpochSecond());
        admin.post().uri("/__admin/mappings").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("request", Map.of("method", "GET", "urlPath", "/api/prices"),
                        "response", Map.of("status", status,
                                "headers", Map.of("Content-Type", "application/json"), "jsonBody", body)))
                .retrieve().toBodilessEntity();
    }

    private static int calls(GenericContainer<?> provider) {
        return admin(provider).get().uri("/__admin/requests").retrieve()
                .body(JsonNode.class).path("requests").size();
    }

    private static RestClient admin(GenericContainer<?> provider) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(5000);
        return RestClient.builder().baseUrl(WireMockTestContainers.baseUrl(provider))
                .requestFactory(factory).build();
    }
}
