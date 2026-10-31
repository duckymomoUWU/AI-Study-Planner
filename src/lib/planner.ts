import { z } from 'zod';

const date = z.string().regex(/^\d{4}-\d{2}-\d{2}$/).refine(v => {
  const d = new Date(v + 'T00:00:00Z');
  return Number.isFinite(d.getTime()) && d.toISOString().slice(0, 10) === v;
}, 'Ngày không hợp lệ');
export const inputSchema = z.object({
  startDate: date, examDate: date,
  minutesPerDay: z.number().int().min(15).max(480),
  tasks: z.array(z.object({title: z.string().trim().min(1).max(200), minutes: z.number().int().min(5).max(480)})).min(1).max(100)
}).refine(v => v.startDate < v.examDate, 'Ngày bắt đầu phải trước ngày thi')
  .refine(v => (Date.parse(v.examDate) - Date.parse(v.startDate)) / 86400000 <= 90, 'Tối đa 90 ngày');
export type PlannerInput = z.infer<typeof inputSchema>;

// Date-only arithmetic: exam day is excluded; callers supply the user's local date.
// Never silently reduce effort or drop a task to fit the budget.
export function plan(raw: unknown) {
  const input = inputSchema.parse(raw);
  const days = (Date.parse(input.examDate) - Date.parse(input.startDate)) / 86400000;
  const required = input.tasks.reduce((s, t) => s + t.minutes, 0);
  const capacity = days * input.minutesPerDay;
  if (required > capacity) return { feasible: false as const, required, capacity, shortage: required - capacity, sessions: [] };
  const sessions: { title: string; date: string; minutes: number; taskIndex: number }[] = [];
  let day = 0, used = 0;
  input.tasks.forEach((task, taskIndex) => {
    let remaining = task.minutes;
    while (remaining > 0) {
      const minutes = Math.min(remaining, input.minutesPerDay - used);
      sessions.push({ title: task.title, taskIndex, minutes, date: new Date(Date.parse(input.startDate) + day * 86400000).toISOString().slice(0, 10) });
      remaining -= minutes; used += minutes;
      if (used === input.minutesPerDay) { day++; used = 0; }
    }
  });
  return { feasible: true as const, required, capacity, shortage: 0, sessions };
}
