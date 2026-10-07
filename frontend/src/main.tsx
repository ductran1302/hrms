import { App as AntApp, ConfigProvider } from 'antd';
import viVN from 'antd/locale/vi_VN';
import { createRoot } from 'react-dom/client';
import App from './App';
import { AuthProvider } from './auth/AuthContext';
import './index.css';

const container = document.getElementById('root');
if (!container) throw new Error('Thiếu #root trong index.html');

document.title = 'HRMS — Quản lý nhân sự';

createRoot(container).render(
  <ConfigProvider locale={viVN}>
    <AntApp>
      <AuthProvider>
        <App />
      </AuthProvider>
    </AntApp>
  </ConfigProvider>,
);
