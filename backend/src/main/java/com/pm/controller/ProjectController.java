package com.pm.controller;

import com.pm.model.User;
import com.pm.model.Project;
import com.pm.model.ProjectUser;
import com.pm.model.ProjectRole;
import com.pm.repository.ProjectRepository;
import com.pm.repository.ProjectUserRepository;
import com.pm.repository.TaskRepository;
import com.pm.repository.UserRepository;
import com.pm.security.ProjectAccessService;
import com.pm.security.UserDetailsImpl;
import com.pm.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectRepository projectRepository;
    private final ProjectUserRepository projectUserRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ProjectAccessService projectAccessService;
    private final AuditService auditService;

    public ProjectController(ProjectRepository projectRepository, ProjectUserRepository projectUserRepository, 
                            TaskRepository taskRepository, UserRepository userRepository, 
                            ProjectAccessService projectAccessService, AuditService auditService) {
        this.projectRepository = projectRepository;
        this.projectUserRepository = projectUserRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.projectAccessService = projectAccessService;
        this.auditService = auditService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Transactional
    public ResponseEntity<?> createProject(@RequestBody Project project, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        if (currentUser == null || currentUser.getUser() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session invalid");
        }

        User user = userRepository.findById(currentUser.getUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Authenticated user not found"));

        Project savedProject = projectRepository.save(project);
        
        // Automatically assign the creator as OWNER
        projectUserRepository.save(ProjectUser.builder()
                .project(savedProject)
                .user(user)
                .projectRole(ProjectRole.OWNER)
                .build());
        
        savedProject.setTaskCount(0L);
        savedProject.setMemberCount(1L);
        auditService.log("CREATE_PROJECT", "PROJECT", savedProject.getId().toString(), "User created project: " + savedProject.getName(), currentUser.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(savedProject);
    }

    @GetMapping("/my-projects")
    public ResponseEntity<?> getMyProjects(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        if (currentUser == null || currentUser.getUser() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User session invalid");
        }
        
        try {
            List<ProjectUser> memberships = projectUserRepository.findByUser_Id(currentUser.getUser().getId());
            
            List<Project> projects = memberships.stream()
                    .map(ProjectUser::getProject)
                    .filter(project -> project != null)
                    .peek(project -> {
                        try {
                            project.setTaskCount(taskRepository.countByProject_Id(project.getId()));
                            project.setMemberCount(projectUserRepository.countByProject_Id(project.getId()));
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
    public ResponseEntity<Project> getProject(@PathVariable Long projectId, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        projectAccessService.requireProjectMember(projectId, currentUser);
        return projectRepository.findById(projectId)
                .map(project -> {
                    project.setTaskCount(taskRepository.countByProject_Id(project.getId()));
                    project.setMemberCount(projectUserRepository.countByProject_Id(project.getId()));
                    return ResponseEntity.ok(project);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{projectId}/members")
    public ResponseEntity<List<ProjectUser>> getProjectMembers(@PathVariable Long projectId, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        projectAccessService.requireProjectMember(projectId, currentUser);
        List<ProjectUser> memberships = projectUserRepository.findByProject_Id(projectId);
        return ResponseEntity.ok(memberships);
    }

    @PutMapping("/{projectId}")
    @Transactional
    public ResponseEntity<?> updateProject(@PathVariable Long projectId, @RequestBody Project request, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        projectAccessService.requireProjectManager(projectId, currentUser);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setMethodology(request.getMethodology());
        Project saved = projectRepository.save(project);
        auditService.log("UPDATE_PROJECT", "PROJECT", projectId.toString(), "User updated project: " + project.getName(), currentUser.getUsername());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{projectId}")
    @Transactional
    public ResponseEntity<?> deleteProject(@PathVariable Long projectId, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        projectAccessService.requireProjectManager(projectId, currentUser);
        Project project = projectRepository.findById(projectId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        String projectName = project.getName();
        taskRepository.deleteByProject_Id(projectId);
        projectUserRepository.findByProject_Id(projectId).forEach(projectUserRepository::delete);
        projectRepository.deleteById(projectId);
        auditService.log("DELETE_PROJECT", "PROJECT", projectId.toString(), "User deleted project: " + projectName, currentUser.getUsername());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{projectId}/owner/{userId}")
    @Transactional
    public ResponseEntity<?> transferOwnership(@PathVariable Long projectId, @PathVariable Long userId, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        ProjectUser currentMembership = projectAccessService.requireProjectManager(projectId, currentUser);
        if (!projectAccessService.isGlobalAdmin(currentUser) && currentMembership.getProjectRole() != ProjectRole.OWNER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only the owner can transfer ownership");
        }
        ProjectUser newOwner = projectUserRepository.findByProject_IdAndUser_Id(projectId, userId)
                .orElseThrow(() -> new RuntimeException("New owner must be a project member"));
        projectUserRepository.findByProject_Id(projectId).forEach(member -> {
            if (member.getProjectRole() == ProjectRole.OWNER) {
                member.setProjectRole(ProjectRole.ADMIN);
                projectUserRepository.save(member);
            }
        });
        newOwner.setProjectRole(ProjectRole.OWNER);
        projectUserRepository.save(newOwner);
        return ResponseEntity.ok(newOwner);
    }

    @PostMapping("/{projectId}/members")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Transactional
    public ResponseEntity<?> addMember(@PathVariable Long projectId, @RequestBody MemberRequest request, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        projectAccessService.requireProjectManager(projectId, currentUser);

        User userToAdd = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (projectUserRepository.findByProject_IdAndUser_Id(projectId, userToAdd.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is already a member");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        ProjectUser newMember = ProjectUser.builder()
                .project(project)
                .user(userToAdd)
                .projectRole(request.getRole())
                .build();

        projectUserRepository.save(newMember);
        auditService.log("ADD_MEMBER", "PROJECT", projectId.toString(), "Added member: " + userToAdd.getEmail() + " as " + request.getRole(), currentUser.getUsername());
        return ResponseEntity.ok(newMember);
    }

    @PutMapping("/{projectId}/members/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Transactional
    public ResponseEntity<?> updateMemberRole(@PathVariable Long projectId, @PathVariable Long userId, @RequestBody RoleUpdateRequest request, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        ProjectUser currentMembership = projectAccessService.requireProjectManager(projectId, currentUser);

        ProjectUser memberToUpdate = projectUserRepository.findByProject_IdAndUser_Id(projectId, userId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        // Only owner can change another admin or owner
        if (!projectAccessService.isGlobalAdmin(currentUser) && memberToUpdate.getProjectRole() == ProjectRole.OWNER && currentMembership.getProjectRole() != ProjectRole.OWNER) {
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
        projectAccessService.requireProjectManager(projectId, currentUser);

        ProjectUser memberToRemove = projectUserRepository.findByProject_IdAndUser_Id(projectId, userId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        if (memberToRemove.getProjectRole() == ProjectRole.OWNER) {
            return ResponseEntity.badRequest().body("Cannot remove the Owner");
        }

        projectUserRepository.delete(memberToRemove);
        return ResponseEntity.ok().build();
    }
}
