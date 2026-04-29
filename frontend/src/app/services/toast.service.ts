import { Injectable } from '@angular/core';
import { BehaviorSubject, filter, Observable } from 'rxjs';

export interface Toast {
  id: number;
  message: string;
  type: 'success' | 'error' | 'warning' | 'info';
  title?: string;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private toasts$ = new BehaviorSubject<Toast[]>([]);
  private counter = 0;

  getToasts(): Observable<Toast[]> {
    return this.toasts$.asObservable();
  }

  show(message: string, type: 'success' | 'error' | 'warning' | 'info' = 'info', title?: string): void {
    const id = this.counter++;
    const toast: Toast = { id, message, type, title };
    const currentToasts = this.toasts$.value;
    this.toasts$.next([...currentToasts, toast]);

    // Auto-remove after 5 seconds
    setTimeout(() => {
      this.remove(id);
    }, 5000);
  }

  success(message: string, title: string = 'Success'): void {
    this.show(message, 'success', title);
  }

  error(message: string, title: string = 'Error'): void {
    this.show(message, 'error', title);
  }

  warning(message: string, title: string = 'Warning'): void {
    this.show(message, 'warning', title);
  }

  info(message: string, title: string = 'Info'): void {
    this.show(message, 'info', title);
  }

  remove(id: number): void {
    const currentToasts = this.toasts$.value;
    this.toasts$.next(currentToasts.filter(t => t.id !== id));
  }
}
