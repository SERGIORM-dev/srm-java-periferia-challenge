package com.periferia.pricing.price.service;

import com.periferia.pricing.price.dto.PriceResponse;
import com.periferia.pricing.price.exception.InactiveProductException;
import com.periferia.pricing.price.exception.PriceUnavailableException;
import com.periferia.pricing.price.model.PriceSource;
import com.periferia.pricing.productrule.exception.ProductRuleNotFoundException;
import com.periferia.pricing.productrule.model.PreferredProvider;
import com.periferia.pricing.productrule.model.ProductRule;
import com.periferia.pricing.productrule.service.ProductRuleService;
import com.periferia.pricing.provider.client.PriceProviderClient;
import com.periferia.pricing.provider.exception.ProviderException;
import com.periferia.pricing.provider.exception.ProviderException.Reason;
import com.periferia.pricing.provider.model.ProviderId;
import com.periferia.pricing.provider.model.ProviderPrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceServiceTest {

    private static final String SKU = "GAS-CORRIENTE";
    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");

    @Mock private ProductRuleService rules;
    @Mock private PriceProviderClient a;
    @Mock private PriceProviderClient b;
    private PriceService service;

    @BeforeEach
    void setUp() {
        service = new PriceService(rules, a, b, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @ParameterizedTest
    @EnumSource(PreferredProvider.class)
    void usesThePreferredFreshProviderWithoutCallingTheOther(PreferredProvider preference) {
        rule(preference, "10");
        boolean preferB = preference == PreferredProvider.PROVIDER_B;
        PriceProviderClient first = preferB ? b : a;
        PriceProviderClient other = preferB ? a : b;
        ProviderId provider = preferB ? ProviderId.PROVIDER_B : ProviderId.PROVIDER_A;
        when(first.fetchPrice(SKU)).thenReturn(price(provider, NOW));

        PriceResponse response = service.getPrice(SKU);

        assertThat(response.source().name()).isEqualTo(provider.name());
        assertThat(response.stale()).isFalse();
        assertThat(response.sku()).isEqualTo(SKU);
        assertThat(response.productName()).isEqualTo("Gasolina corriente");
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.finalPrice()).isEqualByComparingTo("110.00");
        assertThat(response.responseTimeMs()).isNotNegative();
        verifyNoInteractions(other);
    }

    @ParameterizedTest
    @EnumSource(Reason.class)
    void fallsBackFromAForEveryControlledFailure(Reason reason) {
        rule(PreferredProvider.AUTO, "10");
        when(a.fetchPrice(SKU)).thenThrow(failure(ProviderId.PROVIDER_A, reason));
        when(b.fetchPrice(SKU)).thenReturn(price(ProviderId.PROVIDER_B, NOW));

        assertThat(service.getPrice(SKU).source()).isEqualTo(PriceSource.PROVIDER_B);
        var order = inOrder(a, b);
        order.verify(a).fetchPrice(SKU);
        order.verify(b).fetchPrice(SKU);
    }

    @Test
    void fallsBackFromBToA() {
        rule(PreferredProvider.PROVIDER_B, "10");
        when(b.fetchPrice(SKU)).thenThrow(failure(ProviderId.PROVIDER_B, Reason.UNAVAILABLE));
        when(a.fetchPrice(SKU)).thenReturn(price(ProviderId.PROVIDER_A, NOW));

        assertThat(service.getPrice(SKU).source()).isEqualTo(PriceSource.PROVIDER_A);
        var order = inOrder(a, b);
        order.verify(b).fetchPrice(SKU);
        order.verify(a).fetchPrice(SKU);
    }

    @ParameterizedTest
    @EnumSource(PreferredProvider.class)
    void replacesThePreferredStalePriceWithTheOtherFreshPrice(PreferredProvider preference) {
        rule(preference, "10");
        boolean preferB = preference == PreferredProvider.PROVIDER_B;
        when(a.fetchPrice(SKU)).thenReturn(price(ProviderId.PROVIDER_A,
                preferB ? NOW : NOW.minusSeconds(1200)));
        when(b.fetchPrice(SKU)).thenReturn(price(ProviderId.PROVIDER_B,
                preferB ? NOW.minusSeconds(1200) : NOW));

        PriceResponse response = service.getPrice(SKU);

        assertThat(response.source()).isEqualTo(preferB ? PriceSource.PROVIDER_A : PriceSource.PROVIDER_B);
        assertThat(response.stale()).isFalse();
    }

    @Test
    void acceptsExactlyFifteenMinutesAsFresh() {
        rule(PreferredProvider.AUTO, "10");
        when(a.fetchPrice(SKU)).thenReturn(price(ProviderId.PROVIDER_A, NOW.minusSeconds(900)));

        assertThat(service.getPrice(SKU).stale()).isFalse();
        verifyNoInteractions(b);
    }

    @Test
    void considersEvenOneNanosecondBeyondFifteenMinutesStale() {
        rule(PreferredProvider.AUTO, "10");
        when(a.fetchPrice(SKU)).thenReturn(price(ProviderId.PROVIDER_A, NOW.minusSeconds(900).minusNanos(1)));
        when(b.fetchPrice(SKU)).thenReturn(price(ProviderId.PROVIDER_B, NOW));

        assertThat(service.getPrice(SKU).source()).isEqualTo(PriceSource.PROVIDER_B);
    }

    @ParameterizedTest
    @EnumSource(PreferredProvider.class)
    void prioritizesAuthoritativeBWhenBothPricesAreStale(PreferredProvider preference) {
        rule(preference, "10");
        when(a.fetchPrice(SKU)).thenReturn(price(ProviderId.PROVIDER_A, NOW.minusSeconds(1200)));
        when(b.fetchPrice(SKU)).thenReturn(price(ProviderId.PROVIDER_B, NOW.minusSeconds(1800)));

        PriceResponse response = service.getPrice(SKU);

        assertThat(response.source()).isEqualTo(PriceSource.PROVIDER_B);
        assertThat(response.stale()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ProviderId.class)
    void returnsTheAvailableStalePriceWhenTheOtherFails(ProviderId available) {
        rule(PreferredProvider.AUTO, "10");
        when(a.fetchPrice(SKU)).thenAnswer(invocation -> {
            if (available == ProviderId.PROVIDER_A) return price(available, NOW.minusSeconds(1200));
            throw failure(ProviderId.PROVIDER_A, Reason.UNAVAILABLE);
        });
        when(b.fetchPrice(SKU)).thenAnswer(invocation -> {
            if (available == ProviderId.PROVIDER_B) return price(available, NOW.minusSeconds(1200));
            throw failure(ProviderId.PROVIDER_B, Reason.UNAVAILABLE);
        });

        PriceResponse response = service.getPrice(SKU);

        assertThat(response.source().name()).isEqualTo(available.name());
        assertThat(response.stale()).isTrue();
    }

    @Test
    void reportsBothFailuresWithoutReturningAnInventedPrice() {
        rule(PreferredProvider.AUTO, "10");
        when(a.fetchPrice(SKU)).thenThrow(failure(ProviderId.PROVIDER_A, Reason.TIMEOUT));
        when(b.fetchPrice(SKU)).thenThrow(failure(ProviderId.PROVIDER_B, Reason.RATE_LIMITED));

        assertThatThrownBy(() -> service.getPrice(SKU))
                .isInstanceOfSatisfying(PriceUnavailableException.class, exception -> {
                    assertThat(exception.getSuppressed()).hasSize(2);
                    assertThat(exception.getMessage()).doesNotContain("private_details");
                });
    }

    @ParameterizedTest
    @ValueSource(longs = {31, 1793016000000L})
    void rejectsImplausiblyFutureDatesAndTriesB(long secondsAhead) {
        rule(PreferredProvider.AUTO, "10");
        when(a.fetchPrice(SKU)).thenReturn(price(ProviderId.PROVIDER_A, NOW.plusSeconds(secondsAhead)));
        when(b.fetchPrice(SKU)).thenReturn(price(ProviderId.PROVIDER_B, NOW));

        assertThat(service.getPrice(SKU).source()).isEqualTo(PriceSource.PROVIDER_B);
    }

    @Test
    void allowsThirtySecondsOfClockSkew() {
        rule(PreferredProvider.AUTO, "10");
        when(a.fetchPrice(SKU)).thenReturn(price(ProviderId.PROVIDER_A, NOW.plusSeconds(30)));

        assertThat(service.getPrice(SKU).stale()).isFalse();
        verifyNoInteractions(b);
    }

    @ParameterizedTest
    @CsvSource({"100,10,110.00", "100,0,100.00", "100,150,250.00",
                "100,99999.99,100099.99", "10.005,0,10.01", "100.125,10,110.14", "0,20,0.00"})
    void calculatesTheMarginWithOneFinalHalfUpRounding(String base, String margin, String expected) {
        rule(PreferredProvider.AUTO, margin);
        when(a.fetchPrice(SKU)).thenReturn(new ProviderPrice(SKU, new BigDecimal(base),
                "USD", NOW, ProviderId.PROVIDER_A));

        PriceResponse response = service.getPrice(SKU);

        assertThat(response.providerPrice()).isEqualByComparingTo(base);
        assertThat(response.marginPercentage()).isEqualByComparingTo(margin);
        assertThat(response.finalPrice()).isEqualTo(new BigDecimal(expected));
    }

    @Test
    void doesNotConsultProvidersForInactiveProducts() {
        when(rules.findBySku(SKU)).thenReturn(new ProductRule(SKU, "Gasolina", BigDecimal.TEN,
                PreferredProvider.AUTO, false));

        assertThatThrownBy(() -> service.getPrice(SKU)).isInstanceOf(InactiveProductException.class);
        verifyNoInteractions(a, b);
    }

    @Test
    void doesNotConsultProvidersForUnknownProducts() {
        when(rules.findBySku(SKU)).thenThrow(new ProductRuleNotFoundException(SKU));

        assertThatThrownBy(() -> service.getPrice(SKU)).isInstanceOf(ProductRuleNotFoundException.class);
        verifyNoInteractions(a, b);
    }

    @Test
    void passesTheCanonicalSkuFromTheRuleToTheProvider() {
        when(rules.findBySku(" gas-corriente ")).thenReturn(new ProductRule(SKU, "Gasolina",
                BigDecimal.TEN, PreferredProvider.AUTO, true));
        when(a.fetchPrice(SKU)).thenReturn(price(ProviderId.PROVIDER_A, NOW));

        assertThat(service.getPrice(" gas-corriente ").sku()).isEqualTo(SKU);
    }

    private void rule(PreferredProvider preference, String margin) {
        when(rules.findBySku(SKU)).thenReturn(new ProductRule(SKU, "Gasolina corriente",
                new BigDecimal(margin), preference, true));
    }

    private static ProviderPrice price(ProviderId provider, Instant updated) {
        return new ProviderPrice(SKU, new BigDecimal("100.00"), "USD", updated, provider);
    }

    private static ProviderException failure(ProviderId provider, Reason reason) {
        return new ProviderException(provider, reason, null, new IllegalStateException("private_details"));
    }
}
