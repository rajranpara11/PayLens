import {
  AnalyticsOverview,
  CountryHeadcountRow,
  CountryPayrollRow,
  DepartmentSalaryRow,
  DesignationSalaryRow,
} from '../../models/analytics.model';
import { buildCompensationInsights } from './insights';

describe('buildCompensationInsights', () => {
  const overview: AnalyticsOverview = {
    totalEmployees: 1000,
    employedEmployees: 900,
    compensationByCurrency: [
      {
        currency: 'USD',
        employeeCount: 400,
        averageSalary: 120000,
        medianSalary: 110000,
        minSalary: 50000,
        maxSalary: 250000,
        totalPayroll: 48000000,
      },
      {
        currency: 'INR',
        employeeCount: 500,
        averageSalary: 1800000,
        medianSalary: 1600000,
        minSalary: 600000,
        maxSalary: 5000000,
        totalPayroll: 900000000,
      },
    ],
    currencyNote: 'grouped by currency',
  };

  const headcountByCountry: CountryHeadcountRow[] = [
    { country: 'IN', employeeCount: 520 },
    { country: 'US', employeeCount: 300 },
    { country: 'GB', employeeCount: 180 },
  ];

  const payrollByCountry: CountryPayrollRow[] = [
    {
      country: 'US',
      currency: 'USD',
      employeeCount: 300,
      totalPayroll: 36000000,
      averageSalary: 120000,
    },
    {
      country: 'IN',
      currency: 'INR',
      employeeCount: 500,
      totalPayroll: 900000000,
      averageSalary: 1800000,
    },
  ];

  const salaryByDepartment: DepartmentSalaryRow[] = [
    {
      departmentCode: 'ENG',
      departmentName: 'Engineering',
      currency: 'USD',
      employeeCount: 120,
      averageSalary: 145000,
      medianSalary: 140000,
      minSalary: 90000,
      maxSalary: 240000,
      totalPayroll: 17400000,
    },
    {
      departmentCode: 'HR',
      departmentName: 'Human Resources',
      currency: 'USD',
      employeeCount: 40,
      averageSalary: 95000,
      medianSalary: 90000,
      minSalary: 60000,
      maxSalary: 140000,
      totalPayroll: 3800000,
    },
    {
      departmentCode: 'ENG',
      departmentName: 'Engineering',
      currency: 'INR',
      employeeCount: 200,
      averageSalary: 2200000,
      medianSalary: 2000000,
      minSalary: 900000,
      maxSalary: 4500000,
      totalPayroll: 440000000,
    },
  ];

  const salaryByDesignation: DesignationSalaryRow[] = [
    {
      designation: 'Staff Engineer',
      currency: 'USD',
      employeeCount: 20,
      averageSalary: 210000,
      medianSalary: 205000,
      minSalary: 180000,
      maxSalary: 250000,
      totalPayroll: 4200000,
    },
    {
      designation: 'HR Specialist',
      currency: 'USD',
      employeeCount: 15,
      averageSalary: 85000,
      medianSalary: 82000,
      minSalary: 70000,
      maxSalary: 110000,
      totalPayroll: 1275000,
    },
  ];

  it('builds deterministic insights from analytics data', () => {
    const insights = buildCompensationInsights({
      overview,
      headcountByCountry,
      payrollByCountry,
      salaryByDepartment,
      salaryByDesignation,
    });

    expect(insights[0]).toContain('India has the largest employee population');
    expect(insights.some((line) => line.includes('employed people'))).toBe(true);
    expect(insights).toContain('Engineering has the highest average salary in USD.');
    expect(insights).toContain('Engineering has the highest average salary in INR.');
    expect(insights).toContain('India carries the largest INR payroll.');
  });

  it('returns an empty list when there is no analytics signal', () => {
    expect(
      buildCompensationInsights({
        overview: {
          totalEmployees: 0,
          employedEmployees: 0,
          compensationByCurrency: [],
          currencyNote: '',
        },
        headcountByCountry: [],
        payrollByCountry: [],
        salaryByDepartment: [],
        salaryByDesignation: [],
      })
    ).toEqual([]);
  });
});
