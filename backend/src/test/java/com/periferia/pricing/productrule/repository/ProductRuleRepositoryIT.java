package com.periferia.pricing.productrule.repository;

import com.periferia.pricing.productrule.model.PreferredProvider;
import com.periferia.pricing.productrule.model.ProductRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ProductRuleRepositoryIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("pricing")
            .withUsername("pricing_test")
            .withPassword("pricing_test_password")
            .withCommand("--default-time-zone=+00:00", "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_0900_as_cs")
            .withUrlParam("connectionTimeZone", "UTC")
            .withUrlParam("forceConnectionTimeZoneToSession", "true")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("database-init/01_create_product_rules.sql"),
                    "/docker-entrypoint-initdb.d/01_create_product_rules.sql")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("database-init/02_insert_demo_products.sql"),
                    "/docker-entrypoint-initdb.d/02_insert_demo_products.sql");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private ProductRuleRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @AfterEach
    void restoreDatabaseClock() {
        setDatabaseClock(0);
    }

    @Test
    void readsSeededProductsFromTheExistingSchema() {
        ProductRule rule = repository.findBySku("GAS-CORRIENTE").orElseThrow();

        assertThat(repository.findAll()).hasSize(4);
        assertThat(rule.getId()).isPositive();
        assertThat(rule.getSku()).isEqualTo("GAS-CORRIENTE");
        assertThat(rule.getName()).isEqualTo("Gasolina corriente");
        assertThat(rule.getMarginPercentage()).isEqualByComparingTo("10.00");
        assertThat(rule.getPreferredProvider()).isEqualTo(PreferredProvider.AUTO);
        assertThat(rule.isActive()).isTrue();
        assertThat(rule.getUpdatedAt()).isNotNull();
    }

    @Test
    void checksExistenceAndReturnsEmptyForAnUnknownSku() {
        assertThat(repository.existsBySku("GAS-EXTRA")).isTrue();
        assertThat(repository.existsBySku("GAS-INEXISTENTE")).isFalse();
        assertThat(repository.findBySku("GAS-INEXISTENTE")).isEmpty();
    }

    @Test
    void createsAndUpdatesARuleWithDatabaseGeneratedDates() {
        setDatabaseClock(1700000000);
        ProductRule rule = repository.saveAndFlush(new ProductRule(
                "GAS-PRUEBA", "Gasolina de prueba", new BigDecimal("0.00"),
                PreferredProvider.PROVIDER_B, false));

        assertThat(rule.getId()).isPositive();
        assertThat(rule.getUpdatedAt()).isEqualTo(Instant.ofEpochSecond(1700000000));

        setDatabaseClock(1700000060);
        rule.updateDetails("Gasolina de prueba actualizada", new BigDecimal("150.00"),
                PreferredProvider.PROVIDER_A, true);
        repository.saveAndFlush(rule);

        assertThat(rule.getUpdatedAt()).isEqualTo(Instant.ofEpochSecond(1700000060));

        entityManager.clear();
        ProductRule reloaded = repository.findBySku("GAS-PRUEBA").orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(rule.getId());
        assertThat(reloaded.getName()).isEqualTo("Gasolina de prueba actualizada");
        assertThat(reloaded.getMarginPercentage()).isEqualByComparingTo("150.00");
        assertThat(reloaded.getPreferredProvider()).isEqualTo(PreferredProvider.PROVIDER_A);
        assertThat(reloaded.isActive()).isTrue();
        assertThat(reloaded.getUpdatedAt()).isEqualTo(Instant.ofEpochSecond(1700000060));
    }

    @Test
    void deactivatesARuleWithoutDeletingIt() {
        ProductRule rule = repository.findBySku("GAS-CORRIENTE").orElseThrow();
        rule.updateDetails(rule.getName(), rule.getMarginPercentage(),
                rule.getPreferredProvider(), false);
        repository.saveAndFlush(rule);
        entityManager.clear();

        ProductRule reloaded = repository.findBySku("GAS-CORRIENTE").orElseThrow();
        assertThat(reloaded.isActive()).isFalse();
        assertThat(repository.count()).isEqualTo(4);
    }

    @Test
    void physicallyDeletesARuleAndAllowsSkuReuse() {
        ProductRule original = repository.findBySku("GAS-EXTRA").orElseThrow();
        repository.delete(original);
        repository.flush();

        assertThat(repository.findBySku("GAS-EXTRA")).isEmpty();

        ProductRule replacement = repository.saveAndFlush(new ProductRule(
                "GAS-EXTRA", "Gasolina extra nueva", new BigDecimal("25.00"),
                PreferredProvider.PROVIDER_B, true));
        entityManager.clear();

        assertThat(replacement.getId()).isNotEqualTo(original.getId());
        assertThat(repository.findBySku("GAS-EXTRA").orElseThrow().getName())
                .isEqualTo("Gasolina extra nueva");
        assertThat(repository.count()).isEqualTo(4);
    }

    private void setDatabaseClock(long epochSeconds) {
        entityManager.getEntityManager()
                .createNativeQuery("SET timestamp = " + epochSeconds)
                .executeUpdate();
    }
}
