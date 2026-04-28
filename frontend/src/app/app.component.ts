import { Component } from '@angular/core';
import { AuthService } from './services/auth.service';
import { Router } from '@angular/router';

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
              <a class="nav-link px-3 fw-medium" routerLink="/dashboard" routerLinkActive="active">Dashboard</a>
            </li>
          </ul>
          <div class="d-flex align-items-center gap-3">
            <div class="user-profile-pill d-none d-md-flex align-items-center gap-2">
              <div class="avatar-circle-sm bg-primary text-white">{{ authService.getUser()?.email?.charAt(0)?.toUpperCase() }}</div>
              <span class="fw-semibold text-dark small">{{ authService.getUser()?.email }}</span>
            </div>
            <button class="btn btn-outline-danger rounded-pill px-4 btn-sm fw-bold border-2" (click)="logout()">
              <i class="bi bi-box-arrow-right me-1"></i> Logout
            </button>
          </div>
        </div>
      </div>
    </nav>
    <div class="container-fluid px-0 main-content">
      <div class="container">
        <router-outlet></router-outlet>
      </div>
    </div>

    <style>
      @import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap');

      :host {
        --primary-color: #4f46e5;
        --primary-hover: #4338ca;
        --bg-light: #f8fafc;
        font-family: 'Plus Jakarta Sans', sans-serif;
      }

      body {
        background-color: var(--bg-light);
      }

      .logo-icon-sm {
        width: 32px;
        height: 32px;
        background: var(--primary-color);
        color: white;
        border-radius: 8px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 1rem;
      }

      .brand-name-sm {
        font-weight: 700;
        font-size: 1.1rem;
        letter-spacing: -0.5px;
        color: #1e293b;
      }

      .nav-link.active {
        color: var(--primary-color) !important;
        font-weight: 600;
      }

      .user-profile-pill {
        background: #f1f5f9;
        padding: 4px 12px 4px 4px;
        border-radius: 50px;
      }

      .avatar-circle-sm {
        width: 28px;
        height: 28px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 0.75rem;
        font-weight: 700;
      }

      .main-content {
        background-color: #f8fafc;
        min-height: calc(100vh - 76px);
        padding-top: 2rem;
        padding-bottom: 4rem;
      }
    </style>
  `
})
export class AppComponent {
  constructor(public authService: AuthService, private router: Router) {}

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
