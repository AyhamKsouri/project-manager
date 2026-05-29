package com.pm.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskCreateRequest {
    @NotNull
    private Long projectId;

    @NotNull
    @Size(min = 2, max = 160)
    private String title;

    @Size(max = 4000)
    private String description;

    @Pattern(regexp = "TODO|IN_PROGRESS|DONE|COMPLETED|IN_REVIEW", message = "status must be TODO, IN_PROGRESS, DONE, COMPLETED, or IN_REVIEW")
    private String status;

    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL|Low|Medium|High|Critical|low|medium|high|critical", message = "priority must be LOW, MEDIUM, HIGH, or CRITICAL")
    private String priority;

    @Size(max = 120)
    private String sprintName;

    private Long assigneeId;

    @Size(max = 255)
    private String assignee;
}
