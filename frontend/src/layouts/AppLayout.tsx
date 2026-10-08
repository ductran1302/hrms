import {
  ApartmentOutlined,
  ClockCircleOutlined,
  ClusterOutlined,
  FileTextOutlined,
  LogoutOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Avatar, Dropdown, Layout, Menu, Typography } from 'antd';
import type { ReactNode } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

const { Header, Sider, Content } = Layout;

export default function AppLayout() {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const menuItems = [
    auth.hasRole('HR_ADMIN', 'MANAGER') && {
      key: '/nhan-su',
      icon: <TeamOutlined />,
      label: 'Nhân sự',
    },
    auth.hasRole('HR_ADMIN', 'MANAGER') && {
      key: '/phong-ban',
      icon: <ApartmentOutlined />,
      label: 'Phòng ban',
    },
    auth.hasRole('HR_ADMIN') && {
      key: '/hop-dong',
      icon: <FileTextOutlined />,
      label: 'Hợp đồng',
    },
    auth.hasRole('HR_ADMIN', 'MANAGER') && {
      key: '/to-chuc',
      icon: <ClusterOutlined />,
      label: 'Sơ đồ tổ chức',
    },
    auth.hasRole('HR_ADMIN', 'MANAGER') && {
      key: '/cham-cong',
      icon: <ClockCircleOutlined />,
      label: 'Chấm công',
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
          HRMS
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
              items: [
                {
                  key: 'logout',
                  icon: <LogoutOutlined />,
                  label: 'Đăng xuất',
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
