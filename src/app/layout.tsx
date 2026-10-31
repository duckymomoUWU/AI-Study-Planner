import './style.css';
export const metadata = { title: 'AI Study Planner — bản nền tảng', description: 'Lập kế hoạch ôn tập theo quỹ thời gian' };
export default function Layout({children}: Readonly<{children: React.ReactNode}>) {
  return <html lang="vi"><body>{children}</body></html>;
}
