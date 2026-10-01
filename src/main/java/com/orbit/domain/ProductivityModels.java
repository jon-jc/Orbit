package com.orbit.domain;

import static com.orbit.domain.DomainModels.*;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class ProductivityModels {
    private ProductivityModels() { }

    public record BulkTasks(
            @NotNull @Size(min = 1, max = 100) List<@NotBlank @Pattern(regexp = UUID_PATTERN) String> taskIds,
            @NotNull @Size(min = 1, max = 100) Map<@NotBlank @Pattern(regexp = UUID_PATTERN) String, @NotNull @Min(0) Long> versions,
            TaskStatus status, Priority priority,
            @Pattern(regexp = UUID_PATTERN) String assigneeId, Boolean clearAssignee) { }

    public record CreateView(
            @NotBlank @Size(max = 80) @Pattern(regexp = "[^\\x00]*") String label,
            @Size(max = 200) @Pattern(regexp = "[^\\x00]*") String q,
            TaskStatus status, Priority priority,
            @Pattern(regexp = UUID_PATTERN) String projectId,
            @Pattern(regexp = UUID_PATTERN) String assigneeId) { }

    /** PATCH replaces the full filter set; null or omitted filters clear that filter. */
    public record UpdateView(
            @NotBlank @Size(max = 80) @Pattern(regexp = "[^\\x00]*") String label,
            @Size(max = 200) @Pattern(regexp = "[^\\x00]*") String q,
            TaskStatus status, Priority priority,
            @Pattern(regexp = UUID_PATTERN) String projectId,
            @Pattern(regexp = UUID_PATTERN) String assigneeId,
            @NotNull @Min(0) Long version) { }

    public record SavedView(String id, String workspaceId, String label, String q,
            TaskStatus status, Priority priority, String projectId, String assigneeId,
            long version, OffsetDateTime createdAt, OffsetDateTime updatedAt) { }
}
