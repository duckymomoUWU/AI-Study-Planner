import './LandingPage.css'

export default function LandingPage({ currentUser, checkingAuth, onStart, onLogin, onWorkspace }) {
  return <div className="landing-page">
    <a className="skip-link" href="#landing-main">Đến nội dung chính</a>
    <header className="landing-nav">
      <a className="landing-brand" href="#landing" aria-label="Study Planner — trang chủ"><span aria-hidden="true">s✦</span>study<span>planner</span></a>
      <nav aria-label="Điều hướng giới thiệu"><a href="#landing-steps">Cách bắt đầu</a><button className="button button--secondary" disabled={checkingAuth} onClick={currentUser ? onWorkspace : onLogin}>{currentUser ? 'Không gian học tập ↗' : 'Đăng nhập'}</button></nav>
    </header>
    <main id="landing-main" className="landing-main" tabIndex={-1}>
      <section className="landing-hero" aria-labelledby="landing-title">
        <div className="landing-intro"><p className="eyebrow">HỌC CÓ KẾ HOẠCH</p><h1 id="landing-title">Để việc ôn tập<br />nhẹ hơn <span>mỗi ngày.</span></h1><p className="landing-description">Từ đề cương đến những việc vừa sức. Sắp xếp điều cần học trước ngày thi và biết hôm nay mình bắt đầu từ đâu.</p><div className="landing-actions"><button className="button button--primary" disabled={checkingAuth} onClick={onStart}>Bắt đầu lập kế hoạch <span aria-hidden="true">↗</span></button><a href="#landing-steps">Tìm hiểu cách học <span aria-hidden="true">↓</span></a></div><p className="landing-fine">AI đề xuất kế hoạch. Bạn xem, điều chỉnh và quyết định lưu.</p></div>
        <div className="landing-illustration" aria-label="Minh họa các bước lập kế hoạch, không phải dữ liệu tài khoản">
          <div className="landing-paper"><div className="landing-paper-top"><span>MỘT KẾ HOẠCH RÕ RÀNG</span><span aria-hidden="true">✦</span></div><h2>Từng bước nhỏ.<br />Đến gần mục tiêu hơn.</h2><p>Đề cương của bạn</p><div className="landing-document" aria-hidden="true"><i /><i /><i /></div><div className="landing-flow" aria-hidden="true">↓</div><div className="landing-preview-row"><span aria-hidden="true">01</span><div><strong>Hiểu điều cần học</strong><small>Xác nhận chủ đề từ tài liệu</small></div></div><div className="landing-preview-row"><span aria-hidden="true">02</span><div><strong>Chia thành việc vừa sức</strong><small>Rà lại kế hoạch theo quỹ thời gian</small></div></div><div className="landing-preview-row"><span aria-hidden="true">03</span><div><strong>Bắt đầu một việc hôm nay</strong><small>Đánh dấu khi bạn đã hoàn thành</small></div></div><span className="landing-paper-caption">Cách kế hoạch được hình thành</span></div>
          <div className="landing-note"><span aria-hidden="true">↗</span> Không cần học hết trong một ngày.</div>
        </div>
      </section>
      <section id="landing-steps" className="landing-steps" aria-labelledby="landing-how"><div className="landing-section-heading"><p className="eyebrow">BẮT ĐẦU TỪ ĐIỀU BẠN CÓ</p><h2 id="landing-how">Một đề cương.<br />Một hướng đi rõ ràng.</h2><p>Ít thời gian loay hoay hơn. Nhiều khoảng trống cho việc học hơn.</p></div><div className="landing-step-grid">{[
        ['01', 'Mang nội dung cần học vào', 'Tải đề cương hoặc nhập chủ đề. Kiểm tra nội dung, ngày thi và thời gian bạn có thể dành mỗi ngày.'],
        ['02', 'Duyệt một kế hoạch vừa sức', 'Xem đề xuất AI, chỉnh công việc và thời lượng. Phần chưa đủ thời gian được giữ lại để bạn cân nhắc.'],
        ['03', 'Học từng việc mỗi ngày', 'Mở lịch, chọn việc cần làm và đánh dấu hoàn thành. Theo dõi nhịp học qua tiến độ trong tuần.'],
      ].map(([number, title, description]) => <article key={number}><span className="landing-step-number">{number}</span><h3>{title}</h3><p>{description}</p></article>)}</div></section>
      <section className="landing-principles"><p className="eyebrow">KẾ HOẠCH THUỘC VỀ BẠN</p><h2>Vừa với thời gian.<br />Rõ việc cần làm tiếp.</h2><p>Đặt một quỹ thời gian chung cho các môn. Chủ động sửa kế hoạch khi cần. Lịch chỉ được thêm sau khi bạn xác nhận.</p><button className="button button--primary" disabled={checkingAuth} onClick={onStart}>Tạo kế hoạch đầu tiên ↗</button></section>
    </main><footer className="landing-footer"><strong>studyplanner</strong><span>Một kế hoạch rõ ràng. Một khởi đầu nhẹ nhàng.</span><button type="button" onClick={currentUser ? onWorkspace : onLogin}>{currentUser ? 'Về Tổng quan' : 'Đăng nhập'}</button></footer>
  </div>
}
