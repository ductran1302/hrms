import {
  CalculatorOutlined,
  DeleteOutlined,
  EyeOutlined,
  GiftOutlined,
  RollbackOutlined,
  SaveOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import {
  Button,
  Divider,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { errorMessage } from '../api/client';
import {
  deleteLeaveEntitlement,
  getLeaveNote,
  grantLeaveEntitlement,
  listLeaveQuotas,
  listLeaveWorkDays,
  recalculateLeaveQuotas,
  saveLeaveNote,
  upsertLeaveWorkDay,
} from '../api/leaveQuotas';
import type { LeaveQuota, LeaveSourceValue, LeaveWorkDay } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import ActionIcon from '../components/ActionIcon';

const { Text } = Typography;

const SOURCE_COLOR: Record<LeaveSourceValue, string> = {
  BASE: 'blue',
  SENIORITY: 'geekblue',
  POSITION: 'purple',
  BONUS: 'green',
  CARRYOVER: 'cyan',
  ADJUSTMENT: 'orange',
};

const fmt = (n: number) => n.toLocaleString('vi-VN', { maximumFractionDigits: 4 });

const fmtDate = (v: string) => v.slice(0, 10);

export default function LeaveQuotaPage() {
  const { t } = useTranslation();
  const auth = useAuth();
  const canWrite = auth.hasRole('ADMIN');
  const currentYear = new Date().getFullYear();

  const [year, setYear] = useState(currentYear);
  const [q, setQ] = useState('');
  const [rows, setRows] = useState<LeaveQuota[]>([]);
  const [loading, setLoading] = useState(false);

  const [grantTarget, setGrantTarget] = useState<{ quota: LeaveQuota; source: 'BONUS' | 'CARRYOVER' | 'ADJUSTMENT' } | null>(null);
  const [grantForm] = Form.useForm<{ days: number; reason?: string }>();

  const [detailTarget, setDetailTarget] = useState<LeaveQuota | null>(null);
  const [noteValue, setNoteValue] = useState('');
  const [noteSaving, setNoteSaving] = useState(false);
  const [workDays, setWorkDays] = useState<LeaveWorkDay[]>([]);
  const [workDaysLoading, setWorkDaysLoading] = useState(false);
  const [savingPeriod, setSavingPeriod] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await listLeaveQuotas({ year, q: q || undefined }));
    } catch (e) {
      message.error(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }, [year, q]);

  useEffect(() => {
    void load();
  }, [load]);

  const openDetail = async (record: LeaveQuota) => {
    setDetailTarget(record);
    setNoteValue(record.note ?? '');
    setWorkDaysLoading(true);
    try {
      const [note, days] = await Promise.all([
        getLeaveNote(record.employeeId, year),
        listLeaveWorkDays(record.employeeId, year),
      ]);
      setNoteValue(note);
      setWorkDays(days);
    } catch (e) {
      message.error(errorMessage(e));
    } finally {
      setWorkDaysLoading(false);
    }
  };

  const submitNote = async () => {
    if (!detailTarget) return;
    setNoteSaving(true);
    try {
      await saveLeaveNote(detailTarget.employeeId, year, noteValue);
      message.success(t('saved'));
      const data = await listLeaveQuotas({ year, q: q || undefined });
      setRows(data);
      setDetailTarget(
        data.find((r) => r.employeeId === detailTarget.employeeId) ?? detailTarget,
      );
    } catch (e) {
      message.error(errorMessage(e));
    } finally {
      setNoteSaving(false);
    }
  };

  const submitWorkDay = async (row: LeaveWorkDay, value: number) => {
    if (!detailTarget) return;
    setSavingPeriod(row.periodStart);
    try {
      const saved = await upsertLeaveWorkDay(detailTarget.employeeId, {
        periodStart: row.periodStart,
        workDays: value,
      });
      message.success(t('saved'));
      setWorkDays((prev) =>
        prev.map((r) => (r.periodStart === row.periodStart ? saved : r)),
      );
      const data = await listLeaveQuotas({ year, q: q || undefined });
      setRows(data);
      setDetailTarget(
        data.find((r) => r.employeeId === detailTarget.employeeId) ?? detailTarget,
      );
    } catch (e) {
      message.error(errorMessage(e));
    } finally {
      setSavingPeriod(null);
    }
  };

  const submitGrant = async () => {
    if (!grantTarget) return;
    const values = await grantForm.validateFields();
    try {
      await grantLeaveEntitlement(grantTarget.quota.employeeId, year, {
        source: grantTarget.source,
        days: values.days,
        reason: values.reason || undefined,
      });
      message.success(t('saved'));
      setGrantTarget(null);
      await load();
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const submitRecalculate = async () => {
    try {
      const n = await recalculateLeaveQuotas(year);
      message.success(t('leave.recalculated', { n }));
      await load();
      if (detailTarget) {
        const data = await listLeaveQuotas({ year, q: q || undefined });
        const fresh = data.find((r) => r.employeeId === detailTarget.employeeId);
        if (fresh) setDetailTarget(fresh);
        const days = await listLeaveWorkDays(detailTarget.employeeId, year);
        setWorkDays(days);
      }
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const removeEntitlement = async (id: string) => {
    try {
      await deleteLeaveEntitlement(id);
      message.success(t('deleted'));
      const data = await listLeaveQuotas({ year, q: q || undefined });
      setRows(data);
      setDetailTarget((prev) =>
        prev ? (data.find((r) => r.employeeId === prev.employeeId) ?? null) : prev,
      );
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const columns: ColumnsType<LeaveQuota> = [
    { title: t('leave.column.employeeCode'), dataIndex: 'employeeCode', width: 100 },
    { title: t('leave.column.fullName'), dataIndex: 'fullName', width: 170, ellipsis: true },
    { title: t('leave.column.department'), dataIndex: 'departmentName', width: 130, ellipsis: true },
    { title: t('leave.column.position'), dataIndex: 'position', width: 130, ellipsis: true },
    { title: t('leave.column.base'), dataIndex: 'baseDays', width: 80, align: 'right', render: fmt },
    { title: t('leave.column.seniority'), dataIndex: 'seniorityDays', width: 85, align: 'right', render: fmt },
    {
      title: t('leave.column.roleBonus'),
      dataIndex: 'roleDays',
      width: 80,
      align: 'right',
      render: (v: number, record) => (
        <span title={(record.roles ?? '').split(',').join(', ') || undefined}>{fmt(v)}</span>
      ),
    },
    { title: t('leave.column.bonus'), dataIndex: 'bonusDays', width: 80, align: 'right', render: fmt },
    { title: t('leave.column.carryover'), dataIndex: 'carryoverDays', width: 85, align: 'right', render: fmt },
    { title: t('leave.column.adjustment'), dataIndex: 'adjustmentDays', width: 85, align: 'right', render: fmt },
    {
      title: t('leave.column.total'),
      dataIndex: 'totalDays',
      width: 80,
      align: 'right',
      render: (v: number) => <Text strong>{fmt(v)}</Text>,
    },
    {
      title: t('leave.column.remaining'),
      dataIndex: 'remainingDays',
      width: 85,
      align: 'right',
      render: (v: number) => <Text type="success" strong>{fmt(v)}</Text>,
    },
    {
      title: t('leave.column.note'),
      dataIndex: 'note',
      width: 150,
      ellipsis: true,
      render: (v: string | null) => v ?? '—',
    },
    {
      title: t('actions'),
      width: 130,
      render: (_, record) => (
        <Space>
          {canWrite && (
            <>
              <ActionIcon
                title={t('leave.action.grantBonus')}
                color="#52c41a"
                onClick={() => {
                  grantForm.resetFields();
                  setGrantTarget({ quota: record, source: 'BONUS' });
                }}
              >
                <GiftOutlined />
              </ActionIcon>
              <ActionIcon
                title={t('leave.action.grantCarryover')}
                color="#13c2c2"
                onClick={() => {
                  grantForm.resetFields();
                  setGrantTarget({ quota: record, source: 'CARRYOVER' });
                }}
              >
                <RollbackOutlined />
              </ActionIcon>
              <ActionIcon
                title={t('leave.action.adjustment')}
                color="#fa8c16"
                onClick={() => {
                  grantForm.resetFields();
                  setGrantTarget({ quota: record, source: 'ADJUSTMENT' });
                }}
              >
                <ToolOutlined />
              </ActionIcon>
            </>
          )}
          <ActionIcon title={t('leave.action.detail')} onClick={() => void openDetail(record)}>
            <EyeOutlined />
          </ActionIcon>
        </Space>
      ),
    },
  ];

  const detailColumns: ColumnsType<LeaveQuota['entitlements'][number]> = [
    {
      title: t('leave.action.source'),
      dataIndex: 'source',
      render: (v: LeaveSourceValue, record) => (
        <Tag color={SOURCE_COLOR[v]}>
          {t(`leave.source.${v}`)}
          {record.isAuto ? ` · ${t('leave.auto')}` : ''}
        </Tag>
      ),
    },
    { title: t('leave.action.days'), dataIndex: 'days', align: 'right', render: fmt },
    { title: t('leave.action.reason'), dataIndex: 'reason', ellipsis: true, render: (v: string | null) => v ?? '—' },
    { title: t('leave.action.grantedBy'), dataIndex: 'grantedBy', render: (v: string | null) => v ?? '—' },
    {
      title: t('leave.action.createdAt'),
      dataIndex: 'createdAt',
      render: (v: string) => new Date(v).toLocaleString('vi-VN'),
    },
    {
      title: '',
      width: 60,
      render: (_, record) =>
        canWrite && !record.isAuto ? (
          <Popconfirm
            title={t('leave.action.deleteEntitlement')}
            onConfirm={() => removeEntitlement(record.id)}
          >
            <ActionIcon title={t('delete')} color="#ff4d4f">
              <DeleteOutlined />
            </ActionIcon>
          </Popconfirm>
        ) : null,
    },
  ];

  const workDayColumns: ColumnsType<LeaveWorkDay> = [
    {
      title: t('leave.workday.period'),
      width: 200,
      render: (_, record) => (
        <Text code>
          {fmtDate(record.periodStart)} → {fmtDate(record.periodEnd)}
        </Text>
      ),
    },
    {
      title: t('leave.workday.inPeriod'),
      dataIndex: 'periodWorkingDays',
      width: 130,
      align: 'right',
      render: fmt,
    },
    {
      title: t('leave.workday.workDays'),
      width: 220,
      render: (_, record) => {
        const control = (
          <InputNumber
            size="small"
            style={{ width: 130 }}
            min={0}
            max={record.periodWorkingDays}
            step={0.25}
            precision={4}
            value={record.workDays ?? undefined}
            placeholder={t('leave.workday.notSet')}
            disabled={!canWrite}
            onChange={(v) => {
              if (v === null) return;
              setWorkDays((prev) =>
                prev.map((r) =>
                  r.periodStart === record.periodStart
                    ? { ...r, workDays: Number(v), source: 'MANUAL' }
                    : r,
                ),
              );
            }}
          />
        );
        return (
          <Space size={6}>
            {control}
            {canWrite && record.workDays !== null && (
              <ActionIcon
                title={t('save')}
                color="#52c41a"
                loading={savingPeriod === record.periodStart}
                disabled={savingPeriod !== null && savingPeriod !== record.periodStart}
                onClick={() => void submitWorkDay(record, record.workDays ?? 0)}
              >
                <SaveOutlined />
              </ActionIcon>
            )}
          </Space>
        );
      },
    },
    {
      title: t('leave.workday.source'),
      dataIndex: 'source',
      width: 110,
      render: (v: LeaveWorkDay['source']) =>
        v === 'MANUAL' ? (
          <Tag color="orange">{t('leave.workday.manual')}</Tag>
        ) : v === 'DEFAULT' ? (
          <Tag color="blue">{t('leave.workday.default')}</Tag>
        ) : (
          '—'
        ),
    },
  ];

  const detailTabs = [
    {
      key: 'entitlements',
      label: t('leave.action.entitlementsTab'),
      children: (
        <Table
          rowKey="id"
          size="small"
          columns={detailColumns}
          dataSource={detailTarget?.entitlements ?? []}
          pagination={false}
        />
      ),
    },
    {
      key: 'workdays',
      label: t('leave.workday.title'),
      children: (
        <Table
          rowKey="periodStart"
          size="small"
          loading={workDaysLoading}
          columns={workDayColumns}
          dataSource={workDays}
          pagination={false}
          locale={{
            emptyText: (
              <Empty description={t('leave.workday.empty')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ),
          }}
        />
      ),
    },
    {
      key: 'note',
      label: t('leave.column.note'),
      children: (
        <Space direction="vertical" size={8} style={{ width: '100%' }}>
          <Input.TextArea
            rows={4}
            maxLength={500}
            value={noteValue}
            disabled={!canWrite}
            onChange={(e) => setNoteValue(e.target.value)}
            placeholder={t('leave.notePlaceholder')}
          />
          {canWrite && (
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={noteSaving}
              onClick={() => void submitNote()}
            >
              {t('save')}
            </Button>
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
            {t('leave.title')}
          </Typography.Title>
          <Text type="secondary">{t('leave.subtitle')}</Text>
        </div>

        <Space wrap>
          <Select
            style={{ width: 110 }}
            value={year}
            onChange={setYear}
            options={Array.from({ length: 5 }, (_, i) => currentYear - 2 + i).map((y) => ({
              value: y,
              label: String(y),
            }))}
          />
          <Input.Search
            allowClear
            placeholder={t('leave.searchPlaceholder')}
            style={{ width: 240 }}
            onSearch={setQ}
          />
          {canWrite && (
            <Popconfirm
              title={t('leave.recalculateConfirm')}
              onConfirm={() => void submitRecalculate()}
            >
              <Button type="primary" icon={<CalculatorOutlined />}>
                {t('leave.recalculate')}
              </Button>
            </Popconfirm>
          )}
        </Space>

        <Table<LeaveQuota>
          rowKey="employeeId"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1500 }}
          pagination={{ pageSize: 20, hideOnSinglePage: true }}
          locale={{ emptyText: <Empty description={t('leave.empty')} image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
        />
      </Space>

      <Modal
        title={
          grantTarget
            ? t('leave.action.grantTitle', {
                source: t(`leave.source.${grantTarget.source}`),
                name: grantTarget.quota.fullName,
              })
            : ''
        }
        open={!!grantTarget}
        onOk={() => void submitGrant()}
        onCancel={() => setGrantTarget(null)}
        okText={t('save')}
        cancelText={t('cancel')}
        destroyOnClose
      >
        <Form form={grantForm} layout="vertical">
          <Form.Item
            name="days"
            label={t('leave.action.days')}
            rules={[
              { required: true, message: t('required') },
              {
                validator: (_, v: number) =>
                  v !== 0 && Math.abs(v) <= 366 && String(v).split('.')[1]?.length <= 4
                    ? Promise.resolve()
                    : Promise.reject(new Error(t('leave.action.daysInvalid'))),
              },
            ]}
          >
            <InputNumber style={{ width: '100%' }} min={-366} max={366} step={0.125} precision={4} />
          </Form.Item>
          <Form.Item name="reason" label={t('leave.action.reason')}>
            <Input.TextArea rows={2} maxLength={300} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t('leave.action.entitlements', { name: detailTarget?.fullName ?? '' })}
        open={!!detailTarget}
        onCancel={() => setDetailTarget(null)}
        footer={null}
        width={920}
        destroyOnClose
      >
        <Tabs items={detailTabs} />
        <Divider style={{ margin: '12px 0 0' }} />
        <Text type="secondary">
          {t('leave.year')}: {year} · {t('leave.recalculateHint')}
        </Text>
      </Modal>
    </div>
  );
}
