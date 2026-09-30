package com.orbit.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** The public API uses explicit records: no database column names escape into JSON. */
public final class DomainModels {
    private DomainModels() {}

    public enum Role { OWNER, MEMBER, VIEWER }
    public enum ProjectStatus { ACTIVE, ARCHIVED }
    public enum TaskStatus { BACKLOG, TODO, IN_PROGRESS, IN_REVIEW, DONE }
    public enum Priority { LOW, MEDIUM, HIGH, URGENT }

    public record Workspace(String id, String name, Role role, OffsetDateTime createdAt) {}
    public record Member(String id, String name, String email, Role role) {}
    public record Project(String id, String name, String description, String color,
                          ProjectStatus status, long taskCount, long completedTaskCount,
                          OffsetDateTime createdAt, long version) {}
    public record Task(String id, String title, String description, String projectId,
                       String projectName, String projectColor, TaskStatus status, Priority priority,
                       String assigneeId, String assigneeName, LocalDate dueDate,
                       OffsetDateTime createdAt, OffsetDateTime updatedAt, long version, long commentCount) {}
    public record Comment(String id, String authorId, String authorName, String body, OffsetDateTime createdAt) {}
    public record Activity(String id, String actorName, String action, String entityType,
                           String entityName, OffsetDateTime createdAt) {}
    public record Page<T>(List<T> items, int page, int size, long total, long totalPages) {}
    public record Overview(long totalTasks, long completedTasks, long inProgressTasks, long overdueTasks,
                           List<Project> projects, List<Activity> recentActivity, List<Task> upcomingTasks) {}

    public record CreateWorkspace(@NotBlank @Size(max = 100) @Pattern(regexp = "[^\\x00]*") String name) {}
    public record AddMember(@NotBlank @Email @Size(max = 254) String email, @NotNull Role role) {}
    public record UpdateMember(@NotNull Role role) {}
    public record CreateProject(@NotBlank @Size(max = 120) @Pattern(regexp = "[^\\x00]*") String name,
                                @Size(max = 2000) @Pattern(regexp = "[^\\x00]*") String description,
                                @NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String color) {}
    public record UpdateProject(@NotBlank @Size(max = 120) @Pattern(regexp = "[^\\x00]*") String name,
                                @Size(max = 2000) @Pattern(regexp = "[^\\x00]*") String description,
                                @NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String color,
                                @NotNull ProjectStatus status, @NotNull @Min(0) Long version) {}
    public record CreateTask(@NotBlank @Size(max = 200) @Pattern(regexp = "[^\\x00]*") String title,
                             @Size(max = 10000) @Pattern(regexp = "[^\\x00]*") String description,
                             @NotBlank @Pattern(regexp = UUID_PATTERN) String projectId,
                             @NotNull TaskStatus status, @NotNull Priority priority,
                             @Pattern(regexp = UUID_PATTERN) String assigneeId, LocalDate dueDate) {}
    public record UpdateTask(@NotBlank @Size(max = 200) @Pattern(regexp = "[^\\x00]*") String title,
                             @Size(max = 10000) @Pattern(regexp = "[^\\x00]*") String description,
                             @NotBlank @Pattern(regexp = UUID_PATTERN) String projectId,
                             @NotNull TaskStatus status, @NotNull Priority priority,
                             @Pattern(regexp = UUID_PATTERN) String assigneeId, LocalDate dueDate,
                             @NotNull @Min(0) Long version) {}
    public record CreateComment(@NotBlank @Size(max = 4000) @Pattern(regexp = "[^\\x00]*") String body) {}

    public static final String UUID_PATTERN = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
}
