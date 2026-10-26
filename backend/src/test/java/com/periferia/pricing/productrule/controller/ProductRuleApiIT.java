package com.periferia.pricing.productrule.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.periferia.pricing.productrule.repository.ProductRuleRepository;
import com.periferia.pricing.support.MySqlTestContainers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ProductRuleApiIT {

    private static final String BASE = "/api/v1/product-rules";

    @Container
    static final MySQLContainer<?> MYSQL = MySqlTestContainers.pricingDatabase();

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ProductRuleRepository repository;

    @Test
    void completesCrudOverHttpWithNormalizationAndSkuReuse() {
        ResponseEntity<JsonNode> creation = http.postForEntity(BASE,
                createRequest(" gas-api-prueba "), JsonNode.class);
        assertThat(creation.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode created = creation.getBody();
        assertThat(created).isNotNull();
        long originalId = created.path("id").asLong();
        assertThat(originalId).isPositive();
        assertThat(created.path("sku").asText()).isEqualTo("GAS-API-PRUEBA");
        Instant createdAt = Instant.parse(created.path("updatedAt").asText());
        assertThat(repository.existsBySku("GAS-API-PRUEBA")).isTrue();

        ResponseEntity<JsonNode> listing = http.getForEntity(BASE, JsonNode.class);
        assertThat(listing.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listing.getBody()).hasSize(5);
        assertThat(listing.getBody().get(0).path("sku").asText()).isEqualTo("ADIT-LIMPIADOR");
        assertThat(listing.getBody().get(0).path("active").asBoolean()).isFalse();

        Map<String, Object> updateRequest = Map.of(
                "name", "Gasolina API actualizada", "marginPercentage", new BigDecimal("150.00"),
                "preferredProvider", "PROVIDER_B", "active", false);
        ResponseEntity<JsonNode> update = http.exchange(BASE + "/gas-api-prueba", HttpMethod.PUT,
                new HttpEntity<>(updateRequest), JsonNode.class);
        assertThat(update.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(update.getBody().path("id").asLong()).isEqualTo(originalId);
        assertThat(update.getBody().path("sku").asText()).isEqualTo("GAS-API-PRUEBA");
        assertThat(update.getBody().path("name").asText()).isEqualTo("Gasolina API actualizada");
        assertThat(update.getBody().path("marginPercentage").decimalValue())
                .isEqualByComparingTo("150.00");
        assertThat(update.getBody().path("preferredProvider").asText()).isEqualTo("PROVIDER_B");
        assertThat(update.getBody().path("active").asBoolean()).isFalse();
        assertThat(Instant.parse(update.getBody().path("updatedAt").asText()))
                .isAfterOrEqualTo(createdAt);
        assertThat(repository.findBySku("GAS-API-PRUEBA").orElseThrow().isActive()).isFalse();

        ResponseEntity<String> deletion = http.exchange(BASE + "/GAS-API-PRUEBA", HttpMethod.DELETE,
                HttpEntity.EMPTY, String.class);
        assertThat(deletion.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(deletion.getBody()).isNullOrEmpty();
        assertThat(repository.existsBySku("GAS-API-PRUEBA")).isFalse();

        ResponseEntity<JsonNode> replacement = http.postForEntity(BASE,
                createRequest("GAS-API-PRUEBA"), JsonNode.class);
        assertThat(replacement.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replacement.getBody().path("id").asLong()).isNotEqualTo(originalId);
        assertThat(http.exchange(BASE + "/GAS-API-PRUEBA", HttpMethod.DELETE,
                HttpEntity.EMPTY, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void returnsConflictForANormalizedDuplicate() {
        ResponseEntity<JsonNode> response = http.postForEntity(BASE,
                createRequest(" gas-extra "), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().path("status").asInt()).isEqualTo(409);
        assertThat(response.getBody().path("detail").asText()).contains("GAS-EXTRA");
    }

    @Test
    void returnsFieldErrorsWithoutPersistingInvalidMargins() {
        Map<String, Object> request = new LinkedHashMap<>(createRequest("GAS-API-INVALIDO"));
        request.put("marginPercentage", new BigDecimal("-1"));

        ResponseEntity<JsonNode> response = http.postForEntity(BASE, request, JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("errors").has("marginPercentage")).isTrue();
        assertThat(repository.existsBySku("GAS-API-INVALIDO")).isFalse();
    }

    @Test
    void returnsNotFoundForUnknownUpdatesAndDeletes() {
        Map<String, Object> request = Map.of("name", "Gasolina inexistente",
                "marginPercentage", new BigDecimal("10"), "preferredProvider", "AUTO", "active", true);

        ResponseEntity<JsonNode> update = http.exchange(BASE + "/GAS-INEXISTENTE", HttpMethod.PUT,
                new HttpEntity<>(request), JsonNode.class);
        ResponseEntity<JsonNode> deletion = http.exchange(BASE + "/GAS-INEXISTENTE", HttpMethod.DELETE,
                HttpEntity.EMPTY, JsonNode.class);

        assertThat(update.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(deletion.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(deletion.getBody().path("status").asInt()).isEqualTo(404);
    }

    private static Map<String, Object> createRequest(String sku) {
        return Map.of("sku", sku, "name", "Gasolina API", "marginPercentage", new BigDecimal("15.00"),
                "preferredProvider", "PROVIDER_A", "active", true);
    }
}
