package com.pm.repository;
import com.pm.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByProject_Id(Long projectId);
    List<Task> findByProject_IdAndDeletedFalse(Long projectId);
    List<Task> findByAssignee_IdAndDeletedFalse(Long assigneeId);
    List<Task> findByAssignee_Id(Long assigneeId);
    List<Task> findByCreator_Id(Long creatorId);
    boolean existsByProject_IdAndTitleIgnoreCase(Long projectId, String title);
    long countByProject_Id(Long projectId);
    long countByAssignee_Id(Long assigneeId);
    void deleteByProject_Id(Long projectId);
}
