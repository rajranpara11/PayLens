import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, map, of, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthUser, LoginRequest } from '../models/auth.model';
import { SKIP_GLOBAL_ERROR_SNACK } from '../core/http/http-context.tokens';
import { HttpContext } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/auth`;

  private readonly currentUserSignal = signal<AuthUser | null>(null);
  readonly currentUser = this.currentUserSignal.asReadonly();

  login(request: LoginRequest): Observable<AuthUser> {
    return this.http
      .post<AuthUser>(`${this.baseUrl}/login`, request, {
        withCredentials: true,
        context: new HttpContext().set(SKIP_GLOBAL_ERROR_SNACK, true),
      })
      .pipe(tap((user) => this.currentUserSignal.set(user)));
  }

  logout(): Observable<void> {
    return this.http
      .post<void>(`${this.baseUrl}/logout`, null, {
        withCredentials: true,
        context: new HttpContext().set(SKIP_GLOBAL_ERROR_SNACK, true),
      })
      .pipe(
        tap(() => this.clearSession()),
        catchError(() => {
          this.clearSession();
          return of(void 0);
        })
      );
  }

  /** Restores session from cookie via /me. Returns true when authenticated. */
  restoreSession(): Observable<boolean> {
    return this.http
      .get<AuthUser>(`${this.baseUrl}/me`, {
        withCredentials: true,
        context: new HttpContext().set(SKIP_GLOBAL_ERROR_SNACK, true),
      })
      .pipe(
        tap((user) => this.currentUserSignal.set(user)),
        map(() => true),
        catchError(() => {
          this.clearSession();
          return of(false);
        })
      );
  }

  isAuthenticated(): boolean {
    return this.currentUserSignal() !== null;
  }

  clearSession(): void {
    this.currentUserSignal.set(null);
  }
}
