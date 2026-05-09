package com.pm.repository;
import com.pm.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByProjectId(Long projectId);
    List<Task> findByAssigneeId(Long assigneeId);
    List<Task> findByCreatorId(Long creatorId);
    boolean existsByProjectIdAndTitleIgnoreCase(Long projectId, String title);
    long countByProjectId(Long projectId);
    long countByAssigneeId(Long assigneeId);
    void deleteByProjectId(Long projectId);
}
