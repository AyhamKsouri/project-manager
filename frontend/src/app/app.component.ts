import { Component, OnInit } from '@angular/core';
import { AuthService } from './services/auth.service';
import { Router } from '@angular/router';
import { ToastService, Toast } from './services/toast.service';
import { NotificationService } from './services/notification.service';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-root',
  template: `
    <nav class="navbar navbar-expand-lg navbar-light bg-white border-bottom py-3" *ngIf="authService.isLoggedIn()">
      <div class="container">
        <a class="navbar-brand fw-bold d-flex align-items-center" routerLink="/dashboard">
          <div class="logo-icon-sm me-2">
            <i class="bi bi-grid-1x2-fill"></i>
          </div>
          <span class="brand-name-sm">ProManager</span>
        </a>
        <button class="navbar-toggler border-0 shadow-none" type="button" data-bs-toggle="collapse" data-bs-target="#navbarNav">
          <i class="bi bi-list fs-2"></i>
        </button>
        <div class="collapse navbar-collapse" id="navbarNav">
          <ul class="navbar-nav me-auto mb-2 mb-lg-0 ms-lg-4">
            <li class="nav-item">
              <a class="nav-link px-3 fw-medium" routerLink="/dashboard" routerLinkActive="active">Tableau de bord</a>
            </li>
            <li class="nav-item">
              <a class="nav-link px-3 fw-medium" routerLink="/my-tasks" routerLinkActive="active">Mes tâches</a>
            </li>
            <li class="nav-item">
              <a class="nav-link px-3 fw-medium" routerLink="/profile" routerLinkActive="active">Profil</a>
            </li>
            <li class="nav-item" *ngIf="authService.isAdmin()">
              <a class="nav-link px-3 fw-medium" routerLink="/admin" routerLinkActive="active">Administration</a>
            </li>
          </ul>
          <div class="d-flex align-items-center gap-3">
            <!-- Notification Bell -->
            <a routerLink="/notifications" class="btn btn-notification-bell position-relative rounded-circle d-flex align-items-center justify-content-center" 
               title="Notifications">
              <i class="bi bi-bell-fill fs-5 text-dark"></i>
              <span *ngIf="(unreadCount$ | async) !== null && (unreadCount$ | async)! > 0" 
                    class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger border border-white notification-count-badge">
                {{ unreadCount$ | async }}
              </span>
            </a>

            <div class="user-profile-pill d-none d-md-flex align-items-center gap-2">
              <div class="avatar-circle-sm bg-primary text-white">{{ authService.getUser()?.email?.charAt(0)?.toUpperCase() }}</div>
              <span class="fw-semibold text-dark small">{{ authService.getUser()?.email }}</span>
            </div>
            <button class="btn btn-outline-danger rounded-pill px-4 btn-sm fw-bold border-2" (click)="logout()">
              <i class="bi bi-box-arrow-right me-1"></i> Déconnexion
            </button>
          </div>
        </div>
      </div>
    </nav>
    <div class="container-fluid px-0 main-content" [class.kanban-main]="router.url.startsWith('/kanban')">
      <div class="container">
        <router-outlet></router-outlet>
      </div>
    </div>

    <app-chat-widget *ngIf="authService.isLoggedIn()"></app-chat-widget>
    <app-confirm-modal></app-confirm-modal>

    <!-- Toast Notifications Container -->
    <div class="toast-container toast-container-app position-fixed bottom-0 end-0 p-3">
      <div *ngFor="let toast of toasts$ | async" 
           class="toast show mb-2 border-0 shadow-lg animate-slide-in" 
           [ngClass]="getToastClass(toast.type)"
           role="alert" aria-live="assertive" aria-atomic="true">
        <div class="toast-header border-0 bg-transparent text-white py-2">
          <i class="bi me-2" [ngClass]="getToastIcon(toast.type)"></i>
          <strong class="me-auto">{{ toast.title || 'Notification' }}</strong>
          <button type="button" class="btn-close btn-close-white shadow-none" (click)="toastService.remove(toast.id)"></button>
        </div>
        <div class="toast-body text-white pt-0 pb-3">
          {{ toast.message }}
        </div>
      </div>
    </div>
  `,
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  toasts$: Observable<Toast[]>;
  unreadCount$: Observable<number>;

  constructor(
    public authService: AuthService, 
    public router: Router, 
    public toastService: ToastService,
    private notificationService: NotificationService
  ) {
    this.toasts$ = this.toastService.getToasts();
    this.unreadCount$ = this.notificationService.getUnreadCount();
  }

  ngOnInit(): void {}

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  getToastClass(type: string): string {
    return `toast-${type}`;
  }

  getToastIcon(type: string): string {
    switch (type) {
      case 'success': return 'bi-check-circle-fill';
      case 'error': return 'bi-exclamation-triangle-fill';
      case 'warning': return 'bi-exclamation-circle-fill';
      case 'info': return 'bi-info-circle-fill';
      default: return 'bi-info-circle-fill';
    }
  }
}
