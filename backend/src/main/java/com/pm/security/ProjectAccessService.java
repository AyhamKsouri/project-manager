package com.pm.security;

import com.pm.model.ProjectRole;
import com.pm.model.ProjectUser;
import com.pm.model.Task;
import com.pm.repository.ProjectUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectAccessService {
    private final ProjectUserRepository projectUserRepository;

    public ProjectAccessService(ProjectUserRepository projectUserRepository) {
        this.projectUserRepository = projectUserRepository;
    }

    public boolean isGlobalAdmin(UserDetailsImpl user) {
        return user != null
                && user.getUser() != null
                && user.getUser().getGlobalRole() != null
                && "ADMIN".equals(user.getUser().getGlobalRole().name());
    }

    public ProjectUser requireProjectMember(Long projectId, UserDetailsImpl user) {
        requireAuthenticated(user);
        if (isGlobalAdmin(user)) {
            return null;
        }
        return projectUserRepository.findByProject_IdAndUser_Id(projectId, user.getUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this project"));
    }

    public ProjectUser requireProjectManager(Long projectId, UserDetailsImpl user) {
        ProjectUser membership = requireProjectMember(projectId, user);
        if (isGlobalAdmin(user)) {
            return null;
        }
        if (membership.getProjectRole() != ProjectRole.OWNER && membership.getProjectRole() != ProjectRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only project owners or admins can perform this action");
        }
        return membership;
    }

    public void requireTaskAccess(Task task, UserDetailsImpl user) {
        requireProjectMember(task.getProject().getId(), user);
    }

    public void requireAuthenticated(UserDetailsImpl user) {
        if (user == null || user.getUser() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session invalid");
        }
    }

    public void requireAssigneeOrManager(Task task, UserDetailsImpl user) {
        if (isGlobalAdmin(user)) {
            return;
        }
        ProjectUser membership = requireProjectMember(task.getProject().getId(), user);
        boolean isManager = membership.getProjectRole() == ProjectRole.OWNER || membership.getProjectRole() == ProjectRole.ADMIN;
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(user.getUser().getId());
        if (!isManager && !isAssignee) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assignee or project managers can perform this action");
        }
    }
}
