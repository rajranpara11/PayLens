import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-employee-detail',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, RouterLink, PageHeaderComponent],
  template: `
    <app-page-header
      title="Employee detail"
      [subtitle]="'Employee id: ' + employeeId"
    >
      <a mat-button routerLink="/employees">Back to list</a>
    </app-page-header>

    <mat-card appearance="outlined">
      <mat-card-content>
        <p>
          Placeholder profile and salary history. Data will load via
          <code>EmployeeService</code>.
        </p>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    p {
      margin: 0;
      color: #5f6368;
      line-height: 1.5;
    }
  `,
})
export class EmployeeDetailComponent {
  private readonly route = inject(ActivatedRoute);
  readonly employeeId = this.route.snapshot.paramMap.get('id') ?? '';
}
