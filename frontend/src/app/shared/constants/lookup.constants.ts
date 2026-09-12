export const COUNTRY_OPTIONS = [
  { code: 'IN', label: 'India' },
  { code: 'US', label: 'United States' },
  { code: 'GB', label: 'United Kingdom' },
  { code: 'DE', label: 'Germany' },
  { code: 'CA', label: 'Canada' },
] as const;

export const STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'ON_LEAVE', label: 'On leave' },
  { value: 'TERMINATED', label: 'Terminated' },
] as const;

export const CURRENCY_OPTIONS = [
  'USD',
  'EUR',
  'GBP',
  'INR',
  'SGD',
  'AUD',
  'CAD',
  'CHF',
  'JPY',
  'NZD',
] as const;

export function countryLabel(code: string): string {
  return COUNTRY_OPTIONS.find((c) => c.code === code)?.label ?? code;
}

export function statusLabel(status: string): string {
  return STATUS_OPTIONS.find((s) => s.value === status)?.label ?? status;
}
