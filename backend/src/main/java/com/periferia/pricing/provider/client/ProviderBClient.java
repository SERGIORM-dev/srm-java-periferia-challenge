package com.periferia.pricing.provider.client;

import com.periferia.pricing.provider.model.ProviderId;
import com.periferia.pricing.provider.model.ProviderPrice;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;

public class ProviderBClient implements PriceProviderClient {

    private final ProviderHttpClient http;

    public ProviderBClient(RestClient client) {
        http = new ProviderHttpClient(client, provider());
    }

    @Override
    public ProviderId provider() {
        return ProviderId.PROVIDER_B;
    }

    @Override
    public ProviderPrice fetchPrice(String sku) {
        ProviderBResponse response = http.get(sku, ProviderBResponse.class);
        if (response.timestamp() == null) {
            throw http.invalidResponse(null);
        }
        Instant lastUpdated;
        try {
            lastUpdated = Instant.ofEpochSecond(response.timestamp());
        } catch (DateTimeException exception) {
            throw http.invalidResponse(exception);
        }
        return http.toPrice(sku, response.itemCode(), response.cost(), response.curr(), lastUpdated);
    }

    private record ProviderBResponse(String itemCode, BigDecimal cost, String curr, Long timestamp) {
    }
}
