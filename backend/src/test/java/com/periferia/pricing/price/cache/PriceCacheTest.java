package com.periferia.pricing.price.cache;

import com.periferia.pricing.price.exception.PriceUnavailableException;
import com.periferia.pricing.price.model.PriceSource;
import com.periferia.pricing.price.service.PriceFreshness;
import com.periferia.pricing.productrule.event.ProductRuleChanged;
import com.periferia.pricing.productrule.model.PreferredProvider;
import com.periferia.pricing.provider.model.ProviderId;
import com.periferia.pricing.provider.model.ProviderPrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PriceCacheTest {

    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
    private static final PriceCacheKey KEY = key(1L, "Gasolina", "10.00");
    private final AtomicLong nanos = new AtomicLong();
    private final AtomicInteger loads = new AtomicInteger();
    private Clock clock;
    private PriceCache cache;

    @BeforeEach
    void setUp() {
        clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(invocation -> NOW.plusNanos(nanos.get()));
        cache = new PriceCache(new PriceCacheProperties(Duration.ofSeconds(60), Duration.ofMinutes(15), 10000),
                new PriceFreshness(clock), nanos::get);
    }

    @Test
    void reusesFreshPricesWithoutCallingTheLoader() {
        assertThat(cache.resolve(KEY, this::load).source()).isEqualTo(PriceSource.PROVIDER_A);
        nanos.set(Duration.ofSeconds(59).toNanos());

        assertThat(cache.resolve(KEY, this::load).source()).isEqualTo(PriceSource.CACHE);
        assertThat(loads).hasValue(1);
    }

    @Test
    void refreshesAtExactlySixtySeconds() {
        cache.resolve(KEY, this::load);
        nanos.set(Duration.ofSeconds(60).toNanos());

        assertThat(cache.resolve(KEY, this::load).source()).isEqualTo(PriceSource.PROVIDER_A);
        assertThat(loads).hasValue(2);
    }

    @Test
    void doesNotExtendTtlWhenReadingACachedPrice() {
        cache.resolve(KEY, this::load);
        nanos.set(Duration.ofSeconds(59).toNanos());
        cache.resolve(KEY, this::load);
        nanos.set(Duration.ofSeconds(60).toNanos());

        assertThat(cache.resolve(KEY, this::load).source()).isEqualTo(PriceSource.PROVIDER_A);
        assertThat(loads).hasValue(2);
    }

    @Test
    void fallsBackAfterFailedRefreshWithoutExtendingTheRetentionWindow() {
        ProviderPrice first = cache.resolve(KEY, this::load).price();
        nanos.set(Duration.ofSeconds(61).toNanos());

        assertThat(cache.resolve(KEY, this::fail).source()).isEqualTo(PriceSource.FALLBACK_CACHE);
        nanos.set(Duration.ofSeconds(899).toNanos());
        assertThat(cache.resolve(KEY, this::fail).price()).isSameAs(first);
        nanos.set(Duration.ofSeconds(900).toNanos());
        assertThatThrownBy(() -> cache.resolve(KEY, this::fail)).isInstanceOf(PriceUnavailableException.class);
    }

    @Test
    void propagatesFailuresWhenNothingWasCachedAndAllowsRecovery() {
        assertThatThrownBy(() -> cache.resolve(KEY, this::fail)).isInstanceOf(PriceUnavailableException.class);

        assertThat(cache.resolve(KEY, this::load).source()).isEqualTo(PriceSource.PROVIDER_A);
        assertThat(loads).hasValue(1);
    }

    @Test
    void checksTheOriginalProviderTimestampEvenBeforeTheCacheTtlExpires() {
        cache.resolve(KEY, () -> quote(NOW.minusSeconds(899)));
        nanos.set(Duration.ofSeconds(2).toNanos());

        assertThat(cache.resolve(KEY, this::load).source()).isEqualTo(PriceSource.PROVIDER_A);
        assertThat(loads).hasValue(1);
    }

    @Test
    void doesNotTreatAnObsoleteLiveQuoteAsAFreshCacheHit() {
        cache.resolve(KEY, () -> quote(NOW.minusSeconds(1200)));

        assertThat(cache.resolve(KEY, this::load).source()).isEqualTo(PriceSource.PROVIDER_A);
        assertThat(loads).hasValue(1);
    }

    @Test
    void invalidationRemovesFreshAndFallbackData() {
        cache.resolve(KEY, this::load);
        cache.invalidate(new ProductRuleChanged(KEY.sku()));
        nanos.set(Duration.ofSeconds(61).toNanos());

        assertThatThrownBy(() -> cache.resolve(KEY, this::fail)).isInstanceOf(PriceUnavailableException.class);
        assertThat(cache.resolve(KEY, this::load).source()).isEqualTo(PriceSource.PROVIDER_A);
    }

    @Test
    void isolatesRuleChangesAndDeletionFollowedBySkuReuse() {
        cache.resolve(KEY, this::load);

        assertThat(cache.resolve(key(1L, "Gasolina", "20.00"), this::load).source())
                .isEqualTo(PriceSource.PROVIDER_A);
        assertThat(cache.resolve(key(2L, "Gasolina", "10.00"), this::load).source())
                .isEqualTo(PriceSource.PROVIDER_A);
        assertThat(loads).hasValue(3);
    }

    @Test
    void sharesOneSuccessfulLoadAcrossConcurrentRequests() throws Exception {
        int count = 8;
        var executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<PriceCache.Result>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    return cache.resolve(KEY, () -> {
                        entered.countDown();
                        await(release);
                        return load();
                    });
                }));
            }
            await(ready);
            start.countDown();
            await(entered);
            release.countDown();
            List<PriceCache.Result> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(5, TimeUnit.SECONDS));

            assertThat(loads).hasValue(1);
            assertThat(results.stream().filter(result -> result.source() == PriceSource.CACHE)).hasSize(count - 1);
        } finally {
            start.countDown();
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void anOldLoadFinishingAfterInvalidationCannotPopulateTheNewRuleKey() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            var old = executor.submit(() -> cache.resolve(KEY, () -> {
                entered.countDown();
                await(release);
                return load();
            }));
            await(entered);
            cache.invalidate(new ProductRuleChanged(KEY.sku()));
            release.countDown();
            old.get(5, TimeUnit.SECONDS);

            assertThat(cache.resolve(key(1L, "Gasolina nueva", "20.00"), this::load).source())
                    .isEqualTo(PriceSource.PROVIDER_A);
            assertThat(loads).hasValue(2);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private ProviderPrice load() {
        loads.incrementAndGet();
        return quote(clock.instant());
    }

    private ProviderPrice fail() {
        throw new PriceUnavailableException(KEY.sku(), List.of());
    }

    private static ProviderPrice quote(Instant date) {
        return new ProviderPrice(KEY.sku(), new BigDecimal("100.00"), "USD", date, ProviderId.PROVIDER_A);
    }

    private static PriceCacheKey key(Long id, String name, String margin) {
        return new PriceCacheKey(id, "GAS-CORRIENTE", name, new BigDecimal(margin),
                PreferredProvider.AUTO, true, NOW);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("La coordinación de la prueba expiró.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
