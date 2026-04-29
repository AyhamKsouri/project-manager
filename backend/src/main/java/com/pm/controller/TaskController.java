package com.pm.controller;
import com.pm.model.*;
import com.pm.repository.ProjectRepository;
import com.pm.repository.UserRepository;
import com.pm.repository.ProjectUserRepository;
import com.pm.repository.TaskRepository;
import com.pm.security.UserDetailsImpl;
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

    public TaskController(TaskRepository taskRepository, ProjectUserRepository projectUserRepository, 
                          ProjectRepository projectRepository, UserRepository userRepository,
                          SimpMessagingTemplate messagingTemplate) {
        this.taskRepository = taskRepository; 
        this.projectUserRepository = projectUserRepository; 
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping
    public ResponseEntity<Task> createTask(@RequestBody Map<String, Object> payload, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        try {
            Long projectId = Long.valueOf(payload.get("projectId").toString());
            Long assigneeId = payload.get("assigneeId") != null ? Long.valueOf(payload.get("assigneeId").toString()) : null;
            
            Project project = projectRepository.findById(projectId).orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));
            User assignee = assigneeId != null ? userRepository.findById(assigneeId).orElse(null) : null;
    
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
            return ResponseEntity.ok(savedTask);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(TaskController.class).error("Error creating task: ", e);
            throw e;
        }
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<Task>> getProjectTasks(@PathVariable Long projectId) { return ResponseEntity.ok(taskRepository.findByProjectId(projectId)); }

    @PutMapping("/{taskId}/status")
    public ResponseEntity<?> updateTaskStatus(@PathVariable Long taskId, @RequestBody Map<String, String> payload, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        TaskStatus newStatus = TaskStatus.valueOf(payload.get("status"));
        
        ProjectUser projectUser = projectUserRepository.findByProjectIdAndUserId(task.getProject().getId(), currentUser.getUser().getId()).orElseThrow();
        
        // Move task rule: Only Assignee can move the task
        if (task.getAssignee() == null || !task.getAssignee().getId().equals(currentUser.getUser().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the assignee can move this task"));
        }

        if (newStatus == TaskStatus.COMPLETED && task.getStatus() == TaskStatus.IN_REVIEW) {
            if (projectUser.getProjectRole() != ProjectRole.OWNER && projectUser.getProjectRole() != ProjectRole.ADMIN) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only Owners or Admins can validate task completion"));
        }
        task.setStatus(newStatus);
        Task updatedTask = taskRepository.save(task);
        messagingTemplate.convertAndSend("/topic/project/" + task.getProject().getId(), updatedTask);
        return ResponseEntity.ok(updatedTask);
    }

    @PutMapping("/{taskId}/assignee")
    public ResponseEntity<?> updateTaskAssignee(@PathVariable Long taskId, @RequestBody Map<String, Long> payload, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        
        ProjectUser projectUser = projectUserRepository.findByProjectIdAndUserId(task.getProject().getId(), currentUser.getUser().getId()).orElseThrow();
        
        // Reassign task rule: Only Admin or Manager (Owner)
        if (projectUser.getProjectRole() != ProjectRole.OWNER && projectUser.getProjectRole() != ProjectRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only Owners or Admins can reassign tasks"));
        }

        Long assigneeId = payload.get("assigneeId");
        
        if (assigneeId != null) {
            User assignee = userRepository.findById(assigneeId).orElseThrow(() -> new RuntimeException("User not found"));
            task.setAssignee(assignee);
        } else {
            task.setAssignee(null);
        }
        
        Task updatedTask = taskRepository.save(task);
        messagingTemplate.convertAndSend("/topic/project/" + task.getProject().getId(), updatedTask);
        return ResponseEntity.ok(updatedTask);
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<?> updateTask(@PathVariable Long taskId, @RequestBody Map<String, Object> payload, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        ProjectUser projectUser = projectUserRepository.findByProjectIdAndUserId(task.getProject().getId(), currentUser.getUser().getId()).orElseThrow();

        // Edit description rule: Creator + Manager (Owner/Admin) + Assignee
        boolean isCreator = task.getCreator() != null && task.getCreator().getId().equals(currentUser.getUser().getId());
        boolean isManager = projectUser.getProjectRole() == ProjectRole.OWNER || projectUser.getProjectRole() == ProjectRole.ADMIN;
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(currentUser.getUser().getId());

        if (!isCreator && !isManager && !isAssignee) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not have permission to edit this task"));
        }

        if (payload.containsKey("title")) task.setTitle(payload.get("title").toString());
        if (payload.containsKey("description")) task.setDescription(payload.get("description").toString());
        
        Task updatedTask = taskRepository.save(task);
        messagingTemplate.convertAndSend("/topic/project/" + task.getProject().getId(), updatedTask);
        return ResponseEntity.ok(updatedTask);
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<?> deleteTask(@PathVariable Long taskId, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        ProjectUser projectUser = projectUserRepository.findByProjectIdAndUserId(task.getProject().getId(), currentUser.getUser().getId()).orElseThrow();

        // Delete task rule: Admin (Owner/Admin) or Creator
        boolean isCreator = task.getCreator() != null && task.getCreator().getId().equals(currentUser.getUser().getId());
        boolean isAdmin = projectUser.getProjectRole() == ProjectRole.OWNER || projectUser.getProjectRole() == ProjectRole.ADMIN;

        if (!isCreator && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only Admins or the Creator can delete this task"));
        }

        taskRepository.delete(task);
        messagingTemplate.convertAndSend("/topic/project/" + task.getProject().getId(), Map.of("deletedTaskId", taskId));
        return ResponseEntity.ok().build();
    }
}
