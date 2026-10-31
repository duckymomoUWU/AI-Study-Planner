import test from 'node:test';
import assert from 'node:assert/strict';
import { plan } from '../src/lib/planner';
const base = {startDate:'2026-11-01',examDate:'2026-11-04',minutesPerDay:60,tasks:[{title:'Agile',minutes:60},{title:'Scrum',minutes:60},{title:'XP',minutes:60}]};
test('180 minutes fit into three days, excluding exam day', () => {
 const r=plan(base); assert.equal(r.feasible,true); assert.equal(r.sessions.length,3); assert.equal(r.sessions[2].date,'2026-11-03');
});
test('insufficient capacity is explicit and does not silently omit work', () => {
 const r=plan({...base,minutesPerDay:45}); assert.equal(r.feasible,false); assert.equal(r.shortage,45); assert.deepEqual(r.sessions,[]);
});
test('splits long work across days and conserves effort', () => {
 const r=plan({...base,tasks:[{title:'Scrum',minutes:125}]}); assert.deepEqual(r.sessions.map(s=>s.minutes),[60,60,5]);
});
test('rejects impossible dates, zero budget, same-day exam, and empty tasks', () => {
 for (const change of [{startDate:'2026-02-30'},{minutesPerDay:0},{examDate:base.startDate},{tasks:[]}]) assert.throws(()=>plan({...base,...change}));
});
test('calendar handles leap day and year boundary', () => {
 for(const [startDate,examDate,last] of [['2028-02-28','2028-03-02','2028-03-01'],['2026-12-30','2027-01-02','2027-01-01']]) assert.equal(plan({...base,startDate,examDate}).sessions[2].date,last);
});
test('budgets and total minutes hold across many inputs', () => {
 for(let budget=15;budget<=120;budget+=7) for(let effort=5;effort<=360;effort+=11) {
  const r=plan({...base,minutesPerDay:budget,tasks:[{title:'Study',minutes:effort}]});
  if(!r.feasible) {assert.ok(effort>3*budget); continue;}
  assert.equal(r.sessions.reduce((s,t)=>s+t.minutes,0),effort);
  const byDate:Record<string,number>={}; for(const s of r.sessions){assert.ok(s.date<base.examDate);byDate[s.date]=(byDate[s.date]||0)+s.minutes;}
  for(const sum of Object.values(byDate)) assert.ok(sum<=budget);
 }
});
