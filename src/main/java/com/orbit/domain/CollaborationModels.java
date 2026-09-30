package com.orbit.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class CollaborationModels {
    private CollaborationModels() { }
    public record Settings(String id, String name, long version, DomainModels.Role role, OffsetDateTime createdAt) { }
    public record Rename(@NotBlank @Size(max = 100) @Pattern(regexp = "[^\\x00]*") String name,
                         @NotNull @Min(0) Long version) { }
    public record Invite(@NotBlank @Email @Size(max = 254) String email, @NotNull DomainModels.Role role) { }
    public record Invitation(String id, String email, DomainModels.Role role, String status,
                             OffsetDateTime expiresAt, OffsetDateTime createdAt) { }
    public record Preview(String workspaceName, String email, DomainModels.Role role, OffsetDateTime expiresAt) { }
    public record Accept(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token) { }
    public record Notification(String id, String workspaceId, String workspaceName, String taskId,
                               String type, String title, String body, boolean read, OffsetDateTime createdAt) { }
    public record Inbox(List<Notification> items, int page, int size, long total, long totalPages, long unreadCount) { }
}
