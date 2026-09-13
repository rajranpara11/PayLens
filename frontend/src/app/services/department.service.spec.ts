import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { DepartmentService } from './department.service';

describe('DepartmentService', () => {
  let service: DepartmentService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), DepartmentService],
    });
    service = TestBed.inject(DepartmentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('caches department list across subscribers', () => {
    let firstCount = 0;
    let secondCount = 0;
    service.list().subscribe((rows) => (firstCount = rows.length));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/departments`);
    req.flush([{ id: '1', code: 'ENG', name: 'Engineering' }]);

    service.list().subscribe((rows) => (secondCount = rows.length));
    httpMock.expectNone(`${environment.apiBaseUrl}/departments`);
    expect(firstCount).toBe(1);
    expect(secondCount).toBe(1);
  });
});
