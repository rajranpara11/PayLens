import { formatApiErrorMessage, localDateIso } from './api-error.util';

describe('api-error util', () => {
  it('formats fieldErrors into a readable message', () => {
    expect(
      formatApiErrorMessage({
        message: 'Validation failed',
        fieldErrors: [
          { field: 'email', message: 'must be a well-formed email address' },
          { field: 'firstName', message: 'must not be blank' },
        ],
      })
    ).toContain('email:');
  });

  it('uses message when already detailed', () => {
    expect(
      formatApiErrorMessage({
        message: 'email: must not be blank',
        fieldErrors: [{ field: 'email', message: 'must not be blank' }],
      })
    ).toBe('email: must not be blank');
  });

  it('builds a local YYYY-MM-DD date', () => {
    expect(localDateIso(new Date(2026, 8, 13))).toBe('2026-09-13');
  });
});
