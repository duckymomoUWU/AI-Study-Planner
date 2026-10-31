import { databaseHealth } from '@/lib/database';
export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export function GET() {
  try { const ok = databaseHealth(); return Response.json({status: ok ? 'ok' : 'error', database: ok ? 'ok' : 'error', ai: 'not-configured'}, {status: ok ? 200 : 503}); }
  catch { return Response.json({status:'error', database:'error'}, {status:503}); }
}
