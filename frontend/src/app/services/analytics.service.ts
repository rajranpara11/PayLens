import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AnalyticsOverview,
  CountryHeadcountRow,
  CountryPayrollRow,
  DepartmentSalaryRow,
  DesignationSalaryRow,
  SalaryDistributionBucket,
} from '../models/analytics.model';

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/analytics`;

  getOverview(): Observable<AnalyticsOverview> {
    return this.http.get<AnalyticsOverview>(`${this.baseUrl}/overview`);
  }

  getPayrollByCountry(): Observable<CountryPayrollRow[]> {
    return this.http.get<CountryPayrollRow[]>(`${this.baseUrl}/payroll-by-country`);
  }

  getSalaryByDepartment(): Observable<DepartmentSalaryRow[]> {
    return this.http.get<DepartmentSalaryRow[]>(`${this.baseUrl}/salary-by-department`);
  }

  getSalaryByDesignation(): Observable<DesignationSalaryRow[]> {
    return this.http.get<DesignationSalaryRow[]>(`${this.baseUrl}/salary-by-designation`);
  }

  getSalaryDistribution(): Observable<SalaryDistributionBucket[]> {
    return this.http.get<SalaryDistributionBucket[]>(`${this.baseUrl}/salary-distribution`);
  }

  getEmployeeCountByCountry(): Observable<CountryHeadcountRow[]> {
    return this.http.get<CountryHeadcountRow[]>(`${this.baseUrl}/employee-count-by-country`);
  }
}
