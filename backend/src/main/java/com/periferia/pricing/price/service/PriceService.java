package com.periferia.pricing.price.service;

import com.periferia.pricing.price.dto.PriceResponse;
import com.periferia.pricing.price.cache.PriceCache;
import com.periferia.pricing.price.cache.PriceCacheKey;
import com.periferia.pricing.price.exception.InactiveProductException;
import com.periferia.pricing.price.exception.PriceUnavailableException;
import com.periferia.pricing.price.model.PriceSource;
import com.periferia.pricing.productrule.model.PreferredProvider;
import com.periferia.pricing.productrule.model.ProductRule;
import com.periferia.pricing.productrule.service.ProductRuleService;
import com.periferia.pricing.provider.client.PriceProviderClient;
import com.periferia.pricing.provider.exception.ProviderException;
import com.periferia.pricing.provider.model.ProviderId;
import com.periferia.pricing.provider.model.ProviderPrice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class PriceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PriceService.class);

    private final ProductRuleService rules;
    private final PriceProviderClient providerA;
    private final PriceProviderClient providerB;
    private final PriceFreshness freshness;
    private final PriceCache cache;

    public PriceService(ProductRuleService rules,
                        @Qualifier("providerAClient") PriceProviderClient providerA,
                        @Qualifier("providerBClient") PriceProviderClient providerB,
                        PriceFreshness freshness, PriceCache cache) {
        this.rules = rules;
        this.providerA = providerA;
        this.providerB = providerB;
        this.freshness = freshness;
        this.cache = cache;
    }

    public PriceResponse getPrice(String sku) {
        long started = System.nanoTime();
        ProductRule rule = rules.findBySku(sku);
        if (!rule.isActive()) {
            throw new InactiveProductException(rule.getSku());
        }

        PriceCache.Result result = cache.resolve(PriceCacheKey.from(rule), () -> selectPrice(rule));
        boolean stale = result.source() == PriceSource.FALLBACK_CACHE || freshness.isStale(result.price());
        return response(rule, result.price(), result.source(), stale, started);
    }

    private ProviderPrice selectPrice(ProductRule rule) {
        boolean preferB = rule.getPreferredProvider() == PreferredProvider.PROVIDER_B;
        PriceProviderClient first = preferB ? providerB : providerA;
        PriceProviderClient second = preferB ? providerA : providerB;
        List<ProviderException> failures = new ArrayList<>();

        ProviderPrice firstPrice = fetch(first, rule.getSku(), failures);
        if (firstPrice != null && !freshness.isStale(firstPrice)) {
            return firstPrice;
        }

        ProviderPrice secondPrice = fetch(second, rule.getSku(), failures);
        if (secondPrice != null && !freshness.isStale(secondPrice)) {
            return secondPrice;
        }

        ProviderPrice stalePrice = selectStale(firstPrice, secondPrice);
        if (stalePrice == null) {
            throw new PriceUnavailableException(rule.getSku(), failures);
        }
        return stalePrice;
    }

    private ProviderPrice fetch(PriceProviderClient client, String sku, List<ProviderException> failures) {
        try {
            ProviderPrice price = client.fetchPrice(sku);
            if (freshness.isFutureInvalid(price)) {
                throw new ProviderException(price.provider(),
                        ProviderException.Reason.INVALID_RESPONSE, null, null);
            }
            return price;
        } catch (ProviderException exception) {
            failures.add(exception);
            LOGGER.warn("Consulta a {} no disponible: {}.",
                    exception.getProvider(), exception.getReason());
            return null;
        }
    }

    private static ProviderPrice selectStale(ProviderPrice first, ProviderPrice second) {
        if (first != null && first.provider() == ProviderId.PROVIDER_B) {
            return first;
        }
        return second != null ? second : first;
    }

    private PriceResponse response(ProductRule rule, ProviderPrice price, PriceSource source,
                                   boolean stale, long started) {
        BigDecimal factor = BigDecimal.ONE.add(rule.getMarginPercentage().movePointLeft(2));
        BigDecimal finalPrice = price.basePrice().multiply(factor).setScale(2, RoundingMode.HALF_UP);
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        return new PriceResponse(rule.getSku(), rule.getName(), price.basePrice(),
                rule.getMarginPercentage(), finalPrice, price.currency(),
                source, stale, elapsed);
    }
}
