import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
  inject,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { Subject, Subscription, debounceTime, distinctUntilChanged, startWith } from 'rxjs';
import { ApiErrorResponse } from '../../../models/api.model';
import { Department, Employee } from '../../../models/employee.model';
import { DepartmentService } from '../../../services/department.service';
import { EmployeeService } from '../../../services/employee.service';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import {
  COUNTRY_OPTIONS,
  STATUS_OPTIONS,
  countryLabel,
  statusLabel,
} from '../../../shared/constants/lookup.constants';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { formatApiErrorMessage } from '../../../shared/utils/api-error.util';

@Component({
  selector: 'app-employee-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    RouterLink,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    PageHeaderComponent,
    ErrorStateComponent,
    MoneyPipe,
  ],
  templateUrl: './employee-list.component.html',
  styleUrl: './employee-list.component.scss',
})
export class EmployeeListComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly employeeService = inject(EmployeeService);
  private readonly departmentService = inject(DepartmentService);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);

  readonly displayedColumns = [
    'employeeCode',
    'name',
    'country',
    'department',
    'designation',
    'employmentStatus',
    'salary',
    'currency',
    'actions',
  ];
  readonly countries = COUNTRY_OPTIONS;
  readonly statuses = STATUS_OPTIONS;
  readonly countryLabel = countryLabel;
  readonly statusLabel = statusLabel;

  departments: Department[] = [];
  employees: Employee[] = [];
  totalElements = 0;
  pageIndex = 0;
  pageSize = 25;
  sortActive = 'name';
  sortDirection: 'asc' | 'desc' | '' = 'asc';

  loading = false;
  errorMessage: string | null = null;

  readonly filters = this.fb.nonNullable.group({
    search: [''],
    country: [''],
    department: [''],
    designation: [''],
    employmentStatus: ['ACTIVE'],
  });

  private readonly reload$ = new Subject<void>();
  private readonly subscriptions = new Subscription();
  private listRequest?: Subscription;
  private clearingFilters = false;

  ngOnInit(): void {
    this.subscriptions.add(
      this.departmentService.list().subscribe({
        next: (departments) => {
          this.departments = departments;
          this.cdr.markForCheck();
        },
      })
    );

    this.subscriptions.add(
      this.reload$.pipe(startWith(void 0)).subscribe(() => this.fetch())
    );

    this.subscriptions.add(
      this.filters.controls.search.valueChanges
        .pipe(debounceTime(350), distinctUntilChanged())
        .subscribe(() => {
          if (this.clearingFilters) {
            return;
          }
          this.pageIndex = 0;
          this.reload$.next();
        })
    );

    this.subscriptions.add(
      this.filters.controls.designation.valueChanges
        .pipe(debounceTime(350), distinctUntilChanged())
        .subscribe(() => {
          if (this.clearingFilters) {
            return;
          }
          this.pageIndex = 0;
          this.reload$.next();
        })
    );
  }

  ngOnDestroy(): void {
    this.listRequest?.unsubscribe();
    this.subscriptions.unsubscribe();
  }

  onFilterChange(): void {
    this.pageIndex = 0;
    this.reload$.next();
  }

  clearFilters(): void {
    this.clearingFilters = true;
    this.filters.reset({
      search: '',
      country: '',
      department: '',
      designation: '',
      employmentStatus: 'ACTIVE',
    });
    this.pageIndex = 0;
    this.sortActive = 'name';
    this.sortDirection = 'asc';
    this.reload$.next();
    queueMicrotask(() => {
      this.clearingFilters = false;
    });
  }

  onPage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.reload$.next();
  }

  onSort(sort: Sort): void {
    this.sortActive = sort.active || 'name';
    this.sortDirection = (sort.direction as 'asc' | 'desc' | '') || 'asc';
    this.pageIndex = 0;
    this.reload$.next();
  }

  openEmployee(employee: Employee): void {
    void this.router.navigate(['/employees', employee.id]);
  }

  retry(): void {
    this.reload$.next();
  }

  fullName(employee: Employee): string {
    return `${employee.firstName} ${employee.lastName}`;
  }

  initials(employee: Employee): string {
    return `${employee.firstName.charAt(0)}${employee.lastName.charAt(0)}`.toUpperCase();
  }

  private fetch(): void {
    this.listRequest?.unsubscribe();
    this.loading = true;
    this.errorMessage = null;
    this.cdr.markForCheck();
    const value = this.filters.getRawValue();
    const sortField = this.mapSortField(this.sortActive);

    this.listRequest = this.employeeService
      .list(
        {
          search: value.search.trim() || undefined,
          country: value.country || undefined,
          department: value.department || undefined,
          designation: value.designation.trim() || undefined,
          employmentStatus: value.employmentStatus || undefined,
          page: this.pageIndex,
          size: this.pageSize,
          sort: `${sortField},${this.sortDirection || 'asc'}`,
        },
        { skipErrorSnack: true }
      )
      .subscribe({
        next: (page) => {
          this.employees = page.content;
          this.totalElements = page.totalElements;
          this.loading = false;
          this.cdr.markForCheck();
        },
        error: (error: HttpErrorResponse) => {
          this.loading = false;
          this.employees = [];
          this.totalElements = 0;
          const apiError = error.error as ApiErrorResponse | undefined;
          this.errorMessage =
            formatApiErrorMessage(error.error, 'Unable to load employees.') ||
            apiError?.message ||
            'Unable to load employees.';
          this.cdr.markForCheck();
        },
      });
  }

  private mapSortField(active: string): string {
    switch (active) {
      case 'name':
        return 'lastName';
      case 'department':
        return 'department.name';
      case 'salary':
      case 'currency':
      case 'actions':
        return 'lastName';
      default:
        return active;
    }
  }
}
