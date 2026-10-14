package com.smim.server.message;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ReportRequest(@NotNull @Pattern(regexp = "abuse|spam|privacy|other") String reason) {
}
