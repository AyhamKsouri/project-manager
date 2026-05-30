package com.pm.config;

import com.pm.model.*;
import com.pm.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final String DEMO_PASSWORD = "password";

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectUserRepository projectUserRepository;
    private final TaskRepository taskRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${pm.app.reseed-on-startup:true}")
    private boolean reseedOnStartup;

    public DataSeeder(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            ProjectUserRepository projectUserRepository,
            TaskRepository taskRepository,
            NotificationRepository notificationRepository,
            AuditLogRepository auditLogRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectUserRepository = projectUserRepository;
        this.taskRepository = taskRepository;
        this.notificationRepository = notificationRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        migrateChefToOwner();

        if (!reseedOnStartup && userRepository.existsByEmail("admin@promanager.demo")) {
            return;
        }

        clearAllData();
        seedDemoData();
        System.out.println("[DataSeeder] Demo dataset loaded for jury presentation.");
    }

    private void migrateChefToOwner() {
        try {
            projectUserRepository.findAll().stream()
                    .filter(pu -> pu.getProjectRole() != null && "CHEF".equals(pu.getProjectRole().name()))
                    .forEach(pu -> {
                        pu.setProjectRole(ProjectRole.OWNER);
                        projectUserRepository.save(pu);
                    });
        } catch (Exception e) {
            System.err.println("Migration error: " + e.getMessage());
        }
    }

    private void clearAllData() {
        notificationRepository.deleteAll();
        auditLogRepository.deleteAll();

        try {
            entityManager.createNativeQuery("DELETE FROM task_dependencies").executeUpdate();
        } catch (Exception ignored) {
            // join table may not exist yet on first boot
        }
        try {
            entityManager.createNativeQuery("DELETE FROM comment").executeUpdate();
        } catch (Exception ignored) {
            // comments table optional
        }

        taskRepository.deleteAll();
        projectUserRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
        entityManager.flush();
    }

    private void seedDemoData() {
        String encodedPassword = passwordEncoder.encode(DEMO_PASSWORD);

        User admin = saveUser("Alexandre Rousseau", "admin@promanager.demo", GlobalRole.ADMIN,
                "Gestion de projet, Stratégie produit, Agile", encodedPassword);
        User sophie = saveUser("Sophie Martin", "sophie.martin@promanager.demo", GlobalRole.USER,
                "Product Management, UX Research, Scrum", encodedPassword);
        User lucas = saveUser("Lucas Bernard", "lucas.bernard@promanager.demo", GlobalRole.USER,
                "Java, Spring Boot, PostgreSQL, API REST", encodedPassword);
        User emma = saveUser("Emma Dubois", "emma.dubois@promanager.demo", GlobalRole.USER,
                "Angular, TypeScript, Design System, Figma", encodedPassword);
        User thomas = saveUser("Thomas Leroy", "thomas.leroy@promanager.demo", GlobalRole.USER,
                "DevOps, Docker, CI/CD, Kubernetes", encodedPassword);

        // Legacy login alias for README compatibility
        saveUser("Administrateur", "admin@pm.com", GlobalRole.ADMIN,
                "Administration système, Reporting", encodedPassword);

        Project nova = projectRepository.save(Project.builder()
                .name("NovaShop — Plateforme E-Commerce")
                .description("Refonte complète de la boutique en ligne : catalogue, panier, paiement et tableau de bord vendeur.")
                .methodology("Agile")
                .build());

        Project fitTrack = projectRepository.save(Project.builder()
                .name("FitTrack — Application Mobile")
                .description("Application de suivi fitness avec synchronisation wearables et coaching personnalisé par IA.")
                .methodology("Scrum")
                .build());

        Project hrPortal = projectRepository.save(Project.builder()
                .name("Portail RH Entreprise")
                .description("Centralisation des demandes de congés, notes de frais et entretiens annuels.")
                .methodology("Kanban")
                .build());

        addProjectMember(nova, admin, ProjectRole.ADMIN);
        addProjectMember(nova, sophie, ProjectRole.OWNER);
        addProjectMember(nova, lucas, ProjectRole.MEMBER);
        addProjectMember(nova, emma, ProjectRole.MEMBER);
        addProjectMember(nova, thomas, ProjectRole.MEMBER);

        addProjectMember(fitTrack, admin, ProjectRole.ADMIN);
        addProjectMember(fitTrack, lucas, ProjectRole.OWNER);
        addProjectMember(fitTrack, sophie, ProjectRole.MEMBER);
        addProjectMember(fitTrack, emma, ProjectRole.MEMBER);

        addProjectMember(hrPortal, admin, ProjectRole.ADMIN);
        addProjectMember(hrPortal, emma, ProjectRole.OWNER);
        addProjectMember(hrPortal, thomas, ProjectRole.MEMBER);

        seedNovaTasks(nova, sophie, lucas, emma, thomas);
        seedFitTrackTasks(fitTrack, lucas, emma, thomas);
        seedHrTasks(hrPortal, emma, thomas);

        seedNotifications(admin, sophie, lucas, nova, fitTrack);
        seedAuditLogs(admin, sophie, nova);
    }

    private User saveUser(String name, String email, GlobalRole role, String skills, String encodedPassword) {
        return userRepository.save(User.builder()
                .name(name)
                .email(email)
                .password(encodedPassword)
                .globalRole(role)
                .skills(skills)
                .build());
    }

    private void addProjectMember(Project project, User user, ProjectRole role) {
        projectUserRepository.save(ProjectUser.builder()
                .project(project)
                .user(user)
                .projectRole(role)
                .build());
    }

    private void seedNovaTasks(Project project, User sophie, User lucas, User emma, User thomas) {
        LocalDate nextWeek = LocalDate.now().plusDays(7);

        Task t1 = saveTask("Maquettes page d'accueil", "Wireframes haute fidélité et validation UX avec le comité produit.",
                TaskStatus.TODO, "High", 5, 4, nextWeek, "Sprint 2", project, emma, sophie);
        saveTask("Intégration paiement Stripe", "Checkout sécurisé, webhooks et scénarios de remboursement.",
                TaskStatus.TODO, "High", 8, 6, nextWeek.plusDays(3), "Sprint 2", project, lucas, sophie);
        saveTask("API catalogue produits", "Endpoints CRUD, filtres, pagination et cache Redis.",
                TaskStatus.IN_PROGRESS, "High", 8, 5, nextWeek, "Sprint 2", project, lucas, sophie);
        saveTask("Authentification JWT & rôles", "Login, refresh token et politique d'accès par rôle projet.",
                TaskStatus.IN_PROGRESS, "Medium", 5, 4, nextWeek.minusDays(2), "Sprint 2", project, lucas, sophie);
        saveTask("Tests unitaires module panier", "Couverture > 80 % sur les règles de calcul et promotions.",
                TaskStatus.IN_REVIEW, "Medium", 3, 3, nextWeek.minusDays(1), "Sprint 2", project, thomas, sophie);
        saveTask("Revue sécurité OWASP", "Audit des endpoints publics et durcissement des en-têtes HTTP.",
                TaskStatus.IN_REVIEW, "High", 5, 2, nextWeek, "Sprint 2", project, thomas, sophie);
        saveTask("Pipeline CI/CD GitHub Actions", "Build, tests, analyse SonarQube et déploiement staging.",
                TaskStatus.COMPLETED, "Medium", 5, 3, LocalDate.now().minusDays(5), "Sprint 1", project, thomas, sophie);
        saveTask("Schéma base PostgreSQL", "Modèle relationnel, index et migrations Flyway.",
                TaskStatus.COMPLETED, "Medium", 3, 2, LocalDate.now().minusDays(8), "Sprint 1", project, lucas, sophie);

        Task t3 = saveTask("Composants catalogue Angular", "Grille produits, filtres latéraux et états de chargement.",
                TaskStatus.IN_PROGRESS, "Medium", 5, 4, nextWeek.plusDays(2), "Sprint 2", project, emma, sophie);
        t3.getDependencies().add(t1);
        taskRepository.save(t3);
    }

    private void seedFitTrackTasks(Project project, User lucas, User emma, User thomas) {
        LocalDate sprintEnd = LocalDate.now().plusDays(14);
        saveTask("Synchronisation Apple Health", "Import des pas, calories et sessions d'entraînement.",
                TaskStatus.IN_PROGRESS, "High", 8, 7, sprintEnd, "Sprint 3", project, lucas, lucas);
        saveTask("Écran tableau de bord", "Widgets progression, objectifs hebdomadaires et notifications.",
                TaskStatus.TODO, "Medium", 5, 5, sprintEnd, "Sprint 3", project, emma, lucas);
        saveTask("API recommandations IA", "Suggestions d'entraînement basées sur l'historique utilisateur.",
                TaskStatus.IN_REVIEW, "High", 8, 6, sprintEnd.minusDays(2), "Sprint 3", project, lucas, lucas);
        saveTask("Déploiement environnement staging", "Conteneurs Docker et variables d'environnement sécurisées.",
                TaskStatus.COMPLETED, "Low", 3, 2, LocalDate.now().minusDays(3), "Sprint 2", project, thomas, lucas);
    }

    private void seedHrTasks(Project project, User emma, User thomas) {
        saveTask("Formulaire demande de congés", "Workflow validation manager + export calendrier équipe.",
                TaskStatus.IN_PROGRESS, "Medium", 5, 4, LocalDate.now().plusDays(10), null, project, emma, emma);
        saveTask("Module notes de frais", "Upload justificatifs PDF et circuit d'approbation finance.",
                TaskStatus.TODO, "Medium", 5, 5, LocalDate.now().plusDays(12), null, project, thomas, emma);
        saveTask("Tableau suivi entretiens annuels", "Vue Kanban par département et rappels automatiques.",
                TaskStatus.COMPLETED, "Low", 3, 2, LocalDate.now().minusDays(4), null, project, emma, emma);
    }

    private Task saveTask(
            String title,
            String description,
            TaskStatus status,
            String priority,
            Integer storyPoints,
            Integer estimatedDays,
            LocalDate dueDate,
            String sprintName,
            Project project,
            User assignee,
            User creator) {
        return taskRepository.save(Task.builder()
                .title(title)
                .description(description)
                .status(status)
                .priority(priority)
                .storyPoints(storyPoints)
                .estimatedDays(estimatedDays)
                .dueDate(dueDate)
                .sprintName(sprintName)
                .riskLevel(priority.equals("High") ? "HIGH" : "LOW")
                .project(project)
                .assignee(assignee)
                .creator(creator)
                .assignmentReason("Compétences alignées : " + (assignee != null ? assignee.getSkills() : "équipe"))
                .build());
    }

    private void seedNotifications(User admin, User sophie, User lucas, Project nova, Project fitTrack) {
        notificationRepository.saveAll(List.of(
                Notification.builder()
                        .user(admin)
                        .type("info")
                        .title("Bienvenue sur ProManager")
                        .message("Environnement de démonstration prêt. Explorez NovaShop pour la présentation jury.")
                        .readStatus(false)
                        .timestamp(LocalDateTime.now().minusMinutes(10))
                        .build(),
                Notification.builder()
                        .user(admin)
                        .type("success")
                        .title("Sprint 1 terminé — NovaShop")
                        .message("8 story points livrés : CI/CD et schéma base validés par l'équipe.")
                        .readStatus(true)
                        .timestamp(LocalDateTime.now().minusDays(1))
                        .actionUrl("/kanban/" + nova.getId())
                        .build(),
                Notification.builder()
                        .user(sophie)
                        .type("warning")
                        .title("Revue en attente")
                        .message("2 tâches en révision sur NovaShop nécessitent votre validation.")
                        .readStatus(false)
                        .timestamp(LocalDateTime.now().minusHours(3))
                        .actionUrl("/kanban/" + nova.getId())
                        .build(),
                Notification.builder()
                        .user(lucas)
                        .type("info")
                        .title("FitTrack — Sprint 3 démarré")
                        .message("Synchronisation Apple Health et API recommandations IA en cours.")
                        .readStatus(false)
                        .timestamp(LocalDateTime.now().minusHours(5))
                        .actionUrl("/kanban/" + fitTrack.getId())
                        .build(),
                Notification.builder()
                        .user(admin)
                        .type("success")
                        .title("Assistant IA disponible")
                        .message("Posez vos questions sur les projets, tâches et charge équipe via l'assistant en bas à droite.")
                        .readStatus(false)
                        .timestamp(LocalDateTime.now().minusMinutes(45))
                        .build()
        ));
    }

    private void seedAuditLogs(User admin, User sophie, Project nova) {
        auditLogRepository.saveAll(List.of(
                AuditLog.builder()
                        .action("CREATE_PROJECT")
                        .entityType("PROJECT")
                        .entityId(nova.getId().toString())
                        .details("Projet « NovaShop — Plateforme E-Commerce » créé pour la démo jury.")
                        .performedByEmail(admin.getEmail())
                        .timestamp(LocalDateTime.now().minusDays(14))
                        .build(),
                AuditLog.builder()
                        .action("ASSIGN_TASK")
                        .entityType("TASK")
                        .entityId("API catalogue produits")
                        .details("Tâche assignée à Lucas Bernard par Sophie Martin.")
                        .performedByEmail(sophie.getEmail())
                        .timestamp(LocalDateTime.now().minusDays(2))
                        .build(),
                AuditLog.builder()
                        .action("UPDATE_TASK_STATUS")
                        .entityType("TASK")
                        .entityId("Pipeline CI/CD GitHub Actions")
                        .details("Statut passé à COMPLETED après validation QA.")
                        .performedByEmail(admin.getEmail())
                        .timestamp(LocalDateTime.now().minusHours(6))
                        .build()
        ));
    }
}
