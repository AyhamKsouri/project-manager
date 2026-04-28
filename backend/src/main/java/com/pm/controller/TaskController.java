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
    public ResponseEntity<Task> createTask(@RequestBody Map<String, Object> payload) {
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
        
        if (newStatus == TaskStatus.COMPLETED && task.getStatus() == TaskStatus.IN_REVIEW) {
            ProjectUser projectUser = projectUserRepository.findByProjectIdAndUserId(task.getProject().getId(), currentUser.getUser().getId()).orElseThrow();
            if (projectUser.getProjectRole() != ProjectRole.CHEF) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only Chef can validate task completion"));
        }
        task.setStatus(newStatus);
        Task updatedTask = taskRepository.save(task);
        messagingTemplate.convertAndSend("/topic/project/" + task.getProject().getId(), updatedTask);
        return ResponseEntity.ok(updatedTask);
    }
}
