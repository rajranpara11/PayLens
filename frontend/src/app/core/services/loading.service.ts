import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class LoadingService {
  private readonly activeRequests = signal(0);
  readonly loading = signal(false);

  show(): void {
    this.activeRequests.update((count) => count + 1);
    this.loading.set(true);
  }

  hide(): void {
    this.activeRequests.update((count) => Math.max(0, count - 1));
    if (this.activeRequests() === 0) {
      this.loading.set(false);
    }
  }
}
