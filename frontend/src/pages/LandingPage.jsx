import { useEffect, useState } from 'react'
import studyStillLife from '../assets/landing-study-still-life.png'
import './LandingPage.css'

const steps = [
  ['01', 'Bắt đầu từ đề cương', 'Tải tài liệu hoặc nhập chủ đề, ngày thi và thời gian bạn có.'],
  ['02', 'Duyệt kế hoạch của bạn', 'Xem đề xuất AI, chỉnh từng việc và xác nhận trước khi lưu.'],
  ['03', 'Học từng chút mỗi ngày', 'Mở lịch học, hoàn thành từng việc và theo dõi nhịp học trong tuần.'],
]
const faqs = [
  ['Tôi cần chuẩn bị gì để bắt đầu?', 'Bạn cần tên môn học, chủ đề muốn ôn, ngày thi và thời gian có thể dành mỗi ngày. Tải đề cương để rà lại chủ đề hoặc nhập tay. Nếu tài liệu không ghi ngày thi, hãy tự nhập ngày thi của mình.'],
  ['AI có tự thêm việc vào lịch của tôi không?', 'Không. AI tạo một bản nháp để bạn xem và chỉnh sửa. Chỉ khi bạn xác nhận, các công việc mới được thêm vào lịch học.'],
  ['Nếu tôi không có đủ thời gian thì sao?', 'Bản nháp hiển thị phần đã xếp và những việc chưa đủ chỗ. Bạn có thể điều chỉnh quỹ thời gian, giảm phạm vi ôn hoặc lưu phần đã xếp và giữ những việc còn lại để xử lý tiếp. App không âm thầm giảm thời lượng để làm lịch trông đầy đủ.'],
  ['Tôi có thể học nhiều môn cùng lúc không?', 'Có. Bạn đặt quỹ thời gian chung mỗi ngày và mức mong muốn cho từng môn. Tổng thời lượng được xếp không vượt quỹ chung; nếu AI đề xuất vượt mức mong muốn của một môn, bạn sẽ được hỏi trước khi lưu.'],
  ['Tôi có thể sửa kế hoạch sau khi lưu không?', 'Có. Bạn có thể sửa công việc, dời ngày học và đánh dấu hoàn thành hoặc mở lại. Khi dời việc, app kiểm tra quỹ thời gian của ngày mới.'],
  ['Nếu AI tạm thời không hoạt động thì sao?', 'Mục tiêu và lịch đã lưu vẫn được giữ. Bạn có thể tiếp tục thêm công việc thủ công hoặc thử tạo bản nháp lại sau.'],
]
function PreviewLabel({ children }) {
  return <div className="landing-preview-label"><span>{children}</span><span>Minh họa</span></div>
}
function SchedulePreview() {
  return <div className="landing-product-preview landing-schedule-preview">
    <PreviewLabel>LỊCH HỌC CỦA BẠN</PreviewLabel><h3>Mỗi ngày, một bước gần hơn.</h3>
    <div className="landing-preview-week"><span aria-hidden="true">‹</span><strong>Một tuần ôn tập</strong><span aria-hidden="true">›</span></div>
    {[
      ['Thứ Hai', 'Ôn khái niệm nền tảng', 'Kiểm thử phần mềm', '30 phút', true],
      ['Thứ Ba', 'Luyện bài tập theo chủ đề', 'Kiểm thử phần mềm', '45 phút', false],
      ['Thứ Tư', 'Hệ thống lại kiến thức', 'Quản trị mạng', '30 phút', false],
    ].map(([day, title, subject, minutes, done]) => <div className={`landing-schedule-row${done ? ' landing-schedule-row--done' : ''}`} key={day}><small>{day}</small><div><span className="landing-task-symbol" aria-hidden="true">{done ? '✓' : '▧'}</span><div><strong>{title}</strong><span>{subject}</span></div><small>{minutes}</small></div></div>)}
    <p className="landing-preview-caption">Bạn chọn việc để bắt đầu. Kế hoạch giữ mọi thứ rõ ràng.</p>
  </div>
}
function BudgetPreview() {
  return <div className="landing-product-preview landing-budget-preview"><PreviewLabel>THỜI GIAN CHO VIỆC HỌC</PreviewLabel><h3>Vừa với một ngày của bạn.</h3>
    <div className="landing-budget-total"><strong>120 <span>phút / ngày</span></strong><span>Quỹ chung</span></div>
    <div className="landing-budget-track" aria-label="Minh họa 90 trong 120 phút được xếp"><span /><span /><span /></div>
    <ul className="landing-budget-legend"><li><i />Kiểm thử phần mềm <strong>60 phút</strong></li><li><i />Quản trị mạng <strong>30 phút</strong></li><li><i />Còn trống <strong>30 phút</strong></li></ul>
    <div className="landing-budget-note"><span aria-hidden="true">✓</span><p>Nhiều môn, một quỹ thời gian.<br /><small>Phần chưa đủ chỗ được giữ để bạn điều chỉnh.</small></p></div>
  </div>
}
function ProgressPreview() {
  return <div className="landing-product-preview landing-progress-preview"><PreviewLabel>NHỊP HỌC TRONG TUẦN</PreviewLabel>
    <div className="landing-progress-top"><div><p>Từng việc nhỏ đã hoàn thành</p><strong>5<span> / 8 việc</span></strong></div><span className="landing-progress-seal" aria-hidden="true">↗</span></div>
    <div className="landing-progress-bars" aria-label="Biểu đồ tiến độ minh họa theo ngày">{[60, 100, 40, 80, 65, 0, 0].map((height, i) => <div key={i}><div><span style={{ height: `${height}%` }} /></div><small>{['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'][i]}</small></div>)}</div>
    <p className="landing-preview-caption">Đếm việc đã hoàn thành, không đánh giá mức độ hiểu bài.</p>
  </div>
}
function HeroShowcase() {
  const [slide, setSlide] = useState(0)
  const [playing, setPlaying] = useState(true)
  const [paused, setPaused] = useState(false)
  const [reducedMotion, setReducedMotion] = useState(true)
  const titles = ['Nội dung học tập', 'Lịch học theo tuần', 'Tiến độ học tập']
  useEffect(() => {
    const query = window.matchMedia('(prefers-reduced-motion: reduce)')
    const update = () => setReducedMotion(query.matches)
    update(); query.addEventListener('change', update)
    return () => query.removeEventListener('change', update)
  }, [])
  useEffect(() => {
    if (!playing || paused || reducedMotion) return
    const timer = setInterval(() => {
      if (document.visibilityState === 'visible') setSlide(value => (value + 1) % 3)
    }, 6500)
    return () => clearInterval(timer)
  }, [playing, paused, reducedMotion])
  function choose(value) { setPlaying(false); setSlide((value + 3) % 3) }
  return <div className="landing-hero-art" role="region" aria-roledescription="trình chiếu" aria-label="Khám phá Study Planner"
    onMouseEnter={() => setPaused(true)} onMouseLeave={() => setPaused(false)}
    onFocusCapture={() => setPaused(true)} onBlurCapture={event => { if (!event.currentTarget.contains(event.relatedTarget)) setPaused(false) }}>
    <div className="landing-hero-stage" aria-live={playing && !paused && !reducedMotion ? 'off' : 'polite'}>
      <div className="landing-hero-slide" key={slide} role="group" aria-roledescription="cảnh" aria-label={`${slide + 1} / 3: ${titles[slide]}`}>
        {slide === 0 ? <><img className="landing-still-life" src={studyStillLife} width="1254" height="1254" fetchPriority="high" alt="Sách xanh rêu, sổ mở và nhành lá trên nền kem" /><div className="landing-floating-note"><span className="landing-note-icon" aria-hidden="true">▧</span><div><strong>Kế hoạch ôn tập</strong><p>Đề cương · Chủ đề · Ngày thi</p></div></div></> : slide === 1 ? <SchedulePreview /> : <ProgressPreview />}
      </div>
    </div>
    <div className="landing-carousel-controls"><button type="button" onClick={() => choose(slide - 1)} aria-label="Cảnh trước">‹</button><div className="landing-carousel-dots">{titles.map((title, i) => <button type="button" key={title} className={slide === i ? 'landing-dot-active' : ''} aria-label={`Xem ${title.toLowerCase()}`} aria-current={slide === i ? 'true' : undefined} onClick={() => choose(i)}><span /></button>)}</div><button type="button" onClick={() => choose(slide + 1)} aria-label="Cảnh tiếp theo">›</button>{!reducedMotion && <button type="button" className="landing-carousel-play" onClick={() => setPlaying(value => !value)} aria-label={playing ? 'Tạm dừng chuyển cảnh tự động' : 'Bật chuyển cảnh tự động'}>{playing ? 'Ⅱ' : '▷'}</button>}</div>
  </div>
}
export default function LandingPage({ currentUser, checkingAuth, onStart, onLogin, onWorkspace }) {
  const [menuOpen, setMenuOpen] = useState(false)
  const links = [['#landing-steps', 'Cách bắt đầu'], ['#landing-features', 'Tính năng'], ['#landing-faq', 'Câu hỏi thường gặp']]
  const startButton = (label = 'Bắt đầu lập kế hoạch') => <button type="button" className="button button--primary landing-cta" disabled={checkingAuth} onClick={onStart}>{label}<span aria-hidden="true">↗</span></button>
  const brand = <><span className="landing-brand-mark" aria-hidden="true">s<span>✦</span></span><span>study<b>planner</b></span></>
  return <div className="landing-page">
    <a className="skip-link" href="#landing-main">Đến nội dung chính</a>
    <header className="landing-nav"><div className="landing-nav-inner"><a className="landing-brand" href="#landing" aria-label="Study Planner — trang chủ">{brand}</a>
      <nav className="landing-desktop-nav" aria-label="Điều hướng giới thiệu">{links.map(([href, label]) => <a key={href} href={href}>{label}</a>)}</nav>
      <div className="landing-nav-actions"><button type="button" className="landing-login" disabled={checkingAuth} onClick={currentUser ? onWorkspace : onLogin}>{currentUser ? 'Về Tổng quan' : 'Đăng nhập'}</button><button type="button" className="landing-menu-button" aria-label="Menu giới thiệu" aria-expanded={menuOpen} aria-controls="landing-mobile-nav" onClick={() => setMenuOpen(open => !open)}>{menuOpen ? '✕' : '☰'}</button></div>
      </div>{menuOpen && <nav className="landing-mobile-nav" id="landing-mobile-nav" aria-label="Điều hướng giới thiệu trên điện thoại">{links.map(([href, label]) => <a key={href} href={href} onClick={() => setMenuOpen(false)}>{label}</a>)}</nav>}
    </header>
    <main id="landing-main" className="landing-main" tabIndex={-1}>
      <section className="landing-hero landing-container" aria-labelledby="landing-title">
        <div className="landing-intro"><p className="landing-eyebrow"><span aria-hidden="true">✦</span> MỘT KẾ HOẠCH. NHẸ HƠN MỖI NGÀY.</p><h1 id="landing-title">Biết hôm nay học gì.<br /><em>An tâm hơn</em><br />trước ngày thi.</h1><p className="landing-description">Biến đề cương, ngày thi và thời gian bạn có thành những việc học vừa sức. Bớt loay hoay lập kế hoạch, thêm thời gian cho điều cần học.</p><div className="landing-actions">{startButton()}<a className="landing-secondary-link" href="#landing-steps">Khám phá cách học <span aria-hidden="true">↓</span></a></div></div>
        <HeroShowcase />
      </section>
      <div className="landing-path landing-container" aria-label="Quy trình học"><span>Đề cương &amp; ngày thi</span><i aria-hidden="true">→</i><span>Kế hoạch của bạn</span><i aria-hidden="true">→</i><span>Việc học hôm nay</span><i aria-hidden="true">→</i><span>Tiến độ mỗi tuần</span></div>
      <section id="landing-steps" className="landing-steps landing-container" aria-labelledby="landing-how"><div className="landing-section-heading"><p className="landing-eyebrow">TỪ ĐỀ CƯƠNG ĐẾN MỘT KẾ HOẠCH RÕ RÀNG</p><h2 id="landing-how">Bắt đầu đơn giản.<br />Đi từng bước vừa sức.</h2><p>Mọi thứ bạn cần để bắt đầu một buổi học, thay vì nghĩ mãi nên học gì.</p></div><div className="landing-step-grid">{steps.map(([number, title, description]) => <article key={number}><span className="landing-step-number">{number}</span><h3>{title}</h3><p>{description}</p><div className="landing-step-motif" aria-hidden="true">{number === '01' ? <><span>Chủ đề ôn tập</span><i /><i /><i /></> : number === '02' ? <><span>Đề xuất → Bạn xác nhận</span><div>◷ <b>Vừa quỹ thời gian</b></div></> : <><span>Một việc đã hoàn thành</span><div>✓ <b>Tiến thêm một chút</b></div></>}</div></article>)}</div></section>
      <section id="landing-features" className="landing-features" aria-label="Những điều giúp bạn học có kế hoạch"><div className="landing-container">
        <article className="landing-feature-row"><div className="landing-feature-copy"><p className="landing-eyebrow">LỊCH HỌC MỖI NGÀY</p><h2>Mở lịch.<br />Rõ việc tiếp theo.</h2><p>Nhìn thấy môn học, nội dung và thời lượng của từng việc trong tuần. Bạn có thể sửa, dời ngày học hoặc đánh dấu hoàn thành khi đã xong.</p><a className="landing-secondary-link" href="#landing-steps">Từ đề cương đến lịch học <span aria-hidden="true">↗</span></a></div><SchedulePreview /></article>
        <article className="landing-feature-row landing-feature-row--reverse"><div className="landing-feature-copy"><p className="landing-eyebrow">THỜI GIAN CÓ GIỚI HẠN. KẾ HOẠCH CŨNG VẬY.</p><h2>Dành chỗ cho việc học.<br />Và cả cuộc sống.</h2><p>Đặt quỹ thời gian chung mỗi ngày cho tất cả các môn. Kế hoạch tôn trọng giới hạn đó và cho bạn thấy việc nào chưa đủ chỗ để chủ động điều chỉnh.</p><ul><li>Chọn mức mong muốn cho từng môn.</li><li>Xem rõ phần vượt trước khi đồng ý.</li><li>Giữ lại việc chưa xếp để xử lý tiếp.</li></ul></div><BudgetPreview /></article>
        <article className="landing-feature-row"><div className="landing-feature-copy"><p className="landing-eyebrow">NHÌN LẠI NHỮNG GÌ BẠN ĐÃ LÀM</p><h2>Tiến bộ bắt đầu<br />từ những việc nhỏ.</h2><p>Xem số việc đã hoàn thành theo từng ngày và từng tuần. Không cần đoán mình đã đi được bao xa trong kế hoạch.</p><p className="landing-feature-aside">Mỗi lần đánh dấu hoàn thành là một bước bạn đã thực sự làm.</p></div><ProgressPreview /></article>
      </div></section>
      <section id="landing-faq" className="landing-faq landing-container" aria-labelledby="landing-faq-title"><div className="landing-section-heading"><p className="landing-eyebrow">TRƯỚC KHI BẠN BẮT ĐẦU</p><h2 id="landing-faq-title">Có thể bạn đang thắc mắc.</h2></div><div className="landing-faq-list">{faqs.map(([question, answer]) => <details key={question}><summary>{question}<span aria-hidden="true">+</span></summary><p>{answer}</p></details>)}</div></section>
      <section className="landing-final landing-container"><div className="landing-final-inner"><span className="landing-final-spark" aria-hidden="true">✦</span><p className="landing-eyebrow">DÀNH MỘT KHOẢNG NHỎ CHO MỤC TIÊU CỦA BẠN</p><h2>Buổi học tiếp theo,<br /><em>bắt đầu rõ ràng hơn.</em></h2><p>Mang theo đề cương và thời gian bạn có.<br />Cùng biến chúng thành một kế hoạch có thể bắt đầu.</p>{startButton(currentUser ? 'Mở mục tiêu của tôi' : 'Tạo kế hoạch đầu tiên')}</div></section>
    </main>
    <footer className="landing-footer landing-container"><div><a className="landing-brand" href="#landing">{brand}</a><p>Học có kế hoạch. Từng chút mỗi ngày.</p></div><nav aria-label="Điều hướng cuối trang">{links.map(([href, label]) => <a key={href} href={href}>{label}</a>)}<button type="button" disabled={checkingAuth} onClick={currentUser ? onWorkspace : onLogin}>{currentUser ? 'Không gian học tập ↗' : 'Đăng nhập ↗'}</button></nav><div className="landing-footer-bottom"><span>AI Study Planner</span><a href="#landing-faq">Trợ giúp</a></div></footer>
  </div>
}
