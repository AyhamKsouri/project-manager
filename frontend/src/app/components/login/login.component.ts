import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html'
})
export class LoginComponent {
  isLoginMode = true;
  credentials = { email: '', password: '' };
  registerData = { name: '', email: '', password: '', skills: '' };
  error = '';
  success = '';
  loading = false;

  constructor(private authService: AuthService, private router: Router) { }

  toggleMode(): void {
    this.isLoginMode = !this.isLoginMode;
    this.error = '';
    this.success = '';
  }

  onLogin(): void {
    this.loading = true;
    this.error = '';
    this.authService.login(this.credentials).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: (err) => {
        this.error = err.error?.error || 'Invalid email or password';
        this.loading = false;
      }
    });
  }

  onRegister(): void {
    this.loading = true;
    this.error = '';
    this.authService.register(this.registerData).subscribe({
      next: (res) => {
        this.success = res.message || 'Registration successful! Please sign in.';
        this.isLoginMode = true;
        this.credentials.email = this.registerData.email;
        this.loading = false;
      },
      error: (err) => {
        this.error = err.error?.error || 'Registration failed. Please try again.';
        this.loading = false;
      }
    });
  }
}
