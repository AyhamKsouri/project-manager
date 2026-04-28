package com.pm.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Comment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(columnDefinition = "TEXT")
    private String content;
    private LocalDateTime createdAt;
    @ManyToOne @JoinColumn(name = "task_id", nullable = false)
    private Task task;
    @ManyToOne @JoinColumn(name = "user_id", nullable = false)
    private User author;
}
