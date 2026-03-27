package me.abdoabk.reportxapi.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateStatusRequest(@NotBlank String status, String verdict) {}