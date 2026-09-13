import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { AsyncPipe } from '@angular/common';
import { Component, ViewChild, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSidenavContainer, MatSidenavModule } from '@angular/material/sidenav';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map, shareReplay } from 'rxjs';
import { LoadingService } from '../../core/services/loading.service';
import { AuthService } from '../../services/auth.service';

const NAV_COLLAPSED_KEY = 'paylens.navCollapsed';
const NAV_TRANSITION_MS = 240;

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    AsyncPipe,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatIconModule,
    MatButtonModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly router = inject(Router);
  readonly loadingService = inject(LoadingService);
  readonly auth = inject(AuthService);

  @ViewChild(MatSidenavContainer) private sidenavContainer?: MatSidenavContainer;

  readonly isHandset$ = this.breakpointObserver.observe(Breakpoints.Handset).pipe(
    map((result) => result.matches),
    shareReplay({ bufferSize: 1, refCount: true })
  );

  /** Desktop rail collapse only — handset drawer stays labeled. */
  readonly navCollapsed = signal(this.readCollapsedPreference());

  readonly navItems = [
    { label: 'Dashboard', path: '/dashboard', icon: 'dashboard' },
    { label: 'Employees', path: '/employees', icon: 'groups' },
  ];

  toggleNavCollapsed(): void {
    const next = !this.navCollapsed();
    this.navCollapsed.set(next);
    try {
      localStorage.setItem(NAV_COLLAPSED_KEY, next ? '1' : '0');
    } catch {
      /* ignore quota / private mode */
    }
    this.syncContentMargins();
  }

  logout(): void {
    this.auth.logout().subscribe({
      next: () => void this.router.navigateByUrl('/login'),
      error: () => void this.router.navigateByUrl('/login'),
    });
  }

  /** Material caches drawer width — refresh margins during/after width transition. */
  private syncContentMargins(): void {
    requestAnimationFrame(() => {
      this.sidenavContainer?.updateContentMargins();
      window.setTimeout(() => {
        this.sidenavContainer?.updateContentMargins();
        window.dispatchEvent(new Event('resize'));
      }, NAV_TRANSITION_MS);
    });
  }

  private readCollapsedPreference(): boolean {
    try {
      return localStorage.getItem(NAV_COLLAPSED_KEY) === '1';
    } catch {
      return false;
    }
  }
}
