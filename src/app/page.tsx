'use client';
import { useState } from 'react';
import type { plan } from '@/lib/planner';
export default function Home() {
  const [result, setResult] = useState<ReturnType<typeof plan> | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault(); setBusy(true); setResult(null); setError('');
    const form = new FormData(e.currentTarget);
    try {
      const tasks = String(form.get('tasks')).split('\n').filter(s => s.trim()).map(line => {
        const split = line.lastIndexOf('|');
        return {title: line.slice(0, split).trim(), minutes: Number(line.slice(split + 1))};
      });
      const response = await fetch('/api/demo/plan', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({startDate:form.get('start'), examDate:form.get('exam'), minutesPerDay:Number(form.get('budget')), tasks})});
      const value = await response.json();
      if (!response.ok) throw new Error(value.error);
      setResult(value);
    } catch(e) { setError(e instanceof Error ? e.message : 'Có lỗi kết nối'); }
    finally { setBusy(false); }
  }
  return <main><p className="eyebrow">AI STUDY PLANNER · KHỞI ĐẦU</p><h1>Biết hôm nay<br/>cần học gì.</h1><p>Chia nội dung ôn tập thành những phiên học vừa với thời gian của bạn.</p>
    <aside>Bản thử bộ lập lịch: nhập công việc và thời lượng của bạn. Chưa kết nối AI, tài khoản hoặc lưu kế hoạch.</aside>
    <div className="grid"><form onSubmit={submit}><h2>Quỹ thời gian của bạn</h2>
    <label>Ngày bắt đầu<input type="date" name="start" required defaultValue="2026-11-01"/></label>
    <label>Ngày thi (không xếp việc trong ngày này)<input type="date" name="exam" required defaultValue="2026-11-04"/></label>
    <label>Phút học mỗi ngày<input name="budget" type="number" min="15" max="480" defaultValue="60" required/></label>
    <label>Công việc | số phút (mỗi dòng một việc)<textarea name="tasks" rows={6} required defaultValue={'Ôn nguyên tắc Agile | 60\nÔn Scrum và các sự kiện | 60\nLuyện câu hỏi XP | 60'}/></label>
    <button disabled={busy}>{busy ? 'Đang lập lịch…' : 'Kiểm tra và chia lịch'}</button></form>
    <section aria-live="polite"><h2>Kế hoạch dự kiến</h2>{error && <p role="alert">{error}</p>}
    {!result && !error && <p>Điền thông tin để xem lịch. Bạn có thể sửa thông tin rồi tạo lại.</p>}
    {result && !result.feasible && <p role="alert">Cần {result.required} phút nhưng chỉ có {result.capacity} phút. Thiếu {result.shortage} phút: hãy giảm phạm vi hoặc tăng quỹ thời gian.</p>}
    {result?.feasible && <><p>{result.required} / {result.capacity} phút khả dụng</p><ol>{result.sessions.map((s,i) => <li key={i}><strong>{s.date} · {s.minutes} phút</strong><br/>{s.title}</li>)}</ol><p>Thời lượng là dự kiến. Lịch chưa được lưu.</p></>}
    </section></div><footer>Tiến độ công việc không phải mức độ hiểu bài.</footer></main>;
}
