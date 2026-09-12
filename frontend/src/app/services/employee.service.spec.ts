import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { EmployeeService } from './employee.service';

describe('EmployeeService', () => {
  let service: EmployeeService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), EmployeeService],
    });
    service = TestBed.inject(EmployeeService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lists employees via the API', () => {
    service.list({ page: 0, size: 25, search: 'john' }).subscribe((page) => {
      expect(page.totalElements).toBe(1);
    });

    const req = httpMock.expectOne(
      `${environment.apiBaseUrl}/employees?page=0&size=25&search=john`
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      content: [],
      page: 0,
      size: 25,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });
  });
});
