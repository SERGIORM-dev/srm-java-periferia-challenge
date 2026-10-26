package com.periferia.pricing.provider.exception;

import com.periferia.pricing.provider.model.ProviderId;

public class ProviderException extends RuntimeException {

    public enum Reason {
        TIMEOUT,
        UNAVAILABLE,
        NOT_FOUND,
        RATE_LIMITED,
        HTTP_ERROR,
        INVALID_RESPONSE
    }

    private final ProviderId provider;
    private final Reason reason;
    private final Integer httpStatus;

    public ProviderException(ProviderId provider, Reason reason, Integer httpStatus, Throwable cause) {
        super("No fue posible consultar " + provider + " (" + reason + ").", cause);
        this.provider = provider;
        this.reason = reason;
        this.httpStatus = httpStatus;
    }

    public ProviderId getProvider() {
        return provider;
    }

    public Reason getReason() {
        return reason;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }
}
