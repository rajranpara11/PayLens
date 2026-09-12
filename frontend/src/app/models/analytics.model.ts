export interface CurrencyCompensationStats {
  currency: string;
  employeeCount: number;
  averageSalary: number;
  medianSalary: number;
  minSalary: number;
  maxSalary: number;
  totalPayroll: number;
}

export interface AnalyticsOverview {
  totalEmployees: number;
  employedEmployees: number;
  compensationByCurrency: CurrencyCompensationStats[];
  currencyNote: string;
}

export interface CountryPayrollRow {
  country: string;
  currency: string;
  employeeCount: number;
  totalPayroll: number;
  averageSalary: number;
}

export interface DepartmentSalaryRow {
  departmentCode: string;
  departmentName: string;
  currency: string;
  employeeCount: number;
  averageSalary: number;
  medianSalary: number;
  minSalary: number;
  maxSalary: number;
  totalPayroll: number;
}

export interface DesignationSalaryRow {
  designation: string;
  currency: string;
  employeeCount: number;
  averageSalary: number;
  medianSalary: number;
  minSalary: number;
  maxSalary: number;
  totalPayroll: number;
}

export interface SalaryDistributionBucket {
  currency: string;
  bucket: number;
  employeeCount: number;
  bandMin: number;
  bandMax: number;
}

export interface CountryHeadcountRow {
  country: string;
  employeeCount: number;
}
