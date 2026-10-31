import assert from 'node:assert/strict';
const base = process.env.STUDY_SMOKE_BASE || 'http://127.0.0.1:3000';
const health = await fetch(base + '/api/health');
assert.equal(health.status, 200);
assert.equal((await health.json()).database, 'ok');
const page = await fetch(base);
assert.equal(page.status, 200);
assert.match(await page.text(), /AI STUDY PLANNER/);
const input = {startDate:'2026-11-01',examDate:'2026-11-04',minutesPerDay:60,tasks:[{title:'Agile',minutes:60},{title:'Scrum',minutes:60},{title:'XP',minutes:60}]};
async function post(body) {
 return fetch(base+'/api/demo/plan',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
}
let r=await post(input); assert.equal(r.status,200);let data=await r.json();assert.equal(data.feasible,true);assert.equal(data.sessions[2].date,'2026-11-03');
r=await post({...input,minutesPerDay:45});assert.equal(r.status,200);data=await r.json();assert.equal(data.feasible,false);assert.equal(data.shortage,45);
r=await post({...input,examDate:input.startDate});assert.equal(r.status,400);
console.log('PASS: page, SQLite health, feasible plan, insufficient capacity, invalid input');
