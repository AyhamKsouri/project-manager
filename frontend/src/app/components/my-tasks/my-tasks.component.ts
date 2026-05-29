import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { TaskService } from '../../services/task.service';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-my-tasks',
  templateUrl: './my-tasks.component.html',
  styleUrls: ['./my-tasks.component.css']
})
export class MyTasksComponent implements OnInit {
  tasks: any[] = [];
  statusFilter = 'ALL';
  priorityFilter = 'ALL';
  groupBy: 'status' | 'dueDate' = 'status';

  showTaskModal = false;
  selectedTask: any = null;

  constructor(
    private taskService: TaskService,
    private authService: AuthService,
    private router: Router,
    private toastService: ToastService
  ) {}

  ngOnInit(): void {
    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }
    this.loadTasks();
  }

  get filteredTasks(): any[] {
    return this.tasks.filter(task => {
      const statusMatch = this.statusFilter === 'ALL' || task.status === this.statusFilter;
      const priorityMatch = this.priorityFilter === 'ALL' || (task.priority || '').toLowerCase() === this.priorityFilter.toLowerCase();
      return statusMatch && priorityMatch;
    }).sort((a, b) => this.taskRank(a) - this.taskRank(b));
  }

  get openTaskCount(): number {
    return this.tasks.filter(task => task.status !== 'COMPLETED').length;
  }

  get reviewTaskCount(): number {
    return this.tasks.filter(task => task.status === 'IN_REVIEW').length;
  }

  get urgentTaskCount(): number {
    return this.tasks.filter(task => ['critical', 'high'].includes((task.priority || '').toLowerCase()) && task.status !== 'COMPLETED').length;
  }

  loadTasks(): void {
    this.taskService.getMyTasks().subscribe({
      next: tasks => {
        this.tasks = tasks;
        if (this.selectedTask) {
          this.selectedTask = tasks.find(t => t.id === this.selectedTask.id);
        }
      },
      error: () => this.toastService.error('Impossible de charger vos tâches')
    });
  }

  get groupedTasks(): { title: string, tasks: any[] }[] {
    const filtered = this.filteredTasks;
    if (this.groupBy === 'status') {
      return [
        { title: 'Commencer maintenant (À faire)', tasks: filtered.filter(t => t.status === 'TODO') },
        { title: 'En cours', tasks: filtered.filter(t => t.status === 'IN_PROGRESS') },
        { title: 'En attente de révision', tasks: filtered.filter(t => t.status === 'IN_REVIEW') },
        { title: 'Terminé', tasks: filtered.filter(t => t.status === 'COMPLETED') }
      ].filter(g => g.tasks.length > 0);
    } else {
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      const endOfWeek = new Date(today);
      endOfWeek.setDate(today.getDate() + (7 - today.getDay()));

      return [
        { 
          title: 'Échéance aujourd\'hui', 
          tasks: filtered.filter(t => t.dueDate && new Date(t.dueDate).toDateString() === today.toDateString()) 
        },
        { 
          title: 'Échéance cette semaine', 
          tasks: filtered.filter(t => t.dueDate && new Date(t.dueDate) > today && new Date(t.dueDate) <= endOfWeek) 
        },
        { 
          title: 'À venir / Plus tard', 
          tasks: filtered.filter(t => t.dueDate && new Date(t.dueDate) > endOfWeek) 
        },
        { 
          title: 'Sans date d\'échéance', 
          tasks: filtered.filter(t => !t.dueDate) 
        }
      ].filter(g => g.tasks.length > 0);
    }
  }

  editTask(task: any): void {
    this.selectedTask = { ...task };
    this.showTaskModal = true;
  }

  updateTaskDetails(): void {
    if (!this.selectedTask) return;
    this.taskService.updateTask(this.selectedTask.id, {
      title: this.selectedTask.title,
      description: this.selectedTask.description
    }).subscribe({
      next: () => {
        this.showTaskModal = false;
        this.toastService.success('Tâche mise à jour');
        this.loadTasks();
      },
      error: err => this.toastService.error(err.error?.message || 'Échec de la mise à jour de la tâche')
    });
  }

  getActionText(status: string): string {
    switch (status) {
      case 'TODO': return 'Commencer';
      case 'IN_PROGRESS': return 'Envoyer en révision';
      case 'IN_REVIEW': return 'En attente d\'approbation';
      case 'COMPLETED': return 'Terminé';
      default: return 'Déplacer';
    }
  }

  getNextStatus(status: string): string {
    switch (status) {
      case 'TODO': return 'IN_PROGRESS';
      case 'IN_PROGRESS': return 'IN_REVIEW';
      case 'IN_REVIEW': return ''; // No next status for member
      case 'COMPLETED': return ''; // Done
      default: return '';
    }
  }

  moveTask(task: any, status: string): void {
    this.taskService.updateTaskStatus(task.id, status).subscribe({
      next: () => {
        this.toastService.success('Statut de la tâche mis à jour');
        this.loadTasks();
      },
      error: err => this.toastService.error(err.error?.error || 'Impossible de mettre à jour la tâche')
    });
  }

  openProject(task: any): void {
    const projectId = task.projectId;
    if (projectId) {
      this.router.navigate(['/kanban', projectId]);
    }
  }

  private taskRank(task: any): number {
    const statusRank: any = { IN_PROGRESS: 0, TODO: 1, IN_REVIEW: 2, COMPLETED: 4 };
    const priorityRank: any = { critical: 0, high: 1, medium: 2, low: 3 };
    const dueRank = task.dueDate ? new Date(task.dueDate).getTime() / 10000000000000 : 1;
    return (statusRank[task.status] ?? 3) * 10 + (priorityRank[(task.priority || '').toLowerCase()] ?? 2) + dueRank;
  }
}
