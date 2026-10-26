package com.periferia.pricing.provider.client;

import com.periferia.pricing.provider.model.ProviderId;
import com.periferia.pricing.provider.model.ProviderPrice;

public interface PriceProviderClient {

    ProviderId provider();

    ProviderPrice fetchPrice(String sku);
}
