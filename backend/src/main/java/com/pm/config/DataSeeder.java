package com.pm.config;
import com.pm.model.*;
import com.pm.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectUserRepository projectUserRepository;
    private final TaskRepository taskRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, ProjectRepository projectRepository, 
                      ProjectUserRepository projectUserRepository, TaskRepository taskRepository, 
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectUserRepository = projectUserRepository;
        this.taskRepository = taskRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByEmail("admin@pm.com")) {
            User admin = userRepository.save(User.builder().name("Admin").email("admin@pm.com").password(passwordEncoder.encode("password")).globalRole(GlobalRole.ADMIN).build());
            User chef = userRepository.save(User.builder().name("Chef").email("chef@pm.com").password(passwordEncoder.encode("password")).globalRole(GlobalRole.USER).skills("management, architecture").build());
            User member = userRepository.save(User.builder().name("Member").email("member@pm.com").password(passwordEncoder.encode("password")).globalRole(GlobalRole.USER).skills("java, angular").build());

            Project p1 = projectRepository.save(Project.builder().name("E-Commerce Platform").description("Building a modern e-commerce platform").methodology("Agile").build());
            
            projectUserRepository.save(ProjectUser.builder().project(p1).user(admin).projectRole(ProjectRole.CHEF).build());
            projectUserRepository.save(ProjectUser.builder().project(p1).user(chef).projectRole(ProjectRole.CHEF).build());
            projectUserRepository.save(ProjectUser.builder().project(p1).user(member).projectRole(ProjectRole.MEMBER).build());

            taskRepository.save(Task.builder().title("Setup Database").description("Configure PostgreSQL with Docker").status(TaskStatus.TODO).priority("High").project(p1).assignee(member).build());
            taskRepository.save(Task.builder().title("Design API").description("Create REST API documentation").status(TaskStatus.IN_PROGRESS).priority("Medium").project(p1).assignee(chef).build());
        }
    }
}
