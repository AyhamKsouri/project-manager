package com.pm.repository;

import com.pm.model.Notification;
import com.pm.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByUserOrderByTimestampDesc(User user, Pageable pageable);
    List<Notification> findByUserAndReadStatusFalse(User user);
    long countByUserAndReadStatusFalse(User user);
    void deleteByUser(User user);
}
