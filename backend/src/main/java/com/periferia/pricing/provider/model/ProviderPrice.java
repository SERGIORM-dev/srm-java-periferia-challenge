package com.periferia.pricing.provider.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

public record ProviderPrice(String sku, BigDecimal basePrice, String currency,
                            Instant lastUpdated, ProviderId provider) {

    public ProviderPrice {
        if (sku == null || sku.isBlank() || basePrice == null || basePrice.signum() < 0
                || currency == null || lastUpdated == null || provider == null) {
            throw new IllegalArgumentException("El precio del proveedor está incompleto o es inválido.");
        }
        Currency.getInstance(currency);
    }
}
