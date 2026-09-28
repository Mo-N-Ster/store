import { app } from 'electron';
import fs from 'node:fs';
import path from 'node:path';

type LogContext = Record<string, string | number | boolean | null | undefined>;

export function logTechnical(event: string, error?: unknown, context: LogContext = {}) {
  const entry = {
    timestamp: new Date().toISOString(),
    level: error ? 'error' : 'info',
    event,
    context,
    error: error instanceof Error ? error.stack || error.message : error ? String(error) : undefined,
  };
  try {
    const directory = path.join(app.getPath('userData'), 'logs');
    fs.mkdirSync(directory, { recursive: true });
    fs.appendFileSync(path.join(directory, 'technical.jsonl'), `${JSON.stringify(entry)}\n`, 'utf8');
  } catch (loggingError) {
    console.error('Technical logging failed', loggingError, entry);
  }
}
