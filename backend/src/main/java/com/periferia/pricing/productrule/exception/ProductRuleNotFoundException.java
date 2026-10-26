package com.periferia.pricing.productrule.exception;

public class ProductRuleNotFoundException extends RuntimeException {

    public ProductRuleNotFoundException(String sku) {
        super("No existe una regla para el SKU '" + sku + "'.");
    }
}
