package com.periferia.pricing.productrule.exception;

public class ProductRuleValidationException extends IllegalArgumentException {

    private final String field;

    public ProductRuleValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
