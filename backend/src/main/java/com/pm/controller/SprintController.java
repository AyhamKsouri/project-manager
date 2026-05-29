package com.pm.controller;

import com.pm.dto.SprintContextResponse;
import com.pm.model.Project;
import com.pm.model.ProjectUser;
import com.pm.model.Task;
import com.pm.repository.ProjectRepository;
import com.pm.repository.ProjectUserRepository;
import com.pm.repository.TaskRepository;
import com.pm.security.ProjectAccessService;
import com.pm.security.UserDetailsImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sprints")
public class SprintController {
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final ProjectUserRepository projectUserRepository;
    private final ProjectAccessService projectAccessService;

    public SprintController(ProjectRepository projectRepository,
                            TaskRepository taskRepository,
                            ProjectUserRepository projectUserRepository,
                            ProjectAccessService projectAccessService) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.projectUserRepository = projectUserRepository;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping("/{id}/context")
    public ResponseEntity<SprintContextResponse> getSprintContext(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        projectAccessService.requireProjectMember(id, currentUser);
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        return ResponseEntity.ok(SprintContextResponse.builder()
                .sprintId(project.getId())
                .sprintName(project.getName())
                .tasks(taskRepository.findByProject_IdAndDeletedFalse(id).stream()
                        .map(this::toSprintTask)
                        .toList())
                .teamMembers(projectUserRepository.findByProject_Id(id).stream()
                        .map(this::toTeamMember)
                        .toList())
                .build());
    }

    private SprintContextResponse.SprintTask toSprintTask(Task task) {
        return SprintContextResponse.SprintTask.builder()
                .id(task.getId())
                .title(task.getTitle())
                .status(task.getStatus() != null ? task.getStatus().name() : null)
                .priority(task.getPriority())
                .assignee(task.getAssignee() != null ? task.getAssignee().getName() : null)
                .sprintName(task.getSprintName())
                .build();
    }

    private SprintContextResponse.TeamMember toTeamMember(ProjectUser membership) {
        return SprintContextResponse.TeamMember.builder()
                .id(membership.getUser().getId())
                .username(membership.getUser().getEmail())
                .name(membership.getUser().getName())
                .email(membership.getUser().getEmail())
                .build();
    }
}
