package com.periferia.pricing.productrule.service;

import com.periferia.pricing.productrule.exception.DuplicateSkuException;
import com.periferia.pricing.productrule.exception.ProductRuleNotFoundException;
import com.periferia.pricing.productrule.exception.ProductRuleValidationException;
import com.periferia.pricing.productrule.model.PreferredProvider;
import com.periferia.pricing.productrule.model.ProductRule;
import com.periferia.pricing.productrule.repository.ProductRuleRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRuleServiceTest {

    @Mock
    private ProductRuleRepository repository;

    @Mock
    private ApplicationEventPublisher events;

    private ProductRuleService service;

    @BeforeEach
    void setUp() {
        service = new ProductRuleService(repository, events);
    }

    @ParameterizedTest
    @ValueSource(strings = {" gas-extra ", "\tgas-extra\n", "\u00A0gas-extra\u00A0"})
    void normalizesSkuAndNameBeforeCreating(String sku) {
        when(repository.saveAndFlush(any(ProductRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProductRule created = service.create(sku, "  Gasolina extra  ",
                new BigDecimal("15.00"), PreferredProvider.PROVIDER_A, true);

        assertThat(created.getSku()).isEqualTo("GAS-EXTRA");
        assertThat(created.getName()).isEqualTo("Gasolina extra");
        assertThat(created.getPreferredProvider()).isEqualTo(PreferredProvider.PROVIDER_A);
        assertThat(created.isActive()).isTrue();
        verify(repository).existsBySku("GAS-EXTRA");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "150", "99999.99", "10.000"})
    void acceptsValidMarginsWithoutRounding(String value) {
        when(repository.saveAndFlush(any(ProductRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProductRule created = service.create("GAS-PRUEBA", "Gasolina de prueba",
                new BigDecimal(value), PreferredProvider.AUTO, true);

        assertThat(created.getMarginPercentage()).isEqualByComparingTo(value);
        assertThat(created.getMarginPercentage().scale()).isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("invalidRules")
    void rejectsInvalidDataBeforeAccessingTheDatabase(String sku, String name,
            BigDecimal margin, PreferredProvider provider, String field) {
        assertThatThrownBy(() -> service.create(sku, name, margin, provider, true))
                .isInstanceOfSatisfying(ProductRuleValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo(field));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsAnExistingSkuAfterNormalization() {
        when(repository.existsBySku("GAS-EXTRA")).thenReturn(true);

        assertThatThrownBy(() -> service.create(" gas-extra ", "Gasolina extra",
                new BigDecimal("15"), PreferredProvider.AUTO, true))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("GAS-EXTRA");
        verify(repository, never()).saveAndFlush(any(ProductRule.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"uk_product_rules_sku", "product_rules.uk_product_rules_sku"})
    void translatesADuplicateDetectedDuringInsert(String constraint) {
        DataIntegrityViolationException databaseFailure = constraintFailure(constraint);
        when(repository.saveAndFlush(any(ProductRule.class))).thenThrow(databaseFailure);

        assertThatThrownBy(() -> service.create("gas-extra", "Gasolina extra",
                new BigDecimal("15"), PreferredProvider.AUTO, true))
                .isInstanceOf(DuplicateSkuException.class)
                .hasCause(databaseFailure);
    }

    @Test
    void doesNotMisclassifyOtherDatabaseFailuresAsDuplicateSkus() {
        DataIntegrityViolationException databaseFailure = constraintFailure(
                "chk_product_rules_margin");
        when(repository.saveAndFlush(any(ProductRule.class))).thenThrow(databaseFailure);

        assertThatThrownBy(() -> service.create("GAS-EXTRA", "Gasolina extra",
                new BigDecimal("15"), PreferredProvider.AUTO, true))
                .isSameAs(databaseFailure);
    }

    @Test
    void allowsAdministratorsToFindInactiveProducts() {
        ProductRule inactive = rule(false);
        when(repository.findBySku("GAS-EXTRA")).thenReturn(Optional.of(inactive));

        assertThat(service.findBySku(" gas-extra ")).isSameAs(inactive);
        assertThat(inactive.isActive()).isFalse();
    }

    @Test
    void updatesDetailsAndAllowsReactivationWhilePreservingSku() {
        ProductRule existing = rule(false);
        when(repository.findBySku("GAS-EXTRA")).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(existing)).thenReturn(existing);

        ProductRule updated = service.update(" gas-extra ", "  Gasolina extra nueva ",
                new BigDecimal("150"), PreferredProvider.PROVIDER_B, true);

        assertThat(updated.getSku()).isEqualTo("GAS-EXTRA");
        assertThat(updated.getName()).isEqualTo("Gasolina extra nueva");
        assertThat(updated.getMarginPercentage()).isEqualByComparingTo("150.00");
        assertThat(updated.getPreferredProvider()).isEqualTo(PreferredProvider.PROVIDER_B);
        assertThat(updated.isActive()).isTrue();
    }

    @Test
    void rejectsAnInvalidUpdateBeforeLoadingOrChangingTheProduct() {
        assertThatThrownBy(() -> service.update("GAS-EXTRA", "Gasolina extra",
                new BigDecimal("-1"), PreferredProvider.AUTO, true))
                .isInstanceOf(ProductRuleValidationException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void reportsMissingProductsOnRead() {
        when(repository.findBySku("GAS-INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findBySku(" gas-inexistente "))
                .isInstanceOf(ProductRuleNotFoundException.class)
                .hasMessageContaining("GAS-INEXISTENTE");
    }

    @Test
    void reportsMissingProductsOnUpdate() {
        when(repository.findBySku("GAS-INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("gas-inexistente", "Gasolina",
                new BigDecimal("10"), PreferredProvider.AUTO, true))
                .isInstanceOf(ProductRuleNotFoundException.class);
        verify(repository, never()).saveAndFlush(any(ProductRule.class));
    }

    @Test
    void deletesPhysicallyInsteadOfDeactivating() {
        ProductRule existing = rule(true);
        when(repository.findBySku("GAS-EXTRA")).thenReturn(Optional.of(existing));

        service.delete(" gas-extra ");

        verify(repository).delete(existing);
        assertThat(existing.isActive()).isTrue();
    }

    @Test
    void reportsMissingProductsOnDelete() {
        when(repository.findBySku("GAS-INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("gas-inexistente"))
                .isInstanceOf(ProductRuleNotFoundException.class);
        verify(repository, never()).delete(any(ProductRule.class));
    }

    private static ProductRule rule(boolean active) {
        return new ProductRule("GAS-EXTRA", "Gasolina extra", new BigDecimal("15.00"),
                PreferredProvider.PROVIDER_A, active);
    }

    private static DataIntegrityViolationException constraintFailure(String constraint) {
        return new DataIntegrityViolationException("Error de integridad",
                new ConstraintViolationException("Restricción incumplida",
                        new SQLException("Constraint violation", "23000"), constraint));
    }

    private static Stream<Arguments> invalidRules() {
        BigDecimal margin = new BigDecimal("10.00");
        PreferredProvider provider = PreferredProvider.AUTO;
        return Stream.of(
                Arguments.of(null, "Gasolina", margin, provider, "sku"),
                Arguments.of(" \t ", "Gasolina", margin, provider, "sku"),
                Arguments.of("S".repeat(65), "Gasolina", margin, provider, "sku"),
                Arguments.of("GAS-EXTRA", null, margin, provider, "name"),
                Arguments.of("GAS-EXTRA", "\u00A0\t ", margin, provider, "name"),
                Arguments.of("GAS-EXTRA", "N".repeat(151), margin, provider, "name"),
                Arguments.of("GAS-EXTRA", "Gasolina", null, provider, "marginPercentage"),
                Arguments.of("GAS-EXTRA", "Gasolina", new BigDecimal("-0.01"), provider,
                        "marginPercentage"),
                Arguments.of("GAS-EXTRA", "Gasolina", new BigDecimal("100000"), provider,
                        "marginPercentage"),
                Arguments.of("GAS-EXTRA", "Gasolina", new BigDecimal("0.001"), provider,
                        "marginPercentage"),
                Arguments.of("GAS-EXTRA", "Gasolina", margin, null, "preferredProvider")
        );
    }
}
