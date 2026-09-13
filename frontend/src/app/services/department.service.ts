import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { Department } from '../models/employee.model';

@Injectable({ providedIn: 'root' })
export class DepartmentService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/departments`;
  private cached$?: Observable<Department[]>;

  list(options?: { forceRefresh?: boolean }): Observable<Department[]> {
    if (options?.forceRefresh) {
      this.cached$ = undefined;
    }
    if (!this.cached$) {
      this.cached$ = this.http.get<Department[]>(this.baseUrl).pipe(
        tap({
          error: () => {
            this.cached$ = undefined;
          },
        }),
        shareReplay({ bufferSize: 1, refCount: false })
      );
    }
    return this.cached$;
  }

  clearCache(): void {
    this.cached$ = undefined;
  }
}
