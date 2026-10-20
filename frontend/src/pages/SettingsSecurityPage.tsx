import { Button, Card, Descriptions, Form, Input, Space, Typography, message } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { changeOwnPassword } from '../api/accounts';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';

const { Text } = Typography;

interface SecurityFormValues {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

/** Cài đặt > Bảo mật: tự đổi mật khẩu đăng nhập (xác thực mật khẩu hiện tại trước). */
export default function SettingsSecurityPage() {
  const { t } = useTranslation();
  const auth = useAuth();
  const [form] = Form.useForm<SecurityFormValues>();
  const [saving, setSaving] = useState(false);

  const roleLabel = auth.hasRole('ADMIN')
    ? t('role.ADMIN')
    : auth.hasRole('GLOBAL_LEAD')
      ? t('role.GLOBAL_LEAD')
      : auth.hasRole('DEPT_LEAD')
        ? t('role.DEPT_LEAD')
        : auth.hasRole('MANAGER')
          ? t('role.MANAGER')
          : t('role.EMPLOYEE');

  const submit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await changeOwnPassword({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      });
      message.success(t('settings.security.success'));
      form.resetFields();
    } catch (e) {
      message.error(errorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {t('settings.security.title')}
          </Typography.Title>
          <Text type="secondary">{t('settings.security.subtitle')}</Text>
        </div>

        <Card title={t('settings.security.card')} style={{ maxWidth: 480 }}>
          <Form form={form} layout="vertical" onFinish={() => void submit()}>
            <Form.Item
              name="currentPassword"
              label={t('settings.security.currentPassword')}
              rules={[{ required: true, message: t('required') }]}
            >
              <Input.Password autoComplete="current-password" />
            </Form.Item>
            <Form.Item
              name="newPassword"
              label={t('settings.security.newPassword')}
              rules={[
                { required: true, message: t('required') },
                { min: 8, message: t('settings.security.minLength') },
              ]}
            >
              <Input.Password autoComplete="new-password" />
            </Form.Item>
            <Form.Item
              name="confirmPassword"
              label={t('settings.security.confirmPassword')}
              dependencies={['newPassword']}
              rules={[
                { required: true, message: t('required') },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || value === getFieldValue('newPassword')) {
                      return Promise.resolve();
                    }
                    return Promise.reject(new Error(t('settings.security.mismatch')));
                  },
                }),
              ]}
            >
              <Input.Password autoComplete="new-password" />
            </Form.Item>
            <Button type="primary" htmlType="submit" loading={saving}>
              {t('settings.security.submit')}
            </Button>
          </Form>
        </Card>

        <Card title={t('settings.security.account')} style={{ maxWidth: 480 }}>
          <Descriptions column={1} size="small">
            <Descriptions.Item label={t('settings.security.account')}>
              <Space size={6}>
                <Text code>{auth.username}</Text>
                <Text type="secondary">{t('settings.security.usernameHint')}</Text>
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label={t('settings.security.roles')}>
              {roleLabel}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      </Space>
    </div>
  );
}
