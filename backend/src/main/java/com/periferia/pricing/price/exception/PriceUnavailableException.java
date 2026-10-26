package com.periferia.pricing.price.exception;

import com.periferia.pricing.provider.exception.ProviderException;

import java.util.List;

public class PriceUnavailableException extends RuntimeException {

    public PriceUnavailableException(String sku, List<ProviderException> failures) {
        super("No fue posible obtener un precio para el producto " + sku + ".");
        failures.forEach(this::addSuppressed);
    }
}
