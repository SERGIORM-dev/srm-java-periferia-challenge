package com.periferia.pricing.price.controller;

import com.periferia.pricing.price.dto.PriceResponse;
import com.periferia.pricing.price.exception.InactiveProductException;
import com.periferia.pricing.price.exception.PriceUnavailableException;
import com.periferia.pricing.price.model.PriceSource;
import com.periferia.pricing.price.service.PriceService;
import com.periferia.pricing.productrule.exception.ProductRuleNotFoundException;
import com.periferia.pricing.productrule.exception.ProductRuleValidationException;
import com.periferia.pricing.provider.exception.ProviderException;
import com.periferia.pricing.provider.model.ProviderId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PriceController.class, properties =
        "logging.level.com.periferia.pricing.common.web.ApiExceptionHandler=OFF")
class PriceControllerTest {

    private static final String BASE = "/api/v1/prices";
    @Autowired private MockMvc mvc;
    @MockitoBean private PriceService service;

    @Test
    void returnsTheConsolidatedContract() throws Exception {
        when(service.getPrice("GAS-CORRIENTE")).thenReturn(new PriceResponse("GAS-CORRIENTE",
                "Gasolina corriente", new BigDecimal("100.00"), new BigDecimal("10.00"),
                new BigDecimal("110.00"), "USD", PriceSource.PROVIDER_A, false, 82));

        mvc.perform(get(BASE).param("sku", "GAS-CORRIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("GAS-CORRIENTE"))
                .andExpect(jsonPath("$.productName").value("Gasolina corriente"))
                .andExpect(jsonPath("$.providerPrice").value(100))
                .andExpect(jsonPath("$.marginPercentage").value(10))
                .andExpect(jsonPath("$.finalPrice").value(110))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.source").value("PROVIDER_A"))
                .andExpect(jsonPath("$.stale").value(false))
                .andExpect(jsonPath("$.responseTimeMs").value(82));
    }

    @Test
    void requiresTheSkuParameter() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void reportsInvalidSku() throws Exception {
        when(service.getPrice(" ")).thenThrow(new ProductRuleValidationException("sku", "El SKU es obligatorio."));

        mvc.perform(get(BASE).param("sku", " ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sku").exists());
    }

    @Test
    void reportsUnknownProducts() throws Exception {
        when(service.getPrice("NO-EXISTE")).thenThrow(new ProductRuleNotFoundException("NO-EXISTE"));

        mvc.perform(get(BASE).param("sku", "NO-EXISTE")).andExpect(status().isNotFound());
    }

    @Test
    void reportsInactiveProductsWith422() throws Exception {
        when(service.getPrice("ADIT-LIMPIADOR")).thenThrow(new InactiveProductException("ADIT-LIMPIADOR"));

        mvc.perform(get(BASE).param("sku", "ADIT-LIMPIADOR"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Producto inactivo"))
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void returnsControlled503WithoutLeakingUpstreamDetails() throws Exception {
        ProviderException failure = new ProviderException(ProviderId.PROVIDER_A,
                ProviderException.Reason.UNAVAILABLE, null, new IllegalStateException("private_details"));
        when(service.getPrice("GAS-CORRIENTE"))
                .thenThrow(new PriceUnavailableException("GAS-CORRIENTE", List.of(failure)));

        mvc.perform(get(BASE).param("sku", "GAS-CORRIENTE"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Precio no disponible"))
                .andExpect(jsonPath("$.instance").value(BASE))
                .andExpect(content().string(not(containsString("private_details"))));
    }
}
