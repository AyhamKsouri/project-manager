package com.pm.repository;
import com.pm.model.ProjectUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProjectUserRepository extends JpaRepository<ProjectUser, Long> {
    Optional<ProjectUser> findByProject_IdAndUser_Id(Long projectId, Long userId);
    List<ProjectUser> findByUser_Id(Long userId);
    List<ProjectUser> findByProject_Id(Long projectId);
    long countByProject_Id(Long projectId);
}
