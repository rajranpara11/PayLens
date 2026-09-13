import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay, tap } from 'rxjs';
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

type CacheOptions = { skipErrorSnack?: boolean; forceRefresh?: boolean };

/**
 * Short TTL cache for read-mostly analytics. Dashboard navigation reuses
 * responses instead of re-running six aggregate queries every visit.
 */
@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/analytics`;
  private readonly ttlMs = 60_000;
  private readonly cache = new Map<string, { expiresAt: number; value$: Observable<unknown> }>();

  getOverview(options?: CacheOptions): Observable<AnalyticsOverview> {
    return this.cached('overview', () =>
      this.http.get<AnalyticsOverview>(`${this.baseUrl}/overview`, {
        context: this.context(options?.skipErrorSnack),
      })
    , options);
  }

  getPayrollByCountry(options?: CacheOptions): Observable<CountryPayrollRow[]> {
    return this.cached('payroll-by-country', () =>
      this.http.get<CountryPayrollRow[]>(`${this.baseUrl}/payroll-by-country`, {
        context: this.context(options?.skipErrorSnack),
      })
    , options);
  }

  getSalaryByDepartment(options?: CacheOptions): Observable<DepartmentSalaryRow[]> {
    return this.cached('salary-by-department', () =>
      this.http.get<DepartmentSalaryRow[]>(`${this.baseUrl}/salary-by-department`, {
        context: this.context(options?.skipErrorSnack),
      })
    , options);
  }

  getSalaryByDesignation(options?: CacheOptions): Observable<DesignationSalaryRow[]> {
    return this.cached('salary-by-designation', () =>
      this.http.get<DesignationSalaryRow[]>(`${this.baseUrl}/salary-by-designation`, {
        context: this.context(options?.skipErrorSnack),
      })
    , options);
  }

  getSalaryDistribution(options?: CacheOptions): Observable<SalaryDistributionBucket[]> {
    return this.cached('salary-distribution', () =>
      this.http.get<SalaryDistributionBucket[]>(`${this.baseUrl}/salary-distribution`, {
        context: this.context(options?.skipErrorSnack),
      })
    , options);
  }

  getEmployeeCountByCountry(options?: CacheOptions): Observable<CountryHeadcountRow[]> {
    return this.cached('employee-count-by-country', () =>
      this.http.get<CountryHeadcountRow[]>(`${this.baseUrl}/employee-count-by-country`, {
        context: this.context(options?.skipErrorSnack),
      })
    , options);
  }

  clearCache(): void {
    this.cache.clear();
  }

  private cached<T>(
    key: string,
    loader: () => Observable<T>,
    options?: CacheOptions
  ): Observable<T> {
    const now = Date.now();
    if (options?.forceRefresh) {
      this.cache.delete(key);
    }
    const hit = this.cache.get(key);
    if (hit && hit.expiresAt > now) {
      return hit.value$ as Observable<T>;
    }
    const value$ = loader().pipe(
      tap({
        error: () => this.cache.delete(key),
      }),
      shareReplay({ bufferSize: 1, refCount: false })
    );
    this.cache.set(key, { expiresAt: now + this.ttlMs, value$ });
    return value$;
  }

  private context(skipErrorSnack?: boolean): HttpContext {
    return new HttpContext().set(SKIP_GLOBAL_ERROR_SNACK, !!skipErrorSnack);
  }
}
