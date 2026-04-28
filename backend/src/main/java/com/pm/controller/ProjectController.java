package com.pm.controller;

import com.pm.model.User;
import com.pm.model.Project;
import com.pm.model.ProjectUser;
import com.pm.model.ProjectRole;
import com.pm.repository.ProjectRepository;
import com.pm.repository.ProjectUserRepository;
import com.pm.repository.TaskRepository;
import com.pm.repository.UserRepository;
import com.pm.security.UserDetailsImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectRepository projectRepository;
    private final ProjectUserRepository projectUserRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public ProjectController(ProjectRepository projectRepository, ProjectUserRepository projectUserRepository, TaskRepository taskRepository, UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.projectUserRepository = projectUserRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Transactional
    public ResponseEntity<Project> createProject(@RequestBody Project project, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userRepository.findById(currentUser.getUser().getId())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

        Project savedProject = projectRepository.save(project);
        
        // Automatically assign the creator as CHEF
        projectUserRepository.save(ProjectUser.builder()
                .project(savedProject)
                .user(user)
                .projectRole(ProjectRole.CHEF)
                .build());
        
        savedProject.setTaskCount(0L);
        savedProject.setMemberCount(1L);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedProject);
    }

    @GetMapping("/my-projects")
    public ResponseEntity<List<Project>> getMyProjects(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<ProjectUser> memberships = projectUserRepository.findByUserId(currentUser.getUser().getId());
        
        List<Project> projects = memberships.stream()
                .map(ProjectUser::getProject)
                .peek(project -> {
                    project.setTaskCount(taskRepository.countByProjectId(project.getId()));
                    project.setMemberCount(projectUserRepository.countByProjectId(project.getId()));
                })
                .collect(Collectors.toList());
                
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<Project> getProject(@PathVariable Long projectId) {
        return projectRepository.findById(projectId)
                .map(project -> {
                    project.setTaskCount(taskRepository.countByProjectId(project.getId()));
                    project.setMemberCount(projectUserRepository.countByProjectId(project.getId()));
                    return ResponseEntity.ok(project);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{projectId}/members")
    public ResponseEntity<List<User>> getProjectMembers(@PathVariable Long projectId) {
        List<ProjectUser> memberships = projectUserRepository.findByProjectId(projectId);
        List<User> members = memberships.stream()
                .map(ProjectUser::getUser)
                .collect(Collectors.toList());
        return ResponseEntity.ok(members);
    }
}
