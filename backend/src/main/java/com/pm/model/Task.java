package com.pm.model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Task {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Enumerated(EnumType.STRING)
    private TaskStatus status;
    
    private String priority;
    private Integer storyPoints;
    private Integer estimatedDays;
    private LocalDate dueDate;
    private String sprintName;
    private String riskLevel; // Added for future use as per user request (PHASE 1 mentioned risk levels)
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean deleted = false;
    
    @Column(columnDefinition = "TEXT")
    private String assignmentReason;

    @ManyToMany
    @JoinTable(
        name = "task_dependencies",
        joinColumns = @JoinColumn(name = "task_id"),
        inverseJoinColumns = @JoinColumn(name = "dependency_id")
    )
    @Builder.Default
    @JsonIgnore
    private Set<Task> dependencies = new HashSet<>();

    @JsonProperty("dependsOn")
    public List<String> getDependsOnTitles() {
        return dependencies.stream().map(Task::getTitle).toList();
    }

    @ManyToOne @JoinColumn(name = "project_id", nullable = false)
    @JsonIgnore
    private Project project;

    @JsonProperty("projectId")
    public Long getProjectId() {
        return project != null ? project.getId() : null;
    }

    @JsonProperty("projectName")
    public String getProjectName() {
        return project != null ? project.getName() : null;
    }
    
    @ManyToOne @JoinColumn(name = "assignee_id", nullable = true)
    private User assignee;

    @ManyToOne @JoinColumn(name = "creator_id", nullable = true)
    private User creator;
}
