import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
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

  list(params: EmployeeSearchParams = {}): Observable<PageResponse<Employee>> {
    let httpParams = new HttpParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        httpParams = httpParams.set(key, String(value));
      }
    });
    return this.http.get<PageResponse<Employee>>(this.baseUrl, { params: httpParams });
  }

  getById(id: string): Observable<Employee> {
    return this.http.get<Employee>(`${this.baseUrl}/${id}`);
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

  getSalaryHistory(employeeId: string): Observable<Salary[]> {
    return this.http.get<Salary[]>(`${this.baseUrl}/${employeeId}/salary-history`);
  }
}
