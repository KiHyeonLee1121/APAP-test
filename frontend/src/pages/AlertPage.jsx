import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

import '../styles/AlertPage.css';
import logo from '../assets/png/APAP로고.png';

function AlertPage() {
  const navigate = useNavigate();

  const [alerts, setAlerts] = useState([
    '한강 철교 위 난간에 매달린 사람 발견',
    '한강 철교 위 뛰어내린 사람 발견',
    '한강 철교 무단투기하는 사람 발견',
    '한강 철교 위 같은 자리 배회하는 사람 발견',
    '한강 철교 난간에 손 올린 사람 발견',
    'ATM 앞에서 장시간 통화하며 송금하는 사람 발견',
    '횡단보도 위 쓰러진 사람 발견',
  ]);

  const [selectedAlerts, setSelectedAlerts] = useState([]);

  const toggleSelect = (index) => {
    if (selectedAlerts.includes(index)) {
      setSelectedAlerts(selectedAlerts.filter((item) => item !== index));
    } else {
      setSelectedAlerts([...selectedAlerts, index]);
    }
  };

  const deleteSelected = () => {
    setAlerts(alerts.filter((_, index) => !selectedAlerts.includes(index)));

    setSelectedAlerts([]);
  };

  const deleteAll = () => {
    setAlerts([]);
    setSelectedAlerts([]);
  };

  return (
    <div className="alert-container">
      <div className="top-bar">
        <button className="back-button" onClick={() => navigate('/main')}>
          ←
        </button>

        <img src={logo} alt="APAP" className="top-logo" />
      </div>

      <div className="alert-wrapper">
        <div className="alert-header">
          <h1>알림 내역</h1>

          <div className="alert-buttons">
            <button onClick={deleteAll}>전체 삭제</button>

            <button onClick={deleteSelected}>선택 삭제</button>
          </div>
        </div>

        <div className="alert-list">
          {alerts.map((alert, index) => (
            <div
              key={index}
              className={`alert-item ${
                selectedAlerts.includes(index) ? 'selected' : ''
              }`}
              onClick={() => toggleSelect(index)}
            >
              {alert}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

export default AlertPage;
