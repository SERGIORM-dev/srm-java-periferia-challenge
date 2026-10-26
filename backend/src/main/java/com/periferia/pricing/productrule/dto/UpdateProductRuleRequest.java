package com.periferia.pricing.productrule.dto;

import com.periferia.pricing.productrule.model.PreferredProvider;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateProductRuleRequest(
        @NotNull(message = "El nombre es obligatorio.") String name,
        @NotNull(message = "El margen es obligatorio.") BigDecimal marginPercentage,
        @NotNull(message = "El proveedor preferido es obligatorio.") PreferredProvider preferredProvider,
        @NotNull(message = "El estado activo es obligatorio.") Boolean active) {
}
