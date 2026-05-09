package com.pm.service;

import com.pm.model.AuditLog;
import com.pm.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(String action, String entityType, String entityId, String details, String performedByEmail) {
        AuditLog log = AuditLog.builder()
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .performedByEmail(performedByEmail)
                .timestamp(LocalDateTime.now())
                .build();
        auditLogRepository.save(log);
    }
}
