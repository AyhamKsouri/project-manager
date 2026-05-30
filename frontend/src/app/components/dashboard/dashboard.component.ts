import { Component, OnInit } from '@angular/core';
import { ProjectService } from '../../services/project.service';
import { AuthService } from '../../services/auth.service';
import { Router } from '@angular/router';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  projects: any[] = [];
  loading = false;
  showCreateModal = false;
  newProject = { name: '', description: '', methodology: 'Agile' };
  userRole: string = '';
  userName: string = '';

  constructor(
    private projectService: ProjectService, 
    private authService: AuthService,
    private router: Router,
    private toastService: ToastService
  ) { }

  ngOnInit(): void {
    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }
    const user = this.authService.getUser();
    this.userRole = user?.role || '';
    this.userName = user?.name || user?.email || 'there';
    this.loadProjects();
  }

  get isAdmin(): boolean {
    return this.userRole === 'ROLE_ADMIN';
  }

  get totalTasks(): number {
    return this.projects.reduce((sum, project) => sum + (project.taskCount || 0), 0);
  }

  get totalMembers(): number {
    return this.projects.reduce((sum, project) => sum + (project.memberCount || 0), 0);
  }

  get activeProjects(): number {
    return this.projects.length;
  }

  get emptyProjects(): number {
    return this.projects.filter(project => !project.taskCount).length;
  }

  get largestProject(): any {
    return [...this.projects].sort((a, b) => (b.taskCount || 0) - (a.taskCount || 0))[0] || null;
  }

  loadProjects(): void {
    this.loading = true;
    this.projectService.getMyProjects().subscribe({
      next: (projects) => {
        this.projects = projects;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        if (err.status === 401) {
          this.toastService.error('Your session expired. Please sign in again.');
          this.authService.logout();
        } else if (err.status === 0) {
          this.toastService.error('Cannot connect to the backend. Please check that the server is running.');
        } else {
          this.toastService.error(err.error?.message || 'Could not load your projects.');
        }
      }
    });
  }

  createProject(): void {
    this.projectService.createProject(this.newProject).subscribe({
      next: (project) => {
        this.projects.push(project);
        this.showCreateModal = false;
        this.newProject = { name: '', description: '', methodology: 'Agile' };
        this.toastService.success('Projet créé avec succès');
      },
      error: (err) => {
        this.toastService.error(err.error?.message || err.message || 'Failed to create project');
      }
    });
  }

  selectProject(projectId: number): void {
    this.router.navigate(['/kanban', projectId]);
  }
}
