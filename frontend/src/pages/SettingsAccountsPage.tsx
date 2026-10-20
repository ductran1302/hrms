import { KeyOutlined, LockOutlined, TeamOutlined, UnlockOutlined } from '@ant-design/icons';
import {
  Checkbox,
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
import {
  resetAccountPassword,
  searchAccounts,
  setAccountEnabled,
  updateAccountRoles,
} from '../api/accounts';
import { errorMessage } from '../api/client';
import type { Account } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import ActionIcon from '../components/ActionIcon';

const { Text } = Typography;

const ROLE_COLORS: Record<string, string> = {
  ADMIN: 'geekblue',
  GLOBAL_LEAD: 'purple',
  DEPT_LEAD: 'cyan',
  MANAGER: 'blue',
  EMPLOYEE: 'default',
};

/** 5 role chuẩn (Roles.java) — thứ tự theo cấp quyền giảm dần. */
const ALL_ROLES = ['ADMIN', 'GLOBAL_LEAD', 'DEPT_LEAD', 'MANAGER', 'EMPLOYEE'];

interface ResetFormValues {
  newPassword: string;
}

interface RoleFormValues {
  roles: string[];
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

  const [roleOpen, setRoleOpen] = useState(false);
  const [roleTarget, setRoleTarget] = useState<Account | null>(null);
  const [roleForm] = Form.useForm<RoleFormValues>();

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

  const openRoles = (account: Account) => {
    setRoleTarget(account);
    roleForm.setFieldsValue({ roles: account.roles });
    setRoleOpen(true);
  };

  const submitRoles = async () => {
    const values = await roleForm.validateFields();
    if (!roleTarget) return;
    try {
      await updateAccountRoles(roleTarget.username, values.roles);
      message.success(t('saved'));
      setRoleOpen(false);
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
            <ActionIcon
              title={t('settings.accounts.action.editRoles')}
              color="#722ed1"
              onClick={() => openRoles(a)}
            >
              <TeamOutlined />
            </ActionIcon>
          )}
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

      <Modal
        title={t('settings.accounts.roleModal.title')}
        open={roleOpen}
        onOk={() => void submitRoles()}
        onCancel={() => setRoleOpen(false)}
        okText={t('save')}
        cancelText={t('cancel')}
        destroyOnClose
      >
        <Space direction="vertical" size={4} style={{ width: '100%' }}>
          <Text code>{roleTarget?.username}</Text>
          <Text type="secondary">{t('settings.accounts.roleModal.hint')}</Text>
        </Space>
        <Form form={roleForm} layout="vertical" style={{ marginTop: 12 }}>
          <Form.Item
            name="roles"
            rules={[{ required: true, min: 1, message: t('settings.accounts.roleModal.minOne') }]}
          >
            <Checkbox.Group>
              <Space direction="vertical">
                {ALL_ROLES.map((role) => (
                  <Checkbox key={role} value={role}>
                    <Tag color={ROLE_COLORS[role]} style={{ marginRight: 6 }}>
                      {role}
                    </Tag>
                    {t(`role.${role}`)}
                  </Checkbox>
                ))}
              </Space>
            </Checkbox.Group>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
