import {
  ApartmentOutlined,
  BellOutlined,
  CarryOutOutlined,
  ClockCircleOutlined,
  ClusterOutlined,
  DownOutlined,
  FileTextOutlined,
  GiftOutlined,
  LogoutOutlined,
  MoonOutlined,
  ScheduleOutlined,
  SearchOutlined,
  SettingOutlined,
  SunOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Avatar, Badge, Button, Dropdown, Input, Layout, Menu } from 'antd';
import { useEffect, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { LANGUAGES, setLanguage } from '../i18n';
import { useTheme } from '../theme/ThemeContext';

const { Header, Sider, Content } = Layout;

type NavChild = { key: string; label: string };
type NavItem = { key: string; icon?: ReactNode; label: string; children?: NavChild[] };

/** Logo kim cương gradient (SVG inline) — giống mockup GlobalTech */
function BrandMark() {
  return (
    <svg width="34" height="34" viewBox="0 0 40 40" aria-hidden="true">
      <defs>
        <linearGradient id="brand-grad" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#36cfc9" />
          <stop offset="50%" stopColor="#1677ff" />
          <stop offset="100%" stopColor="#722ed1" />
        </linearGradient>
      </defs>
      <path d="M20 2 L36 20 L20 38 L4 20 Z" fill="url(#brand-grad)" />
      <path d="M20 10 L29 20 L20 30 L11 20 Z" fill="#fff" opacity="0.9" />
      <path d="M20 2 L36 20 L20 20 Z" fill="#fff" opacity="0.25" />
    </svg>
  );
}

const FLAG_VI = (
  <svg width="18" height="13" viewBox="0 0 18 13" aria-hidden="true">
    <rect width="18" height="13" rx="2" fill="#da251d" />
    <path
      d="M9 2.2l1.2 3.7h3.9l-3.1 2.3 1.2 3.7L9 9.6l-3.2 2.3 1.2-3.7-3.1-2.3h3.9z"
      fill="#ff0"
    />
  </svg>
);

/** Cờ Union Jack (Anh) cho ngôn ngữ EN */
const FLAG_EN = (
  <svg width="18" height="13" viewBox="0 0 60 40" aria-hidden="true">
    <rect width="60" height="40" fill="#012169" />
    <path d="M0 0l60 40M60 0L0 40" stroke="#fff" strokeWidth="8" />
    <path d="M0 0l60 40M60 0L0 40" stroke="#C8102E" strokeWidth="4" />
    <path d="M30 0v40M0 20h60" stroke="#fff" strokeWidth="14" />
    <path d="M30 0v40M0 20h60" stroke="#C8102E" strokeWidth="8" />
  </svg>
);

export default function AppLayout() {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const { t, i18n } = useTranslation();
  const { theme, toggleTheme } = useTheme();

  // Submenu (Cài đặt) tự mở khi đang ở trang con; user vẫn đóng/mở tay được
  const [openKeys, setOpenKeys] = useState<string[]>([]);
  useEffect(() => {
    const parentKey = `/${location.pathname.split('/')[1]}`;
    setOpenKeys((prev) => (prev.includes(parentKey) ? prev : [...prev, parentKey]));
  }, [location.pathname]);

  const menuItems = [
    auth.hasRole('ADMIN', 'GLOBAL_LEAD', 'DEPT_LEAD', 'MANAGER') && {
      key: '/employees',
      icon: <TeamOutlined />,
      label: t('menu.employees'),
    },
    auth.hasRole('ADMIN', 'GLOBAL_LEAD', 'DEPT_LEAD', 'MANAGER') && {
      key: '/departments',
      icon: <ApartmentOutlined />,
      label: t('menu.departments'),
    },
    auth.hasRole('ADMIN', 'GLOBAL_LEAD', 'DEPT_LEAD', 'MANAGER') && {
      key: '/contracts',
      icon: <FileTextOutlined />,
      label: t('menu.contracts'),
    },
    auth.hasRole('ADMIN', 'GLOBAL_LEAD', 'DEPT_LEAD', 'MANAGER') && {
      key: '/org-chart',
      icon: <ClusterOutlined />,
      label: t('menu.orgchart'),
    },
    auth.hasRole('ADMIN', 'GLOBAL_LEAD', 'DEPT_LEAD', 'MANAGER') && {
      key: '/attendance',
      icon: <ClockCircleOutlined />,
      label: t('menu.attendance'),
    },
    auth.hasRole('ADMIN', 'GLOBAL_LEAD', 'DEPT_LEAD', 'MANAGER') && {
      key: '/holidays',
      icon: <ThunderboltOutlined />,
      label: t('menu.holidays'),
    },
    auth.hasRole('ADMIN', 'GLOBAL_LEAD', 'DEPT_LEAD', 'MANAGER') && {
      key: '/leave-quota',
      icon: <GiftOutlined />,
      label: t('menu.leaveQuotas'),
    },
    auth.hasRole('ADMIN', 'GLOBAL_LEAD', 'DEPT_LEAD', 'MANAGER') && {
      key: '/work-shifts',
      icon: <ScheduleOutlined />,
      label: t('menu.workShifts'),
    },
    {
      key: '/my-leave',
      icon: <CarryOutOutlined />,
      label: t('menu.myLeave'),
    },
    // Cài đặt thấy mọi role; màn Tài khoản chỉ ADMIN (Q&A chốt)
    {
      key: '/settings',
      icon: <SettingOutlined />,
      label: t('menu.settings'),
      children: [
        auth.hasRole('ADMIN') && { key: '/settings/accounts', label: t('menu.settingsAccounts') },
        { key: '/settings/security', label: t('menu.settingsSecurity') },
      ].filter(Boolean) as NavChild[],
    },
  ].filter(Boolean) as NavItem[];

  const roleLabel = auth.hasRole('ADMIN')
    ? t('role.ADMIN')
    : auth.hasRole('GLOBAL_LEAD')
      ? t('role.GLOBAL_LEAD')
      : auth.hasRole('DEPT_LEAD')
        ? t('role.DEPT_LEAD')
        : auth.hasRole('MANAGER')
          ? t('role.MANAGER')
          : t('role.EMPLOYEE');

  const langShort = i18n.language === 'en' ? 'EN' : 'VI';
  const langFlag = i18n.language === 'en' ? FLAG_EN : FLAG_VI;

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider className="hr-sider" breakpoint="lg" collapsedWidth={64} width={240}>
        <div className="hr-brand">
          <BrandMark />
          <div className="hr-brand-text">
            <span className="hr-brand-name">{t('brand')}</span>
            <span className="hr-brand-tag">{t('brandTagline')}</span>
          </div>
        </div>
        <Menu
          className="hr-menu"
          mode="inline"
          openKeys={openKeys}
          onOpenChange={(keys) => setOpenKeys(keys as string[])}
          selectedKeys={[
            menuItems.some((i) => i.key === `/${location.pathname.split('/')[1]}` && i.children)
              ? location.pathname
              : `/${location.pathname.split('/')[1]}`,
          ]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout className="hr-body">
        <Header className="hr-header">
          <Input
            className="hr-search"
            allowClear
            prefix={<SearchOutlined className="hr-search-icon" />}
            placeholder={t('searchPlaceholder')}
          />
          <div className="hr-header-right">
            <Badge dot offset={[-2, 4]}>
              <BellOutlined className="hr-bell" title={t('notifications')} />
            </Badge>
            <Button
              type="text"
              className="hr-theme-toggle"
              aria-label={theme === 'dark' ? t('theme.light') : t('theme.dark')}
              title={theme === 'dark' ? t('theme.light') : t('theme.dark')}
              icon={theme === 'dark' ? <SunOutlined /> : <MoonOutlined />}
              onClick={toggleTheme}
            />
            <Dropdown
              menu={{
                items: Object.entries(LANGUAGES).map(([key, label]) => ({ key, label })),
                selectedKeys: [i18n.language],
                onClick: ({ key }) => setLanguage(key === 'en' ? 'en' : 'vi'),
              }}
            >
              <span className="hr-lang">
                {langFlag}
                <span className="hr-lang-code">{langShort}</span>
                <DownOutlined className="hr-lang-arrow" />
              </span>
            </Dropdown>
            <Dropdown
              menu={{
                items: [
                  {
                    key: 'logout',
                    icon: <LogoutOutlined />,
                    label: t('logout'),
                    onClick: auth.logout,
                  },
                ],
              }}
            >
              <span className="hr-user">
                <Avatar size={36} icon={<UserOutlined />} className="hr-user-avatar" />
                <span className="hr-user-meta">
                  <span className="hr-user-name">{auth.username}</span>
                  <span className="hr-user-role">{roleLabel}</span>
                </span>
                <DownOutlined className="hr-user-arrow" />
              </span>
            </Dropdown>
          </div>
        </Header>
        <Content className="hr-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
