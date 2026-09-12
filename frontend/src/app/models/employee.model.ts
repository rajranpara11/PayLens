import { Salary, SalaryRequest } from './salary.model';

export type EmploymentStatus = 'ACTIVE' | 'ON_LEAVE' | 'TERMINATED';

export interface Department {
  id: string;
  code: string;
  name: string;
}

export interface Employee {
  id: string;
  employeeCode: string;
  firstName: string;
  lastName: string;
  email: string;
  country: string;
  department: Department;
  designation: string;
  employmentStatus: EmploymentStatus | string;
  joiningDate: string;
  currentSalary: Salary | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateEmployeeRequest {
  employeeCode: string;
  firstName: string;
  lastName: string;
  email: string;
  country: string;
  department: string;
  designation: string;
  employmentStatus?: string;
  joiningDate: string;
  salary: SalaryRequest;
}

export interface UpdateEmployeeRequest {
  employeeCode: string;
  firstName: string;
  lastName: string;
  email: string;
  country: string;
  department: string;
  designation: string;
  employmentStatus: string;
  joiningDate: string;
  salary?: SalaryRequest | null;
}

export interface EmployeeSearchParams {
  search?: string;
  country?: string;
  department?: string;
  designation?: string;
  employmentStatus?: string;
  currency?: string;
  page?: number;
  size?: number;
  sort?: string;
}
