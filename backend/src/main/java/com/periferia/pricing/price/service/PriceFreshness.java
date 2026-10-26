package com.periferia.pricing.price.service;

import com.periferia.pricing.provider.model.ProviderPrice;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

@Component
public class PriceFreshness {

    private static final Duration MAX_PRICE_AGE = Duration.ofMinutes(15);
    private static final Duration MAX_FUTURE_SKEW = Duration.ofSeconds(30);
    private final Clock clock;

    public PriceFreshness(Clock clock) {
        this.clock = clock;
    }

    public boolean isStale(ProviderPrice price) {
        return price.lastUpdated().isBefore(clock.instant().minus(MAX_PRICE_AGE));
    }

    public boolean isFutureInvalid(ProviderPrice price) {
        return price.lastUpdated().isAfter(clock.instant().plus(MAX_FUTURE_SKEW));
    }
}
