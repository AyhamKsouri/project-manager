package com.pm.controller;

import com.pm.model.ProjectRole;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberRequest {
    private String email;
    private ProjectRole role;
}
