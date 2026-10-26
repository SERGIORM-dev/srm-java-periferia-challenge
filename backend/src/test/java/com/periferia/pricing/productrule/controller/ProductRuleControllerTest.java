package com.periferia.pricing.productrule.controller;

import com.periferia.pricing.productrule.exception.DuplicateSkuException;
import com.periferia.pricing.productrule.exception.ProductRuleNotFoundException;
import com.periferia.pricing.productrule.exception.ProductRuleValidationException;
import com.periferia.pricing.productrule.model.PreferredProvider;
import com.periferia.pricing.productrule.model.ProductRule;
import com.periferia.pricing.productrule.service.ProductRuleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductRuleController.class, properties =
        "logging.level.com.periferia.pricing.common.web.ApiExceptionHandler=OFF")
class ProductRuleControllerTest {

    private static final String BASE = "/api/v1/product-rules";
    private static final String CREATE_JSON = """
            {
              "sku": " gas-extra ",
              "name": "Gasolina extra",
              "marginPercentage": 15.00,
              "preferredProvider": "PROVIDER_A",
              "active": true
            }
            """;
    private static final String UPDATE_JSON = """
            {
              "name": "Gasolina extra",
              "marginPercentage": 15.00,
              "preferredProvider": "PROVIDER_A",
              "active": true
            }
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductRuleService service;

    @Test
    void createsAndReturnsThePublicResponse() throws Exception {
        ProductRule created = rule(true);
        when(service.create(" gas-extra ", "Gasolina extra", new BigDecimal("15.00"),
                PreferredProvider.PROVIDER_A, true)).thenReturn(created);

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(17))
                .andExpect(jsonPath("$.sku").value("GAS-EXTRA"))
                .andExpect(jsonPath("$.name").value("Gasolina extra"))
                .andExpect(jsonPath("$.marginPercentage").value(15.0))
                .andExpect(jsonPath("$.preferredProvider").value("PROVIDER_A"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-25T12:00:00Z"));
    }

    @Test
    void listsRulesIncludingInactiveProducts() throws Exception {
        ProductRule inactive = rule(false);
        when(service.findAll()).thenReturn(List.of(inactive));

        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("GAS-EXTRA"))
                .andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    void updatesTheSkuIdentifiedByTheRoute() throws Exception {
        ProductRule updated = rule(true);
        when(service.update("gas-extra", "Gasolina extra", new BigDecimal("15.00"),
                PreferredProvider.PROVIDER_A, true)).thenReturn(updated);

        mvc.perform(put(BASE + "/gas-extra")
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("GAS-EXTRA"));
    }

    @Test
    void deletesWithNoResponseBody() throws Exception {
        mvc.perform(delete(BASE + "/GAS-EXTRA"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).delete("GAS-EXTRA");
    }

    @Test
    void reportsAllMissingCreateFieldsWithoutCallingTheService() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.sku").exists())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.marginPercentage").exists())
                .andExpect(jsonPath("$.errors.preferredProvider").exists())
                .andExpect(jsonPath("$.errors.active").exists());
        verifyNoInteractions(service);
    }

    @Test
    void doesNotTreatMissingActiveAsFalseOnUpdate() throws Exception {
        mvc.perform(put(BASE + "/GAS-EXTRA").contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_JSON.replace("\"active\": true", "\"active\": null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.active").exists());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("unreadablePayloads")
    void rejectsMalformedBodiesAndInvalidValueTypes(String payload) throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("JSON inválido"))
                .andExpect(content().string(not(containsString("Exception"))));
        verifyNoInteractions(service);
    }

    @Test
    void mapsBusinessValidationToAFieldError() throws Exception {
        when(service.create(anyString(), anyString(), any(BigDecimal.class),
                any(PreferredProvider.class), anyBoolean()))
                .thenThrow(new ProductRuleValidationException("marginPercentage", "Margen inválido."));

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.marginPercentage").value("Margen inválido."))
                .andExpect(jsonPath("$.instance").value(BASE));
    }

    @Test
    void mapsDuplicateSkuToConflict() throws Exception {
        when(service.create(anyString(), anyString(), any(BigDecimal.class),
                any(PreferredProvider.class), anyBoolean()))
                .thenThrow(new DuplicateSkuException("GAS-EXTRA"));

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("SKU duplicado"));
    }

    @Test
    void mapsUnknownSkuToNotFound() throws Exception {
        when(service.update(anyString(), anyString(), any(BigDecimal.class),
                any(PreferredProvider.class), anyBoolean()))
                .thenThrow(new ProductRuleNotFoundException("GAS-INEXISTENTE"));

        mvc.perform(put(BASE + "/GAS-INEXISTENTE")
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @ParameterizedTest
    @MethodSource("databaseFailures")
    void hidesDatabaseDetailsAndReturnsServiceUnavailable(RuntimeException failure) throws Exception {
        when(service.findAll()).thenThrow(failure);

        mvc.perform(get(BASE))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(content().string(not(containsString("private_database_info"))));
    }

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        when(service.findAll()).thenThrow(new IllegalStateException("internal_sensitive_details"));

        mvc.perform(get(BASE))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No fue posible completar la solicitud."))
                .andExpect(content().string(not(containsString("internal_sensitive_details"))));
    }

    @Test
    void preservesMethodNotAllowedInsteadOfReturning500() throws Exception {
        mvc.perform(patch(BASE + "/GAS-EXTRA"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void rejectsUnsupportedContentTypes() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.TEXT_PLAIN).content(CREATE_JSON))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    private static ProductRule rule(boolean active) {
        ProductRule rule = mock(ProductRule.class);
        when(rule.getId()).thenReturn(17L);
        when(rule.getSku()).thenReturn("GAS-EXTRA");
        when(rule.getName()).thenReturn("Gasolina extra");
        when(rule.getMarginPercentage()).thenReturn(new BigDecimal("15.00"));
        when(rule.getPreferredProvider()).thenReturn(PreferredProvider.PROVIDER_A);
        when(rule.isActive()).thenReturn(active);
        when(rule.getUpdatedAt()).thenReturn(Instant.parse("2026-10-25T12:00:00Z"));
        return rule;
    }

    private static Stream<String> unreadablePayloads() {
        return Stream.of("{", "[]", CREATE_JSON.replace("PROVIDER_A", "UNKNOWN"),
                CREATE_JSON.replace("\"active\": true", "\"active\": \"yes\""),
                CREATE_JSON.replace("15.00", "\"not-a-number\""));
    }

    private static Stream<RuntimeException> databaseFailures() {
        return Stream.of(new DataAccessResourceFailureException("private_database_info"),
                new CannotCreateTransactionException("private_database_info"));
    }
}
