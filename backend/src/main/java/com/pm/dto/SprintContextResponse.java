package com.pm.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class SprintContextResponse {
    private Long sprintId;
    private String sprintName;
    private List<SprintTask> tasks;
    private List<TeamMember> teamMembers;

    @Getter
    @Builder
    public static class SprintTask {
        private Long id;
        private String title;
        private String status;
        private String priority;
        private String assignee;
        private String sprintName;
    }

    @Getter
    @Builder
    public static class TeamMember {
        private Long id;
        private String username;
        private String name;
        private String email;
    }
}
