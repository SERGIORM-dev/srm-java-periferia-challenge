package com.periferia.pricing.price.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.periferia.pricing.price.exception.PriceUnavailableException;
import com.periferia.pricing.price.model.PriceSource;
import com.periferia.pricing.price.service.PriceFreshness;
import com.periferia.pricing.productrule.event.ProductRuleChanged;
import com.periferia.pricing.provider.model.ProviderPrice;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Component
public class PriceCache {

    public record Result(ProviderPrice price, PriceSource source) {
    }

    private record Entry(ProviderPrice price, long loadedAtNanos) {
    }

    private final Cache<PriceCacheKey, Entry> entries;
    private final PriceFreshness freshness;
    private final Ticker ticker;
    private final long freshTtlNanos;
    private final long fallbackTtlNanos;

    public PriceCache(PriceCacheProperties properties, PriceFreshness freshness, Ticker ticker) {
        this.freshness = freshness;
        this.ticker = ticker;
        freshTtlNanos = properties.freshTtl().toNanos();
        fallbackTtlNanos = properties.fallbackTtl().toNanos();
        entries = Caffeine.newBuilder().maximumSize(properties.maximumSize())
                .expireAfterWrite(properties.fallbackTtl()).ticker(ticker).build();
    }

    public Result resolve(PriceCacheKey key, Supplier<ProviderPrice> loader) {
        AtomicReference<Result> result = new AtomicReference<>();
        entries.asMap().compute(key, (ignored, current) -> {
            if (current != null && age(current) < freshTtlNanos
                    && !freshness.isStale(current.price()) && !freshness.isFutureInvalid(current.price())) {
                result.set(new Result(current.price(), PriceSource.CACHE));
                return current;
            }
            try {
                ProviderPrice price = loader.get();
                result.set(new Result(price, PriceSource.valueOf(price.provider().name())));
                return new Entry(price, ticker.read());
            } catch (PriceUnavailableException exception) {
                if (current == null || age(current) >= fallbackTtlNanos
                        || freshness.isFutureInvalid(current.price())) {
                    throw exception;
                }
                result.set(new Result(current.price(), PriceSource.FALLBACK_CACHE));
                return current;
            }
        });
        return result.get();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void invalidate(ProductRuleChanged event) {
        entries.asMap().keySet().removeIf(key -> key.sku().equals(event.sku()));
    }

    private long age(Entry entry) {
        return ticker.read() - entry.loadedAtNanos();
    }
}
