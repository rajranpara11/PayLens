import { Component, Inject, OnInit, inject } from '@angular/core';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Employee } from '../../../models/employee.model';
import { Salary } from '../../../models/salary.model';
import { EmployeeService } from '../../../services/employee.service';
import { CURRENCY_OPTIONS } from '../../../shared/constants/lookup.constants';
import { localDateIso } from '../../../shared/utils/api-error.util';

export interface UpdateSalaryDialogData {
  employee: Employee;
}

@Component({
  selector: 'app-update-salary-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './update-salary-dialog.component.html',
  styleUrl: './update-salary-dialog.component.scss',
})
export class UpdateSalaryDialogComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly employeeService = inject(EmployeeService);

  readonly currencies = CURRENCY_OPTIONS;
  saving = false;

  readonly form = this.fb.nonNullable.group({
    annualSalary: [0, [Validators.required, Validators.min(0.01)]],
    currency: ['USD', Validators.required],
    effectiveFrom: ['', Validators.required],
  });

  constructor(
    private readonly dialogRef: MatDialogRef<UpdateSalaryDialogComponent, Salary | undefined>,
    @Inject(MAT_DIALOG_DATA) readonly data: UpdateSalaryDialogData
  ) {}

  ngOnInit(): void {
    const current = this.data.employee.currentSalary;
    const today = localDateIso();
    this.form.patchValue({
      annualSalary: current?.annualSalary ?? 0,
      currency: current?.currency ?? 'USD',
      effectiveFrom: today,
    });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving = true;
    const value = this.form.getRawValue();
    this.employeeService
      .updateSalary(this.data.employee.id, {
        annualSalary: Number(value.annualSalary),
        currency: value.currency,
        effectiveFrom: value.effectiveFrom,
      })
      .subscribe({
        next: (salary) => this.dialogRef.close(salary),
        error: () => {
          this.saving = false;
        },
      });
  }
}
