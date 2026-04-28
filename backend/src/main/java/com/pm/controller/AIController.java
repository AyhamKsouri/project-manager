package com.pm.controller;
import com.pm.model.*;
import com.pm.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/ai")
@PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
public class AIController {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AIController.class);

    @Value("${AI_SERVICE_URL:http://localhost:8000}")
    private String aiServiceUrl;

    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectUserRepository projectUserRepository;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/generate-tasks")
    @Transactional
    public ResponseEntity<Object> generateTasks(@RequestBody Map<String, Object> request) {
        logger.info("Received request to generate tasks with AI: {}", request);
        
        Long projectId = Long.valueOf(request.get("projectId").toString());
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        try {
            logger.info("Forwarding request to AI service at: {}", aiServiceUrl);
            ResponseEntity<String> response = restTemplate.postForEntity(
                aiServiceUrl + "/generate-tasks", 
                new HttpEntity<>(request, headers), 
                String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.getBody());
                JsonNode tasksNode = root.get("tasks");
                
                List<Task> createdTasks = new ArrayList<>();
                if (tasksNode != null && tasksNode.isArray()) {
                    List<ProjectUser> memberships = projectUserRepository.findByProjectId(projectId);
                    List<String> validPriorities = List.of("low", "medium", "high", "critical");
                    
                    Map<String, Task> taskMap = new java.util.HashMap<>();
                    taskRepository.findByProjectId(projectId).forEach(t -> taskMap.put(t.getTitle().toLowerCase(), t));
                    
                    for (JsonNode taskNode : tasksNode) {
                        String title = taskNode.get("title").asText();
                        if (taskRepository.existsByProjectIdAndTitleIgnoreCase(projectId, title)) {
                            logger.warn("Skipping duplicate task title: '{}' in project {}", title, projectId);
                            continue;
                        }

                        String description = taskNode.path("description").asText("No description provided");
                        String assignedTo = taskNode.path("assigned_to").asText("Unassigned");
                        String priority = taskNode.path("priority").asText("medium").toLowerCase();
                        
                        if (!validPriorities.contains(priority)) {
                            priority = "medium";
                        }

                        int storyPoints = taskNode.path("story_points").asInt(1);
                        int estimatedDays = taskNode.path("estimated_days").asInt(1);
                        int deadlineOffset = taskNode.path("deadline_offset_days").asInt(7);
                        String sprint = taskNode.path("sprint").asText("Backlog");
                        
                        Task task = new Task();
                        task.setTitle(title);
                        task.setDescription(description);
                        task.setStatus(TaskStatus.TODO);
                        task.setPriority(priority);
                        task.setStoryPoints(storyPoints);
                        task.setEstimatedDays(estimatedDays);
                        task.setDueDate(LocalDate.now().plusDays(deadlineOffset));
                        task.setSprintName(sprint);
                        task.setProject(project);
                        
                        Optional<User> assignee = memberships.stream()
                                .map(ProjectUser::getUser)
                                .filter(u -> u.getName().equalsIgnoreCase(assignedTo) || u.getEmail().equalsIgnoreCase(assignedTo))
                                .findFirst();
                        
                        assignee.ifPresent(task::setAssignee);
                        
                        Task savedTask = taskRepository.save(task);
                        createdTasks.add(savedTask);
                        taskMap.put(title.toLowerCase(), savedTask);
                    }

                    for (JsonNode taskNode : tasksNode) {
                        String title = taskNode.get("title").asText().toLowerCase();
                        if (!taskMap.containsKey(title)) continue;
                        
                        Task task = taskMap.get(title);
                        JsonNode depsNode = taskNode.path("depends_on");
                        if (depsNode.isArray()) {
                            for (JsonNode depNode : depsNode) {
                                String depTitle = depNode.asText().toLowerCase();
                                if (taskMap.containsKey(depTitle)) {
                                    task.getDependencies().add(taskMap.get(depTitle));
                                }
                            }
                            taskRepository.save(task);
                        }
                    }
                }
                
                messagingTemplate.convertAndSend("/topic/project/" + projectId, Map.of("action", "REFRESH"));
                return ResponseEntity.ok(createdTasks);
            }
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (Exception e) { 
            logger.error("Error calling AI service or saving tasks: ", e);
            throw new RuntimeException("AI Task Generation failed: " + e.getMessage());
        }
    }

    @PostMapping("/{projectId}/analyze-risk")
    @Transactional(readOnly = true)
    public ResponseEntity<Object> analyzeProjectRisk(@PathVariable Long projectId) {
        try {
            projectRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found"));

            List<Task> tasks = taskRepository.findByProjectId(projectId);
            List<ProjectUser> memberships = projectUserRepository.findByProjectId(projectId);
            List<String> teamMembers = memberships.stream()
                    .map(m -> m.getUser().getName())
                    .toList();

            List<Map<String, Object>> taskData = tasks.stream().map(t -> {
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("title", t.getTitle());
                map.put("status", t.getStatus());
                map.put("priority", t.getPriority());
                map.put("story_points", t.getStoryPoints());
                map.put("due_date", t.getDueDate() != null ? t.getDueDate().toString() : null);
                map.put("assignee", t.getAssignee() != null ? t.getAssignee().getName() : "Unassigned");
                map.put("is_overdue", t.getDueDate() != null && t.getDueDate().isBefore(LocalDate.now()) && t.getStatus() != TaskStatus.COMPLETED);
                return map;
            }).toList();

            Map<String, Object> aiRequest = new java.util.HashMap<>();
            aiRequest.put("tasks", taskData);
            aiRequest.put("team_members", teamMembers);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            ResponseEntity<Object> response = restTemplate.postForEntity(
                aiServiceUrl + "/analyze-project-risk", 
                new HttpEntity<>(aiRequest, headers), 
                Object.class
            );
            
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            logger.error("Error analyzing project risk: ", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
