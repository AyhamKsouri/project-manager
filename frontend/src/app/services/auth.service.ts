import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, tap } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private apiUrl = '/api/auth';
  private currentUserSubject = new BehaviorSubject<any>(this.getUser());
  currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient, private router: Router) { }

  login(credentials: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/signin`, credentials).pipe(
      tap((res: any) => {
        if (res.token) {
          localStorage.setItem('token', res.token);
          localStorage.setItem('user', JSON.stringify(res));
          this.currentUserSubject.next(res);
        }
      })
    );
  }

  register(user: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/signup`, user).pipe(
      tap((res: any) => {
        if (res.token) {
          localStorage.setItem('token', res.token);
          localStorage.setItem('user', JSON.stringify(res));
          this.currentUserSubject.next(res);
        }
      })
    );
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    this.currentUserSubject.next(null);
    this.router.navigate(['/login']);
  }

  isLoggedIn(): boolean {
    return !!localStorage.getItem('token');
  }

  isAdmin(): boolean {
    return this.getUser()?.role === 'ROLE_ADMIN';
  }

  searchUsers(query: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/users/search`, { params: { query } });
  }

  updateSkills(skills: string): Observable<any> {
    return this.http.put(`${this.apiUrl}/profile/skills`, { skills }).pipe(
      tap((updatedUser: any) => {
        const currentUser = this.getUser();
        if (currentUser) {
          currentUser.skills = updatedUser.skills;
          localStorage.setItem('user', JSON.stringify(currentUser));
          this.currentUserSubject.next(currentUser);
        }
      })
    );
  }

  getUserDetails(): Observable<any> {
    return this.http.get(`${this.apiUrl}/profile`);
  }

  getCurrentUser(): any {
    return this.getUser();
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  getUser(): any {
    const userJson = localStorage.getItem('user');
    if (!userJson) return null;
    try {
      return JSON.parse(userJson);
    } catch (e) {
      return null;
    }
  }
}
