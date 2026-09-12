import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [MatCardModule, PageHeaderComponent],
  template: `
    <app-page-header
      title="Dashboard"
      subtitle="Compensation overview for ACME. Charts and analytics land in a later phase."
    />

    <mat-card appearance="outlined">
      <mat-card-header>
        <mat-card-title>Coming next</mat-card-title>
        <mat-card-subtitle>Analytics wiring</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content>
        <p>
          This shell is ready for currency-safe analytics from
          <code>/api/v1/analytics/*</code>. No dashboard widgets yet.
        </p>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    p {
      margin: 0.75rem 0 0;
      color: #5f6368;
      line-height: 1.5;
    }
    code {
      font-size: 0.9em;
    }
  `,
})
export class DashboardComponent {}
