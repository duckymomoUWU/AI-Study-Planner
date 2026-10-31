import { plan } from '@/lib/planner';
import { ZodError } from 'zod';
export async function POST(request: Request) {
  try {
    const body = await request.text();
    if (body.length > 32000) return Response.json({error:'Nội dung quá dài'}, {status:413});
    return Response.json(plan(JSON.parse(body)));
  } catch (e) {
    if (e instanceof ZodError || e instanceof SyntaxError) return Response.json({error:'Kiểm tra ngày, công việc và thời lượng (15–480 phút/ngày, tối đa 90 ngày).'}, {status:400});
    return Response.json({error:'Không thể lập lịch'}, {status:500});
  }
}
