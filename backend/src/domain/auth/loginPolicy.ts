export type LoginPolicy = {
  maxAttempts: number;
  lockMinutes: number;
};

const boundedInteger = (value: unknown, fallback: number, minimum: number, maximum: number) => {
  const parsed = Number(value);
  return Number.isInteger(parsed) ? Math.min(maximum, Math.max(minimum, parsed)) : fallback;
};

export function loginPolicy(settings: Record<string, string>): LoginPolicy {
  return {
    maxAttempts: boundedInteger(settings.authMaxAttempts, 5, 3, 10),
    lockMinutes: boundedInteger(settings.authLockMinutes, 15, 1, 1_440),
  };
}

export function nextFailedLogin(
  previousAttempts: number,
  policy: LoginPolicy,
  currentTime = new Date(),
) {
  const failedAttempts = Math.max(0, previousAttempts) + 1;
  return {
    failedAttempts,
    lockedUntil:
      failedAttempts >= policy.maxAttempts
        ? new Date(currentTime.getTime() + policy.lockMinutes * 60_000).toISOString()
        : null,
  };
}

export function isLoginLocked(lockedUntil: string | null | undefined, currentTime = new Date()) {
  if (!lockedUntil) return false;
  const timestamp = Date.parse(lockedUntil);
  return Number.isFinite(timestamp) && timestamp > currentTime.getTime();
}
