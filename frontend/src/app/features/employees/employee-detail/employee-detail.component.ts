import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiErrorResponse } from '../../../models/api.model';
import { Department, Employee } from '../../../models/employee.model';
import { Salary } from '../../../models/salary.model';
import { DepartmentService } from '../../../services/department.service';
import { EmployeeService } from '../../../services/employee.service';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import {
  countryLabel,
  statusLabel,
} from '../../../shared/constants/lookup.constants';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import {
  EditEmployeeDialogComponent,
  EditEmployeeDialogData,
} from '../edit-employee-dialog/edit-employee-dialog.component';
import {
  UpdateSalaryDialogComponent,
  UpdateSalaryDialogData,
} from '../update-salary-dialog/update-salary-dialog.component';

@Component({
  selector: 'app-employee-detail',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    PageHeaderComponent,
    ErrorStateComponent,
    MoneyPipe,
  ],
  templateUrl: './employee-detail.component.html',
  styleUrl: './employee-detail.component.scss',
})
export class EmployeeDetailComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly employeeService = inject(EmployeeService);
  private readonly departmentService = inject(DepartmentService);
  private readonly dialog = inject(MatDialog);

  readonly historyColumns = ['effectiveFrom', 'annualSalary', 'currency'];
  readonly countryLabel = countryLabel;
  readonly statusLabel = statusLabel;

  employeeId = '';
  employee: Employee | null = null;
  history: Salary[] = [];
  departments: Department[] = [];

  loading = false;
  deactivating = false;
  errorMessage: string | null = null;

  private loadSub?: Subscription;

  ngOnInit(): void {
    this.employeeId = this.route.snapshot.paramMap.get('id') ?? '';
    this.departmentService.list().subscribe({
      next: (departments) => {
        this.departments = departments;
      },
    });
    this.load();
  }

  ngOnDestroy(): void {
    this.loadSub?.unsubscribe();
  }

  load(): void {
    if (!this.employeeId) {
      this.errorMessage = 'Employee id is missing.';
      return;
    }

    this.loadSub?.unsubscribe();
    this.loading = true;
    this.errorMessage = null;

    this.loadSub = forkJoin({
      employee: this.employeeService.getById(this.employeeId, { skipErrorSnack: true }),
      history: this.employeeService
        .getSalaryHistory(this.employeeId, { skipErrorSnack: true })
        .pipe(catchError(() => of([] as Salary[]))),
    }).subscribe({
      next: ({ employee, history }) => {
        this.employee = employee;
        this.history = history;
        this.loading = false;
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.employee = null;
        this.history = [];
        const apiError = error.error as ApiErrorResponse | undefined;
        this.errorMessage = apiError?.message || 'Unable to load employee.';
      },
    });
  }

  editEmployee(): void {
    if (!this.employee) {
      return;
    }
    const data: EditEmployeeDialogData = {
      employee: this.employee,
      departments: this.departments,
    };
    this.dialog
      .open(EditEmployeeDialogComponent, {
        width: '40rem',
        data,
        disableClose: true,
      })
      .afterClosed()
      .subscribe((updated?: Employee) => {
        if (updated) {
          this.employee = updated;
        }
      });
  }

  updateSalary(): void {
    if (!this.employee) {
      return;
    }
    const data: UpdateSalaryDialogData = { employee: this.employee };
    this.dialog
      .open(UpdateSalaryDialogComponent, {
        width: '28rem',
        data,
        disableClose: true,
      })
      .afterClosed()
      .subscribe((salary?: Salary) => {
        if (salary) {
          this.load();
        }
      });
  }

  deactivate(): void {
    if (!this.employee || this.employee.employmentStatus === 'TERMINATED') {
      return;
    }
    const data: ConfirmDialogData = {
      title: 'Deactivate employee',
      message: `Deactivate ${this.employee.firstName} ${this.employee.lastName}? Status will change to Terminated.`,
      confirmLabel: 'Deactivate',
      danger: true,
    };
    this.dialog
      .open(ConfirmDialogComponent, { width: '28rem', data })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed || !this.employee) {
          return;
        }
        this.deactivating = true;
        this.employeeService.deactivate(this.employee.id).subscribe({
          next: () => {
            this.deactivating = false;
            this.load();
          },
          error: () => {
            this.deactivating = false;
          },
        });
      });
  }

  fullName(employee: Employee): string {
    return `${employee.firstName} ${employee.lastName}`;
  }

  initials(employee: Employee): string {
    return `${employee.firstName.charAt(0)}${employee.lastName.charAt(0)}`.toUpperCase();
  }
}
