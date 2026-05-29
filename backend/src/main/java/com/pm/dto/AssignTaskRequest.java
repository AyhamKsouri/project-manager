package com.pm.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignTaskRequest {
    private Long assigneeId;

    @Size(max = 255)
    private String assignee;
}
