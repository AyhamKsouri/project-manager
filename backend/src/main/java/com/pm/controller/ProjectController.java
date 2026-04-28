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
    public ResponseEntity<?> createProject(@RequestBody Project project, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        if (currentUser == null || currentUser.getUser() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User session invalid");
        }

        try {
            User user = userRepository.findById(currentUser.getUser().getId())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

            Project savedProject = projectRepository.save(project);
            
            // Automatically assign the creator as OWNER
            projectUserRepository.save(ProjectUser.builder()
                    .project(savedProject)
                    .user(user)
                    .projectRole(ProjectRole.OWNER)
                    .build());
            
            savedProject.setTaskCount(0L);
            savedProject.setMemberCount(1L);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedProject);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating project: " + e.getMessage());
        }
    }

    @GetMapping("/my-projects")
    public ResponseEntity<?> getMyProjects(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        if (currentUser == null || currentUser.getUser() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User session invalid");
        }
        
        try {
            List<ProjectUser> memberships = projectUserRepository.findByUserId(currentUser.getUser().getId());
            
            List<Project> projects = memberships.stream()
                    .map(ProjectUser::getProject)
                    .filter(project -> project != null)
                    .peek(project -> {
                        try {
                            project.setTaskCount(taskRepository.countByProjectId(project.getId()));
                            project.setMemberCount(projectUserRepository.countByProjectId(project.getId()));
                        } catch (Exception e) {
                            project.setTaskCount(0L);
                            project.setMemberCount(0L);
                        }
                    })
                    .collect(Collectors.toList());
                    
            return ResponseEntity.ok(projects);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error loading projects: " + e.getMessage());
        }
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
    public ResponseEntity<List<ProjectUser>> getProjectMembers(@PathVariable Long projectId) {
        List<ProjectUser> memberships = projectUserRepository.findByProjectId(projectId);
        return ResponseEntity.ok(memberships);
    }

    @PostMapping("/{projectId}/members")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Transactional
    public ResponseEntity<?> addMember(@PathVariable Long projectId, @RequestBody MemberRequest request, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        ProjectUser currentMembership = projectUserRepository.findByProjectIdAndUserId(projectId, currentUser.getUser().getId())
                .orElseThrow(() -> new RuntimeException("Not a project member"));
        
        if (currentMembership.getProjectRole() != ProjectRole.OWNER && currentMembership.getProjectRole() != ProjectRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only Owners or Admins can add members");
        }

        User userToAdd = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (projectUserRepository.findByProjectIdAndUserId(projectId, userToAdd.getId()).isPresent()) {
            return ResponseEntity.badRequest().body("User is already a member");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        ProjectUser newMember = ProjectUser.builder()
                .project(project)
                .user(userToAdd)
                .projectRole(request.getRole())
                .build();

        projectUserRepository.save(newMember);
        return ResponseEntity.ok(newMember);
    }

    @PutMapping("/{projectId}/members/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Transactional
    public ResponseEntity<?> updateMemberRole(@PathVariable Long projectId, @PathVariable Long userId, @RequestBody RoleUpdateRequest request, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        ProjectUser currentMembership = projectUserRepository.findByProjectIdAndUserId(projectId, currentUser.getUser().getId())
                .orElseThrow(() -> new RuntimeException("Not a project member"));
        
        if (currentMembership.getProjectRole() != ProjectRole.OWNER && currentMembership.getProjectRole() != ProjectRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only Owners or Admins can update roles");
        }

        ProjectUser memberToUpdate = projectUserRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        // Only owner can change another admin or owner
        if (memberToUpdate.getProjectRole() == ProjectRole.OWNER && currentMembership.getProjectRole() != ProjectRole.OWNER) {
             return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only Owner can change Owner");
        }

        memberToUpdate.setProjectRole(request.getRole());
        projectUserRepository.save(memberToUpdate);
        return ResponseEntity.ok(memberToUpdate);
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Transactional
    public ResponseEntity<?> removeMember(@PathVariable Long projectId, @PathVariable Long userId, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        ProjectUser currentMembership = projectUserRepository.findByProjectIdAndUserId(projectId, currentUser.getUser().getId())
                .orElseThrow(() -> new RuntimeException("Not a project member"));
        
        if (currentMembership.getProjectRole() != ProjectRole.OWNER && currentMembership.getProjectRole() != ProjectRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only Owners or Admins can remove members");
        }

        ProjectUser memberToRemove = projectUserRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        if (memberToRemove.getProjectRole() == ProjectRole.OWNER) {
            return ResponseEntity.badRequest().body("Cannot remove the Owner");
        }

        projectUserRepository.delete(memberToRemove);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/profile/skills")
    public ResponseEntity<?> updateSkills(@RequestBody SkillsUpdateRequest request, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        User user = userRepository.findById(currentUser.getUser().getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setSkills(request.getSkills());
        userRepository.save(user);
        return ResponseEntity.ok(user);
    }
}
