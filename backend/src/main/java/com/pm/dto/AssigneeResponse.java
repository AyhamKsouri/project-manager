package com.pm.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AssigneeResponse {
    private Long id;
    private String name;
    private String email;
}
