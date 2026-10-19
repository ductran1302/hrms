import { KeyOutlined, LockOutlined, UnlockOutlined } from '@ant-design/icons';
import {
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { resetAccountPassword, searchAccounts, setAccountEnabled } from '../api/accounts';
import { errorMessage } from '../api/client';
import type { Account } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import ActionIcon from '../components/ActionIcon';

const { Text } = Typography;

const ROLE_COLORS: Record<string, string> = {
  HR_ADMIN: 'geekblue',
  MANAGER: 'purple',
  EMPLOYEE: 'default',
};

interface ResetFormValues {
  newPassword: string;
}

/** Cài đặt > Tài khoản: admin quản lý tài khoản Keycloak (đặt lại MK, bật/tắt). */
export default function SettingsAccountsPage() {
  const { t } = useTranslation();
  const auth = useAuth();

  const [accounts, setAccounts] = useState<Account[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [q, setQ] = useState('');
  const [loading, setLoading] = useState(false);

  const [resetOpen, setResetOpen] = useState(false);
  const [resetTarget, setResetTarget] = useState<Account | null>(null);
  const [resetForm] = Form.useForm<ResetFormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await searchAccounts({ q: q || undefined, page: page - 1, size: pageSize });
      setAccounts(data.items);
      setTotal(data.total);
    } catch (e) {
      message.error(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }, [q, page, pageSize]);

  useEffect(() => {
    void load();
  }, [load]);

  const openReset = (account: Account) => {
    setResetTarget(account);
    resetForm.resetFields();
    setResetOpen(true);
  };

  const submitReset = async () => {
    const values = await resetForm.validateFields();
    if (!resetTarget) return;
    try {
      await resetAccountPassword(resetTarget.username, values.newPassword);
      message.success(t('settings.accounts.resetSuccess'));
      setResetOpen(false);
      await load();
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const toggleEnabled = async (account: Account) => {
    try {
      await setAccountEnabled(account.username, !account.enabled);
      message.success(t('saved'));
      await load();
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const columns: ColumnsType<Account> = [
    {
      title: t('settings.accounts.column.username'),
      dataIndex: 'username',
      width: 130,
      render: (v: string) => <Text code>{v}</Text>,
    },
    {
      title: t('settings.accounts.column.fullName'),
      dataIndex: 'fullName',
      width: 170,
      ellipsis: true,
    },
    {
      title: t('settings.accounts.column.email'),
      dataIndex: 'email',
      width: 190,
      ellipsis: true,
      render: (v: string | null) => v ?? '—',
    },
    {
      title: t('settings.accounts.column.roles'),
      dataIndex: 'roles',
      width: 150,
      render: (roles: string[]) => (
        <Space size={4}>
          {roles.length === 0
            ? '—'
            : roles.map((role) => (
                <Tag key={role} color={ROLE_COLORS[role] ?? 'default'}>
                  {t(`role.${role}`, { defaultValue: role })}
                </Tag>
              ))}
        </Space>
      ),
    },
    {
      title: t('settings.accounts.column.enabled'),
      dataIndex: 'enabled',
      width: 100,
      render: (v: boolean) =>
        v ? (
          <Tag color="green">{t('settings.accounts.status.enabled')}</Tag>
        ) : (
          <Tag color="red">{t('settings.accounts.status.disabled')}</Tag>
        ),
    },
    {
      title: t('settings.accounts.column.created'),
      dataIndex: 'createdTimestamp',
      width: 110,
      render: (v: number | null) => (v ? dayjs(v).format('DD/MM/YYYY') : '—'),
    },
    {
      title: t('settings.accounts.column.updated'),
      dataIndex: 'updatedAt',
      width: 150,
      render: (v: string | null, a) =>
        v ? (
          <>
            {dayjs(v).format('DD/MM/YYYY HH:mm')}
            {a.updatedBy ? (
              <>
                {' · '}
                <Text type="secondary">{a.updatedBy}</Text>
              </>
            ) : null}
          </>
        ) : (
          '—'
        ),
    },
    {
      title: t('actions'),
      width: 110,
      render: (_, a) => (
        <Space>
          <ActionIcon
            title={t('settings.accounts.action.resetPassword')}
            color="#1677ff"
            onClick={() => openReset(a)}
          >
            <KeyOutlined />
          </ActionIcon>
          {a.username !== auth.username && (
            <Popconfirm
              title={
                a.enabled
                  ? t('settings.accounts.action.confirmDisable')
                  : t('settings.accounts.action.confirmEnable')
              }
              onConfirm={() => void toggleEnabled(a)}
            >
              <ActionIcon
                title={a.enabled ? t('settings.accounts.action.disable') : t('settings.accounts.action.enable')}
                color={a.enabled ? '#ff4d4f' : '#52c41a'}
              >
                {a.enabled ? <LockOutlined /> : <UnlockOutlined />}
              </ActionIcon>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {t('settings.accounts.title')}
          </Typography.Title>
          <Text type="secondary">{t('settings.accounts.subtitle')}</Text>
        </div>

        <Space wrap>
          <Input.Search
            allowClear
            placeholder={t('settings.accounts.searchPlaceholder')}
            style={{ width: 300 }}
            onSearch={(value) => {
              setQ(value);
              setPage(1);
            }}
          />
        </Space>

        <Table<Account>
          rowKey="username"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={accounts}
          scroll={{ x: 1100 }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            onChange: (p, ps) => {
              setPage(p);
              setPageSize(ps);
            },
          }}
          locale={{
            emptyText: (
              <Empty
                description={t('settings.accounts.empty')}
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              />
            ),
          }}
        />
      </Space>

      <Modal
        title={t('settings.accounts.resetModal.title')}
        open={resetOpen}
        onOk={() => void submitReset()}
        onCancel={() => setResetOpen(false)}
        okText={t('settings.accounts.resetModal.confirm')}
        cancelText={t('cancel')}
        destroyOnClose
      >
        <Space direction="vertical" size={4} style={{ width: '100%' }}>
          <Text code>{resetTarget?.username}</Text>
          <Text type="secondary">{t('settings.accounts.resetModal.hint')}</Text>
        </Space>
        <Form form={resetForm} layout="vertical" style={{ marginTop: 12 }}>
          <Form.Item
            name="newPassword"
            label={t('settings.accounts.resetModal.newPassword')}
            rules={[
              { required: true, message: t('required') },
              { min: 8, message: t('settings.security.minLength') },
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
