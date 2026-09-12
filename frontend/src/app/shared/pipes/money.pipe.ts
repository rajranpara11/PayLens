import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'money', standalone: true })
export class MoneyPipe implements PipeTransform {
  transform(value: number | null | undefined, currency?: string | null): string {
    if (value === null || value === undefined || Number.isNaN(value)) {
      return '—';
    }
    const formatted = new Intl.NumberFormat(undefined, {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value);
    return currency ? `${formatted} ${currency}` : formatted;
  }
}
