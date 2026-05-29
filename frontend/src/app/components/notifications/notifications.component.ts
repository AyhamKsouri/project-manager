import { Component, OnInit } from '@angular/core';
import { NotificationService, Notification } from '../../services/notification.service';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './notifications.component.html',
  styleUrls: ['./notifications.component.css']
})
export class NotificationsComponent implements OnInit {
  notifications: Notification[] = [];
  page = 0;
  totalPages = 0;
  loading = false;

  get groupedNotifications() {
    const groups: { [key: string]: Notification[] } = {};
    this.notifications.forEach(n => {
      const date = new Date(n.timestamp);
      let label = 'Plus ancien';
      const today = new Date();
      const yesterday = new Date();
      yesterday.setDate(today.getDate() - 1);

      if (date.toDateString() === today.toDateString()) {
        label = 'Aujourd\'hui';
      } else if (date.toDateString() === yesterday.toDateString()) {
        label = 'Hier';
      } else {
        label = date.toLocaleDateString('fr-FR', { month: 'long', day: 'numeric', year: 'numeric' });
      }

      if (!groups[label]) groups[label] = [];
      groups[label].push(n);
    });
    return Object.entries(groups);
  }

  constructor(private notificationService: NotificationService) {}

  ngOnInit(): void {
    this.loadNotifications();
  }

  loadNotifications(): void {
    this.loading = true;
    this.notificationService.getNotifications(this.page).subscribe(data => {
      this.notifications = data.content;
      this.totalPages = data.totalPages;
      this.loading = false;
    });
  }

  markAsRead(notification: Notification): void {
    if (!notification.readStatus) {
      this.notificationService.markAsRead(notification.id).subscribe(() => {
        notification.readStatus = true;
      });
    }
  }

  markAllAsRead(): void {
    this.notificationService.markAllAsRead().subscribe(() => {
      this.notifications.forEach(n => n.readStatus = true);
    });
  }

  deleteNotification(id: number): void {
    this.notificationService.deleteNotification(id).subscribe(() => {
      this.notifications = this.notifications.filter(n => n.id !== id);
    });
  }

  clearAll(): void {
    if (confirm('Êtes-vous sûr de vouloir effacer toutes les notifications ?')) {
      this.notificationService.clearAll().subscribe(() => {
        this.notifications = [];
      });
    }
  }

  formatDate(dateStr: string): string {
    const date = new Date(dateStr);
    return date.toLocaleString();
  }

  getIcon(type: string): string {
    switch (type) {
      case 'success': return 'bi-check-circle-fill text-success';
      case 'error': return 'bi-x-circle-fill text-danger';
      case 'warning': return 'bi-exclamation-triangle-fill text-warning';
      case 'info': return 'bi-info-circle-fill text-primary';
      default: return 'bi-bell-fill text-secondary';
    }
  }
}
