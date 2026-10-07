import { Button, Result, Spin } from 'antd';
import type { ReactNode } from 'react';
import { useAuth } from './AuthContext';

interface Props {
  roles?: string[];
  children: ReactNode;
}

export default function ProtectedRoute({ roles, children }: Props) {
  const auth = useAuth();

  if (!auth.initialized) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
        <Spin size="large" tip="Đang đăng nhập..." />
      </div>
    );
  }

  if (auth.initFailed) {
    return (
      <Result
        status="error"
        title="Không kết nối được Keycloak"
        subTitle={`Kiểm tra Keycloak (${import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8081'}) và thử lại.`}
        extra={
          <Button type="primary" onClick={auth.login}>
            Thử lại
          </Button>
        }
      />
    );
  }

  if (!auth.authenticated) return null;

  if (roles && roles.length > 0 && !auth.hasRole(...roles)) {
    return (
      <Result
        status="403"
        title="403"
        subTitle="Bạn không có quyền xem trang này (cần role HR_ADMIN hoặc MANAGER)."
        extra={
          <Button type="primary" onClick={() => window.location.assign('/')}>
            Về trang chủ
          </Button>
        }
      />
    );
  }

  return <>{children}</>;
}
