package com.periferia.pricing.price.exception;

public class InactiveProductException extends RuntimeException {

    public InactiveProductException(String sku) {
        super("El producto " + sku + " está inactivo.");
    }
}
