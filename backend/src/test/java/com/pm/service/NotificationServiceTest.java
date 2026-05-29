package com.pm.service;

import com.pm.model.Notification;
import com.pm.model.User;
import com.pm.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private NotificationService notificationService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
    }

    @Test
    void createNotification_ShouldSaveAndSendWebSocketMessage() {
        // Arrange
        String type = "info";
        String title = "Test Title";
        String message = "Test Message";
        String actionUrl = "/test";

        Notification notification = Notification.builder()
                .id(1L)
                .user(testUser)
                .type(type)
                .title(title)
                .message(message)
                .actionUrl(actionUrl)
                .build();

        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        // Act
        Notification result = notificationService.createNotification(testUser, type, title, message, actionUrl);

        // Assert
        assertNotNull(result);
        assertEquals(title, result.getTitle());
        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(messagingTemplate, times(1)).convertAndSendToUser(
                eq(testUser.getEmail()),
                eq("/topic/notifications"),
                any(Notification.class)
        );
    }

    @Test
    void markAsRead_ShouldUpdateStatus() {
        // Arrange
        Notification notification = new Notification();
        notification.setId(1L);
        notification.setReadStatus(false);

        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        // Act
        notificationService.markAsRead(1L);

        // Assert
        assertTrue(notification.isReadStatus());
        verify(notificationRepository, times(1)).save(notification);
    }
}
