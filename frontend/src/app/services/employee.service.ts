import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { SKIP_GLOBAL_ERROR_SNACK } from '../core/http/http-context.tokens';
import { PageResponse } from '../models/api.model';
import {
  CreateEmployeeRequest,
  Employee,
  EmployeeSearchParams,
  UpdateEmployeeRequest,
} from '../models/employee.model';
import { Salary, SalaryRequest } from '../models/salary.model';

@Injectable({ providedIn: 'root' })
export class EmployeeService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/employees`;

  list(
    params: EmployeeSearchParams = {},
    options?: { skipErrorSnack?: boolean }
  ): Observable<PageResponse<Employee>> {
    let httpParams = new HttpParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        httpParams = httpParams.set(key, String(value));
      }
    });
    return this.http.get<PageResponse<Employee>>(this.baseUrl, {
      params: httpParams,
      context: this.context(options?.skipErrorSnack),
    });
  }

  getById(id: string, options?: { skipErrorSnack?: boolean }): Observable<Employee> {
    return this.http.get<Employee>(`${this.baseUrl}/${id}`, {
      context: this.context(options?.skipErrorSnack),
    });
  }

  create(payload: CreateEmployeeRequest): Observable<Employee> {
    return this.http.post<Employee>(this.baseUrl, payload);
  }

  update(id: string, payload: UpdateEmployeeRequest): Observable<Employee> {
    return this.http.put<Employee>(`${this.baseUrl}/${id}`, payload);
  }

  deactivate(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  getCurrentSalary(employeeId: string): Observable<Salary> {
    return this.http.get<Salary>(`${this.baseUrl}/${employeeId}/salary`);
  }

  updateSalary(employeeId: string, payload: SalaryRequest): Observable<Salary> {
    return this.http.put<Salary>(`${this.baseUrl}/${employeeId}/salary`, payload);
  }

  getSalaryHistory(
    employeeId: string,
    options?: { skipErrorSnack?: boolean }
  ): Observable<Salary[]> {
    return this.http.get<Salary[]>(`${this.baseUrl}/${employeeId}/salary-history`, {
      context: this.context(options?.skipErrorSnack),
    });
  }

  private context(skipErrorSnack?: boolean): HttpContext {
    return new HttpContext().set(SKIP_GLOBAL_ERROR_SNACK, !!skipErrorSnack);
  }
}
