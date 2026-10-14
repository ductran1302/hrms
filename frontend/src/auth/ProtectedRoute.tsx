import { Button, Result, Spin } from 'antd';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from './AuthContext';

interface Props {
  roles?: string[];
  children: ReactNode;
}

export default function ProtectedRoute({ roles, children }: Props) {
  const auth = useAuth();
  const { t } = useTranslation();

  if (!auth.initialized) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
        <Spin size="large" tip={t('loggingIn')} />
      </div>
    );
  }

  if (auth.initFailed) {
    return (
      <Result
        status="error"
        title={t('keycloakError')}
        subTitle={t('keycloakErrorDesc', {
          url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8081',
        })}
        extra={
          <Button type="primary" onClick={auth.login}>
            {t('retry')}
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
        subTitle={t('forbiddenDesc')}
        extra={
          <Button type="primary" onClick={() => window.location.assign('/')}>
            {t('home')}
          </Button>
        }
      />
    );
  }

  return <>{children}</>;
}
