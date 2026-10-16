import {
  ApartmentOutlined,
  CarryOutOutlined,
  ClockCircleOutlined,
  ClusterOutlined,
  FileTextOutlined,
  GiftOutlined,
  GlobalOutlined,
  LogoutOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Avatar, Dropdown, Layout, Menu, Typography } from 'antd';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { LANGUAGES, setLanguage } from '../i18n';

const { Header, Sider, Content } = Layout;

export default function AppLayout() {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const { t, i18n } = useTranslation();

  const menuItems = [
    auth.hasRole('HR_ADMIN', 'MANAGER') && {
      key: '/employees',
      icon: <TeamOutlined />,
      label: t('menu.employees'),
    },
    auth.hasRole('HR_ADMIN', 'MANAGER') && {
      key: '/departments',
      icon: <ApartmentOutlined />,
      label: t('menu.departments'),
    },
    auth.hasRole('HR_ADMIN') && {
      key: '/contracts',
      icon: <FileTextOutlined />,
      label: t('menu.contracts'),
    },
    auth.hasRole('HR_ADMIN', 'MANAGER') && {
      key: '/org-chart',
      icon: <ClusterOutlined />,
      label: t('menu.orgchart'),
    },
    auth.hasRole('HR_ADMIN', 'MANAGER') && {
      key: '/attendance',
      icon: <ClockCircleOutlined />,
      label: t('menu.attendance'),
    },
    auth.hasRole('HR_ADMIN', 'MANAGER') && {
      key: '/holidays',
      icon: <ThunderboltOutlined />,
      label: t('menu.holidays'),
    },
    auth.hasRole('HR_ADMIN', 'MANAGER') && {
      key: '/leave-quota',
      icon: <GiftOutlined />,
      label: t('menu.leaveQuotas'),
    },
    {
      key: '/my-leave',
      icon: <CarryOutOutlined />,
      label: t('menu.myLeave'),
    },
  ].filter(Boolean) as { key: string; icon: ReactNode; label: string }[];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider breakpoint="lg" collapsedWidth={64}>
        <div
          style={{
            height: 56,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
            fontWeight: 700,
            letterSpacing: 1,
          }}
        >
          {t('brand')}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[`/${location.pathname.split('/')[1]}`]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: '#fff',
            padding: '0 24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            gap: 12,
            boxShadow: '0 1px 4px rgba(0,21,41,0.08)',
          }}
        >
          <Dropdown
            menu={{
              items: Object.entries(LANGUAGES).map(([key, label]) => ({ key, label })),
              selectedKeys: [i18n.language],
              onClick: ({ key }) => setLanguage(key === 'en' ? 'en' : 'vi'),
            }}
          >
            <span style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6 }}>
              <GlobalOutlined />
              <Typography.Text>{LANGUAGES[i18n.language] ?? LANGUAGES.vi}</Typography.Text>
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
            <span style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
              <Avatar size="small" icon={<UserOutlined />} />
              <Typography.Text>{auth.username}</Typography.Text>
            </span>
          </Dropdown>
        </Header>
        <Content style={{ margin: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
