import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { AnalyticsService } from './analytics.service';

describe('AnalyticsService', () => {
  let service: AnalyticsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), AnalyticsService],
    });
    service = TestBed.inject(AnalyticsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('loads overview from analytics API', () => {
    service.getOverview().subscribe((overview) => {
      expect(overview.totalEmployees).toBe(10000);
    });

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/analytics/overview`);
    expect(req.request.method).toBe('GET');
    req.flush({
      totalEmployees: 10000,
      employedEmployees: 9000,
      compensationByCurrency: [],
      currencyNote: 'grouped by currency',
    });
  });

  it('reuses cached overview within TTL', () => {
    let first: unknown;
    let second: unknown;
    service.getOverview().subscribe((overview) => (first = overview));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/analytics/overview`);
    req.flush({
      totalEmployees: 10000,
      employedEmployees: 9000,
      compensationByCurrency: [],
      currencyNote: 'grouped by currency',
    });

    service.getOverview().subscribe((overview) => (second = overview));
    httpMock.expectNone(`${environment.apiBaseUrl}/analytics/overview`);
    expect(second).toEqual(first);
  });

  it('forceRefresh bypasses cache', () => {
    service.getOverview().subscribe();
    httpMock.expectOne(`${environment.apiBaseUrl}/analytics/overview`).flush({
      totalEmployees: 1,
      employedEmployees: 1,
      compensationByCurrency: [],
      currencyNote: 'grouped by currency',
    });

    service.getOverview({ forceRefresh: true }).subscribe((overview) => {
      expect(overview.totalEmployees).toBe(2);
    });
    httpMock.expectOne(`${environment.apiBaseUrl}/analytics/overview`).flush({
      totalEmployees: 2,
      employedEmployees: 2,
      compensationByCurrency: [],
      currencyNote: 'grouped by currency',
    });
  });

  it('clearCache drops TTL entries so next call refetches', () => {
    service.getOverview().subscribe();
    httpMock.expectOne(`${environment.apiBaseUrl}/analytics/overview`).flush({
      totalEmployees: 1,
      employedEmployees: 1,
      compensationByCurrency: [],
      currencyNote: 'grouped by currency',
    });

    service.clearCache();
    service.getOverview().subscribe((overview) => {
      expect(overview.totalEmployees).toBe(2);
    });
    httpMock.expectOne(`${environment.apiBaseUrl}/analytics/overview`).flush({
      totalEmployees: 2,
      employedEmployees: 2,
      compensationByCurrency: [],
      currencyNote: 'grouped by currency',
    });
  });
});
