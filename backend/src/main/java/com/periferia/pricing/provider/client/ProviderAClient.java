package com.periferia.pricing.provider.client;

import com.periferia.pricing.provider.model.ProviderId;
import com.periferia.pricing.provider.model.ProviderPrice;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;

public class ProviderAClient implements PriceProviderClient {

    private final ProviderHttpClient http;

    public ProviderAClient(RestClient client) {
        http = new ProviderHttpClient(client, provider());
    }

    @Override
    public ProviderId provider() {
        return ProviderId.PROVIDER_A;
    }

    @Override
    public ProviderPrice fetchPrice(String sku) {
        ProviderAResponse response = http.get(sku, ProviderAResponse.class);
        return http.toPrice(sku, response.sku(), response.basePrice(),
                response.currency(), response.lastUpdated());
    }

    private record ProviderAResponse(String sku, BigDecimal basePrice,
                                     String currency, Instant lastUpdated) {
    }
}
