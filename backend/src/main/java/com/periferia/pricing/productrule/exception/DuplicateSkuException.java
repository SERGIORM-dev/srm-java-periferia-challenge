package com.periferia.pricing.productrule.exception;

public class DuplicateSkuException extends RuntimeException {

    public DuplicateSkuException(String sku) {
        this(sku, null);
    }

    public DuplicateSkuException(String sku, Throwable cause) {
        super("Ya existe una regla para el SKU '" + sku + "'.", cause);
    }
}
