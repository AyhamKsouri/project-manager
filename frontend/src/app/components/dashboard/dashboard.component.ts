import { Component, OnInit } from '@angular/core';
import { ProjectService } from '../../services/project.service';
import { AuthService } from '../../services/auth.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit {
  projects: any[] = [];
  showCreateModal = false;
  newProject = { name: '', description: '', methodology: 'Agile' };
  userRole: string = '';

  constructor(
    private projectService: ProjectService, 
    private authService: AuthService,
    private router: Router
  ) { }

  ngOnInit(): void {
    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }
    const user = this.authService.getUser();
    this.userRole = user?.role || '';
    this.loadProjects();
  }

  loadProjects(): void {
    this.projectService.getMyProjects().subscribe({
      next: (projects) => {
        console.log('Dashboard: Successfully loaded projects', projects.length);
        this.projects = projects;
      },
      error: (err) => {
        console.error('Dashboard: Failed to load projects', err);
        if (err.status === 401) {
          console.warn('Dashboard: 401 Unauthorized - Token might be invalid or expired');
        } else if (err.status === 0) {
          console.error('Dashboard: Unknown Error (Status 0). This usually means the server is down, a CORS issue, or the connection was dropped mid-response.');
          alert('Cannot connect to server. Please check if the backend is running or if there is a network issue.');
        }
      }
    });
  }

  createProject(): void {
    console.log('Dashboard: Attempting to create project', this.newProject.name);
    this.projectService.createProject(this.newProject).subscribe({
      next: (project) => {
        console.log('Dashboard: Project created successfully', project.id);
        this.projects.push(project);
        this.showCreateModal = false;
        this.newProject = { name: '', description: '', methodology: 'Agile' };
      },
      error: (err) => {
        console.error('Dashboard: Project creation failed', err);
        alert('Failed to create project: ' + (err.error?.message || err.message || 'Unauthorized'));
      }
    });
  }

  selectProject(projectId: number): void {
    this.router.navigate(['/kanban', projectId]);
  }
}
