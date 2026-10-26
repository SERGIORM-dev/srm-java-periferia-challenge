package com.periferia.pricing.price.cache;

import com.periferia.pricing.productrule.model.PreferredProvider;
import com.periferia.pricing.productrule.model.ProductRule;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceCacheKey(Long id, String sku, String name, BigDecimal marginPercentage,
                            PreferredProvider preferredProvider, boolean active, Instant updatedAt) {

    public static PriceCacheKey from(ProductRule rule) {
        return new PriceCacheKey(rule.getId(), rule.getSku(), rule.getName(), rule.getMarginPercentage(),
                rule.getPreferredProvider(), rule.isActive(), rule.getUpdatedAt());
    }
}
