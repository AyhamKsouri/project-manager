package com.pm.config;
import com.pm.model.*;
import com.pm.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class DataSeeder implements CommandLineRunner {
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectUserRepository projectUserRepository;
    private final TaskRepository taskRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, ProjectRepository projectRepository, 
                      ProjectUserRepository projectUserRepository, TaskRepository taskRepository, 
                      NotificationRepository notificationRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectUserRepository = projectUserRepository;
        this.taskRepository = taskRepository;
        this.notificationRepository = notificationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Migration: Update any existing CHEF roles to OWNER
        try {
            projectUserRepository.findAll().stream()
                .filter(pu -> pu.getProjectRole() != null && "CHEF".equals(pu.getProjectRole().name()))
                .forEach(pu -> {
                    pu.setProjectRole(ProjectRole.OWNER);
                    projectUserRepository.save(pu);
                });
        } catch (Exception e) {
            // Log the error but don't crash the seeder
            System.err.println("Migration error: " + e.getMessage());
        }

        if (!userRepository.existsByEmail("admin@pm.com")) {
            User admin = userRepository.save(User.builder().name("Admin").email("admin@pm.com").password(passwordEncoder.encode("password")).globalRole(GlobalRole.ADMIN).build());
            User chef = userRepository.save(User.builder().name("Chef").email("chef@pm.com").password(passwordEncoder.encode("password")).globalRole(GlobalRole.USER).skills("management, architecture").build());
            User member = userRepository.save(User.builder().name("Member").email("member@pm.com").password(passwordEncoder.encode("password")).globalRole(GlobalRole.USER).skills("java, angular").build());

            Project p1 = projectRepository.save(Project.builder().name("E-Commerce Platform").description("Building a modern e-commerce platform").methodology("Agile").build());
            
            projectUserRepository.save(ProjectUser.builder().project(p1).user(admin).projectRole(ProjectRole.ADMIN).build());
            projectUserRepository.save(ProjectUser.builder().project(p1).user(chef).projectRole(ProjectRole.OWNER).build());
            projectUserRepository.save(ProjectUser.builder().project(p1).user(member).projectRole(ProjectRole.MEMBER).build());

            taskRepository.save(Task.builder().title("Setup Database").description("Configure PostgreSQL with Docker").status(TaskStatus.TODO).priority("High").project(p1).assignee(member).build());
            taskRepository.save(Task.builder().title("Design API").description("Create REST API documentation").status(TaskStatus.IN_PROGRESS).priority("Medium").project(p1).assignee(chef).build());

            // Seed sample notifications for Admin
            notificationRepository.save(Notification.builder()
                .user(admin)
                .type("info")
                .title("Welcome to ProManager")
                .message("We're glad to have you here. Start by creating a new project!")
                .readStatus(false)
                .timestamp(LocalDateTime.now())
                .build());

            notificationRepository.save(Notification.builder()
                .user(admin)
                .type("success")
                .title("Project Approved")
                .message("Your project 'E-Commerce Platform' has been successfully initialized.")
                .readStatus(true)
                .timestamp(LocalDateTime.now().minusDays(1))
                .build());

            notificationRepository.save(Notification.builder()
                .user(admin)
                .type("warning")
                .title("Deadline Approaching")
                .message("The 'Setup Database' task is due in 24 hours.")
                .readStatus(false)
                .timestamp(LocalDateTime.now().minusHours(2))
                .actionUrl("/kanban/" + p1.getId())
                .build());

            notificationRepository.save(Notification.builder()
                .user(admin)
                .type("error")
                .title("System Alert")
                .message("Unauthorized login attempt detected from IP 192.168.1.105.")
                .readStatus(false)
                .timestamp(LocalDateTime.now().minusMinutes(30))
                .build());
        }
    }
}
