package com.mykart.payment.dto.request

import jakarta.validation.constraints.NotNull

data class ReconcileRequest(
    @field:NotNull val successfulExternalReferenceIds: List<String> = emptyList()
)
