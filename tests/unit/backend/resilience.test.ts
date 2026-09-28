import Database from 'better-sqlite3';
import { afterEach, describe, expect, it } from 'vitest';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { schema } from '../../../backend/src/database/schema';

const temporaryDirectories: string[] = [];
afterEach(() => {
  for (const directory of temporaryDirectories.splice(0))
    fs.rmSync(directory, { recursive: true, force: true });
});

describe('local database resilience', () => {
  it('makes invoice request identifiers unique', () => {
    const database = new Database(':memory:');
    try {
      database.exec(schema);
      database.prepare(
        `INSERT INTO users(id,username,password_hash,role,first_name,last_name,initials)
         VALUES(1,'cashier','hash','employee','Cash','User','CU')`,
      ).run();
      const insert = database.prepare(
        `INSERT INTO invoices(
          id,employee_id,idempotency_key,subtotal,total_amount,discount
        ) VALUES(?,1,?,10,10,0)`,
      );
      insert.run('FACT-1', 'same-request');
      expect(() => insert.run('FACT-2', 'same-request')).toThrow();
      expect(database.prepare('SELECT COUNT(*) count FROM invoices').get()).toEqual({ count: 1 });
    } finally {
      database.close();
    }
  });

  it('rolls back stock changes when a sale transaction fails', () => {
    const database = new Database(':memory:');
    try {
      database.exec(schema);
      database.prepare(
        `INSERT INTO products(id,reference,name,category,price,stock_quantity,min_stock_threshold)
         VALUES(1,'PROD-1','Test','Test',2,5,0)`,
      ).run();
      const operation = database.transaction(() => {
        database.prepare('UPDATE products SET stock_quantity=stock_quantity-3 WHERE id=1').run();
        throw new Error('simulated interruption');
      });
      expect(operation).toThrow('simulated interruption');
      expect(database.prepare('SELECT stock_quantity stock FROM products WHERE id=1').get()).toEqual({
        stock: 5,
      });
    } finally {
      database.close();
    }
  });

  it('reopens a WAL database with committed data and valid integrity', () => {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'store-resilience-'));
    temporaryDirectories.push(directory);
    const filename = path.join(directory, 'store.db');
    const writer = new Database(filename);
    writer.pragma('journal_mode = WAL');
    writer.pragma('synchronous = FULL');
    writer.exec('CREATE TABLE events(id INTEGER PRIMARY KEY,value TEXT NOT NULL)');
    writer.prepare("INSERT INTO events(value) VALUES('committed')").run();

    const reader = new Database(filename, { readonly: true });
    expect(reader.prepare('SELECT value FROM events').get()).toEqual({ value: 'committed' });
    expect(reader.pragma('quick_check')).toEqual([{ quick_check: 'ok' }]);
    reader.close();
    writer.close();
  });
});
