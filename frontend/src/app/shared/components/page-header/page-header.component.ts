import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-page-header',
  standalone: true,
  template: `
    <header class="page-header">
      <div class="copy">
        @if (eyebrow) {
          <p class="eyebrow">{{ eyebrow }}</p>
        }
        <h1>{{ title }}</h1>
        @if (subtitle) {
          <p class="subtitle">{{ subtitle }}</p>
        }
      </div>
      <div class="actions">
        <ng-content></ng-content>
      </div>
    </header>
  `,
  styles: `
    .page-header {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-end;
      justify-content: space-between;
      gap: 1rem 1.5rem;
      margin-bottom: 1.5rem;
      padding-bottom: 1.15rem;
      border-bottom: 1px solid rgba(215, 224, 234, 0.95);
    }
    .eyebrow {
      margin: 0 0 0.35rem;
      font-size: 0.72rem;
      font-weight: 700;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--pl-accent);
    }
    h1 {
      margin: 0;
      font-family: var(--pl-display);
      font-size: clamp(1.7rem, 2.2vw, 2.15rem);
      font-weight: 600;
      letter-spacing: -0.03em;
      color: var(--pl-ink);
      line-height: 1.15;
    }
    .subtitle {
      margin: 0.45rem 0 0;
      max-width: 40rem;
      color: var(--pl-muted);
      font-size: 0.95rem;
      line-height: 1.45;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.55rem;
      align-items: center;
    }
  `,
})
export class PageHeaderComponent {
  @Input({ required: true }) title!: string;
  @Input() subtitle = '';
  @Input() eyebrow = '';
}
