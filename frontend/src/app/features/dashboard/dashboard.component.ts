import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
  inject,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import {
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  ChartConfiguration,
  Legend,
  LinearScale,
  Title,
  Tooltip,
} from 'chart.js';
import { Subscription, forkJoin } from 'rxjs';
import {
  AnalyticsOverview,
  CountryHeadcountRow,
  CountryPayrollRow,
  CurrencyCompensationStats,
  DepartmentSalaryRow,
  DesignationSalaryRow,
  SalaryDistributionBucket,
} from '../../models/analytics.model';
import { NotificationService } from '../../core/services/notification.service';
import { AnalyticsService } from '../../services/analytics.service';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { countryLabel } from '../../shared/constants/lookup.constants';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { formatApiErrorMessage } from '../../shared/utils/api-error.util';
import { buildCompensationInsights } from './insights';

Chart.register(BarController, BarElement, CategoryScale, LinearScale, Tooltip, Legend, Title);

const CHART_TEAL = '#0f766e';
const CHART_TEAL_SOFT = 'rgba(15, 118, 110, 0.72)';
const CHART_SLATE = '#3a4f63';
const CHART_GRID = 'rgba(18, 38, 58, 0.08)';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    DecimalPipe,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatIconModule,
    MatProgressSpinnerModule,
    PageHeaderComponent,
    ErrorStateComponent,
    MoneyPipe,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit, OnDestroy {
  private readonly analyticsService = inject(AnalyticsService);
  private readonly notifications = inject(NotificationService);

  @ViewChild('headcountCanvas') headcountCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('payrollCanvas') payrollCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('departmentCanvas') departmentCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('designationCanvas') designationCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('distributionCanvas') distributionCanvas?: ElementRef<HTMLCanvasElement>;

  loading = false;
  errorMessage: string | null = null;

  overview: AnalyticsOverview | null = null;
  headcountByCountry: CountryHeadcountRow[] = [];
  payrollByCountry: CountryPayrollRow[] = [];
  salaryByDepartment: DepartmentSalaryRow[] = [];
  salaryByDesignation: DesignationSalaryRow[] = [];
  salaryDistribution: SalaryDistributionBucket[] = [];

  selectedCurrency = '';
  insights: string[] = [];

  private loadSub?: Subscription;
  private renderTimer: ReturnType<typeof setTimeout> | null = null;
  private headcountChart?: Chart;
  private payrollChart?: Chart;
  private departmentChart?: Chart;
  private designationChart?: Chart;
  private distributionChart?: Chart;

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.loadSub?.unsubscribe();
    this.clearRenderTimer();
    this.destroyCharts();
  }

  load(): void {
    this.loadSub?.unsubscribe();
    this.clearRenderTimer();
    const hadData = !!this.overview;
    this.loading = !hadData;
    this.errorMessage = null;
    if (!hadData) {
      this.destroyCharts();
    }

    const opts = { skipErrorSnack: true, forceRefresh: hadData };
    this.loadSub = forkJoin({
      overview: this.analyticsService.getOverview(opts),
      headcountByCountry: this.analyticsService.getEmployeeCountByCountry(opts),
      payrollByCountry: this.analyticsService.getPayrollByCountry(opts),
      salaryByDepartment: this.analyticsService.getSalaryByDepartment(opts),
      salaryByDesignation: this.analyticsService.getSalaryByDesignation(opts),
      salaryDistribution: this.analyticsService.getSalaryDistribution(opts),
    }).subscribe({
      next: (data) => {
        this.overview = data.overview;
        this.headcountByCountry = [...data.headcountByCountry].sort(
          (a, b) => b.employeeCount - a.employeeCount
        );
        this.payrollByCountry = data.payrollByCountry;
        this.salaryByDepartment = data.salaryByDepartment;
        this.salaryByDesignation = data.salaryByDesignation;
        this.salaryDistribution = data.salaryDistribution;
        this.selectedCurrency = this.selectedCurrency || this.defaultCurrency(data.overview);
        if (!this.currencies.includes(this.selectedCurrency)) {
          this.selectedCurrency = this.defaultCurrency(data.overview);
        }
        this.insights = buildCompensationInsights(data);
        this.loading = false;
        this.destroyCharts();
        this.queueChartRender();
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        const message = formatApiErrorMessage(
          error.error,
          'Unable to load compensation analytics.'
        );
        if (hadData) {
          this.notifications.error(message);
          return;
        }
        this.overview = null;
        this.insights = [];
        this.errorMessage = message;
      },
    });
  }

  onCurrencyChange(): void {
    this.destroyCharts();
    this.queueChartRender();
  }

  get selectedStats(): CurrencyCompensationStats | null {
    if (!this.overview || !this.selectedCurrency) {
      return null;
    }
    return (
      this.overview.compensationByCurrency.find((row) => row.currency === this.selectedCurrency) ??
      null
    );
  }

  get currencies(): string[] {
    return (this.overview?.compensationByCurrency ?? [])
      .slice()
      .sort((a, b) => b.employeeCount - a.employeeCount)
      .map((row) => row.currency);
  }

  get payrollByCurrency(): CurrencyCompensationStats[] {
    return [...(this.overview?.compensationByCurrency ?? [])].sort(
      (a, b) => b.employeeCount - a.employeeCount
    );
  }

  hasRows(kind: 'payroll' | 'department' | 'designation' | 'distribution' | 'headcount'): boolean {
    switch (kind) {
      case 'headcount':
        return this.headcountByCountry.length > 0;
      case 'payroll':
        return this.payrollByCountry.some((row) => row.currency === this.selectedCurrency);
      case 'department':
        return this.salaryByDepartment.some((row) => row.currency === this.selectedCurrency);
      case 'designation':
        return this.salaryByDesignation.some((row) => row.currency === this.selectedCurrency);
      case 'distribution':
        return this.salaryDistribution.some((row) => row.currency === this.selectedCurrency);
    }
  }

  private defaultCurrency(overview: AnalyticsOverview): string {
    const sorted = [...overview.compensationByCurrency].sort(
      (a, b) => b.employeeCount - a.employeeCount
    );
    return sorted[0]?.currency ?? '';
  }

  private queueChartRender(): void {
    this.clearRenderTimer();
    this.renderTimer = setTimeout(() => this.renderCharts(), 0);
  }

  private clearRenderTimer(): void {
    if (this.renderTimer !== null) {
      clearTimeout(this.renderTimer);
      this.renderTimer = null;
    }
  }

  private renderCharts(): void {
    this.renderHeadcountChart();
    this.renderPayrollChart();
    this.renderDepartmentChart();
    this.renderDesignationChart();
    this.renderDistributionChart();
  }

  private renderHeadcountChart(): void {
    const canvas = this.headcountCanvas?.nativeElement;
    if (!canvas || !this.hasRows('headcount')) {
      return;
    }
    this.headcountChart?.destroy();
    const labels = this.headcountByCountry.map((row) => countryLabel(row.country));
    const values = this.headcountByCountry.map((row) => row.employeeCount);
    this.headcountChart = new Chart(canvas, this.horizontalBarConfig(labels, values, 'Employees'));
  }

  private renderPayrollChart(): void {
    const canvas = this.payrollCanvas?.nativeElement;
    if (!canvas || !this.hasRows('payroll')) {
      return;
    }
    this.payrollChart?.destroy();
    const rows = this.payrollByCountry
      .filter((row) => row.currency === this.selectedCurrency)
      .sort((a, b) => b.totalPayroll - a.totalPayroll);
    const labels = rows.map((row) => countryLabel(row.country));
    const values = rows.map((row) => row.totalPayroll);
    this.payrollChart = new Chart(
      canvas,
      this.horizontalBarConfig(labels, values, `Payroll (${this.selectedCurrency})`, true)
    );
  }

  private renderDepartmentChart(): void {
    const canvas = this.departmentCanvas?.nativeElement;
    if (!canvas || !this.hasRows('department')) {
      return;
    }
    this.departmentChart?.destroy();
    const rows = this.salaryByDepartment
      .filter((row) => row.currency === this.selectedCurrency)
      .sort((a, b) => b.averageSalary - a.averageSalary);
    const labels = rows.map((row) => row.departmentName);
    const values = rows.map((row) => row.averageSalary);
    this.departmentChart = new Chart(
      canvas,
      this.horizontalBarConfig(labels, values, `Avg salary (${this.selectedCurrency})`, true)
    );
  }

  private renderDesignationChart(): void {
    const canvas = this.designationCanvas?.nativeElement;
    if (!canvas || !this.hasRows('designation')) {
      return;
    }
    this.designationChart?.destroy();
    const rows = this.salaryByDesignation
      .filter((row) => row.currency === this.selectedCurrency)
      .sort((a, b) => b.averageSalary - a.averageSalary)
      .slice(0, 8);
    const labels = rows.map((row) => row.designation);
    const values = rows.map((row) => row.averageSalary);
    this.designationChart = new Chart(
      canvas,
      this.horizontalBarConfig(labels, values, `Avg salary (${this.selectedCurrency})`, true)
    );
  }

  private renderDistributionChart(): void {
    const canvas = this.distributionCanvas?.nativeElement;
    if (!canvas || !this.hasRows('distribution')) {
      return;
    }
    this.distributionChart?.destroy();
    const rows = this.salaryDistribution
      .filter((row) => row.currency === this.selectedCurrency)
      .sort((a, b) => a.bucket - b.bucket);
    const labels = rows.map(
      (row) => `${this.shortMoney(row.bandMin)}–${this.shortMoney(row.bandMax)}`
    );
    const values = rows.map((row) => row.employeeCount);
    this.distributionChart = new Chart(canvas, {
      type: 'bar',
      data: {
        labels,
        datasets: [
          {
            label: `Employees (${this.selectedCurrency})`,
            data: values,
            backgroundColor: CHART_TEAL_SOFT,
            borderColor: CHART_TEAL,
            borderWidth: 1,
            borderRadius: 6,
          },
        ],
      },
      options: this.baseOptions(false),
    });
  }

  private horizontalBarConfig(
    labels: string[],
    values: number[],
    datasetLabel: string,
    money = false
  ): ChartConfiguration<'bar'> {
    return {
      type: 'bar',
      data: {
        labels,
        datasets: [
          {
            label: datasetLabel,
            data: values,
            backgroundColor: CHART_TEAL_SOFT,
            borderColor: CHART_TEAL,
            borderWidth: 1,
            borderRadius: 6,
          },
        ],
      },
      options: {
        ...this.baseOptions(money),
        indexAxis: 'y',
      },
    };
  }

  private baseOptions(money: boolean): ChartConfiguration<'bar'>['options'] {
    return {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          display: true,
          position: 'bottom',
          labels: {
            boxWidth: 12,
            color: CHART_SLATE,
            font: { family: "'Plus Jakarta Sans', sans-serif", size: 12 },
          },
        },
        tooltip: {
          callbacks: {
            label: (context) => {
              const value = Number(context.parsed.x ?? context.parsed.y ?? 0);
              const formatted = money
                ? new Intl.NumberFormat(undefined, {
                    minimumFractionDigits: 0,
                    maximumFractionDigits: 0,
                  }).format(value)
                : new Intl.NumberFormat(undefined).format(value);
              const suffix =
                money && this.selectedCurrency ? ` ${this.selectedCurrency}` : '';
              return `${context.dataset.label}: ${formatted}${suffix}`;
            },
          },
        },
      },
      scales: {
        x: {
          grid: { color: CHART_GRID },
          ticks: {
            color: CHART_SLATE,
            callback: (value) => {
              const numeric = Number(value);
              return money ? this.shortMoney(numeric) : numeric;
            },
          },
        },
        y: {
          grid: { display: false },
          ticks: { color: CHART_SLATE },
        },
      },
    };
  }

  private shortMoney(value: number): string {
    return new Intl.NumberFormat(undefined, {
      notation: 'compact',
      maximumFractionDigits: 1,
    }).format(value);
  }

  private destroyCharts(): void {
    this.headcountChart?.destroy();
    this.payrollChart?.destroy();
    this.departmentChart?.destroy();
    this.designationChart?.destroy();
    this.distributionChart?.destroy();
    this.headcountChart = undefined;
    this.payrollChart = undefined;
    this.departmentChart = undefined;
    this.designationChart = undefined;
    this.distributionChart = undefined;
  }
}
