import './LandingPage.css'

const repoUrl = 'https://github.com/duckymomoUWU/AI-Study-Planner'

export default function LandingPage({ currentUser, onLogin, onStart }) {
  return (
    <div className="landing-container">
      {/* Public Header */}
      <header className="landing-header" role="banner">
        <div className="landing-header-inner">
          <a
            href="#landing"
            className="landing-brand"
            aria-label="AI Study Planner — Trang chủ"
            onClick={e => {
              e.preventDefault()
              window.scrollTo({ top: 0, behavior: 'smooth' })
            }}
          >
            <span className="landing-brand-mark" aria-hidden="true">
              s<span>✦</span>
            </span>
            <div className="landing-brand-text">
              <span className="landing-brand-title">
                study<span>planner</span>
              </span>
              <span className="landing-brand-tagline">Học có kế hoạch</span>
            </div>
          </a>

          <nav className="landing-nav" aria-label="Điều hướng trang giới thiệu">
            <a href="#workflow" className="landing-nav-link">
              3 bước học tập
            </a>
            <a href="#features" className="landing-nav-link">
              Tính năng
            </a>
            <a href="#philosophy" className="landing-nav-link">
              Nguyên tắc
            </a>
          </nav>

          <div className="landing-header-actions">
            {currentUser ? (
              <>
                <span className="landing-user-badge" title={`Tài khoản: ${currentUser.email}`}>
                  <span>👤</span> {currentUser.email}
                </span>
                <button
                  type="button"
                  className="landing-btn-primary"
                  onClick={onStart}
                  style={{ minHeight: '40px', padding: '8px 18px', fontSize: '14px' }}
                >
                  Vào không gian học ↗
                </button>
              </>
            ) : (
              <>
                <button
                  type="button"
                  className="landing-btn-secondary"
                  onClick={onLogin}
                  style={{ minHeight: '40px', padding: '8px 16px', fontSize: '14px' }}
                >
                  Đăng nhập
                </button>
                <button
                  type="button"
                  className="landing-btn-primary"
                  onClick={onStart}
                  style={{ minHeight: '40px', padding: '8px 18px', fontSize: '14px' }}
                >
                  Bắt đầu lập kế hoạch
                </button>
              </>
            )}
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main id="main" className="landing-main" tabIndex={-1}>
        {/* Hero Section */}
        <section className="landing-hero" aria-labelledby="hero-title">
          <div className="landing-hero-content">
            <span className="landing-eyebrow">✦ Kế hoạch học tập trung thực · Bền vững</span>
            <h1 id="hero-title" className="landing-title">
              Biến đề cương thành kế hoạch học tập mỗi ngày.
            </h1>
            <p className="landing-subtitle">
              Lập lộ trình ôn thi bám sát đề cương, cân đối quỹ thời gian thực tế mỗi ngày, rèn luyện sự tập trung với
              đồng hồ Pomodoro và củng cố kiến thức bằng Active Recall.
            </p>

            <div className="landing-actions">
              <button
                type="button"
                className="landing-btn-primary"
                onClick={onStart}
              >
                {currentUser ? 'Đi tới mục tiêu của tôi ↗' : 'Bắt đầu lập kế hoạch ↗'}
              </button>
              {!currentUser && (
                <button
                  type="button"
                  className="landing-btn-secondary"
                  onClick={onLogin}
                >
                  Đăng nhập tài khoản
                </button>
              )}
            </div>

            <ul className="landing-values-list" aria-label="Điểm cốt lõi của giải pháp">
              <li className="landing-value-item">
                <span className="landing-value-check" aria-hidden="true">✓</span>
                <span>AI phân tích cấu trúc đề cương; bạn luôn là người duyệt và chỉnh sửa cuối cùng.</span>
              </li>
              <li className="landing-value-item">
                <span className="landing-value-check" aria-hidden="true">✓</span>
                <span>Thời gian học tính theo giây thực tế trên máy chủ, không ghi nhận thời gian ảo.</span>
              </li>
              <li className="landing-value-item">
                <span className="landing-value-check" aria-hidden="true">✓</span>
                <span>Ôn tập chủ động bằng flashcard trích xuất minh bạch từ chính giáo trình của bạn.</span>
              </li>
            </ul>
          </div>

          {/* Interactive Hero Showcase */}
          <div className="landing-hero-card" aria-hidden="true">
            <div className="landing-hero-card-header">
              <span className="landing-card-pill">✦ Phiên học Pomodoro</span>
              <span style={{ fontSize: '13px', color: 'var(--muted)' }}>25 phút tiêu chuẩn</span>
            </div>
            <div className="landing-card-mock-timer">
              <div className="landing-mock-clock">25:00</div>
              <div className="landing-mock-task">Ôn tập kiểm thử hộp đen & hộp trắng</div>
              <div className="landing-mock-subject">Môn học: Kiểm tra chất lượng phần mềm</div>
            </div>
            <div style={{ marginTop: '20px', borderTop: '1px solid var(--line)', paddingTop: '16px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', color: 'var(--muted)' }}>
                <span>Trạng thái máy chủ: Sẵn sàng</span>
                <span>Nghỉ giải lao: 5 phút</span>
              </div>
            </div>
          </div>
        </section>

        {/* Workflow Section */}
        <section id="workflow" className="landing-section" aria-labelledby="workflow-title">
          <div className="landing-section-header">
            <span className="landing-eyebrow">QUY TRÌNH 3 BƯỚC</span>
            <h2 id="workflow-title" className="landing-section-title">
              Rõ ràng từng bước, chủ động từng ngày
            </h2>
            <p className="landing-section-desc">
              Không phức tạp hóa việc học. Chỉ 3 bước đơn giản để biến tài liệu ôn tập thành thói quen mỗi ngày.
            </p>
          </div>

          <div className="landing-steps-grid">
            <article className="landing-step-card">
              <div className="landing-step-num" aria-hidden="true">01</div>
              <h3 className="landing-step-title">Tải đề cương môn học</h3>
              <p className="landing-step-desc">
                Tải lên tệp đề cương giáo trình (PDF, DOCX) hoặc dán trực tiếp danh sách nội dung cần ôn. Hệ thống trích
                xuất các phần trọng tâm mà không tự ý lưu trữ dữ liệu cá nhân ngoài phạm vi bài học.
              </p>
            </article>

            <article className="landing-step-card">
              <div className="landing-step-num" aria-hidden="true">02</div>
              <h3 className="landing-step-title">Duyệt và cân đối kế hoạch</h3>
              <p className="landing-step-desc">
                Nhập ngày thi và quỹ thời gian tối đa bạn có thể học mỗi ngày. Xem bản phác thảo lịch học, tùy ý tinh chỉnh
                thời lượng và ngày học trước khi xác nhận lưu.
              </p>
            </article>

            <article className="landing-step-card">
              <div className="landing-step-num" aria-hidden="true">03</div>
              <h3 className="landing-step-title">Học tập trung mỗi ngày</h3>
              <p className="landing-step-desc">
                Bật đồng hồ Pomodoro 25 phút để bắt đầu phiên học, nghỉ giải lao 5 phút và ôn tập củng cố với bộ thẻ ghi
                nhớ flashcard được tạo từ chính bài học của bạn.
              </p>
            </article>
          </div>
        </section>

        {/* Features Section */}
        <section id="features" className="landing-section" aria-labelledby="features-title">
          <div className="landing-section-header">
            <span className="landing-eyebrow">TÍNH NĂNG ĐỒNG HÀNH</span>
            <h2 id="features-title" className="landing-section-title">
              Công cụ thiết thực cho việc tự học
            </h2>
            <p className="landing-section-desc">
              Được thiết kế tinh gọn theo phương pháp học tập khoa học đã được kiểm chứng.
            </p>
          </div>

          <div className="landing-features-grid">
            <article className="landing-feature-card">
              <div className="landing-feature-header">
                <span className="landing-card-pill">TẬP TRUNG TỐI ĐA</span>
                <span style={{ fontSize: '13px', color: 'var(--muted)' }}>TMR-UI-01</span>
              </div>
              <h3 className="landing-feature-title">Đồng hồ Pomodoro lưu thời gian thực</h3>
              <p className="landing-feature-desc">
                Phiên học 25 phút với đồng hồ số rõ ràng, tự động giữ trạng thái khi tải lại trang hoặc đổi tab. Thời gian
                học thực tế do máy chủ tính toán, bảo đảm số liệu tích lũy phản ánh đúng công sức bạn bỏ ra.
              </p>
              <ul className="landing-feature-bullets">
                <li><span>✦</span> Chu kỳ 25 phút học chuẩn Pomodoro</li>
                <li><span>✦</span> Tạm dừng, tiếp tục linh hoạt không mất phiên</li>
                <li><span>✦</span> Đồng hồ nghỉ giải lao 5 phút độc lập trên giao diện</li>
              </ul>
            </article>

            <article className="landing-feature-card">
              <div className="landing-feature-header">
                <span className="landing-card-pill">GHI NHỚ CHỦ ĐỘNG</span>
                <span style={{ fontSize: '13px', color: 'var(--muted)' }}>AR-UI-01</span>
              </div>
              <h3 className="landing-feature-title">Active Recall với trích dẫn minh bạch</h3>
              <p className="landing-feature-desc">
                Tạo bộ thẻ ghi nhớ trực tiếp từ tài liệu bạn cung cấp. Mỗi câu hỏi đều đi kèm trích dẫn văn bản gốc, giúp bạn
                tự đánh giá trung thực mức độ nắm bắt kiến thức thay vì trông cậy vào điểm số ảo.
              </p>
              <ul className="landing-feature-bullets">
                <li><span>✦</span> Thẻ câu hỏi và đáp án từ chính giáo trình</li>
                <li><span>✦</span> Trích dẫn số trang và đoạn văn nguồn rõ ràng</li>
                <li><span>✦</span> Tự đánh giá trung thực: Tôi nhớ / Chưa nhớ</li>
              </ul>
            </article>
          </div>
        </section>

        {/* Philosophy Section */}
        <section id="philosophy" className="landing-section" aria-labelledby="philosophy-title">
          <div className="landing-philosophy-card">
            <div className="landing-philosophy-content">
              <span className="landing-eyebrow">TRIẾT LÝ PHÁT TRIỂN</span>
              <h3 id="philosophy-title">Một kế hoạch rõ ràng. Một khởi đầu nhẹ nhàng.</h3>
              <p>
                Chúng tôi không đưa ra các số liệu người dùng tự bịa hay hứa hẹn điểm số thần kỳ. Thành quả học tập đến
                từ sự kiên trì thực tế và một lộ trình phù hợp với năng lực của bạn.
              </p>
            </div>
            <div className="landing-philosophy-box">
              <h4>AI hỗ trợ, bạn quyết định</h4>
              <p>
                Hệ thống chỉ đóng vai trò trợ lý tổ chức và gợi ý cấu trúc. Bạn luôn là người quyết định cuối cùng về
                khối lượng học, mục tiêu bài vở và thời gian biểu của chính mình.
              </p>
            </div>
          </div>
        </section>

        {/* Bottom CTA Banner */}
        <section className="landing-cta-banner" aria-labelledby="cta-banner-title">
          <h2 id="cta-banner-title">Sẵn sàng bắt đầu kỳ học với tâm thế chủ động?</h2>
          <p>
            Tạo tài khoản và thiết lập kế hoạch học tập đầu tiên của bạn chỉ trong vài phút. Hoàn toàn miễn phí và không
            ràng buộc.
          </p>
          <button
            type="button"
            className="landing-btn-primary"
            onClick={onStart}
          >
            {currentUser ? 'Đi tới mục tiêu của tôi ↗' : 'Bắt đầu lập kế hoạch ngay ↗'}
          </button>
        </section>
      </main>

      {/* Footer */}
      <footer className="landing-footer" role="contentinfo">
        <div className="landing-footer-inner">
          <div className="landing-footer-left">
            <strong>AI Study Planner</strong> · Học có kế hoạch · 2026.
          </div>
          <nav aria-label="Liên kết chân trang">
            <ul className="landing-footer-links">
              <li>
                <a href={repoUrl} target="_blank" rel="noreferrer">
                  Mã nguồn GitHub
                </a>
              </li>
              {!currentUser && (
                <li>
                  <button type="button" onClick={onLogin}>
                    Đăng nhập
                  </button>
                </li>
              )}
            </ul>
          </nav>
        </div>
      </footer>
    </div>
  )
}

