package me.abdoabk.reportxapi.dto;

import jakarta.validation.constraints.NotBlank;

public record AddNoteRequest(@NotBlank String note) {}