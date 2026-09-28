/** Explicit renderer DTO. Internal authentication columns must never be spread. */
export function publicIdentity(user: Record<string, unknown>) {
  return {
    id: user.id as number,
    username: user.username as string,
    email: user.email as string | null,
    role: user.role as 'owner' | 'manager' | 'employee',
    first_name: user.first_name as string,
    last_name: user.last_name as string,
    initials: user.initials as string,
    phone: user.phone as string | null,
    hire_date: user.hire_date as string | null,
    active: user.active as number,
    photo: user.photo as string | null,
  };
}
