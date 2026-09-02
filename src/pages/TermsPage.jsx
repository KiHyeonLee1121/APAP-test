import { useNavigate } from 'react-router-dom';

import '../styles/TermsPage.css';
import logo from '../assets/png/APAP로고.png';

const termsSections = [
  {
    title: '제1조 목적',
    content:
      '본 약관은 APAP CCTV 기반 이상행동 알림 플랫폼의 영상 업로드, 실시간 영상 확인, AI 분석, 알림 제공 기능 이용 조건과 절차를 정합니다.',
  },
  {
    title: '제2조 영상정보 이용 범위',
    content:
      '이용자가 업로드하거나 등록한 CCTV 영상은 이상행동 탐지, 분석 결과 생성, 알림 제공 및 서비스 품질 개선 목적 범위 안에서 처리됩니다.',
  },
  {
    title: '제3조 영상 저장 및 관리',
    content:
      '저장된 영상은 등록 시점, 제목, 작성자, 처리 상태와 함께 관리되며, 서비스 운영에 필요한 기간 동안 보관될 수 있습니다.',
  },
  {
    title: '제4조 접근 권한',
    content:
      '영상과 분석 결과는 로그인한 이용자와 권한이 부여된 관리자만 접근할 수 있으며, 이용자는 계정 정보를 안전하게 관리해야 합니다.',
  },
  {
    title: '제5조 이용자 책임',
    content:
      '이용자는 적법하게 확보한 영상만 업로드해야 하며, 타인의 초상권, 개인정보, 관련 법령을 침해하지 않도록 주의해야 합니다.',
  },
  {
    title: '제6조 분석 결과 안내',
    content:
      'AI 분석 결과는 이상행동 판단을 보조하기 위한 정보이며, 실제 상황 판단과 후속 조치는 이용자 또는 관리자의 확인을 거쳐야 합니다.',
  },
];

function TermsPage() {
  const navigate = useNavigate();

  return (
    <div className="terms-container">
      <div className="top-bar">
        <button className="back-button" onClick={() => navigate('/main')}>
          ←
        </button>

        <img src={logo} alt="APAP" className="top-logo" />
      </div>

      <main className="terms-wrapper">
        <header className="terms-header">
          <h1>CCTV 관련 이용약관</h1>
          <p>APAP 서비스의 영상정보 처리와 분석 기능 이용 기준입니다.</p>
        </header>

        <div className="terms-content">
          {termsSections.map((section) => (
            <section className="terms-section" key={section.title}>
              <h2>{section.title}</h2>
              <p>{section.content}</p>
            </section>
          ))}
        </div>
      </main>
    </div>
  );
}

export default TermsPage;
