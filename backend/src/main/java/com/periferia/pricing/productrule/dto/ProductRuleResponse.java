package com.periferia.pricing.productrule.dto;

import com.periferia.pricing.productrule.model.PreferredProvider;
import com.periferia.pricing.productrule.model.ProductRule;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductRuleResponse(Long id, String sku, String name,
                                  BigDecimal marginPercentage, PreferredProvider preferredProvider,
                                  boolean active, Instant updatedAt) {

    public static ProductRuleResponse from(ProductRule rule) {
        return new ProductRuleResponse(rule.getId(), rule.getSku(), rule.getName(),
                rule.getMarginPercentage(), rule.getPreferredProvider(), rule.isActive(),
                rule.getUpdatedAt());
    }
}
