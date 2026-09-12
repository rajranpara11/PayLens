export interface Salary {
  id: string;
  annualSalary: number;
  currency: string;
  effectiveFrom: string;
}

export interface SalaryRequest {
  annualSalary: number;
  currency: string;
  effectiveFrom: string;
}
