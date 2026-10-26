package com.periferia.pricing.provider.client;

import com.periferia.pricing.provider.exception.ProviderException;
import com.periferia.pricing.provider.exception.ProviderException.Reason;
import com.periferia.pricing.provider.model.ProviderId;
import com.periferia.pricing.provider.model.ProviderPrice;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Instant;

final class ProviderHttpClient {

    private final RestClient client;
    private final ProviderId provider;

    ProviderHttpClient(RestClient client, ProviderId provider) {
        this.client = client;
        this.provider = provider;
    }

    <T> T get(String sku, Class<T> responseType) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("El SKU de la consulta es obligatorio.");
        }
        try {
            T body = client.get()
                    .uri(builder -> builder.path("/api/prices")
                            .queryParam("sku", "{sku}").build(sku))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (request, response) -> {
                        int status = response.getStatusCode().value();
                        Reason reason = switch (status) {
                            case 404 -> Reason.NOT_FOUND;
                            case 429 -> Reason.RATE_LIMITED;
                            default -> Reason.HTTP_ERROR;
                        };
                        throw new ProviderException(provider, reason, status, null);
                    })
                    .body(responseType);
            if (body == null) {
                throw invalidResponse(null);
            }
            return body;
        } catch (ResourceAccessException exception) {
            Reason reason = hasTimeoutCause(exception) ? Reason.TIMEOUT : Reason.UNAVAILABLE;
            throw new ProviderException(provider, reason, null, exception);
        } catch (RestClientException exception) {
            throw invalidResponse(exception);
        }
    }

    ProviderPrice toPrice(String requestedSku, String returnedSku, BigDecimal price,
                          String currency, Instant lastUpdated) {
        try {
            if (!requestedSku.equals(returnedSku)) {
                throw new IllegalArgumentException("El SKU recibido no coincide con el solicitado.");
            }
            return new ProviderPrice(returnedSku, price, currency, lastUpdated, provider);
        } catch (IllegalArgumentException exception) {
            throw invalidResponse(exception);
        }
    }

    ProviderException invalidResponse(Throwable cause) {
        return new ProviderException(provider, Reason.INVALID_RESPONSE, null, cause);
    }

    private static boolean hasTimeoutCause(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
