import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
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
        this.error = err.error?.error || 'Adresse e-mail ou mot de passe invalide.';
        this.loading = false;
      }
    });
  }

  onRegister(): void {
    this.loading = true;
    this.error = '';
    this.authService.register(this.registerData).subscribe({
      next: (res) => {
        this.success = res.message || 'Inscription réussie. Vous pouvez maintenant vous connecter.';
        this.isLoginMode = true;
        this.credentials.email = this.registerData.email;
        this.loading = false;
      },
      error: (err) => {
        this.error = err.error?.error || 'Inscription impossible. Veuillez réessayer.';
        this.loading = false;
      }
    });
  }

  onForgotPassword(): void {
    this.success = 'Les instructions de réinitialisation ont été envoyées à votre adresse e-mail (mode démo).';
    setTimeout(() => this.success = '', 5000);
  }
}
