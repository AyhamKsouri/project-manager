package com.pm.controller;
import com.pm.model.*;
import com.pm.repository.ProjectRepository;
import com.pm.repository.UserRepository;
import com.pm.repository.ProjectUserRepository;
import com.pm.repository.TaskRepository;
import com.pm.security.ProjectAccessService;
import com.pm.security.UserDetailsImpl;
import com.pm.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

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

    public TaskController(TaskRepository taskRepository, ProjectUserRepository projectUserRepository, 
                          ProjectRepository projectRepository, UserRepository userRepository,
                          SimpMessagingTemplate messagingTemplate, ProjectAccessService projectAccessService,
                          AuditService auditService) {
        this.taskRepository = taskRepository; 
        this.projectUserRepository = projectUserRepository; 
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.projectAccessService = projectAccessService;
        this.auditService = auditService;
    }

    @PostMapping
    public ResponseEntity<Task> createTask(@RequestBody Map<String, Object> payload, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        try {
            Long projectId = Long.valueOf(payload.get("projectId").toString());
            Long assigneeId = payload.get("assigneeId") != null ? Long.valueOf(payload.get("assigneeId").toString()) : null;
            
            Project project = projectRepository.findById(projectId).orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));
            projectAccessService.requireProjectMember(projectId, currentUser);
            User assignee = assigneeId != null ? userRepository.findById(assigneeId).orElse(null) : null;
            if (assignee != null && !projectAccessService.isGlobalAdmin(currentUser)
                    && projectUserRepository.findByProjectIdAndUserId(projectId, assignee.getId()).isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
    
            Task task = Task.builder()
                    .title(payload.get("title").toString())
                    .description(payload.get("description").toString())
                    .status(TaskStatus.TODO)
                    .priority(payload.get("priority").toString())
                    .project(project)
                    .assignee(assignee)
                    .creator(currentUser.getUser())
                    .build();
            
            Task savedTask = taskRepository.save(task);
            messagingTemplate.convertAndSend("/topic/project/" + projectId, savedTask);
            auditService.log("CREATE_TASK", "TASK", savedTask.getId().toString(), "User created task: " + savedTask.getTitle(), currentUser.getUsername());
            return ResponseEntity.ok(savedTask);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(TaskController.class).error("Error creating task: ", e);
            throw e;
        }
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<Task>> getProjectTasks(@PathVariable Long projectId, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        projectAccessService.requireProjectMember(projectId, currentUser);
        return ResponseEntity.ok(taskRepository.findByProjectId(projectId));
    }

    @GetMapping("/my-tasks")
    public ResponseEntity<List<Task>> getMyTasks(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        projectAccessService.requireAuthenticated(currentUser);
        if (projectAccessService.isGlobalAdmin(currentUser)) {
            return ResponseEntity.ok(taskRepository.findAll());
        }
        return ResponseEntity.ok(taskRepository.findByAssigneeId(currentUser.getUser().getId()));
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

    @PutMapping("/{taskId}/assignee")
    public ResponseEntity<?> updateTaskAssignee(@PathVariable Long taskId, @RequestBody Map<String, Long> payload, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        
        ProjectUser projectUser = projectAccessService.requireProjectMember(task.getProject().getId(), currentUser);
        
        // Reassign task rule: Only Admin or Manager (Owner)
        if (!projectAccessService.isGlobalAdmin(currentUser) && projectUser.getProjectRole() != ProjectRole.OWNER && projectUser.getProjectRole() != ProjectRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only Owners or Admins can reassign tasks"));
        }

        Long assigneeId = payload.get("assigneeId");
        
        if (assigneeId != null) {
            User assignee = userRepository.findById(assigneeId).orElseThrow(() -> new RuntimeException("User not found"));
            if (!projectAccessService.isGlobalAdmin(currentUser)
                    && projectUserRepository.findByProjectIdAndUserId(task.getProject().getId(), assignee.getId()).isEmpty()) {
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

    @PutMapping("/{taskId}")
    public ResponseEntity<?> updateTask(@PathVariable Long taskId, @RequestBody Map<String, Object> payload, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        ProjectUser projectUser = projectAccessService.requireProjectMember(task.getProject().getId(), currentUser);

        // Edit description rule: Creator + Manager (Owner/Admin) + Assignee
        boolean isCreator = task.getCreator() != null && task.getCreator().getId().equals(currentUser.getUser().getId());
        boolean isManager = projectAccessService.isGlobalAdmin(currentUser) || projectUser.getProjectRole() == ProjectRole.OWNER || projectUser.getProjectRole() == ProjectRole.ADMIN;
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(currentUser.getUser().getId());

        if (!isCreator && !isManager && !isAssignee) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not have permission to edit this task"));
        }

        if (payload.containsKey("title")) task.setTitle(payload.get("title").toString());
        if (payload.containsKey("description")) task.setDescription(payload.get("description").toString());
        
        Task updatedTask = taskRepository.save(task);
        messagingTemplate.convertAndSend("/topic/project/" + task.getProject().getId(), updatedTask);
        auditService.log("UPDATE_TASK", "TASK", taskId.toString(), "Updated task details", currentUser.getUsername());
        return ResponseEntity.ok(updatedTask);
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<?> deleteTask(@PathVariable Long taskId, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        ProjectUser projectUser = projectAccessService.requireProjectMember(task.getProject().getId(), currentUser);

        // Delete task rule: Admin (Owner/Admin) or Creator
        boolean isCreator = task.getCreator() != null && task.getCreator().getId().equals(currentUser.getUser().getId());
        boolean isAdmin = projectAccessService.isGlobalAdmin(currentUser) || projectUser.getProjectRole() == ProjectRole.OWNER || projectUser.getProjectRole() == ProjectRole.ADMIN;

        if (!isCreator && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only Admins or the Creator can delete this task"));
        }

        String title = task.getTitle();
        taskRepository.delete(task);
        messagingTemplate.convertAndSend("/topic/project/" + task.getProject().getId(), Map.of("deletedTaskId", taskId));
        auditService.log("DELETE_TASK", "TASK", taskId.toString(), "Deleted task: " + title, currentUser.getUsername());
        return ResponseEntity.ok().build();
    }
}
