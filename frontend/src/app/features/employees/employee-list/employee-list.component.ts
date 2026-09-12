import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-employee-list',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, RouterLink, PageHeaderComponent],
  template: `
    <app-page-header
      title="Employees"
      subtitle="Directory, search, and filters will connect to the employee API next."
    >
      <button mat-stroked-button type="button" disabled>Add employee</button>
    </app-page-header>

    <mat-card appearance="outlined">
      <mat-card-content>
        <p>Placeholder list. Open a sample detail route to verify navigation.</p>
        <a mat-button color="primary" routerLink="/employees/demo">Open detail placeholder</a>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    p {
      margin: 0 0 0.75rem;
      color: #5f6368;
    }
  `,
})
export class EmployeeListComponent {}
