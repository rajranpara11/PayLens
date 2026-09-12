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
import { Department, Employee, UpdateEmployeeRequest } from '../../../models/employee.model';
import { EmployeeService } from '../../../services/employee.service';
import {
  COUNTRY_OPTIONS,
  STATUS_OPTIONS,
} from '../../../shared/constants/lookup.constants';

export interface EditEmployeeDialogData {
  employee: Employee;
  departments: Department[];
}

@Component({
  selector: 'app-edit-employee-dialog',
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
  templateUrl: './edit-employee-dialog.component.html',
  styleUrl: './edit-employee-dialog.component.scss',
})
export class EditEmployeeDialogComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly employeeService = inject(EmployeeService);

  readonly countries = COUNTRY_OPTIONS;
  readonly statuses = STATUS_OPTIONS;
  saving = false;

  readonly form = this.fb.nonNullable.group({
    employeeCode: ['', [Validators.required, Validators.maxLength(32)]],
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    country: ['', Validators.required],
    department: ['', Validators.required],
    designation: ['', [Validators.required, Validators.maxLength(120)]],
    employmentStatus: ['', Validators.required],
    joiningDate: ['', Validators.required],
  });

  constructor(
    private readonly dialogRef: MatDialogRef<EditEmployeeDialogComponent, Employee | undefined>,
    @Inject(MAT_DIALOG_DATA) readonly data: EditEmployeeDialogData
  ) {}

  ngOnInit(): void {
    const employee = this.data.employee;
    this.form.patchValue({
      employeeCode: employee.employeeCode,
      firstName: employee.firstName,
      lastName: employee.lastName,
      email: employee.email,
      country: employee.country,
      department: employee.department.name,
      designation: employee.designation,
      employmentStatus: employee.employmentStatus,
      joiningDate: employee.joiningDate,
    });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving = true;
    const value = this.form.getRawValue();
    const payload: UpdateEmployeeRequest = {
      ...value,
      salary: null,
    };
    this.employeeService.update(this.data.employee.id, payload).subscribe({
      next: (updated) => this.dialogRef.close(updated),
      error: () => {
        this.saving = false;
      },
    });
  }
}
