import {
  CalculatorOutlined,
  DeleteOutlined,
  EyeOutlined,
  GiftOutlined,
  RollbackOutlined,
  SettingOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import {
  Button,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
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
  getLeavePolicy,
  grantLeaveEntitlement,
  listLeaveQuotas,
  recalculateLeaveQuotas,
  saveLeavePolicy,
} from '../api/leaveQuotas';
import type { LeaveQuota, LeaveSourceValue } from '../api/types';
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

interface PolicyFormValues {
  baseDays: number;
  seniorityStepYears: number;
  seniorityBonusDays: number;
  seniorityMaxBonus: number;
  carryoverMax: number;
  positionQuotas: { positionName: string; bonusDays: number }[];
}

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

  const [policyOpen, setPolicyOpen] = useState(false);
  const [policyForm] = Form.useForm<PolicyFormValues>();

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

  const openPolicy = async () => {
    try {
      const policy = await getLeavePolicy(year);
      policyForm.setFieldsValue({
        baseDays: policy.baseDays,
        seniorityStepYears: policy.seniorityStepYears,
        seniorityBonusDays: policy.seniorityBonusDays,
        seniorityMaxBonus: policy.seniorityMaxBonus,
        carryoverMax: policy.carryoverMax,
        positionQuotas: policy.positionQuotas,
      });
      setPolicyOpen(true);
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const submitPolicy = async () => {
    const values = await policyForm.validateFields();
    try {
      await saveLeavePolicy({ year, ...values });
      message.success(t('saved'));
      setPolicyOpen(false);
      await load();
    } catch (e) {
      message.error(errorMessage(e));
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
    { title: t('leave.column.positionBonus'), dataIndex: 'positionDays', width: 80, align: 'right', render: fmt },
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
          <ActionIcon title={t('leave.action.detail')} onClick={() => setDetailTarget(record)}>
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
      render: (v: LeaveSourceValue) => <Tag color={SOURCE_COLOR[v]}>{t(`leave.source.${v}`)}</Tag>,
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
        canWrite && !['BASE', 'SENIORITY', 'POSITION'].includes(record.source) ? (
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
            <>
              <Button icon={<SettingOutlined />} onClick={() => void openPolicy()}>
                {t('leave.policy')}
              </Button>
              <Popconfirm
                title={t('leave.recalculateConfirm')}
                onConfirm={() => void submitRecalculate()}
              >
                <Button type="primary" icon={<CalculatorOutlined />}>
                  {t('leave.recalculate')}
                </Button>
              </Popconfirm>
            </>
          )}
        </Space>

        <Table<LeaveQuota>
          rowKey="employeeId"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1300 }}
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
        width={820}
        destroyOnClose
      >
        <Table
          rowKey="id"
          size="small"
          columns={detailColumns}
          dataSource={detailTarget?.entitlements ?? []}
          pagination={false}
        />
      </Modal>

      <Modal
        title={t('leave.policyTitle', { year })}
        open={policyOpen}
        onOk={() => void submitPolicy()}
        onCancel={() => setPolicyOpen(false)}
        okText={t('save')}
        cancelText={t('cancel')}
        width={640}
        destroyOnClose
      >
        <Form form={policyForm} layout="vertical">
          <Space size={12} style={{ display: 'flex' }} align="start">
            <Form.Item name="baseDays" label={t('leave.baseDays')} rules={[{ required: true, message: t('required') }]}>
              <InputNumber min={0} max={366} precision={4} style={{ width: 140 }} />
            </Form.Item>
            <Form.Item
              name="seniorityStepYears"
              label={t('leave.seniorityStepYears')}
              rules={[{ required: true, message: t('required') }]}
            >
              <InputNumber min={0} max={50} style={{ width: 140 }} />
            </Form.Item>
            <Form.Item
              name="seniorityBonusDays"
              label={t('leave.seniorityBonusDays')}
              rules={[{ required: true, message: t('required') }]}
            >
              <InputNumber min={0} max={366} precision={4} style={{ width: 140 }} />
            </Form.Item>
          </Space>
          <Space size={12} style={{ display: 'flex' }} align="start">
            <Form.Item
              name="seniorityMaxBonus"
              label={t('leave.seniorityMaxBonus')}
              rules={[{ required: true, message: t('required') }]}
            >
              <InputNumber min={0} max={366} precision={4} style={{ width: 140 }} />
            </Form.Item>
            <Form.Item
              name="carryoverMax"
              label={t('leave.carryoverMax')}
              rules={[{ required: true, message: t('required') }]}
            >
              <InputNumber min={0} max={366} precision={4} style={{ width: 140 }} />
            </Form.Item>
          </Space>

          <Text strong>{t('leave.positionQuotas')}</Text>
          <Form.List name="positionQuotas">
            {(fields, { add, remove: removeQuota }) => (
              <>
                {fields.map((field) => (
                  <Space key={field.key} align="baseline" style={{ display: 'flex', marginBottom: 8 }}>
                    <Form.Item
                      name={[field.name, 'positionName']}
                      rules={[{ required: true, message: t('required') }]}
                      style={{ marginBottom: 0 }}
                    >
                      <Input placeholder={t('leave.positionName')} style={{ width: 280 }} maxLength={100} />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'bonusDays']}
                      rules={[{ required: true, message: t('required') }]}
                      style={{ marginBottom: 0 }}
                    >
                      <InputNumber min={0.0001} max={366} precision={4} style={{ width: 120 }} />
                    </Form.Item>
                    <Button type="text" danger onClick={() => removeQuota(field.name)}>
                      {t('delete')}
                    </Button>
                  </Space>
                ))}
                <Button type="dashed" block onClick={() => add({ bonusDays: 1 })}>
                  {t('leave.addPositionQuota')}
                </Button>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>
    </div>
  );
}
