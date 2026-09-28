export type StorageHealth = 'unknown' | 'healthy' | 'warning' | 'critical';

export function storageHealth(availableBytes: number | null): StorageHealth {
  if (availableBytes === null || !Number.isFinite(availableBytes) || availableBytes < 0)
    return 'unknown';
  if (availableBytes < 128 * 1024 * 1024) return 'critical';
  if (availableBytes < 512 * 1024 * 1024) return 'warning';
  return 'healthy';
}
