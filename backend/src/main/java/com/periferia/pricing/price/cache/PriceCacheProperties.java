package com.periferia.pricing.price.cache;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("pricing.cache")
public record PriceCacheProperties(@NotNull Duration freshTtl, @NotNull Duration fallbackTtl,
                                   @Min(1) long maximumSize) {

    @AssertTrue(message = "Los TTL deben ser positivos y el respaldo debe durar al menos tanto como el TTL fresco.")
    public boolean isDurationConfigurationValid() {
        return freshTtl == null || fallbackTtl == null
                || (!freshTtl.isZero() && !freshTtl.isNegative()
                && !fallbackTtl.isZero() && !fallbackTtl.isNegative()
                && freshTtl.compareTo(fallbackTtl) <= 0);
    }
}
