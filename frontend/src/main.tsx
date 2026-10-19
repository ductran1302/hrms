import { App as AntApp, ConfigProvider, theme as antdTheme } from 'antd';
import enUS from 'antd/locale/en_US';
import viVN from 'antd/locale/vi_VN';
import dayjs from 'dayjs';
import 'dayjs/locale/en';
import 'dayjs/locale/vi';
import { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { AuthProvider } from './auth/AuthContext';
import i18n from './i18n';
import { ThemeProvider, useTheme } from './theme/ThemeContext';
import './index.css';

const container = document.getElementById('root');
if (!container) throw new Error('Thiếu #root trong index.html');

/** Đổi antd locale + dayjs + lang/title theo i18n language; đổi algorithm theo theme. */
function RootInner() {
  const [lang, setLang] = useState(i18n.language === 'en' ? 'en' : 'vi');
  const { theme: themeMode } = useTheme();

  useEffect(() => {
    const onLanguageChanged = (value: string) => setLang(value === 'en' ? 'en' : 'vi');
    i18n.on('languageChanged', onLanguageChanged);
    return () => {
      i18n.off('languageChanged', onLanguageChanged);
    };
  }, []);

  useEffect(() => {
    dayjs.locale(lang);
    document.documentElement.lang = lang;
    document.title = lang === 'vi' ? 'HRMS — Quản lý nhân sự' : 'HRMS — Human Resources';
  }, [lang]);

  return (
    <ConfigProvider
      locale={lang === 'vi' ? viVN : enUS}
      theme={{
        algorithm: themeMode === 'dark' ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
      }}
    >
      <AntApp>
        <AuthProvider>
          <App />
        </AuthProvider>
      </AntApp>
    </ConfigProvider>
  );
}

function Root() {
  return (
    <ThemeProvider>
      <RootInner />
    </ThemeProvider>
  );
}

createRoot(container).render(<Root />);
