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
});
