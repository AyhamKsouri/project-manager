package com.pm.repository;
import com.pm.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByProjectId(Long projectId);
    boolean existsByProjectIdAndTitleIgnoreCase(Long projectId, String title);
    long countByProjectId(Long projectId);
}
