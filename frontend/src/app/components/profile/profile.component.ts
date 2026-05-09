import { Component, OnInit } from '@angular/core';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-profile',
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.css']
})
export class ProfileComponent implements OnInit {
  user: any = null;
  newSkills: string = '';
  loading = false;

  constructor(
    private authService: AuthService,
    private toastService: ToastService
  ) {}

  ngOnInit(): void {
    this.loadProfile();
  }

  loadProfile(): void {
    this.loading = true;
    this.authService.getUserDetails().subscribe({
      next: (user) => {
        this.user = user;
        this.newSkills = user.skills || '';
        this.loading = false;
      },
      error: () => {
        this.toastService.error('Failed to load profile');
        this.loading = false;
      }
    });
  }

  saveSkills(): void {
    this.loading = true;
    this.authService.updateSkills(this.newSkills).subscribe({
      next: (user) => {
        this.user = user;
        this.toastService.success('Skills updated successfully');
        this.loading = false;
      },
      error: () => {
        this.toastService.error('Failed to update skills');
        this.loading = false;
      }
    });
  }
}
