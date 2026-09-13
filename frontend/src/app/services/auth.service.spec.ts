import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), AuthService],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('logs in and stores the current user', () => {
    service.login({ username: 'hr.manager', password: 'secret' }).subscribe((user) => {
      expect(user.username).toBe('hr.manager');
      expect(service.isAuthenticated()).toBe(true);
    });

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`);
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBe(true);
    req.flush({ username: 'hr.manager', roles: ['HR_MANAGER'] });
  });

  it('clears session on failed restore', () => {
    service.restoreSession().subscribe((ok) => {
      expect(ok).toBe(false);
      expect(service.isAuthenticated()).toBe(false);
    });

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/me`);
    req.flush({ message: 'Authentication required' }, { status: 401, statusText: 'Unauthorized' });
  });
});
