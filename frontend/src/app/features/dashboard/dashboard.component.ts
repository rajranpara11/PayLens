import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, RouterLink, PageHeaderComponent],
  template: `
    <app-page-header
      eyebrow="Overview"
      title="Dashboard"
      subtitle="Currency-safe compensation analytics for ACME. Charts wire in next."
    >
      <a mat-flat-button color="primary" routerLink="/employees">
        <mat-icon>groups</mat-icon>
        Open employees
      </a>
    </app-page-header>

    <section class="hero pl-panel">
      <div>
        <p class="kicker">PayLens workspace</p>
        <h2>See payroll clearly. Act with confidence.</h2>
        <p class="lede">
          Manage headcount, review current compensation, and keep salary history
          auditable — without mixing currencies in totals.
        </p>
      </div>
      <div class="hero-visual" aria-hidden="true">
        <div class="bar b1"></div>
        <div class="bar b2"></div>
        <div class="bar b3"></div>
        <div class="bar b4"></div>
      </div>
    </section>

    <section class="cards">
      <article class="card pl-panel">
        <mat-icon>insights</mat-icon>
        <h3>Analytics ready</h3>
        <p>Endpoints under <code>/api/v1/analytics/*</code> are prepared for widgets.</p>
      </article>
      <article class="card pl-panel">
        <mat-icon>public</mat-icon>
        <h3>Currency-safe</h3>
        <p>Rollups stay grouped by currency so INR never blends into USD.</p>
      </article>
      <article class="card pl-panel">
        <mat-icon>history</mat-icon>
        <h3>Salary history</h3>
        <p>Effective-dated changes stay traceable from the employee profile.</p>
      </article>
    </section>
  `,
  styles: `
    .hero {
      display: grid;
      grid-template-columns: 1.4fr 0.8fr;
      gap: 1.5rem;
      align-items: center;
      padding: 1.6rem 1.75rem;
      margin-bottom: 1rem;
      background:
        linear-gradient(135deg, rgba(15, 118, 110, 0.08), transparent 42%),
        var(--pl-surface);
    }
    .kicker {
      margin: 0 0 0.45rem;
      font-size: 0.72rem;
      font-weight: 700;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--pl-accent);
    }
    h2 {
      margin: 0;
      font-family: var(--pl-display);
      font-size: clamp(1.35rem, 2vw, 1.75rem);
      font-weight: 600;
      letter-spacing: -0.02em;
      color: var(--pl-ink);
      line-height: 1.2;
    }
    .lede {
      margin: 0.7rem 0 0;
      max-width: 34rem;
      color: var(--pl-muted);
      line-height: 1.55;
    }
    .hero-visual {
      display: flex;
      align-items: flex-end;
      justify-content: center;
      gap: 0.55rem;
      min-height: 7rem;
      padding: 0.5rem;
    }
    .bar {
      width: 1.35rem;
      border-radius: 8px 8px 4px 4px;
      background: linear-gradient(180deg, #14b8a6, #0f766e);
      opacity: 0.85;
    }
    .b1 { height: 42%; }
    .b2 { height: 68%; opacity: 0.7; }
    .b3 { height: 88%; }
    .b4 { height: 56%; opacity: 0.75; }
    .cards {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 1rem;
    }
    .card {
      padding: 1.25rem 1.3rem;
    }
    .card mat-icon {
      color: var(--pl-accent);
      margin-bottom: 0.55rem;
    }
    .card h3 {
      margin: 0 0 0.4rem;
      font-size: 1rem;
      font-weight: 700;
    }
    .card p {
      margin: 0;
      color: var(--pl-muted);
      line-height: 1.5;
      font-size: 0.9rem;
    }
    @media (max-width: 900px) {
      .hero {
        grid-template-columns: 1fr;
      }
      .hero-visual {
        display: none;
      }
      .cards {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class DashboardComponent {}
