package com.pm.service;

import com.pm.model.Notification;
import com.pm.model.User;
import com.pm.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public Notification createNotification(User user, String type, String title, String message, String actionUrl) {
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .actionUrl(actionUrl)
                .readStatus(false)
                .timestamp(LocalDateTime.now())
                .build();

        Notification saved = notificationRepository.save(notification);
        
        // Push to user via WebSocket
        messagingTemplate.convertAndSendToUser(
            user.getEmail(), 
            "/topic/notifications", 
            saved
        );

        return saved;
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setReadStatus(true);
            notificationRepository.save(n);
        });
    }

    @Transactional
    public void markAllAsRead(User user) {
        List<Notification> unread = notificationRepository.findByUserAndReadStatusFalse(user);
        unread.forEach(n -> n.setReadStatus(true));
        notificationRepository.saveAll(unread);
    }

    @Transactional
    public void deleteNotification(Long id) {
        notificationRepository.deleteById(id);
    }

    @Transactional
    public void deleteAllNotifications(User user) {
        notificationRepository.deleteByUser(user);
    }
}
