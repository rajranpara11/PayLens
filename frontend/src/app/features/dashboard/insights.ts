import {
  AnalyticsOverview,
  CountryHeadcountRow,
  CountryPayrollRow,
  DepartmentSalaryRow,
  DesignationSalaryRow,
} from '../../models/analytics.model';
import { countryLabel } from '../../shared/constants/lookup.constants';

export interface CompensationInsightInput {
  overview: AnalyticsOverview;
  headcountByCountry: CountryHeadcountRow[];
  payrollByCountry: CountryPayrollRow[];
  salaryByDepartment: DepartmentSalaryRow[];
  salaryByDesignation: DesignationSalaryRow[];
}

/** Deterministic, human-readable compensation insights from analytics payloads. */
export function buildCompensationInsights(input: CompensationInsightInput): string[] {
  const insights: string[] = [];

  const largestCountry = maxBy(input.headcountByCountry, (row) => row.employeeCount);
  if (largestCountry && largestCountry.employeeCount > 0) {
    insights.push(
      `${countryLabel(largestCountry.country)} has the largest employee population (${formatCount(largestCountry.employeeCount)}).`
    );
  }

  if (input.overview.employedEmployees > 0 && input.overview.totalEmployees > 0) {
    const employedShare = Math.round(
      (input.overview.employedEmployees / input.overview.totalEmployees) * 100
    );
    insights.push(
      `${formatCount(input.overview.employedEmployees)} employed people (${employedShare}% of headcount) drive current compensation metrics.`
    );
  }

  const currencies = [...input.overview.compensationByCurrency].sort(
    (a, b) => b.employeeCount - a.employeeCount
  );

  for (const currencyStats of currencies.slice(0, 2)) {
    const currency = currencyStats.currency;
    const deptRows = input.salaryByDepartment.filter((row) => row.currency === currency);
    const topDept = maxBy(deptRows, (row) => row.averageSalary);
    if (topDept) {
      insights.push(
        `${topDept.departmentName} has the highest average salary in ${currency}.`
      );
    }
  }

  const primaryCurrency = currencies[0]?.currency;
  if (primaryCurrency) {
    const topDesignation = maxBy(
      input.salaryByDesignation.filter((row) => row.currency === primaryCurrency),
      (row) => row.averageSalary
    );
    if (topDesignation) {
      insights.push(
        `${topDesignation.designation} is the highest-paid designation in ${primaryCurrency}.`
      );
    }

    const topPayroll = maxBy(
      input.payrollByCountry.filter((row) => row.currency === primaryCurrency),
      (row) => row.totalPayroll
    );
    if (topPayroll) {
      insights.push(
        `${countryLabel(topPayroll.country)} carries the largest ${primaryCurrency} payroll.`
      );
    }
  }

  // Prefer an explicit high-signal currency insight when USD exists but is not primary.
  const usdDept = maxBy(
    input.salaryByDepartment.filter((row) => row.currency === 'USD'),
    (row) => row.averageSalary
  );
  if (usdDept && primaryCurrency !== 'USD') {
    insights.push(`${usdDept.departmentName} has the highest average salary in USD.`);
  }

  return uniquePreserveOrder(insights).slice(0, 6);
}

function maxBy<T>(items: T[], score: (item: T) => number): T | null {
  if (items.length === 0) {
    return null;
  }
  return items.reduce((best, item) => (score(item) > score(best) ? item : best));
}

function formatCount(value: number): string {
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 }).format(value);
}

function uniquePreserveOrder(values: string[]): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const value of values) {
    if (!seen.has(value)) {
      seen.add(value);
      result.push(value);
    }
  }
  return result;
}
