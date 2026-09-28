/** Disable native stepping while retaining numeric validation and keyboard entry. */
export function installNumericInputGuards(root: Document) {
  const wheel = (event: WheelEvent) => {
    if (event.target instanceof HTMLInputElement && event.target.type === 'number' && root.activeElement === event.target)
      event.preventDefault();
  };
  const key = (event: KeyboardEvent) => {
    if (event.target instanceof HTMLInputElement && event.target.type === 'number' && ['ArrowUp', 'ArrowDown'].includes(event.key))
      event.preventDefault();
  };
  root.addEventListener('wheel', wheel, { passive: false, capture: true });
  root.addEventListener('keydown', key, true);
  return () => {
    root.removeEventListener('wheel', wheel, true);
    root.removeEventListener('keydown', key, true);
  };
}
