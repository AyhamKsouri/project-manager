package com.pm.controller;

import com.pm.model.*;
import com.pm.repository.*;
import com.pm.security.UserDetailsImpl;
import com.pm.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectUserRepository projectUserRepository;
    private final TaskRepository taskRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    public AdminController(UserRepository userRepository, ProjectRepository projectRepository,
                           ProjectUserRepository projectUserRepository, TaskRepository taskRepository,
                           AuditLogRepository auditLogRepository, AuditService auditService,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectUserRepository = projectUserRepository;
        this.taskRepository = taskRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        long totalUsers = userRepository.count();
        long totalProjects = projectRepository.count();
        long totalTasks = taskRepository.count();
        
        long projectsWithoutOwner = projectRepository.findAll().stream()
                .filter(p -> projectUserRepository.findByProject_Id(p.getId()).stream()
                        .noneMatch(pu -> pu.getProjectRole() == ProjectRole.OWNER))
                .count();
        
        long unassignedTasks = taskRepository.findAll().stream()
                .filter(t -> t.getAssignee() == null)
                .count();
        
        long highPriorityTasks = taskRepository.findAll().stream()
                .filter(t -> "high".equalsIgnoreCase(t.getPriority()) || "critical".equalsIgnoreCase(t.getPriority()))
                .count();
        
        long idleUsers = userRepository.findAll().stream()
                .filter(u -> projectUserRepository.findByUser_Id(u.getId()).isEmpty())
                .count();

        return ResponseEntity.ok(Map.of(
                "users", totalUsers,
                "projects", totalProjects,
                "tasks", totalTasks,
                "projectsWithoutOwner", projectsWithoutOwner,
                "unassignedTasks", unassignedTasks,
                "highPriorityTasks", highPriorityTasks,
                "idleUsers", idleUsers,
                "systemHealth", "Healthy" // Basic health indicator
        ));
    }

    @GetMapping("/logs")
    public ResponseEntity<List<AuditLog>> getLogs() {
        return ResponseEntity.ok(auditLogRepository.findAllByOrderByTimestampDesc());
    }

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getUsers() {
        List<Map<String, Object>> users = userRepository.findAll().stream().map(user -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", user.getId());
            map.put("name", user.getName());
            map.put("email", user.getEmail());
            map.put("skills", user.getSkills());
            map.put("globalRole", user.getGlobalRole());
            map.put("assignedTaskCount", taskRepository.countByAssignee_Id(user.getId()));
            map.put("projectCount", (long) projectUserRepository.findByUser_Id(user.getId()).size());
            return map;
        }).toList();
        return ResponseEntity.ok(users);
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody Map<String, String> request, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        String email = request.get("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is already taken");
        }
        User user = User.builder()
                .name(request.getOrDefault("name", email))
                .email(email)
                .password(passwordEncoder.encode(request.getOrDefault("password", "password")))
                .skills(request.getOrDefault("skills", ""))
                .globalRole(parseGlobalRole(request.getOrDefault("globalRole", "USER")))
                .build();
        User saved = userRepository.save(user);
        auditService.log("CREATE_USER", "USER", saved.getId().toString(), "Created user: " + saved.getEmail(), currentUser.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<?> updateUser(@PathVariable Long userId, @RequestBody Map<String, String> request, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        
        StringBuilder changes = new StringBuilder("Updated user: " + user.getEmail() + ". Changes: ");
        if (request.containsKey("name")) {
            changes.append("name=").append(request.get("name")).append(", ");
            user.setName(request.get("name"));
        }
        
        if (request.containsKey("email")) {
            String newEmail = request.get("email");
            if (!newEmail.equals(user.getEmail()) && userRepository.existsByEmail(newEmail)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is already taken");
            }
            changes.append("email=").append(newEmail).append(", ");
            user.setEmail(newEmail);
        }

        if (request.containsKey("skills")) {
            changes.append("skills=").append(request.get("skills")).append(", ");
            user.setSkills(request.get("skills"));
        }
        
        if (request.containsKey("globalRole")) {
            GlobalRole newRole = parseGlobalRole(request.get("globalRole"));
            if (user.getGlobalRole() == GlobalRole.ADMIN && newRole == GlobalRole.USER) {
                // Check if this is the last admin
                long adminCount = userRepository.findAll().stream().filter(u -> u.getGlobalRole() == GlobalRole.ADMIN).count();
                if (adminCount <= 1) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot demote the last admin");
                }
                // Prevent self-demotion
                if (user.getId().equals(currentUser.getUser().getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot demote yourself. Another admin must do it.");
                }
            }
            changes.append("role=").append(newRole).append(", ");
            user.setGlobalRole(newRole);
        }

        if (request.containsKey("password") && request.get("password") != null && !request.get("password").isBlank()) {
            changes.append("password updated, ");
            user.setPassword(passwordEncoder.encode(request.get("password")));
        }
        User saved = userRepository.save(user);
        auditService.log("UPDATE_USER", "USER", saved.getId().toString(), changes.toString(), currentUser.getUsername());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/users/{userId}")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable Long userId, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String userEmail = user.getEmail();
        
        // Prevent self-deletion
        if (user.getId().equals(currentUser.getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete yourself");
        }

        // Prevent deleting the last admin
        if (user.getGlobalRole() == GlobalRole.ADMIN) {
            long adminCount = userRepository.findAll().stream().filter(u -> u.getGlobalRole() == GlobalRole.ADMIN).count();
            if (adminCount <= 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete the last admin");
            }
        }

        taskRepository.findByAssignee_Id(userId).forEach(task -> {
            task.setAssignee(null);
            taskRepository.save(task);
        });
        taskRepository.findByCreator_Id(userId).forEach(task -> {
            task.setCreator(null);
            taskRepository.save(task);
        });
        projectUserRepository.findByUser_Id(userId).forEach(projectUserRepository::delete);
        userRepository.delete(user);
        auditService.log("DELETE_USER", "USER", userId.toString(), "Deleted user: " + userEmail, currentUser.getUsername());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/projects")
    public ResponseEntity<List<Map<String, Object>>> getProjects() {
        List<Map<String, Object>> projects = projectRepository.findAll().stream().map(project -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", project.getId());
            map.put("name", project.getName());
            map.put("description", project.getDescription());
            map.put("methodology", project.getMethodology());
            map.put("taskCount", taskRepository.countByProject_Id(project.getId()));
            map.put("memberCount", projectUserRepository.countByProject_Id(project.getId()));
            projectUserRepository.findByProject_Id(project.getId()).stream()
                    .filter(member -> member.getProjectRole() == ProjectRole.OWNER)
                    .findFirst()
                    .ifPresent(owner -> map.put("owner", owner.getUser()));
            return map;
        }).toList();
        return ResponseEntity.ok(projects);
    }

    @PostMapping("/projects")
    @Transactional
    public ResponseEntity<?> createProject(@RequestBody Map<String, Object> request, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        Project project = Project.builder()
                .name(String.valueOf(request.getOrDefault("name", "Untitled Project")))
                .description(String.valueOf(request.getOrDefault("description", "")))
                .methodology(String.valueOf(request.getOrDefault("methodology", "Agile")))
                .build();
        Project saved = projectRepository.save(project);
        if (request.get("ownerId") != null) {
            Long ownerId = Long.valueOf(request.get("ownerId").toString());
            User owner = userRepository.findById(ownerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found"));
            projectUserRepository.save(ProjectUser.builder().project(saved).user(owner).projectRole(ProjectRole.OWNER).build());
        }
        auditService.log("CREATE_PROJECT", "PROJECT", saved.getId().toString(), "Created project: " + saved.getName(), currentUser.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/projects/{projectId}")
    public ResponseEntity<?> updateProject(@PathVariable Long projectId, @RequestBody Map<String, String> request, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        Project project = projectRepository.findById(projectId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        StringBuilder changes = new StringBuilder("Updated project: " + project.getName() + ". Changes: ");
        if (request.containsKey("name")) {
            changes.append("name=").append(request.get("name")).append(", ");
            project.setName(request.get("name"));
        }
        if (request.containsKey("description")) {
            changes.append("description=").append(request.get("description")).append(", ");
            project.setDescription(request.get("description"));
        }
        if (request.containsKey("methodology")) {
            changes.append("methodology=").append(request.get("methodology")).append(", ");
            project.setMethodology(request.get("methodology"));
        }
        Project saved = projectRepository.save(project);
        auditService.log("UPDATE_PROJECT", "PROJECT", saved.getId().toString(), changes.toString(), currentUser.getUsername());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/projects/{projectId}")
    @Transactional
    public ResponseEntity<?> deleteProject(@PathVariable Long projectId, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        Project project = projectRepository.findById(projectId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        String projectName = project.getName();
        taskRepository.deleteByProject_Id(projectId);
        projectUserRepository.findByProject_Id(projectId).forEach(projectUserRepository::delete);
        projectRepository.deleteById(projectId);
        auditService.log("DELETE_PROJECT", "PROJECT", projectId.toString(), "Deleted project: " + projectName, currentUser.getUsername());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<Task>> getTasks() {
        return ResponseEntity.ok(taskRepository.findAll());
    }

    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<?> deleteTask(@PathVariable Long taskId, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        String taskTitle = task.getTitle();
        taskRepository.deleteById(taskId);
        auditService.log("DELETE_TASK", "TASK", taskId.toString(), "Deleted task: " + taskTitle, currentUser.getUsername());
        return ResponseEntity.ok().build();
    }

    // --- BULK ACTIONS ---

    @PostMapping("/users/bulk-delete")
    @Transactional
    public ResponseEntity<?> bulkDeleteUsers(@RequestBody List<Long> userIds, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        List<User> users = userRepository.findAllById(userIds);
        for (User user : users) {
            if (user.getId().equals(currentUser.getUser().getId())) continue;
            if (user.getGlobalRole() == GlobalRole.ADMIN) {
                long adminCount = userRepository.findAll().stream().filter(u -> u.getGlobalRole() == GlobalRole.ADMIN).count();
                if (adminCount <= 1) continue;
            }
            taskRepository.findByAssignee_Id(user.getId()).forEach(t -> { t.setAssignee(null); taskRepository.save(t); });
            taskRepository.findByCreator_Id(user.getId()).forEach(t -> { t.setCreator(null); taskRepository.save(t); });
            projectUserRepository.findByUser_Id(user.getId()).forEach(projectUserRepository::delete);
            userRepository.delete(user);
        }
        auditService.log("BULK_DELETE_USERS", "USER", "multiple", "Deleted users: " + users.stream().map(User::getEmail).collect(Collectors.joining(", ")), currentUser.getUsername());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/projects/bulk-delete")
    @Transactional
    public ResponseEntity<?> bulkDeleteProjects(@RequestBody List<Long> projectIds, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        List<Project> projects = projectRepository.findAllById(projectIds);
        for (Long id : projectIds) {
            taskRepository.deleteByProject_Id(id);
            projectUserRepository.findByProject_Id(id).forEach(projectUserRepository::delete);
            projectRepository.deleteById(id);
        }
        auditService.log("BULK_DELETE_PROJECTS", "PROJECT", "multiple", "Deleted projects: " + projects.stream().map(Project::getName).collect(Collectors.joining(", ")), currentUser.getUsername());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tasks/bulk-delete")
    @Transactional
    public ResponseEntity<?> bulkDeleteTasks(@RequestBody List<Long> taskIds, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        List<Task> tasks = taskRepository.findAllById(taskIds);
        taskRepository.deleteAllById(taskIds);
        auditService.log("BULK_DELETE_TASKS", "TASK", "multiple", "Deleted tasks: " + tasks.stream().map(Task::getTitle).collect(Collectors.joining(", ")), currentUser.getUsername());
        return ResponseEntity.ok().build();
    }

    private GlobalRole parseGlobalRole(String role) {
        try {
            return GlobalRole.valueOf(role);
        } catch (Exception e) {
            return GlobalRole.USER;
        }
    }
}
