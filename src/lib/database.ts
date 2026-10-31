import { DatabaseSync } from 'node:sqlite';
import { mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

export function databaseHealth() {
  const path = resolve(process.env.STUDY_DATABASE_PATH || '.data/study.sqlite');
  mkdirSync(dirname(path), { recursive: true });
  const db = new DatabaseSync(path);
  try {
    db.exec('PRAGMA journal_mode = WAL; CREATE TABLE IF NOT EXISTS schema_meta (version INTEGER PRIMARY KEY); INSERT OR IGNORE INTO schema_meta VALUES (1);');
    return db.prepare('SELECT version FROM schema_meta WHERE version = 1').get()?.version === 1;
  } finally { db.close(); }
}
