package com.pm.controller;
import com.pm.model.*;
import com.pm.dto.AssignTaskRequest;
import com.pm.dto.MoveTaskRequest;
import com.pm.dto.SprintContextResponse;
import com.pm.dto.TaskCreateRequest;
import com.pm.dto.TaskResponse;
import com.pm.dto.TaskUpdateRequest;
import com.pm.repository.ProjectRepository;
import com.pm.repository.UserRepository;
import com.pm.repository.ProjectUserRepository;
import com.pm.repository.TaskRepository;
import com.pm.security.ProjectAccessService;
import com.pm.security.UserDetailsImpl;
import com.pm.service.AuditService;
import com.pm.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskRepository taskRepository;
    private final ProjectUserRepository projectUserRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ProjectAccessService projectAccessService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public TaskController(TaskRepository taskRepository, ProjectUserRepository projectUserRepository, 
                          ProjectRepository projectRepository, UserRepository userRepository,
                          SimpMessagingTemplate messagingTemplate, ProjectAccessService projectAccessService,
                          AuditService auditService, NotificationService notificationService) {
        this.taskRepository = taskRepository; 
        this.projectUserRepository = projectUserRepository; 
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.projectAccessService = projectAccessService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody TaskCreateRequest payload, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        try {
            Long projectId = payload.getProjectId();
            
            Project project = projectRepository.findById(projectId).orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));
            projectAccessService.requireProjectMember(projectId, currentUser);
            User assignee = resolveAssignee(projectId, payload.getAssigneeId(), payload.getAssignee()).orElse(null);
            if (assignee != null && !projectAccessService.isGlobalAdmin(currentUser)
                    && projectUserRepository.findByProject_IdAndUser_Id(projectId, assignee.getId()).isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
    
            Task task = Task.builder()
                    .title(payload.getTitle().trim())
                    .description(Optional.ofNullable(payload.getDescription()).orElse(""))
                    .status(normalizeStatus(payload.getStatus()))
                    .priority(normalizePriority(payload.getPriority()))
                    .sprintName(Optional.ofNullable(payload.getSprintName()).orElse("Backlog"))
                    .project(project)
                    .assignee(assignee)
                    .creator(currentUser.getUser())
                    .build();
            
            Task savedTask = taskRepository.save(task);
            messagingTemplate.convertAndSend("/topic/project/" + projectId, savedTask);
            auditService.log("CREATE_TASK", "TASK", savedTask.getId().toString(), "User created task: " + savedTask.getTitle(), currentUser.getUsername());
            
            // Notify assignee
            if (assignee != null && !assignee.getId().equals(currentUser.getUser().getId())) {
                notificationService.createNotification(
                    assignee,
                    "info",
                    "New Task Assigned",
                    "You have been assigned to task: " + savedTask.getTitle(),
                    "/kanban/" + projectId
                );
            }
            
            return ResponseEntity.status(HttpStatus.CREATED).body(TaskResponse.from(savedTask));
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(TaskController.class).error("Error creating task: ", e);
            throw e;
        }
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<Task>> getProjectTasks(@PathVariable Long projectId, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        projectAccessService.requireProjectMember(projectId, currentUser);
        return ResponseEntity.ok(taskRepository.findByProject_IdAndDeletedFalse(projectId));
    }

    @GetMapping("/my-tasks")
    public ResponseEntity<List<Task>> getMyTasks(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        projectAccessService.requireAuthenticated(currentUser);
        if (projectAccessService.isGlobalAdmin(currentUser)) {
            return ResponseEntity.ok(taskRepository.findAll().stream().filter(t -> !t.isDeleted()).toList());
        }
        return ResponseEntity.ok(taskRepository.findByAssignee_IdAndDeletedFalse(currentUser.getUser().getId()));
    }

    @PutMapping("/{taskId}/status")
    public ResponseEntity<?> updateTaskStatus(@PathVariable Long taskId, @RequestBody Map<String, String> payload, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        TaskStatus newStatus = TaskStatus.valueOf(payload.get("status"));
        
        ProjectUser projectUser = projectAccessService.requireProjectMember(task.getProject().getId(), currentUser);
        
        boolean isGlobalAdmin = projectAccessService.isGlobalAdmin(currentUser);
        boolean isProjectManager = isGlobalAdmin || projectUser.getProjectRole() == ProjectRole.OWNER || projectUser.getProjectRole() == ProjectRole.ADMIN;
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(currentUser.getUser().getId());

        // Rule 1: Only assignee or project manager can move tasks
        if (!isProjectManager && !isAssignee) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the assignee or project managers can move this task"));
        }

        // Rule 2: Only project managers can move task to COMPLETED if it's currently IN_REVIEW (Approval)
        if (newStatus == TaskStatus.COMPLETED && task.getStatus() == TaskStatus.IN_REVIEW && !isProjectManager) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only Owners or Admins can approve task completion"));
        }

        // Rule 3: Only project managers can move task OUT of IN_REVIEW (e.g., Send Back)
        if (task.getStatus() == TaskStatus.IN_REVIEW && newStatus != TaskStatus.COMPLETED && !isProjectManager) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only Owners or Admins can send back tasks for revision"));
        }

        task.setStatus(newStatus);
        Task updatedTask = taskRepository.save(task);
        messagingTemplate.convertAndSend("/topic/project/" + task.getProject().getId(), updatedTask);
        auditService.log("UPDATE_TASK_STATUS", "TASK", taskId.toString(), "Changed status to " + newStatus, currentUser.getUsername());
        return ResponseEntity.ok(updatedTask);
    }

    @PatchMapping("/{taskId}/assignee")
    public ResponseEntity<?> patchTaskAssignee(@PathVariable Long taskId, @Valid @RequestBody AssignTaskRequest payload, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        if (task.isDeleted()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Task not found"));
        
        ProjectUser projectUser = projectAccessService.requireProjectMember(task.getProject().getId(), currentUser);
        
        // Reassign task rule: Only Admin or Manager (Owner)
        if (!projectAccessService.isGlobalAdmin(currentUser) && projectUser.getProjectRole() != ProjectRole.OWNER && projectUser.getProjectRole() != ProjectRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only Owners or Admins can reassign tasks"));
        }

        if (payload.getAssigneeId() != null || (payload.getAssignee() != null && !payload.getAssignee().isBlank())) {
            User assignee = resolveAssignee(task.getProject().getId(), payload.getAssigneeId(), payload.getAssignee())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            if (!projectAccessService.isGlobalAdmin(currentUser)
                    && projectUserRepository.findByProject_IdAndUser_Id(task.getProject().getId(), assignee.getId()).isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Assignee must be a project member"));
            }
            task.setAssignee(assignee);
        } else {
            task.setAssignee(null);
        }
        
        Task updatedTask = taskRepository.save(task);
        messagingTemplate.convertAndSend("/topic/project/" + task.getProject().getId(), updatedTask);
        String assigneeEmail = task.getAssignee() != null ? task.getAssignee().getEmail() : "Unassigned";
        auditService.log("UPDATE_TASK_ASSIGNEE", "TASK", taskId.toString(), "Assigned to: " + assigneeEmail, currentUser.getUsername());
        return ResponseEntity.ok(updatedTask);
    }

    @PutMapping("/{taskId}/assignee")
    public ResponseEntity<?> updateTaskAssignee(@PathVariable Long taskId, @RequestBody Map<String, Long> payload, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        AssignTaskRequest request = new AssignTaskRequest();
        request.setAssigneeId(payload.get("assigneeId"));
        return patchTaskAssignee(taskId, request, currentUser);
    }

    @PatchMapping("/{taskId}")
    public ResponseEntity<?> patchTask(@PathVariable Long taskId, @Valid @RequestBody TaskUpdateRequest payload, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        if (task.isDeleted()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Task not found"));
        ProjectUser projectUser = projectAccessService.requireProjectMember(task.getProject().getId(), currentUser);

        boolean isCreator = task.getCreator() != null && task.getCreator().getId().equals(currentUser.getUser().getId());
        boolean isManager = projectAccessService.isGlobalAdmin(currentUser) || projectUser.getProjectRole() == ProjectRole.OWNER || projectUser.getProjectRole() == ProjectRole.ADMIN;
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(currentUser.getUser().getId());

        if (!isCreator && !isManager && !isAssignee) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not have permission to edit this task"));
        }

        if (payload.getTitle() != null) task.setTitle(payload.getTitle().trim());
        if (payload.getDescription() != null) task.setDescription(payload.getDescription());
        if (payload.getStatus() != null) task.setStatus(normalizeStatus(payload.getStatus()));
        if (payload.getPriority() != null) task.setPriority(normalizePriority(payload.getPriority()));
        if (payload.getAssigneeId() != null || (payload.getAssignee() != null && !payload.getAssignee().isBlank())) {
            User assignee = resolveAssignee(task.getProject().getId(), payload.getAssigneeId(), payload.getAssignee())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            task.setAssignee(assignee);
        }

        Task updatedTask = taskRepository.save(task);
        messagingTemplate.convertAndSend("/topic/project/" + task.getProject().getId(), updatedTask);
        auditService.log("UPDATE_TASK", "TASK", taskId.toString(), "Updated task details", currentUser.getUsername());
        return ResponseEntity.ok(TaskResponse.from(updatedTask));
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<?> updateTask(@PathVariable Long taskId, @RequestBody Map<String, Object> payload, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        if (task.isDeleted()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Task not found"));
        ProjectUser projectUser = projectAccessService.requireProjectMember(task.getProject().getId(), currentUser);

        // Edit description rule: Creator + Manager (Owner/Admin) + Assignee
        boolean isCreator = task.getCreator() != null && task.getCreator().getId().equals(currentUser.getUser().getId());
        boolean isManager = projectAccessService.isGlobalAdmin(currentUser) || projectUser.getProjectRole() == ProjectRole.OWNER || projectUser.getProjectRole() == ProjectRole.ADMIN;
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(currentUser.getUser().getId());

        if (!isCreator && !isManager && !isAssignee) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not have permission to edit this task"));
        }

        if (payload.containsKey("title")) task.setTitle(payload.get("title").toString().trim());
        if (payload.containsKey("description")) task.setDescription(payload.get("description").toString());
        if (payload.containsKey("status")) task.setStatus(normalizeStatus(payload.get("status").toString()));
        if (payload.containsKey("priority")) task.setPriority(normalizePriority(payload.get("priority").toString()));
        
        Task updatedTask = taskRepository.save(task);
        messagingTemplate.convertAndSend("/topic/project/" + task.getProject().getId(), updatedTask);
        auditService.log("UPDATE_TASK", "TASK", taskId.toString(), "Updated task details", currentUser.getUsername());
        return ResponseEntity.ok(updatedTask);
    }

    @PatchMapping("/{taskId}/sprint")
    public ResponseEntity<?> moveTaskToSprint(@PathVariable Long taskId, @Valid @RequestBody MoveTaskRequest payload, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        if (task.isDeleted()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Task not found"));
        ProjectUser projectUser = projectAccessService.requireProjectMember(task.getProject().getId(), currentUser);
        boolean isManager = projectAccessService.isGlobalAdmin(currentUser) || projectUser.getProjectRole() == ProjectRole.OWNER || projectUser.getProjectRole() == ProjectRole.ADMIN;
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(currentUser.getUser().getId());
        if (!isManager && !isAssignee) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the assignee or project managers can move this task"));
        }

        task.setSprintName(payload.getSprintId().trim());
        Task updatedTask = taskRepository.save(task);
        messagingTemplate.convertAndSend("/topic/project/" + task.getProject().getId(), updatedTask);
        auditService.log("MOVE_TASK_SPRINT", "TASK", taskId.toString(), "Moved task to sprint " + payload.getSprintId(), currentUser.getUsername());
        return ResponseEntity.ok(TaskResponse.from(updatedTask));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<?> deleteTask(@PathVariable Long taskId, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        if (task.isDeleted()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Task not found"));
        ProjectUser projectUser = projectAccessService.requireProjectMember(task.getProject().getId(), currentUser);

        // Delete task rule: Admin (Owner/Admin) or Creator
        boolean isCreator = task.getCreator() != null && task.getCreator().getId().equals(currentUser.getUser().getId());
        boolean isAdmin = projectAccessService.isGlobalAdmin(currentUser) || projectUser.getProjectRole() == ProjectRole.OWNER || projectUser.getProjectRole() == ProjectRole.ADMIN;

        if (!isCreator && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only Admins or the Creator can delete this task"));
        }

        String title = task.getTitle();
        task.setDeleted(true);
        taskRepository.save(task);
        messagingTemplate.convertAndSend("/topic/project/" + task.getProject().getId(), Map.of("deletedTaskId", taskId));
        auditService.log("DELETE_TASK", "TASK", taskId.toString(), "Deleted task: " + title, currentUser.getUsername());
        return ResponseEntity.ok().build();
    }

    private TaskStatus normalizeStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return TaskStatus.TODO;
        }
        String status = rawStatus.trim().toUpperCase();
        if ("DONE".equals(status)) {
            return TaskStatus.COMPLETED;
        }
        return TaskStatus.valueOf(status);
    }

    private String normalizePriority(String rawPriority) {
        if (rawPriority == null || rawPriority.isBlank()) {
            return "Medium";
        }
        String normalized = rawPriority.trim().toUpperCase();
        return switch (normalized) {
            case "LOW" -> "Low";
            case "HIGH" -> "High";
            case "CRITICAL" -> "Critical";
            default -> "Medium";
        };
    }

    private Optional<User> resolveAssignee(Long projectId, Long assigneeId, String assigneeNameOrEmail) {
        if (assigneeId != null) {
            return userRepository.findById(assigneeId);
        }
        if (assigneeNameOrEmail == null || assigneeNameOrEmail.isBlank()) {
            return Optional.empty();
        }
        String lookup = assigneeNameOrEmail.trim();
        return projectUserRepository.findByProject_Id(projectId).stream()
                .map(ProjectUser::getUser)
                .filter(user -> lookup.equalsIgnoreCase(user.getName()) || lookup.equalsIgnoreCase(user.getEmail()))
                .findFirst();
    }
}
