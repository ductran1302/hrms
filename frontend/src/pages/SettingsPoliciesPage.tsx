import {
  CheckOutlined,
  CloseOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  SaveOutlined,
  SendOutlined,
  StopOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import {
  Button,
  Card,
  DatePicker,
  Empty,
  Form,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { errorMessage } from '../api/client';
import {
  activateLeavePolicy,
  approveLeavePolicy,
  cancelLeavePolicy,
  createLeavePolicy,
  deleteLeavePolicy,
  getLeavePolicy,
  listLeavePolicies,
  rejectLeavePolicy,
  submitLeavePolicy,
  updateLeavePolicy,
  getLeaveSettings,
  updateLeaveSettings,
} from '../api/leaveQuotas';
import type {
  LeavePolicy,
  LeavePolicyCreate,
  LeaveSeniorityTier,
  LeaveSettings,
  PolicyStatusValue,
  SaturdayPolicyValue,
  SeniorityRuleValue,
} from '../api/types';
import ActionIcon from '../components/ActionIcon';
import { useAuth } from '../auth/AuthContext';

const { Text } = Typography;

/** 5 role chuẩn (Roles.java) — cùng thứ tự với màn Tài khoản. */
const ALL_ROLES = ['ADMIN', 'GLOBAL_LEAD', 'DEPT_LEAD', 'MANAGER', 'EMPLOYEE'];

const ROLE_COLORS: Record<string, string> = {
  ADMIN: 'geekblue',
  GLOBAL_LEAD: 'purple',
  DEPT_LEAD: 'cyan',
  MANAGER: 'blue',
  EMPLOYEE: 'default',
};

const STATUS_COLORS: Record<PolicyStatusValue, string> = {
  DRAFT: 'default',
  PENDING_APPROVAL: 'gold',
  APPROVED: 'blue',
  ACTIVE: 'green',
  EXPIRED: 'default',
  CANCELLED: 'red',
};

const SATURDAY_OPTIONS: SaturdayPolicyValue[] = ['OFF', 'HALF', 'ALTERNATING', 'ALL'];

const SENIORITY_RULE_OPTIONS: SeniorityRuleValue[] = ['VN_TIERED', 'LINEAR'];

/** Bậc mặc định theo luật VN: 0/5/10/15/20 năm → +0/2/4/6/8 (base 12 → 12/14/16/18/20). */
const DEFAULT_VN_TIERS: LeaveSeniorityTier[] = [
  { minYears: 0, bonusDays: 0 },
  { minYears: 5, bonusDays: 2 },
  { minYears: 10, bonusDays: 4 },
  { minYears: 15, bonusDays: 6 },
  { minYears: 20, bonusDays: 8 },
];

const sortTiers = (tiers: LeaveSeniorityTier[]): LeaveSeniorityTier[] =>
  [...tiers].sort((a, b) => a.minYears - b.minYears);

interface PolicyFormValues {
  year: number;
  effectiveFrom?: Dayjs | null;
  baseDays: number;
  seniorityRule: SeniorityRuleValue;
  seniorityStepYears: number;
  seniorityBonusDays: number;
  seniorityMaxBonus: number;
  seniorityTiers: LeaveSeniorityTier[];
  carryoverMax: number;
  roleQuotas: { role: string; bonusDays: number }[];
}

type WorkflowAction = 'submit' | 'approve' | 'reject' | 'activate' | 'cancel';

const fmt = (n: number) => n.toLocaleString('vi-VN', { maximumFractionDigits: 4 });

const fmtRange = (p: LeavePolicy) =>
  p.effectiveFrom ? `${p.effectiveFrom} → ${p.effectiveTo ?? '…'}` : '—';

/** Cài đặt > Chính sách: Tab 1 tham số global, Tab 2 chính sách hiện tại, Tab 3 lịch sử phiên bản. */
export default function SettingsPoliciesPage() {
  const { t } = useTranslation();
  const { hasRole } = useAuth();
  const canWrite = hasRole('ADMIN');
  /** Duyệt/từ chối: ADMIN hoặc GLOBAL_LEAD (duyệt cuối chính sách toàn công ty). */
  const canApprove = hasRole('ADMIN', 'GLOBAL_LEAD');

  // ---- Tab 1: tham số global ----
  const [settingsLoading, setSettingsLoading] = useState(false);
  const [settingsSaving, setSettingsSaving] = useState(false);
  const [settingsForm] = Form.useForm<LeaveSettings>();
  const [requireApproval, setRequireApproval] = useState(true);

  // ---- Tab 2: chính sách hiện tại ----
  const [year, setYear] = useState(() => new Date().getFullYear());
  const [inForce, setInForce] = useState<LeavePolicy | null>(null);
  const [versions, setVersions] = useState<LeavePolicy[]>([]);
  const [currentLoading, setCurrentLoading] = useState(false);

  // ---- Tab 3: lịch sử ----
  const [history, setHistory] = useState<LeavePolicy[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);

  // ---- Modal tạo/sửa phiên bản ----
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<LeavePolicy | null>(null);
  const [policyForm] = Form.useForm<PolicyFormValues>();

  const loadSettings = useCallback(async () => {
    setSettingsLoading(true);
    try {
      const data = await getLeaveSettings();
      settingsForm.setFieldsValue(data);
      setRequireApproval(data.requirePolicyApproval);
    } catch (e) {
      message.error(errorMessage(e));
    } finally {
      setSettingsLoading(false);
    }
  }, [settingsForm]);

  const loadCurrent = useCallback(async (y: number) => {
    setCurrentLoading(true);
    try {
      const [active, list] = await Promise.all([getLeavePolicy(y), listLeavePolicies(y)]);
      setInForce(active);
      setVersions(list);
    } catch (e) {
      message.error(errorMessage(e));
    } finally {
      setCurrentLoading(false);
    }
  }, []);

  const loadHistory = useCallback(async () => {
    setHistoryLoading(true);
    try {
      setHistory(await listLeavePolicies());
    } catch (e) {
      message.error(errorMessage(e));
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  const reloadAll = useCallback(
    (y: number) => Promise.all([loadCurrent(y), loadHistory()]),
    [loadCurrent, loadHistory],
  );

  useEffect(() => {
    void loadSettings();
    void loadCurrent(year);
    void loadHistory();
  }, [loadSettings, loadCurrent, loadHistory, year]);

  const onYearChange = (value: number | null) => {
    if (value === null || value === year) return;
    setYear(value);
    void loadCurrent(value);
  };

  const submitSettings = async () => {
    let values: LeaveSettings;
    try {
      values = await settingsForm.validateFields();
    } catch {
      return; // form chưa hợp lệ — antd đã hiện lỗi dưới ô
    }
    setSettingsSaving(true);
    try {
      const saved = await updateLeaveSettings(values);
      settingsForm.setFieldsValue(saved);
      setRequireApproval(saved.requirePolicyApproval);
      message.success(t('saved'));
    } catch (e) {
      message.error(errorMessage(e));
    } finally {
      setSettingsSaving(false);
    }
  };

  // ---- Tạo / sửa phiên bản ----

  const openCreate = () => {
    setEditing(null);
    policyForm.resetFields();
    policyForm.setFieldsValue({
      year: inForce?.year ?? year,
      effectiveFrom: null,
      baseDays: inForce?.baseDays ?? 12,
      seniorityRule: inForce?.seniorityRule ?? 'VN_TIERED',
      seniorityStepYears: inForce?.seniorityStepYears ?? 1,
      seniorityBonusDays: inForce?.seniorityBonusDays ?? 1,
      seniorityMaxBonus: inForce?.seniorityMaxBonus ?? 5,
      seniorityTiers:
        inForce && inForce.seniorityTiers.length > 0
          ? inForce.seniorityTiers.map((t) => ({ ...t }))
          : DEFAULT_VN_TIERS,
      carryoverMax: inForce?.carryoverMax ?? 5,
      roleQuotas: inForce?.roleQuotas ?? [],
    });
    setModalOpen(true);
  };

  const openEdit = (policy: LeavePolicy) => {
    setEditing(policy);
    policyForm.setFieldsValue({
      year: policy.year,
      effectiveFrom: policy.effectiveFrom ? dayjs(policy.effectiveFrom) : null,
      baseDays: policy.baseDays,
      seniorityRule: policy.seniorityRule,
      seniorityStepYears: policy.seniorityStepYears,
      seniorityBonusDays: policy.seniorityBonusDays,
      seniorityMaxBonus: policy.seniorityMaxBonus,
      seniorityTiers:
        policy.seniorityTiers.length > 0
          ? policy.seniorityTiers.map((t) => ({ ...t }))
          : DEFAULT_VN_TIERS,
      carryoverMax: policy.carryoverMax,
      roleQuotas: policy.roleQuotas,
    });
    setModalOpen(true);
  };

  const submitPolicy = async () => {
    let values: PolicyFormValues;
    try {
      values = await policyForm.validateFields();
    } catch {
      return; // form chưa hợp lệ — antd đã hiện lỗi dưới ô
    }
    const payload: LeavePolicyCreate = {
      year: values.year,
      baseDays: values.baseDays,
      seniorityRule: values.seniorityRule,
      seniorityStepYears: values.seniorityStepYears,
      seniorityBonusDays: values.seniorityBonusDays,
      seniorityMaxBonus: values.seniorityMaxBonus,
      seniorityTiers: sortTiers(values.seniorityTiers ?? []),
      carryoverMax: values.carryoverMax,
      roleQuotas: values.roleQuotas ?? [],
      effectiveFrom: values.effectiveFrom ? values.effectiveFrom.format('YYYY-MM-DD') : undefined,
      sourcePolicyId: editing === null ? inForce?.id : undefined,
    };
    try {
      if (editing === null) {
        await createLeavePolicy(payload);
      } else {
        await updateLeavePolicy(editing.id, payload);
      }
      message.success(t('saved'));
      setModalOpen(false);
      await reloadAll(year);
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  // ---- Workflow (submit → approve → activate; cancel/reject/delete) ----

  const runWorkflow = async (policy: LeavePolicy, action: WorkflowAction) => {
    try {
      switch (action) {
        case 'submit':
          await submitLeavePolicy(policy.id);
          break;
        case 'approve':
          await approveLeavePolicy(policy.id);
          break;
        case 'reject':
          await rejectLeavePolicy(policy.id);
          break;
        case 'activate':
          await activateLeavePolicy(policy.id);
          break;
        case 'cancel':
          await cancelLeavePolicy(policy.id);
          break;
      }
      message.success(t(`settings.leavePolicies.workflowDone.${action}`));
      await reloadAll(year);
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const removePolicy = async (policy: LeavePolicy) => {
    try {
      await deleteLeavePolicy(policy.id);
      message.success(t('deleted'));
      await reloadAll(year);
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const statusTag = (status: PolicyStatusValue) => (
    <Tag color={STATUS_COLORS[status]}>{t(`settings.leavePolicies.status.${status}`)}</Tag>
  );

  const workflowButton = (policy: LeavePolicy, action: WorkflowAction, label?: string) => {
    // Kích hoạt ngày quá khứ → Popconfirm cảnh báo riêng (backend cho phép retroactive).
    const pastActivate =
      action === 'activate' &&
      policy.effectiveFrom !== null &&
      dayjs(policy.effectiveFrom).isBefore(dayjs(), 'day');
    return (
      <Popconfirm
        key={action}
        title={
          pastActivate
            ? t('settings.leavePolicies.confirm.activatePast', { date: policy.effectiveFrom })
            : t(`settings.leavePolicies.confirm.${action}`, {
                year: policy.year,
                version: policy.versionNo,
              })
        }
        onConfirm={() => void runWorkflow(policy, action)}
      >
        <Button
          size="small"
          type={
            action === 'approve' || action === 'activate'
              ? 'primary'
              : action === 'reject' || action === 'cancel'
                ? 'text'
                : 'default'
          }
          danger={action === 'reject' || action === 'cancel'}
          icon={
            action === 'submit' ? (
              <SendOutlined />
            ) : action === 'approve' ? (
              <CheckOutlined />
            ) : action === 'reject' ? (
              <CloseOutlined />
            ) : action === 'activate' ? (
              <ThunderboltOutlined />
            ) : (
              <StopOutlined />
            )
          }
        >
          {label ?? t(`settings.leavePolicies.actions.${action}`)}
        </Button>
      </Popconfirm>
    );
  };

  const policyColumns: ColumnsType<LeavePolicy> = [
    {
      title: t('leave.year'),
      dataIndex: 'year',
      width: 80,
      align: 'center',
      render: (v: number) => <Text code>{v}</Text>,
    },
    {
      title: t('settings.leavePolicies.version'),
      dataIndex: 'versionNo',
      width: 85,
      align: 'center',
      render: (v: number) => <Text strong>V{v}</Text>,
    },
    {
      title: t('settings.leavePolicies.statusColumn'),
      dataIndex: 'status',
      width: 135,
      render: statusTag,
    },
    {
      title: t('settings.leavePolicies.effectiveRange'),
      width: 190,
      render: (_, p) => <Text type="secondary">{fmtRange(p)}</Text>,
    },
    {
      title: t('leave.baseDays'),
      dataIndex: 'baseDays',
      width: 105,
      align: 'right',
      render: fmt,
    },
    {
      title: t('leave.carryoverMax'),
      dataIndex: 'carryoverMax',
      width: 110,
      align: 'right',
      render: fmt,
    },
    {
      title: t('leave.roleQuotas'),
      dataIndex: 'roleQuotas',
      ellipsis: true,
      render: (quotas: LeavePolicy['roleQuotas']) =>
        quotas.length === 0
          ? '—'
          : quotas.map((q) => (
              <Tag key={q.role} color={ROLE_COLORS[q.role] ?? 'default'} style={{ marginInlineEnd: 4 }}>
                {t(`role.${q.role}`, { defaultValue: q.role })}: {fmt(q.bonusDays)}
              </Tag>
            )),
    },
    {
      title: t('settings.leavePolicies.createdBy'),
      dataIndex: 'createdBy',
      width: 110,
      ellipsis: true,
      render: (v: string | null) => v ?? '—',
    },
    {
      title: t('actions'),
      width: 300,
      render: (_, record) => {
        if (!canWrite && !canApprove) return null;
        const space: ReactNode[] = [];
        if (record.status === 'DRAFT') {
          if (!canWrite) return null;
          space.push(
            <ActionIcon
              key="edit"
              title={t('edit')}
              color="#1677ff"
              onClick={() => openEdit(record)}
            >
              <EditOutlined />
            </ActionIcon>,
          );
          space.push(
            requireApproval
              ? workflowButton(record, 'submit')
              : workflowButton(record, 'activate'),
          );
          space.push(
            <Popconfirm
              key="delete"
              title={t('settings.leavePolicies.confirm.delete', {
                year: record.year,
                version: record.versionNo,
              })}
              onConfirm={() => void removePolicy(record)}
            >
              <ActionIcon title={t('delete')} color="#ff4d4f">
                <DeleteOutlined />
              </ActionIcon>
            </Popconfirm>,
          );
        } else if (record.status === 'PENDING_APPROVAL') {
          if (canApprove) {
            space.push(workflowButton(record, 'approve'), workflowButton(record, 'reject'));
          }
          if (canWrite) {
            space.push(workflowButton(record, 'cancel'));
          }
        } else if (record.status === 'APPROVED') {
          if (canWrite) {
            space.push(workflowButton(record, 'activate'), workflowButton(record, 'cancel'));
          }
        }
        return <Space wrap>{space}</Space>;
      },
    },
  ];

  const tabSettings = (
    <Space direction="vertical" size={16} style={{ width: '100%', maxWidth: 720 }}>
      <Text type="secondary">{t('settings.leavePolicies.settingsHint')}</Text>
      <Form form={settingsForm} layout="vertical" disabled={settingsLoading}>
        <Space size={16} wrap>
          <Form.Item
            name="fyStartMonth"
            label={t('settings.leavePolicies.fyStartMonth')}
            rules={[{ required: true, message: t('required') }]}
          >
            <InputNumber min={1} max={12} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item
            name="fyStartDay"
            label={t('settings.leavePolicies.fyStartDay')}
            rules={[{ required: true, message: t('required') }]}
          >
            <InputNumber min={1} max={31} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item
            name="minWorkPercent"
            label={t('settings.leavePolicies.minWorkPercent')}
            rules={[{ required: true, message: t('required') }]}
            tooltip={t('settings.leavePolicies.minWorkPercentHint')}
          >
            <InputNumber min={1} max={100} step={1} style={{ width: 160 }} addonAfter="%" />
          </Form.Item>
        </Space>
        <Space size={16} wrap>
          <Form.Item
            name="periodStartDay"
            label={t('settings.leavePolicies.periodStartDay')}
            rules={[{ required: true, message: t('required') }]}
            tooltip={t('settings.leavePolicies.periodComboHint')}
          >
            <InputNumber min={1} max={31} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item
            name="periodEndDay"
            label={t('settings.leavePolicies.periodEndDay')}
            rules={[{ required: true, message: t('required') }]}
            tooltip={t('settings.leavePolicies.periodComboHint')}
          >
            <InputNumber min={1} max={31} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item
            name="saturdayPolicy"
            label={t('settings.leavePolicies.saturdayPolicy')}
            rules={[{ required: true, message: t('required') }]}
          >
            <Select
              style={{ width: 180 }}
              options={SATURDAY_OPTIONS.map((v) => ({
                value: v,
                label: t(`settings.leavePolicies.saturday.${v}`),
              }))}
            />
          </Form.Item>
        </Space>
        <Space size={16} wrap>
          <Form.Item
            name="requirePolicyApproval"
            label={t('settings.leavePolicies.requirePolicyApproval')}
            valuePropName="checked"
            tooltip={t('settings.leavePolicies.requirePolicyApprovalHint')}
          >
            <Switch />
          </Form.Item>
        </Space>
        <div>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={settingsSaving}
            onClick={() => void submitSettings()}
          >
            {t('save')}
          </Button>
        </div>
      </Form>
    </Space>
  );

  const tabCurrent = (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space wrap>
        <Text type="secondary">{t('settings.leavePolicies.currentHint')}</Text>
        <Text>{t('settings.leavePolicies.yearLabel')}</Text>
        <InputNumber
          min={2020}
          max={2100}
          value={year}
          onChange={onYearChange}
          style={{ width: 100 }}
        />
        <Button
          type="primary"
          icon={<PlusOutlined />}
          disabled={!canWrite || inForce === null}
          onClick={openCreate}
        >
          {t('settings.leavePolicies.newVersion')}
        </Button>
      </Space>
      <Card size="small" loading={currentLoading}>
        {inForce === null ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            <Space wrap>
              <Text strong>V{inForce.versionNo}</Text>
              {statusTag(inForce.status)}
              <Text type="secondary">{fmtRange(inForce)}</Text>
            </Space>
            <Space size={24} wrap>
              <Text>
                {t('leave.baseDays')}: <Text code>{fmt(inForce.baseDays)}</Text>
              </Text>
              <Text>
                {t('leave.seniorityRule')}:{' '}
                <Tag color={inForce.seniorityRule === 'VN_TIERED' ? 'green' : 'blue'}>
                  {t(`leave.seniorityRule_${inForce.seniorityRule}`)}
                </Tag>
              </Text>
              {inForce.seniorityRule === 'LINEAR' ? (
                <>
                  <Text>
                    {t('leave.seniorityStepYears')}:{' '}
                    <Text code>{inForce.seniorityStepYears}</Text>
                  </Text>
                  <Text>
                    {t('leave.seniorityBonusDays')}:{' '}
                    <Text code>{fmt(inForce.seniorityBonusDays)}</Text>
                  </Text>
                  <Text>
                    {t('leave.seniorityMaxBonus')}: <Text code>{fmt(inForce.seniorityMaxBonus)}</Text>
                  </Text>
                </>
              ) : (
                <Text>
                  {t('leave.seniorityTiers')}:{' '}
                  {inForce.seniorityTiers.length === 0 ? (
                    <Text type="secondary">—</Text>
                  ) : (
                    sortTiers(inForce.seniorityTiers).map((tier) => (
                      <Tag key={tier.minYears} style={{ marginInlineEnd: 4 }}>
                        ≥{tier.minYears}: +{fmt(tier.bonusDays)}
                      </Tag>
                    ))
                  )}
                </Text>
              )}
              <Text>
                {t('leave.carryoverMax')}: <Text code>{fmt(inForce.carryoverMax)}</Text>
              </Text>
            </Space>
            <Space size={4} wrap>
              <Text type="secondary">{t('leave.roleQuotas')}:</Text>
              {inForce.roleQuotas.length === 0 ? (
                <Text type="secondary">—</Text>
              ) : (
                inForce.roleQuotas.map((q) => (
                  <Tag key={q.role} color={ROLE_COLORS[q.role] ?? 'default'}>
                    {t(`role.${q.role}`, { defaultValue: q.role })}: {fmt(q.bonusDays)}
                  </Tag>
                ))
              )}
            </Space>
          </Space>
        )}
      </Card>
      <Table<LeavePolicy>
        rowKey="id"
        size="small"
        loading={currentLoading}
        columns={policyColumns}
        dataSource={versions}
        scroll={{ x: 1250 }}
        pagination={false}
        locale={{
          emptyText: (
            <Empty
              description={t('settings.leavePolicies.empty')}
              image={Empty.PRESENTED_IMAGE_SIMPLE}
            />
          ),
        }}
      />
    </Space>
  );

  const tabHistory = (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Text type="secondary">{t('settings.leavePolicies.historyHint')}</Text>
      <Table<LeavePolicy>
        rowKey="id"
        size="small"
        loading={historyLoading}
        columns={policyColumns}
        dataSource={history}
        scroll={{ x: 1250 }}
        pagination={{ pageSize: 20, hideOnSinglePage: true }}
        locale={{
          emptyText: (
            <Empty
              description={t('settings.leavePolicies.empty')}
              image={Empty.PRESENTED_IMAGE_SIMPLE}
            />
          ),
        }}
      />
    </Space>
  );

  const items = [
    { key: 'settings', label: t('settings.leavePolicies.tabSettings'), children: tabSettings },
    { key: 'current', label: t('settings.leavePolicies.tabCurrent'), children: tabCurrent },
    { key: 'history', label: t('settings.leavePolicies.tabHistory'), children: tabHistory },
  ];

  return (
    <div>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {t('settings.leavePolicies.title')}
          </Typography.Title>
          <Text type="secondary">{t('settings.leavePolicies.subtitle')}</Text>
        </div>
        <Tabs items={items} />
      </Space>

      <Form form={policyForm} layout="vertical">
        <Modal
          title={
            editing !== null
              ? t('settings.leavePolicies.editTitle', {
                  year: editing.year,
                  version: editing.versionNo,
                })
              : t('settings.leavePolicies.createTitle', { year: policyForm.getFieldValue('year') })
          }
          open={modalOpen}
          onOk={() => void submitPolicy()}
          onCancel={() => setModalOpen(false)}
          okText={t('save')}
          cancelText={t('cancel')}
          width={640}
          destroyOnClose
        >
          <Space size={12} style={{ display: 'flex' }} align="start">
            <Form.Item
              name="year"
              label={t('leave.year')}
              rules={[{ required: true, message: t('required') }]}
            >
              <InputNumber min={2020} max={2100} style={{ width: 110 }} disabled />
            </Form.Item>
            <Form.Item
              name="effectiveFrom"
              label={t('settings.leavePolicies.effectiveFrom')}
              tooltip={t('settings.leavePolicies.effectiveFromHint')}
            >
              <DatePicker format="YYYY-MM-DD" style={{ width: 170 }} />
            </Form.Item>
            <Form.Item
              name="baseDays"
              label={t('leave.baseDays')}
              rules={[{ required: true, message: t('required') }]}
            >
              <InputNumber min={0} max={366} precision={2} style={{ width: 140 }} />
            </Form.Item>
          </Space>
          <Space size={12} style={{ display: 'flex' }} align="start">
            <Form.Item
              name="carryoverMax"
              label={t('leave.carryoverMax')}
              rules={[{ required: true, message: t('required') }]}
            >
              <InputNumber min={0} max={366} precision={2} style={{ width: 140 }} />
            </Form.Item>
            <Form.Item
              name="seniorityRule"
              label={t('leave.seniorityRule')}
              tooltip={t('leave.seniorityRuleHint')}
              rules={[{ required: true, message: t('required') }]}
            >
              <Select
                style={{ width: 280 }}
                options={SENIORITY_RULE_OPTIONS.map((v) => ({
                  value: v,
                  label: t(`leave.seniorityRule_${v}`),
                }))}
              />
            </Form.Item>
          </Space>

          <Form.Item noStyle shouldUpdate={(prev, cur) => prev.seniorityRule !== cur.seniorityRule}>
            {() =>
              policyForm.getFieldValue('seniorityRule') === 'LINEAR' ? (
                <Space size={12} style={{ display: 'flex' }} align="start">
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
                    <InputNumber min={0} max={366} precision={2} style={{ width: 140 }} />
                  </Form.Item>
                  <Form.Item
                    name="seniorityMaxBonus"
                    label={t('leave.seniorityMaxBonus')}
                    rules={[{ required: true, message: t('required') }]}
                  >
                    <InputNumber min={0} max={366} precision={2} style={{ width: 140 }} />
                  </Form.Item>
                </Space>
              ) : (
                <div>
                  <Text strong>{t('leave.seniorityTiers')}</Text>
                  <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
                    {t('leave.seniorityTiersHint')}
                  </Text>
                  <Form.List name="seniorityTiers">
                    {(fields, { add, remove: removeTier }) => (
                      <>
                        {fields.map((field) => (
                          <Space
                            key={field.key}
                            align="baseline"
                            style={{ display: 'flex', marginBottom: 8, marginTop: 8 }}
                          >
                            <Form.Item
                              name={[field.name, 'minYears']}
                              rules={[{ required: true, message: t('required') }]}
                              style={{ marginBottom: 0 }}
                            >
                              <InputNumber min={0} max={100} style={{ width: 130 }} />
                            </Form.Item>
                            <Form.Item
                              name={[field.name, 'bonusDays']}
                              rules={[{ required: true, message: t('required') }]}
                              style={{ marginBottom: 0 }}
                            >
                              <InputNumber min={0} max={366} precision={2} style={{ width: 130 }} />
                            </Form.Item>
                            <Button type="text" danger onClick={() => removeTier(field.name)}>
                              {t('delete')}
                            </Button>
                          </Space>
                        ))}
                        <Button
                          type="dashed"
                          block
                          onClick={() => {
                            const tiers: LeaveSeniorityTier[] =
                              policyForm.getFieldValue('seniorityTiers') ?? [];
                            const next =
                              tiers.length > 0
                                ? Math.max(...tiers.map((x) => x.minYears)) + 5
                                : 0;
                            add({ minYears: next, bonusDays: 0 });
                          }}
                        >
                          {t('leave.addTier')}
                        </Button>
                      </>
                    )}
                  </Form.List>
                </div>
              )
            }
          </Form.Item>

          <Text strong>{t('leave.roleQuotas')}</Text>
          <Form.List name="roleQuotas">
            {(fields, { add, remove: removeQuota }) => (
              <>
                {fields.map((field) => (
                  <Space
                    key={field.key}
                    align="baseline"
                    style={{ display: 'flex', marginBottom: 8, marginTop: 8 }}
                  >
                    <Form.Item
                      name={[field.name, 'role']}
                      rules={[{ required: true, message: t('required') }]}
                      style={{ marginBottom: 0 }}
                    >
                      <Select
                        style={{ width: 220 }}
                        placeholder={t('settings.leavePolicies.selectRole')}
                        options={ALL_ROLES.map((r) => ({
                          value: r,
                          label: `${r} — ${t(`role.${r}`)}`,
                        }))}
                      />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'bonusDays']}
                      rules={[{ required: true, message: t('required') }]}
                      style={{ marginBottom: 0 }}
                    >
                      <InputNumber min={0.0001} max={366} precision={2} style={{ width: 120 }} />
                    </Form.Item>
                    <Button type="text" danger onClick={() => removeQuota(field.name)}>
                      {t('delete')}
                    </Button>
                  </Space>
                ))}
                <Button
                  type="dashed"
                  block
                  onClick={() => add({ bonusDays: 1, role: 'DEPT_LEAD' })}
                >
                  {t('leave.addRoleQuota')}
                </Button>
              </>
            )}
          </Form.List>
        </Modal>
      </Form>
    </div>
  );
}
