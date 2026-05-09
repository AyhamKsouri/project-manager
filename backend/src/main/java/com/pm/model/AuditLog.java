package com.pm.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String action; // e.g., "CREATE_USER", "DELETE_PROJECT", "REASSIGN_TASK"
    private String entityType; // e.g., "USER", "PROJECT", "TASK"
    private String entityId;
    @Column(columnDefinition = "TEXT")
    private String details;
    
    private String performedByEmail;
    private LocalDateTime timestamp;
}
