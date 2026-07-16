import type { UserInput } from './user.types.js';
export function validateUser(input: UserInput, passwordRequired: boolean) {
  if (
    !input.username?.trim() ||
    !input.email?.includes('@') ||
    !input.firstName?.trim() ||
    !input.lastName?.trim() ||
    (passwordRequired && (!input.password || input.password.length < 8))
  )
    throw new Error('INVALID_USER');
}
