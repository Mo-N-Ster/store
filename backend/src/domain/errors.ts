export const publicErrorCodes = [
  'AUTH_REQUIRED',
  'BACKUP_FAILED',
  'CASH_SESSION_REQUIRED',
  'CASH_SESSION_OPEN',
  'CONFLICT',
  'DUPLICATE_PRODUCT',
  'DUPLICATE_USER',
  'EMPTY_CSV',
  'FORBIDDEN',
  'INSUFFICIENT_STOCK',
  'INVALID_BACKUP',
  'INVALID_CREDENTIALS',
  'INVALID_CSV_HEADERS',
  'INVALID_EXPORT',
  'INVALID_INVOICE',
  'INVALID_MESSAGE',
  'INVALID_MEDIA',
  'INVALID_PDF',
  'INVALID_PRODUCT',
  'INVALID_RECIPIENT',
  'INVALID_SECURITY_ANSWER',
  'INVALID_STORE_PDF',
  'INVALID_USER',
  'LAST_OWNER_REQUIRED',
  'MEDIA_TOO_LARGE',
  'NO_OPEN_SERVICE',
  'NOT_FOUND',
  'OWNER_REQUIRED',
  'PDF_DATA_TOO_LARGE',
  'PDF_TOO_LARGE',
  'PRINT_FAILED',
  'QUESTION_NOT_CONFIGURED',
  'SECURITY_QUESTION_REQUIRED',
  'SERVICE_ALREADY_OPEN',
  'SETUP_ALREADY_COMPLETED',
  'SMTP_NOT_CONFIGURED',
  'SMTP_FAILED',
  'UNAUTHORIZED',
  'USER_NOT_FOUND',
  'VALIDATION_ERROR',
  'WEAK_PASSWORD',
] as const;

const publicCodes = new Set<string>(publicErrorCodes);

export function publicErrorCode(error: unknown) {
  const message = error instanceof Error ? error.message : String(error);
  return publicCodes.has(message) ? message : 'INTERNAL_ERROR';
}

export function isExpectedApplicationError(error: unknown) {
  return publicErrorCode(error) !== 'INTERNAL_ERROR';
}
