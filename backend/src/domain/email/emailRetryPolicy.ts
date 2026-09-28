export type EmailRetryDecision = {
  attempts: number;
  status: 'pending' | 'failed';
  nextAttemptAt: string;
};

export function nextEmailRetry(
  previousAttempts: number,
  errorCode: string,
  currentTime = new Date(),
): EmailRetryDecision {
  const configurationMissing = errorCode === 'SMTP_NOT_CONFIGURED';
  const attempts = configurationMissing ? Math.max(0, previousAttempts) : Math.max(0, previousAttempts) + 1;
  const retryMinutes = configurationMissing ? 15 : Math.min(60, 2 ** attempts);
  return {
    attempts,
    status: !configurationMissing && attempts >= 5 ? 'failed' : 'pending',
    nextAttemptAt: new Date(currentTime.getTime() + retryMinutes * 60_000).toISOString(),
  };
}
