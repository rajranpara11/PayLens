import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { SKIP_GLOBAL_ERROR_SNACK } from '../core/http/http-context.tokens';
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

  getOverview(options?: { skipErrorSnack?: boolean }): Observable<AnalyticsOverview> {
    return this.http.get<AnalyticsOverview>(`${this.baseUrl}/overview`, {
      context: this.context(options?.skipErrorSnack),
    });
  }

  getPayrollByCountry(options?: { skipErrorSnack?: boolean }): Observable<CountryPayrollRow[]> {
    return this.http.get<CountryPayrollRow[]>(`${this.baseUrl}/payroll-by-country`, {
      context: this.context(options?.skipErrorSnack),
    });
  }

  getSalaryByDepartment(options?: {
    skipErrorSnack?: boolean;
  }): Observable<DepartmentSalaryRow[]> {
    return this.http.get<DepartmentSalaryRow[]>(`${this.baseUrl}/salary-by-department`, {
      context: this.context(options?.skipErrorSnack),
    });
  }

  getSalaryByDesignation(options?: {
    skipErrorSnack?: boolean;
  }): Observable<DesignationSalaryRow[]> {
    return this.http.get<DesignationSalaryRow[]>(`${this.baseUrl}/salary-by-designation`, {
      context: this.context(options?.skipErrorSnack),
    });
  }

  getSalaryDistribution(options?: {
    skipErrorSnack?: boolean;
  }): Observable<SalaryDistributionBucket[]> {
    return this.http.get<SalaryDistributionBucket[]>(`${this.baseUrl}/salary-distribution`, {
      context: this.context(options?.skipErrorSnack),
    });
  }

  getEmployeeCountByCountry(options?: {
    skipErrorSnack?: boolean;
  }): Observable<CountryHeadcountRow[]> {
    return this.http.get<CountryHeadcountRow[]>(`${this.baseUrl}/employee-count-by-country`, {
      context: this.context(options?.skipErrorSnack),
    });
  }

  private context(skipErrorSnack?: boolean): HttpContext {
    return new HttpContext().set(SKIP_GLOBAL_ERROR_SNACK, !!skipErrorSnack);
  }
}
