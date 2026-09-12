import { Component, Input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-error-state',
  standalone: true,
  imports: [MatButtonModule, MatIconModule],
  template: `
    <div class="error-state" role="alert">
      <mat-icon aria-hidden="true">error_outline</mat-icon>
      <h2>{{ title }}</h2>
      <p>{{ message }}</p>
      @if (actionLabel) {
        <button mat-stroked-button type="button" (click)="onAction?.()">
          {{ actionLabel }}
        </button>
      }
    </div>
  `,
  styles: `
    .error-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.5rem;
      padding: 2rem;
      text-align: center;
      color: var(--mat-sys-on-surface, #1f1f1f);
    }
    mat-icon {
      font-size: 40px;
      width: 40px;
      height: 40px;
      color: #b3261e;
    }
    h2 {
      margin: 0;
      font-size: 1.25rem;
    }
    p {
      margin: 0 0 0.75rem;
      max-width: 28rem;
      color: #5f6368;
    }
  `,
})
export class ErrorStateComponent {
  @Input() title = 'Something went wrong';
  @Input() message = 'Unable to load data. Try again.';
  @Input() actionLabel = '';
  @Input() onAction?: () => void;
}
