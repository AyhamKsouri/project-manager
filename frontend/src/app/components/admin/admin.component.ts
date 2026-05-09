import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AdminService } from '../../services/admin.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { ConfirmService } from '../../services/confirm.service';

@Component({
  selector: 'app-admin',
  templateUrl: './admin.component.html'
})
export class AdminComponent implements OnInit {
  summary: any = { 
    users: 0, projects: 0, tasks: 0, 
    projectsWithoutOwner: 0, unassignedTasks: 0, 
    highPriorityTasks: 0, idleUsers: 0, systemHealth: 'Unknown' 
  };
  users: any[] = [];
  projects: any[] = [];
  tasks: any[] = [];
  logs: any[] = [];
  activeTab: 'users' | 'projects' | 'tasks' | 'logs' = 'users';
  loading = false;

  userSearch = '';
  projectSearch = '';
  taskSearch = '';
  taskStatusFilter = 'ALL';
  
  // Specific Insight Filters
  showIdleUsersOnly = false;
  showProjectsWithoutOwnerOnly = false;
  showUnassignedTasksOnly = false;
  showHighPriorityTasksOnly = false;

  // Bulk Selection
  selectedUserIds = new Set<number>();
  selectedProjectIds = new Set<number>();
  selectedTaskIds = new Set<number>();

  userForm: any = this.emptyUser();
  projectForm: any = this.emptyProject();
  editingUserId: number | null = null;
  editingProjectId: number | null = null;

  constructor(
    private adminService: AdminService,
    private authService: AuthService,
    private router: Router,
    private toastService: ToastService,
    private confirmService: ConfirmService
  ) {}

  ngOnInit(): void {
    if (!this.isAdmin()) {
      this.toastService.error('Admin access required');
      this.router.navigate(['/dashboard']);
      return;
    }
    this.loadAll();
  }

  isAdmin(): boolean {
    return this.authService.getUser()?.role === 'ROLE_ADMIN';
  }

  get filteredUsers(): any[] {
    let filtered = this.users;
    if (this.showIdleUsersOnly) {
      filtered = filtered.filter(u => u.projectCount === 0);
    }
    if (!this.userSearch) return filtered;
    const q = this.userSearch.toLowerCase();
    return filtered.filter(u =>
      u.name?.toLowerCase().includes(q) ||
      u.email?.toLowerCase().includes(q) ||
      u.globalRole?.toLowerCase().includes(q) ||
      u.skills?.toLowerCase().includes(q)
    );
  }

  get filteredProjects(): any[] {
    let filtered = this.projects;
    if (this.showProjectsWithoutOwnerOnly) {
      filtered = filtered.filter(p => !p.owner);
    }
    if (!this.projectSearch) return filtered;
    const q = this.projectSearch.toLowerCase();
    return filtered.filter(p =>
      p.name?.toLowerCase().includes(q) ||
      p.description?.toLowerCase().includes(q) ||
      p.methodology?.toLowerCase().includes(q) ||
      p.owner?.name?.toLowerCase().includes(q)
    );
  }

  get filteredTasks(): any[] {
    let filtered = this.tasks;
    if (this.showUnassignedTasksOnly) {
      filtered = filtered.filter(t => !t.assignee);
    }
    if (this.showHighPriorityTasksOnly) {
      filtered = filtered.filter(t => t.priority?.toLowerCase() === 'high' || t.priority?.toLowerCase() === 'critical');
    }
    if (this.taskStatusFilter && this.taskStatusFilter !== 'ALL') {
      filtered = filtered.filter(t => t.status === this.taskStatusFilter);
    }
    if (!this.taskSearch) return filtered;
    const q = this.taskSearch.toLowerCase();
    return filtered.filter(t =>
      t.title?.toLowerCase().includes(q) ||
      t.description?.toLowerCase().includes(q) ||
      t.status?.toLowerCase().includes(q) ||
      t.assignee?.name?.toLowerCase().includes(q) ||
      t.priority?.toLowerCase().includes(q) ||
      t.projectName?.toLowerCase().includes(q)
    );
  }

  get projectsWithoutOwner(): number {
    return this.projects.filter(project => !project.owner).length;
  }

  get unassignedTasks(): number {
    return this.tasks.filter(task => !task.assignee).length;
  }

  get highPriorityTasks(): number {
    return this.tasks.filter(task => ['critical', 'high'].includes((task.priority || '').toLowerCase())).length;
  }

  get usersWithoutProjects(): number {
    return this.users.filter(user => !user.projectCount).length;
  }

  loadAll(): void {
    this.loading = true;
    forkJoin({
      summary: this.adminService.getSummary(),
      users: this.adminService.getUsers(),
      projects: this.adminService.getProjects(),
      tasks: this.adminService.getTasks(),
      logs: this.adminService.getLogs()
    }).subscribe({
      next: (res) => {
        this.summary = res.summary;
        this.users = res.users;
        this.projects = res.projects;
        this.tasks = res.tasks;
        this.logs = res.logs;
        this.loading = false;
      },
      error: (err) => {
        this.toastService.error('Failed to load system data');
        this.loading = false;
      }
    });
  }

  loadUsers(): void {
    this.adminService.getUsers().subscribe({ next: users => this.users = users });
  }

  loadProjects(): void {
    this.adminService.getProjects().subscribe({ next: projects => this.projects = projects });
  }

  loadTasks(): void {
    this.adminService.getTasks().subscribe({ next: tasks => this.tasks = tasks });
  }

  // --- Insight Click Handlers ---
  filterIdleUsers(): void {
    this.activeTab = 'users';
    this.showIdleUsersOnly = true;
    this.userSearch = '';
  }

  filterProjectsWithoutOwner(): void {
    this.activeTab = 'projects';
    this.showProjectsWithoutOwnerOnly = true;
    this.projectSearch = '';
  }

  filterUnassignedTasks(): void {
    this.activeTab = 'tasks';
    this.showUnassignedTasksOnly = true;
    this.showHighPriorityTasksOnly = false;
    this.taskSearch = '';
  }

  filterHighPriorityTasks(): void {
    this.activeTab = 'tasks';
    this.showHighPriorityTasksOnly = true;
    this.showUnassignedTasksOnly = false;
    this.taskSearch = '';
  }

  clearInsightFilters(): void {
    this.showIdleUsersOnly = false;
    this.showProjectsWithoutOwnerOnly = false;
    this.showUnassignedTasksOnly = false;
    this.showHighPriorityTasksOnly = false;
  }

  // --- Bulk Selection ---
  toggleUserSelection(userId: number): void {
    if (this.selectedUserIds.has(userId)) this.selectedUserIds.delete(userId);
    else this.selectedUserIds.add(userId);
  }

  toggleProjectSelection(projectId: number): void {
    if (this.selectedProjectIds.has(projectId)) this.selectedProjectIds.delete(projectId);
    else this.selectedProjectIds.add(projectId);
  }

  toggleTaskSelection(taskId: number): void {
    if (this.selectedTaskIds.has(taskId)) this.selectedTaskIds.delete(taskId);
    else this.selectedTaskIds.add(taskId);
  }

  bulkDeleteUsers(): void {
    if (this.selectedUserIds.size === 0) return;
    this.confirmService.confirm({
      title: 'Bulk Delete Users',
      message: `Are you sure you want to delete ${this.selectedUserIds.size} users?`,
      type: 'danger'
    }).then(ok => {
      if (!ok) return;
      this.adminService.bulkDeleteUsers(Array.from(this.selectedUserIds)).subscribe({
        next: () => {
          this.toastService.success('Users deleted');
          this.selectedUserIds.clear();
          this.loadAll();
        },
        error: err => this.toastService.error(err.error?.error || 'Could not delete users')
      });
    });
  }

  bulkDeleteProjects(): void {
    if (this.selectedProjectIds.size === 0) return;
    this.confirmService.confirm({
      title: 'Bulk Delete Projects',
      message: `Are you sure you want to delete ${this.selectedProjectIds.size} projects?`,
      type: 'danger'
    }).then(ok => {
      if (!ok) return;
      this.adminService.bulkDeleteProjects(Array.from(this.selectedProjectIds)).subscribe({
        next: () => {
          this.toastService.success('Projects deleted');
          this.selectedProjectIds.clear();
          this.loadAll();
        },
        error: err => this.toastService.error(err.error?.error || 'Could not delete projects')
      });
    });
  }

  bulkDeleteTasks(): void {
    if (this.selectedTaskIds.size === 0) return;
    this.confirmService.confirm({
      title: 'Bulk Delete Tasks',
      message: `Are you sure you want to delete ${this.selectedTaskIds.size} tasks?`,
      type: 'danger'
    }).then(ok => {
      if (!ok) return;
      this.adminService.bulkDeleteTasks(Array.from(this.selectedTaskIds)).subscribe({
        next: () => {
          this.toastService.success('Tasks deleted');
          this.selectedTaskIds.clear();
          this.loadAll();
        },
        error: err => this.toastService.error(err.error?.error || 'Could not delete tasks')
      });
    });
  }

  saveUser(): void {
    const action = this.editingUserId
      ? this.adminService.updateUser(this.editingUserId, this.userForm)
      : this.adminService.createUser(this.userForm);

    action.subscribe({
      next: () => {
        this.toastService.success(this.editingUserId ? 'User updated' : 'User created');
        this.resetUserForm();
        this.loadUsers();
        this.adminService.getSummary().subscribe({ next: res => this.summary = res });
      },
      error: err => this.toastService.error(err.error?.error || 'Could not save user')
    });
  }

  editUser(user: any): void {
    this.editingUserId = user.id;
    this.userForm = { ...user, password: '' };
  }

  deleteUser(user: any): void {
    this.confirmService.confirm({
      title: 'Delete User',
      message: `Delete ${user.email}? Their task assignments and memberships will be removed.`,
      confirmText: 'Delete',
      type: 'danger'
    }).then(confirmed => {
      if (!confirmed) return;
      this.adminService.deleteUser(user.id).subscribe({
        next: () => {
          this.toastService.success('User deleted');
          this.loadUsers();
          this.loadProjects();
          this.loadTasks();
        },
        error: err => this.toastService.error(err.error?.error || 'Could not delete user')
      });
    });
  }

  saveProject(): void {
    const action = this.editingProjectId
      ? this.adminService.updateProject(this.editingProjectId, this.projectForm)
      : this.adminService.createProject(this.projectForm);

    action.subscribe({
      next: () => {
        this.toastService.success(this.editingProjectId ? 'Project updated' : 'Project created');
        this.resetProjectForm();
        this.loadProjects();
        this.adminService.getSummary().subscribe({ next: res => this.summary = res });
      },
      error: err => this.toastService.error(err.error?.error || 'Could not save project')
    });
  }

  editProject(project: any): void {
    this.editingProjectId = project.id;
    this.projectForm = {
      name: project.name,
      description: project.description,
      methodology: project.methodology,
      ownerId: project.owner?.id || null
    };
  }

  deleteProject(project: any): void {
    this.confirmService.confirm({
      title: 'Delete Project',
      message: `Delete project "${project.name}" and its tasks?`,
      confirmText: 'Delete',
      type: 'danger'
    }).then(confirmed => {
      if (!confirmed) return;
      this.adminService.deleteProject(project.id).subscribe({
        next: () => {
          this.toastService.success('Project deleted');
          this.loadProjects();
          this.loadTasks();
        },
        error: err => this.toastService.error(err.error?.error || 'Could not delete project')
      });
    });
  }

  deleteTask(task: any): void {
    this.confirmService.confirm({
      title: 'Delete Task',
      message: `Delete task "${task.title}"?`,
      confirmText: 'Delete',
      type: 'danger'
    }).then(confirmed => {
      if (!confirmed) return;
      this.adminService.deleteTask(task.id).subscribe({
        next: () => {
          this.toastService.success('Task deleted');
          this.loadTasks();
        },
        error: err => this.toastService.error(err.error?.error || 'Could not delete task')
      });
    });
  }

  resetUserForm(): void {
    this.editingUserId = null;
    this.userForm = this.emptyUser();
  }

  resetProjectForm(): void {
    this.editingProjectId = null;
    this.projectForm = this.emptyProject();
  }

  private emptyUser(): any {
    return { name: '', email: '', password: '', skills: '', globalRole: 'USER' };
  }

  private emptyProject(): any {
    return { name: '', description: '', methodology: 'Agile', ownerId: null };
  }
}
