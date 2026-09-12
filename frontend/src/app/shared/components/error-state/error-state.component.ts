import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-error-state',
  standalone: true,
  imports: [MatButtonModule, MatIconModule],
  template: `
    <div class="error-state pl-panel" role="alert">
      <mat-icon aria-hidden="true">error_outline</mat-icon>
      <h2>{{ title }}</h2>
      <p>{{ message }}</p>
      @if (actionLabel) {
        <button mat-flat-button color="primary" type="button" (click)="action.emit()">
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
      gap: 0.45rem;
      padding: 2.5rem 1.5rem;
      text-align: center;
      color: var(--pl-ink);
    }
    mat-icon {
      font-size: 2.4rem;
      width: 2.4rem;
      height: 2.4rem;
      color: var(--pl-danger);
      margin-bottom: 0.25rem;
    }
    h2 {
      margin: 0;
      font-family: var(--pl-display);
      font-size: 1.3rem;
      font-weight: 600;
    }
    p {
      margin: 0 0 0.85rem;
      max-width: 28rem;
      color: var(--pl-muted);
      line-height: 1.5;
    }
  `,
})
export class ErrorStateComponent {
  @Input() title = 'Something went wrong';
  @Input() message = 'Unable to load data. Try again.';
  @Input() actionLabel = '';
  @Output() action = new EventEmitter<void>();
}
