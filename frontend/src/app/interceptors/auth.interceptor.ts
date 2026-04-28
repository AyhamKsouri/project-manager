import { Injectable } from '@angular/core';
import { HttpRequest, HttpHandler, HttpEvent, HttpInterceptor, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(private authService: AuthService) {}

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const token = localStorage.getItem('token');
    
    if (token) {
      console.log(`AuthInterceptor: Attaching token (${token.length} chars) to ${request.method} ${request.url}`);
      request = request.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      });
    } else {
      console.warn(`AuthInterceptor: No token found in localStorage for ${request.method} ${request.url}`);
    }

    return next.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        console.error(`AuthInterceptor: Request failed ${request.method} ${request.url}`, error.status, error.error);
        if (error.status === 401) {
          console.warn('AuthInterceptor: 401 Unauthorized detected. Token may be invalid or expired.');
          // Don't logout automatically yet to avoid infinite loops if it's a configuration issue
          // this.authService.logout();
        }
        return throwError(() => error);
      })
    );
  }
}
