package com.periferia.pricing.price.dto;

import com.periferia.pricing.price.model.PriceSource;

import java.math.BigDecimal;

public record PriceResponse(String sku, String productName, BigDecimal providerPrice,
                            BigDecimal marginPercentage, BigDecimal finalPrice,
                            String currency, PriceSource source, boolean stale,
                            long responseTimeMs) {
}
