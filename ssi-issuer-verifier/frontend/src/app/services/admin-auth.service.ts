import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, catchError, of, tap } from 'rxjs';

export interface AdminUser {
  username: string;
  realm: string;
}

@Injectable({ providedIn: 'root' })
export class AdminAuthService {
  private readonly currentUserSubject = new BehaviorSubject<AdminUser | null>(null);
  private sessionChecked = false;

  constructor(private readonly http: HttpClient) {}

  currentUser(): Observable<AdminUser | null> {
    return this.currentUserSubject.asObservable();
  }

  get snapshot(): AdminUser | null {
    return this.currentUserSubject.value;
  }

  login(username: string, password: string): Observable<AdminUser> {
    return this.http
      .post<AdminUser>(
        '/api/admin/auth/login',
        { username, password },
        { withCredentials: true }
      )
      .pipe(
        tap(user => {
          this.currentUserSubject.next(user);
          this.sessionChecked = true;
        })
      );
  }

  logout(): Observable<void> {
    return this.http
      .post<void>('/api/admin/auth/logout', {}, { withCredentials: true })
      .pipe(
        tap(() => {
          this.currentUserSubject.next(null);
          this.sessionChecked = true;
        })
      );
  }

  ensureSession(): Observable<AdminUser | null> {
    if (this.sessionChecked) {
      return of(this.currentUserSubject.value);
    }
    return this.http
      .get<AdminUser>('/api/admin/auth/me', { withCredentials: true })
      .pipe(
        tap(user => {
          this.sessionChecked = true;
          this.currentUserSubject.next(user);
        }),
        catchError(() => {
          this.sessionChecked = true;
          this.currentUserSubject.next(null);
          return of(null);
        })
      );
  }
}
