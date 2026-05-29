package com.pm.dto;

import com.pm.model.Task;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TaskResponse {
    private Long id;
    private String title;
    private String description;
    private String status;
    private String priority;
    private String sprintName;
    private AssigneeResponse assignee;
    private Long projectId;

    public static TaskResponse from(Task task) {
        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus() != null ? task.getStatus().name() : null)
                .priority(task.getPriority())
                .sprintName(task.getSprintName())
                .projectId(task.getProject() != null ? task.getProject().getId() : null)
                .assignee(task.getAssignee() == null ? null : AssigneeResponse.builder()
                        .id(task.getAssignee().getId())
                        .name(task.getAssignee().getName())
                        .email(task.getAssignee().getEmail())
                        .build())
                .build();
    }
}
