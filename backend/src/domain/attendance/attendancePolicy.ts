export function validateAttendanceCorrection(input: {
  startTime: string;
  endTime: string;
  reason: string;
}) {
  const start = Date.parse(input.startTime);
  const end = Date.parse(input.endTime);
  if (
    !Number.isFinite(start) ||
    !Number.isFinite(end) ||
    end <= start ||
    input.reason.trim().length < 3 || start > Date.now() || end > Date.now()
  )
    throw new Error('VALIDATION_ERROR');
  return {
    startTime: new Date(start).toISOString(),
    endTime: new Date(end).toISOString(),
    reason: input.reason.trim(),
  };
}

export type AttendanceAction = 'CLOCK_IN' | 'CLOCK_OUT';

export function validateAttendanceTransition(action: unknown, hasOpenAttendance: boolean) {
  if (action !== 'CLOCK_IN' && action !== 'CLOCK_OUT') throw new Error('VALIDATION_ERROR');
  if (action === 'CLOCK_IN' && hasOpenAttendance) throw new Error('SERVICE_ALREADY_OPEN');
  if (action === 'CLOCK_OUT' && !hasOpenAttendance) throw new Error('NO_OPEN_SERVICE');
  return action;
}
