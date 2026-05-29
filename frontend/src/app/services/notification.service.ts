import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { Client } from '@stomp/stompjs';
import { ToastService } from './toast.service';
import { AuthService } from './auth.service';

export interface Notification {
  id: number;
  type: 'info' | 'success' | 'warning' | 'error' | 'system';
  title: string;
  message: string;
  readStatus: boolean;
  timestamp: string;
  actionUrl?: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private apiUrl = '/api/notifications';
  private stompClient: Client | null = null;
  private unreadCount$ = new BehaviorSubject<number>(0);

  constructor(
    private http: HttpClient,
    private toastService: ToastService,
    private authService: AuthService
  ) {
    this.authService.currentUser$.subscribe(user => {
      if (user) {
        this.connectWebSocket(user.email);
        this.fetchUnreadCount();
      } else {
        this.disconnectWebSocket();
      }
    });
  }

  getNotifications(page: number = 0, size: number = 20): Observable<any> {
    return this.http.get(`${this.apiUrl}?page=${page}&size=${size}`);
  }

  getUnreadCount(): Observable<number> {
    return this.unreadCount$.asObservable();
  }

  markAsRead(id: number): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/${id}/read`, {});
  }

  markAllAsRead(): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/read-all`, {});
  }

  deleteNotification(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  clearAll(): Observable<void> {
    return this.http.delete<void>(this.apiUrl);
  }

  private fetchUnreadCount(): void {
    this.http.get<number>(`${this.apiUrl}/unread-count`).subscribe(count => {
      this.unreadCount$.next(count);
    });
  }

  private connectWebSocket(email: string): void {
    if (this.stompClient?.active) {
      return;
    }

    const token = this.authService.getToken();
    if (!token) {
      return;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    
    this.stompClient = new Client({
      brokerURL: `${protocol}//${host}/ws?token=${encodeURIComponent(token)}`,
      onConnect: () => {
        this.stompClient?.subscribe(`/user/${email}/topic/notifications`, message => {
          const notification: Notification = JSON.parse(message.body);
          this.handleNewNotification(notification);
        });
      },
      debug: (str) => {
        console.log(str);
      }
    });

    this.stompClient.activate();
  }

  private disconnectWebSocket(): void {
    if (this.stompClient) {
      this.stompClient.deactivate();
      this.stompClient = null;
    }
  }

  private handleNewNotification(notification: Notification): void {
    // Increment unread count
    this.unreadCount$.next(this.unreadCount$.value + 1);
    
    // Show toast
    this.toastService.show(
      notification.message,
      notification.type === 'system' ? 'info' : notification.type,
      notification.title
    );
  }
}
