import { ReloadOutlined, UndoOutlined } from '@ant-design/icons';
import { App as AntApp, Button, Card, Popconfirm, Space, Switch, Table, Tag, Tooltip, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { retrySyncTask, searchSyncTasks } from '../api/accounts';
import { errorMessage } from '../api/client';
import type { KeycloakSyncStatusValue, KeycloakSyncTask } from '../api/types';
import ActionIcon from './ActionIcon';

const { Text } = Typography;

const STATUS_COLORS: Record<KeycloakSyncStatusValue, string> = {
  PENDING: 'gold',
  DONE: 'green',
  FAILED: 'red',
};

interface Props {
  /** Username của các task FAILED — để màn tài khoản đánh dấu dòng chưa đồng bộ. */
  onFailedUsernamesChange?: (usernames: Set<string>) => void;
}

/**
 * Panel vận hành P0-6: hàng đợi đồng bộ tài khoản Keycloak.
 * Mặc định chỉ hiện task FAILED (đã thử 5 lần vẫn lỗi) + nút thử lại.
 */
export default function KeycloakSyncPanel({ onFailedUsernamesChange }: Props) {
  const { t } = useTranslation();
  const { message } = AntApp.useApp();

  const [tasks, setTasks] = useState<KeycloakSyncTask[]>([]);
  const [total, setTotal] = useState(0);
  const [failedTotal, setFailedTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [showAll, setShowAll] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await searchSyncTasks({
        status: showAll ? undefined : 'FAILED',
        page: 0,
        size: 50,
      });
      setTasks(data.items);
      setTotal(data.total);
      onFailedUsernamesChange?.(
        new Set(data.items.filter((x) => x.status === 'FAILED').map((x) => x.username)),
      );
      const failed = await searchSyncTasks({ status: 'FAILED', page: 0, size: 1 });
      setFailedTotal(failed.total);
    } catch (e) {
      message.error(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }, [showAll, onFailedUsernamesChange]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleRetry = async (task: KeycloakSyncTask) => {
    try {
      await retrySyncTask(task.id);
      message.success(t('settings.accounts.sync.retryQueued'));
      await load();
      // Task thường xử lý xong trong ~1s — tải lại lần nữa để thấy kết quả.
      window.setTimeout(() => void load(), 2000);
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const columns: ColumnsType<KeycloakSyncTask> = [
    {
      title: t('settings.accounts.sync.column.status'),
      dataIndex: 'status',
      width: 110,
      render: (v: KeycloakSyncStatusValue) => (
        <Tag color={STATUS_COLORS[v]}>{t(`settings.accounts.sync.status.${v}`)}</Tag>
      ),
    },
    {
      title: t('settings.accounts.sync.column.operation'),
      dataIndex: 'operation',
      width: 150,
      render: (v: KeycloakSyncTask['operation']) =>
        t(`settings.accounts.sync.operation.${v}`, { defaultValue: v }),
    },
    {
      title: t('settings.accounts.sync.column.username'),
      dataIndex: 'username',
      width: 140,
      render: (v: string) => <Text code>{v}</Text>,
    },
    {
      title: t('settings.accounts.sync.column.attempts'),
      dataIndex: 'attempts',
      width: 90,
      align: 'center',
    },
    {
      title: t('settings.accounts.sync.column.error'),
      dataIndex: 'lastError',
      ellipsis: true,
      render: (v: string | null) =>
        v ? (
          <Tooltip title={v}>
            <Text type="danger">{v}</Text>
          </Tooltip>
        ) : (
          '—'
        ),
    },
    {
      title: t('settings.accounts.sync.column.updated'),
      dataIndex: 'updatedAt',
      width: 150,
      render: (v: string) => (v ? dayjs(v).format('DD/MM/YYYY HH:mm') : '—'),
    },
    {
      title: t('actions'),
      width: 80,
      align: 'center',
      render: (_, task) =>
        task.status === 'FAILED' ? (
          <Popconfirm
            title={t('settings.accounts.sync.confirmRetry')}
            onConfirm={() => void handleRetry(task)}
          >
            <ActionIcon title={t('settings.accounts.sync.retry')} color="#fa8c16">
              <UndoOutlined />
            </ActionIcon>
          </Popconfirm>
        ) : null,
    },
  ];

  return (
    <Card
      size="small"
      title={
        <Space size={8}>
          {t('settings.accounts.sync.title')}
          {failedTotal > 0 ? <Tag color="red">{failedTotal}</Tag> : <Tag color="green">0</Tag>}
        </Space>
      }
      extra={
        <Space size={8}>
          <Switch
            size="small"
            checked={showAll}
            onChange={setShowAll}
            checkedChildren={t('settings.accounts.sync.showAll')}
            unCheckedChildren={t('settings.accounts.sync.onlyFailed')}
          />
          <Button size="small" icon={<ReloadOutlined />} onClick={() => void load()}>
            {t('settings.accounts.sync.reload')}
          </Button>
        </Space>
      }
    >
      <Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
        {t('settings.accounts.sync.subtitle')}
      </Text>
      <Table<KeycloakSyncTask>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={tasks}
        pagination={
          total > 50
            ? { pageSize: 10, size: 'small', showSizeChanger: false }
            : false
        }
        locale={{ emptyText: t('settings.accounts.sync.none') }}
      />
    </Card>
  );
}
