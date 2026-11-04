import {
  EditOutlined,
  DeleteOutlined,
  CheckOutlined,
  CloseOutlined,
  StopOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Radio,
  Space,
  Table,
  Tabs,
  Tag,
  TimePicker,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type {
  OTConfig,
  OTConfigHistory,
  OTRequest,
} from '../api/types';
import {
  approveOTRequest,
  cancelOTRequest,
  createOTRequest,
  deleteOTRequest,
  getOTApprover,
  getOTConfig,
  getOTConfigHistory,
  listOTRequests,
  updateOTConfig,
  updateOTRequest,
} from '../api/ot';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import ActionIcon from '../components/ActionIcon';
const { Text } = Typography;

interface ConfigFormValues {
  dayStartTime: Dayjs;
  nightStartTime: Dayjs;
  postShiftBufferMinutes: number;
  preShiftBufferMinutes: number;
  breakDurationMinutes: number;
  breakDeductionThresholdMinutes: number;
  rateWeekday: number;
  rateWeekdayNight: number;
  rateWeekend: number;
  rateWeekendNight: number;
  rateHoliday: number;
  rateHolidayNight: number;
}

interface RequestFormValues {
  reason: string;
  requestType: 'SINGLE' | 'RANGE';
  otDate: Dayjs | null;
  dateRange: [Dayjs, Dayjs] | undefined;
  defaultStartTime: Dayjs | null;
  defaultEndTime: Dayjs | null;
}

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  PENDING: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
  CANCELLED: 'default',
  PENDING_CANCEL: 'warning',
  CANCELLED_APPROVED: 'warning',
};

export default function OTManagementPage() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const auth = useAuth();
  const canEditConfig = auth.hasRole('ADMIN', 'GLOBAL_LEAD');
  const canViewHistory = auth.hasRole('ADMIN', 'GLOBAL_LEAD', 'DEPT_LEAD', 'MANAGER');

  const [tab, setTab] = useState('requests');
  const [config, setConfig] = useState<OTConfig | null>(null);
  const [configLoading, setConfigLoading] = useState(false);
  const [configSaving, setConfigSaving] = useState(false);
  const [history, setHistory] = useState<OTConfigHistory[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyTotal, setHistoryTotal] = useState(0);
  const [historyPage, setHistoryPage] = useState(1);
  const [historyPageSize, setHistoryPageSize] = useState(20);

  const [requests, setRequests] = useState<OTRequest[]>([]);
  const [requestsLoading, setRequestsLoading] = useState(false);
  const [requestsTotal, setRequestsTotal] = useState(0);
  const [requestsPage, setRequestsPage] = useState(1);
  const [requestsPageSize, setRequestsPageSize] = useState(20);

  const [configForm] = Form.useForm<ConfigFormValues>();

  const [requestModalOpen, setRequestModalOpen] = useState(false);
  const [editingRequest, setEditingRequest] = useState<OTRequest | null>(null);
  const [requestForm] = Form.useForm<RequestFormValues>();
  const [approverInfo, setApproverInfo] = useState<string | null>(null);
  const [approverLoading, setApproverLoading] = useState(false);

  const [rejectModalOpen, setRejectModalOpen] = useState(false);
  const [rejectTarget, setRejectTarget] = useState<string | null>(null);
  const [rejectForm] = Form.useForm<{ reason: string }>();

  const [cancelModalOpen, setCancelModalOpen] = useState(false);
  const [cancelTarget, setCancelTarget] = useState<string | null>(null);
  const [cancelForm] = Form.useForm<{ reason: string }>();

  const loadConfig = useCallback(async () => {
    setConfigLoading(true);
    try {
      const data = await getOTConfig();
      setConfig(data);
    } catch (e) {
      message.error(errorMessage(e));
    } finally {
      setConfigLoading(false);
    }
  }, [message]);

  const loadHistory = useCallback(async (page = 0, size = 20) => {
    setHistoryLoading(true);
    try {
      const res = await getOTConfigHistory(page, size);
      setHistory(res.items);
      setHistoryTotal(res.total);
    } catch (e) {
      message.error(errorMessage(e));
    } finally {
      setHistoryLoading(false);
    }
  }, [message]);

  const loadRequests = useCallback(
    async (page = 0, size = 20) => {
      setRequestsLoading(true);
      try {
        const res = await listOTRequests(page, size);
        setRequests(res.items);
        setRequestsTotal(res.total);
      } catch (e) {
        message.error(errorMessage(e));
      } finally {
        setRequestsLoading(false);
      }
    },
    [message],
  );

  /** Reload theo trang hiện tại sau khi mutate. */
  const reloadRequests = useCallback(() => {
    void loadRequests(requestsPage - 1, requestsPageSize);
  }, [loadRequests, requestsPage, requestsPageSize]);

  useEffect(() => {
    void loadConfig();
  }, [loadConfig]);

  useEffect(() => {
    if (tab === 'history') void loadHistory(historyPage - 1, historyPageSize);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab]);

  useEffect(() => {
    if (tab === 'requests') {
      setApproverLoading(true);
      void getOTApprover()
        .then((info) => {
          setApproverInfo(
            info
              ? `${info.fullName} (${info.employeeCode})${info.position ? ` - ${info.position}` : ''}`
              : null,
          );
        })
        .catch(() => setApproverInfo(null))
        .finally(() => setApproverLoading(false));
      void loadRequests(requestsPage - 1, requestsPageSize);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab]);

  useEffect(() => {
    if (config) {
      configForm.setFieldsValue({
        dayStartTime: dayjs(`1970-01-01T${config.dayStartTime}`),
        nightStartTime: dayjs(`1970-01-01T${config.nightStartTime}`),
        postShiftBufferMinutes: config.postShiftBufferMinutes,
        preShiftBufferMinutes: config.preShiftBufferMinutes,
        breakDurationMinutes: config.breakDurationMinutes,
        breakDeductionThresholdMinutes: config.breakDeductionThresholdMinutes,
        rateWeekday: config.rateWeekday,
        rateWeekdayNight: config.rateWeekdayNight,
        rateWeekend: config.rateWeekend,
        rateWeekendNight: config.rateWeekendNight,
        rateHoliday: config.rateHoliday,
        rateHolidayNight: config.rateHolidayNight,
      });
    }
  }, [config, configForm]);

  const submitConfig = async () => {
    const values = await configForm.validateFields();
    try {
      setConfigSaving(true);
      const payload = {
        dayStartTime: values.dayStartTime.format('HH:mm:ss'),
        nightStartTime: values.nightStartTime.format('HH:mm:ss'),
        postShiftBufferMinutes: values.postShiftBufferMinutes,
        preShiftBufferMinutes: values.preShiftBufferMinutes,
        breakDurationMinutes: values.breakDurationMinutes,
        breakDeductionThresholdMinutes: values.breakDeductionThresholdMinutes,
        rateWeekday: values.rateWeekday,
        rateWeekdayNight: values.rateWeekdayNight,
        rateWeekend: values.rateWeekend,
        rateWeekendNight: values.rateWeekendNight,
        rateHoliday: values.rateHoliday,
        rateHolidayNight: values.rateHolidayNight,
      };
      await updateOTConfig(payload);
      message.success(t('ot.config.saved'));
      void loadConfig();
      void loadHistory(historyPage - 1, historyPageSize);
    } catch (e) {
      message.error(errorMessage(e));
    } finally {
      setConfigSaving(false);
    }
  };

  const handleEditHistory = (record: OTConfigHistory) => {
    configForm.setFieldsValue({
      dayStartTime: dayjs(`1970-01-01T${record.dayStartTime ?? '06:00:00'}`),
      nightStartTime: dayjs(`1970-01-01T${record.nightStartTime ?? '22:00:00'}`),
      postShiftBufferMinutes: Number(record.postShiftBufferMinutes ?? 60),
      preShiftBufferMinutes: Number(record.preShiftBufferMinutes ?? 60),
      breakDurationMinutes: Number(record.breakDurationMinutes ?? 0),
      breakDeductionThresholdMinutes: Number(record.breakDeductionThresholdMinutes ?? 240),
      rateWeekday: Number(record.rateWeekday ?? 1.5),
      rateWeekdayNight: Number(record.rateWeekdayNight ?? 2.1),
      rateWeekend: Number(record.rateWeekend ?? 2.0),
      rateWeekendNight: Number(record.rateWeekendNight ?? 2.7),
      rateHoliday: Number(record.rateHoliday ?? 3.0),
      rateHolidayNight: Number(record.rateHolidayNight ?? 3.9),
    });
    setTab('config');
    message.info(t('ot.history.editingFromHistory'));
  };

  const openCreateRequest = () => {
    setEditingRequest(null);
    requestForm.resetFields();
    requestForm.setFieldsValue({
      requestType: 'SINGLE',
      otDate: null,
      dateRange: undefined,
      defaultStartTime: null,
      defaultEndTime: null,
    });
    setRequestModalOpen(true);
  };

  const submitRequest = async () => {
    const values = await requestForm.validateFields();
    try {
      const start = values.defaultStartTime;
      const end = values.defaultEndTime;
      if (!start || !end) {
        message.error(t('ot.error.invalidTimeRange'));
        return;
      }
      if (start.isSame(end)) {
        message.error(t('ot.error.invalidTimeRange'));
        return;
      }
      // end < start → OT qua đêm (kết thúc ngày hôm sau)
      const crossesMidnight = start.isAfter(end);

      let items: { startTime: string; endTime: string }[] = [];

      if (values.requestType === 'SINGLE') {
        if (!values.otDate) {
          message.error(t('required'));
          return;
        }
        const dateStr = values.otDate.format('YYYY-MM-DD');
        const endDateStr = crossesMidnight
          ? values.otDate.add(1, 'day').format('YYYY-MM-DD')
          : dateStr;
        items = [
          {
            startTime: `${dateStr}T${start.format('HH:mm:ss')}`,
            endTime: `${endDateStr}T${end.format('HH:mm:ss')}`,
          },
        ];
      } else if (values.requestType === 'RANGE') {
        if (!values.dateRange || !values.dateRange[0] || !values.dateRange[1]) {
          message.error(t('ot.error.invalidDateRange'));
          return;
        }
        let current = values.dateRange[0].startOf('day');
        const rangeEnd = values.dateRange[1].startOf('day');
        while (current.isBefore(rangeEnd) || current.isSame(rangeEnd, 'day')) {
          const dateStr = current.format('YYYY-MM-DD');
          const endDateStr = crossesMidnight
            ? current.add(1, 'day').format('YYYY-MM-DD')
            : dateStr;
          items.push({
            startTime: `${dateStr}T${start.format('HH:mm:ss')}`,
            endTime: `${endDateStr}T${end.format('HH:mm:ss')}`,
          });
          current = current.add(1, 'day');
        }
      }

      const payload = {
        reason: values.reason,
        items,
      };

      if (editingRequest) {
        await updateOTRequest(editingRequest.id, payload);
        message.success(t('saved'));
        setEditingRequest(null);
      } else {
        await createOTRequest(payload);
        message.success(t('saved'));
      }
      setRequestModalOpen(false);
      reloadRequests();
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const handleEdit = async (record: OTRequest) => {
    setEditingRequest(record);
    requestForm.setFieldsValue({
      requestType: record.items.length > 1 ? "RANGE" : "SINGLE",
      otDate: record.items[0] ? dayjs(record.items[0].otDate) : null,
      dateRange: record.items.length > 1 ? [dayjs(record.items[0].otDate), dayjs(record.items[record.items.length - 1].otDate)] : undefined,
      defaultStartTime: record.items[0] ? dayjs(record.items[0].startTime) : null,
      defaultEndTime: record.items[0] ? dayjs(record.items[0].endTime) : null,
      reason: record.reason,
    });
    setRequestModalOpen(true);
  };

  const handleApprove = async (id: string, approved: boolean) => {
    if (approved) {
      try {
        await approveOTRequest(id, true);
        message.success(t('ot.requests.approve'));
        reloadRequests();
      } catch (e) {
        message.error(errorMessage(e));
      }
      return;
    }
    setRejectTarget(id);
    rejectForm.resetFields();
    setRejectModalOpen(true);
  };

  const submitReject = async () => {
    if (!rejectTarget) return;
    const values = await rejectForm.validateFields();
    try {
      await approveOTRequest(rejectTarget, false, values.reason);
      message.success(t('ot.requests.reject'));
      setRejectModalOpen(false);
      reloadRequests();
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const handleCancel = async (id: string) => {
    setCancelTarget(id);
    cancelForm.resetFields();
    setCancelModalOpen(true);
  };

  const submitCancel = async () => {
    if (!cancelTarget) return;
    const values = await cancelForm.validateFields();
    try {
      await cancelOTRequest(cancelTarget, values.reason);
      message.success(t('ot.requests.cancel'));
      setCancelModalOpen(false);
      reloadRequests();
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteOTRequest(id);
      message.success(t('deleted'));
      reloadRequests();
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const requestColumns: ColumnsType<OTRequest> = [
    {
      title: t('ot.requests.employee'),
      width: 180,
      ellipsis: true,
      render: (_v: string, record: OTRequest) => (
        <span style={{ lineHeight: 1.4 }}>
          <div style={{ fontWeight: 500 }}>{record.fullName}</div>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {record.employeeCode}
          </Text>
        </span>
      ),
    },
    {
      title: t('ot.requests.createdAt'),
      dataIndex: 'createdAt',
      width: 150,
      render: (v: string) => dayjs(v).format('DD/MM/YYYY HH:mm'),
    },
    {
      title: t('ot.requests.otDate'),
      width: 170,
      render: (_v: string, record: OTRequest) => {
        if (!record.items || record.items.length === 0) return '—';
        const dates = Array.from(
          new Set(record.items.map((i) => dayjs(i.otDate ?? i.startTime).format('DD/MM/YYYY'))),
        );
        return <span>{dates.join(', ')}</span>;
      },
    },
    {
      title: t('ot.requests.otTimeRange'),
      width: 150,
      render: (_v: string, record: OTRequest) => {
        if (!record.items || record.items.length === 0) return '—';
        const first = record.items[0];
        const fmt = (t: string) => dayjs(t).format('HH:mm');
        return <Text code>{fmt(first.startTime)} - {fmt(first.endTime)}</Text>;
      },
    },
    {
      title: t('ot.requests.totalHours'),
      dataIndex: 'totalHours',
      width: 100,
      align: 'right',
      render: (_v: number, record: OTRequest) => (
        <Text strong>{Number(record.totalHours ?? 0).toFixed(2)}</Text>
      ),
    },
    {
      title: t('ot.requests.reason'),
      dataIndex: 'reason',
      ellipsis: true,
      render: (v: string) => (
        <Tooltip placement="topLeft" title={v}>
          <span>{v}</span>
        </Tooltip>
      ),
    },
    {
      title: t('ot.requests.approver'),
      width: 180,
      ellipsis: true,
      render: (_v: string, record: OTRequest) => {
        if (record.approverName && record.approverEmployeeCode) {
          return (
            <span style={{ lineHeight: 1.4 }}>
              <div style={{ fontWeight: 500 }}>{record.approverName}</div>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {record.approverEmployeeCode}
              </Text>
            </span>
          );
        }
        if (record.approverId) {
          return <span>{record.approverId}</span>;
        }
        return <span>—</span>;
      },
    },
    {
      title: t('ot.requests.status'),
      dataIndex: 'status',
      width: 120,
      render: (v: string, record: OTRequest) => {
        const tag = <Tag color={STATUS_COLOR[v] ?? 'default'}>{t(`ot.status.${v}`)}</Tag>;
        const reason =
          v === 'REJECTED'
            ? record.rejectionReason
            : v === 'CANCELLED' || v === 'PENDING_CANCEL' || v === 'CANCELLED_APPROVED'
              ? record.cancellationReason
              : null;
        return reason ? (
          <Tooltip placement="topLeft" title={reason}>
            {tag}
          </Tooltip>
        ) : (
          tag
        );
      },
    },
    {
      title: t('actions'),
      width: 200,
      render: (_, record) => {
        const isCreator = record.employeeCode === auth.username;
        const isAdminOrLead = auth.hasRole('ADMIN', 'GLOBAL_LEAD');
        const isManagerial = auth.hasRole('DEPT_LEAD', 'MANAGER');
        const notSelf = record.employeeCode !== auth.username;
        // Backend tự check quan hệ duyệt (approver/manager của creator/DEPT_LEAD scope)
        const canApprove = notSelf && (isAdminOrLead || isManagerial);
        const canDelete =
          (isCreator || isAdminOrLead) &&
          (record.status === 'DRAFT' ||
            record.status === 'REJECTED' ||
            record.status === 'CANCELLED' ||
            record.status === 'CANCELLED_APPROVED');
        const actions: React.ReactNode[] = [];
        if ((record.status === 'DRAFT' || record.status === 'PENDING') && isCreator) {
          actions.push(
            <ActionIcon key="edit" title={t('ot.requests.edit')} color="#1677ff" onClick={() => void handleEdit(record)}>
              <EditOutlined />
            </ActionIcon>,
          );
        }
        if ((record.status === 'PENDING' || record.status === 'PENDING_CANCEL') && canApprove) {
          actions.push(
            <ActionIcon key="approve" title={t('ot.requests.approve')} color="#52c41a" onClick={() => handleApprove(record.id, true)}>
              <CheckOutlined />
            </ActionIcon>,
            <ActionIcon key="reject" title={t('ot.requests.reject')} color="#ff4d4f" onClick={() => handleApprove(record.id, false)}>
              <CloseOutlined />
            </ActionIcon>,
          );
        }
        if (
          (record.status === 'APPROVED' || record.status === 'PENDING') &&
          isCreator
        ) {
          actions.push(
            <Popconfirm key="cancel" title={t('ot.requests.confirmCancel')} onConfirm={() => void handleCancel(record.id)}>
              <ActionIcon title={t('ot.requests.cancel')} color="#faad14">
                <StopOutlined />
              </ActionIcon>
            </Popconfirm>,
          );
        }
        if (canDelete) {
          actions.push(
            <Popconfirm key="delete" title={t('ot.requests.confirmDelete')} onConfirm={() => void handleDelete(record.id)}>
              <ActionIcon title={t('ot.requests.delete')} color="#ff4d4f">
                <DeleteOutlined />
              </ActionIcon>
            </Popconfirm>,
          );
        }
        return <Space>{actions}</Space>;
      },
    },
  ];

  const configColumns: ColumnsType<OTConfigHistory> = [
    {
      title: t('ot.config.dayStartTime'),
      dataIndex: 'dayStartTime',
      width: 100,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: t('ot.config.nightStartTime'),
      dataIndex: 'nightStartTime',
      width: 100,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: t('ot.config.postShiftBuffer'),
      dataIndex: 'postShiftBufferMinutes',
      width: 100,
      align: 'right',
      render: (v: number | null) => v ?? '-',
    },
    {
      title: t('ot.config.preShiftBuffer'),
      dataIndex: 'preShiftBufferMinutes',
      width: 100,
      align: 'right',
      render: (v: number | null) => v ?? '-',
    },
    {
      title: t('ot.config.breakDuration'),
      dataIndex: 'breakDurationMinutes',
      width: 100,
      align: 'right',
      render: (v: number | null) => v ?? '-',
    },
    {
      title: t('ot.config.breakDeductionThreshold'),
      dataIndex: 'breakDeductionThresholdMinutes',
      width: 120,
      align: 'right',
      render: (v: number | null) => v ?? '-',
    },
    {
      title: t('ot.config.rateWeekday'),
      dataIndex: 'rateWeekday',
      width: 90,
      align: 'right',
      render: (v: string | null) => v ?? '-',
    },
    {
      title: t('ot.config.rateWeekdayNight'),
      dataIndex: 'rateWeekdayNight',
      width: 110,
      align: 'right',
      render: (v: string | null) => v ?? '-',
    },
    {
      title: t('ot.config.rateWeekend'),
      dataIndex: 'rateWeekend',
      width: 90,
      align: 'right',
      render: (v: string | null) => v ?? '-',
    },
    {
      title: t('ot.config.rateWeekendNight'),
      dataIndex: 'rateWeekendNight',
      width: 110,
      align: 'right',
      render: (v: string | null) => v ?? '-',
    },
    {
      title: t('ot.config.rateHoliday'),
      dataIndex: 'rateHoliday',
      width: 90,
      align: 'right',
      render: (v: string | null) => v ?? '-',
    },
    {
      title: t('ot.config.rateHolidayNight'),
      dataIndex: 'rateHolidayNight',
      width: 110,
      align: 'right',
      render: (v: string | null) => v ?? '-',
    },
    {
      title: t('ot.history.performedBy'),
      dataIndex: 'performedBy',
      width: 150,
      ellipsis: true,
    },
    {
      title: t('ot.history.performedAt'),
      dataIndex: 'performedAt',
      width: 160,
      render: (v: string) => dayjs(v).format('DD/MM/YYYY HH:mm'),
    },
    {
      title: t('ot.history.action'),
      dataIndex: 'action',
      width: 120,
      fixed: 'right',
      render: (_action: string, record: OTConfigHistory) => (
        <Space>
          {canEditConfig && (
            <ActionIcon
              key="edit"
              title={t('ot.history.edit')}
              color="#1677ff"
              onClick={() => handleEditHistory(record)}
            >
              <EditOutlined />
            </ActionIcon>
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
            {t('ot.title')}
          </Typography.Title>
          <Text type="secondary">{t('ot.subtitle')}</Text>
        </div>

        <Tabs
          activeKey={tab}
          onChange={setTab}
          items={[
            {
              key: 'requests',
              label: t('ot.tab.requests'),
              children: (
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                    <Button type="primary" icon={<PlusOutlined />} onClick={openCreateRequest}>
                      {t('ot.requests.create')}
                    </Button>
                  </div>
                  <Table<OTRequest>
                    rowKey="id"
                    size="small"
                    loading={requestsLoading}
                    dataSource={requests}
                    columns={requestColumns}
                    scroll={{ x: 1400 }}
                    pagination={{
                      showSizeChanger: true,
                      pageSizeOptions: [10, 20, 50, 100],
                      current: requestsPage,
                      pageSize: requestsPageSize,
                      total: requestsTotal,
                      onChange: (p, ps) => {
                        setRequestsPage(p);
                        setRequestsPageSize(ps);
                        void loadRequests(p - 1, ps);
                      },
                    }}
                    locale={{
                      emptyText: (
                        <Empty description={t('ot.requests.empty')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
                      ),
                    }}
                  />
                </Space>
              ),
            },
            {
              key: 'config',
              label: t('ot.tab.config'),
              children: (
                <Space direction="vertical" size={16} style={{ width: '100%', maxWidth: 800 }}>
                  <Text type="secondary">{t('ot.config.settingsHint')}</Text>
                  <Form form={configForm} layout="vertical" disabled={configLoading || !canEditConfig}>
                    <Space size={16} wrap style={{ width: '100%' }}>
                      <Form.Item
                        name="dayStartTime"
                        label={t('ot.config.dayStartTime')}
                        rules={[{ required: true, message: t('required') }]}
                      >
                        <TimePicker format="HH:mm" style={{ width: 160 }} />
                      </Form.Item>
                      <Form.Item
                        name="nightStartTime"
                        label={t('ot.config.nightStartTime')}
                        rules={[{ required: true, message: t('required') }]}
                      >
                        <TimePicker format="HH:mm" style={{ width: 160 }} />
                      </Form.Item>
                    </Space>
                    <Space size={16} wrap style={{ width: '100%' }}>
                      <Form.Item name="postShiftBufferMinutes" label={t('ot.config.postShiftBuffer')}>
                        <InputNumber min={0} max={240} style={{ width: 160 }} />
                      </Form.Item>
                      <Form.Item name="preShiftBufferMinutes" label={t('ot.config.preShiftBuffer')}>
                        <InputNumber min={0} max={240} style={{ width: 160 }} />
                      </Form.Item>
                      <Form.Item name="breakDurationMinutes" label={t('ot.config.breakDuration')}>
                        <InputNumber min={0} max={120} style={{ width: 160 }} />
                      </Form.Item>
                      <Form.Item
                        name="breakDeductionThresholdMinutes"
                        label={t('ot.config.breakDeductionThreshold')}
                        tooltip={t('ot.config.breakDeductionThresholdHint')}
                      >
                        <InputNumber min={0} max={1440} step={30} style={{ width: 160 }} />
                      </Form.Item>
                    </Space>
                    <Space size={16} wrap style={{ width: '100%' }}>
                      <Form.Item name="rateWeekday" label={t('ot.config.rateWeekday')}>
                        <InputNumber min={1} max={5} step={0.25} style={{ width: 160 }} />
                      </Form.Item>
                      <Form.Item name="rateWeekdayNight" label={t('ot.config.rateWeekdayNight')}>
                        <InputNumber min={1} max={5} step={0.25} style={{ width: 160 }} />
                      </Form.Item>
                    </Space>
                    <Space size={16} wrap style={{ width: '100%' }}>
                      <Form.Item name="rateWeekend" label={t('ot.config.rateWeekend')}>
                        <InputNumber min={1} max={5} step={0.25} style={{ width: 160 }} />
                      </Form.Item>
                      <Form.Item name="rateWeekendNight" label={t('ot.config.rateWeekendNight')}>
                        <InputNumber min={1} max={5} step={0.25} style={{ width: 160 }} />
                      </Form.Item>
                    </Space>
                    <Space size={16} wrap style={{ width: '100%' }}>
                      <Form.Item name="rateHoliday" label={t('ot.config.rateHoliday')}>
                        <InputNumber min={1} max={5} step={0.25} style={{ width: 160 }} />
                      </Form.Item>
                      <Form.Item name="rateHolidayNight" label={t('ot.config.rateHolidayNight')}>
                        <InputNumber min={1} max={5} step={0.25} style={{ width: 160 }} />
                      </Form.Item>
                    </Space>
                    {canEditConfig && (
                      <div>
                        <Button
                          type="primary"
                          icon={<PlusOutlined />}
                          loading={configSaving}
                          onClick={() => void submitConfig()}
                        >
                          {t('ot.config.save')}
                        </Button>
                      </div>
                    )}
                  </Form>
                </Space>
              ),
            },
            ...(canViewHistory
              ? [
                  {
                    key: 'history',
                    label: t('ot.tab.history'),
                    children: (
                      <Table<OTConfigHistory>
                        rowKey="id"
                        size="small"
                        loading={historyLoading}
                        dataSource={history}
                        columns={configColumns}
                        scroll={{ x: 1400 }}
                        pagination={{
                          showSizeChanger: true,
                          pageSizeOptions: [10, 20, 50, 100],
                          current: historyPage,
                          pageSize: historyPageSize,
                          total: historyTotal,
                          onChange: (p, ps) => {
                            setHistoryPage(p);
                            setHistoryPageSize(ps);
                            void loadHistory(p - 1, ps);
                          },
                        }}
                        locale={{
                          emptyText: (
                            <Empty description={t('ot.history.empty')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
                          ),
                        }}
                      />
                    ),
                  },
                ]
              : []),
          ]}
        />
      </Space>

      <Modal
        title={editingRequest ? t('ot.requests.editTitle') : t('ot.requests.createTitle')}
        open={requestModalOpen}
        onOk={() => void submitRequest()}
        onCancel={() => setRequestModalOpen(false)}
        okText={t('save')}
        cancelText={t('cancel')}
        width={720}
        destroyOnClose
      >
        <Form form={requestForm} layout="vertical">
          <Form.Item
            name="requestType"
            label={t('ot.requests.type')}
            initialValue="SINGLE"
            rules={[{ required: true, message: t('required') }]}
          >
            <Radio.Group style={{ width: '100%' }}>
              <Radio value="SINGLE">{t('ot.requests.typeSingle')}</Radio>
              <Radio value="RANGE">{t('ot.requests.typeRange')}</Radio>
            </Radio.Group>
          </Form.Item>

          <Form.Item noStyle shouldUpdate={(prev, curr) => prev.requestType !== curr.requestType}>
            {({ getFieldValue }) => {
              const type = getFieldValue('requestType');
              return type === 'SINGLE' ? (
                <Form.Item
                  name="otDate"
                  label={t('ot.requests.otDate')}
                  rules={[{ required: true, message: t('required') }]}
                >
                  <DatePicker style={{ width: 220 }} />
                </Form.Item>
              ) : (
                <Form.Item
                  name="dateRange"
                  label={t('ot.requests.rangeLabel')}
                  rules={[{ required: true, message: t('required') }]}
                >
                  <DatePicker.RangePicker style={{ width: 400 }} />
                </Form.Item>
              );
            }}
          </Form.Item>

          <Space size={16} wrap>
            <Form.Item
              name="defaultStartTime"
              label={t('ot.requests.defaultStartTime')}
              rules={[{ required: true, message: t('required') }]}
            >
              <TimePicker format="HH:mm" style={{ width: 180 }} />
            </Form.Item>
            <Form.Item
              name="defaultEndTime"
              label={t('ot.requests.defaultEndTime')}
              rules={[{ required: true, message: t('required') }]}
            >
              <TimePicker format="HH:mm" style={{ width: 180 }} />
            </Form.Item>
          </Space>

          <Card size="small" style={{ marginBottom: 16 }}>
            <Descriptions size="small" column={1}>
              <Descriptions.Item label={t('ot.requests.approverInfo')}>
                {approverLoading
                  ? t('loading')
                  : approverInfo
                    ? approverInfo
                    : t('ot.requests.approverNotSet')}
              </Descriptions.Item>
            </Descriptions>
          </Card>

          <Form.Item
            name="reason"
            label={t('ot.requests.reason')}
            rules={[{ required: true, message: t('ot.error.reasonRequired') }]}
          >
            <Input.TextArea rows={3} maxLength={500} placeholder={t('ot.requests.reasonPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t('ot.requests.reject')}
        open={rejectModalOpen}
        onOk={() => void submitReject()}
        onCancel={() => setRejectModalOpen(false)}
        okText={t('ot.requests.reject')}
        cancelText={t('cancel')}
        destroyOnClose
      >
        <Form form={rejectForm} layout="vertical">
          <Form.Item
            name="reason"
            label={t('ot.requests.rejectReasonPlaceholder')}
            rules={[{ required: true, message: t('required') }]}
          >
            <Input.TextArea rows={3} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t('ot.requests.cancel')}
        open={cancelModalOpen}
        onOk={() => void submitCancel()}
        onCancel={() => setCancelModalOpen(false)}
        okText={t('ot.requests.cancel')}
        cancelText={t('cancel')}
        destroyOnClose
      >
        <Form form={cancelForm} layout="vertical">
          <Form.Item
            name="reason"
            label={t('ot.requests.cancelReasonPlaceholder')}
            rules={[{ required: true, message: t('required') }]}
          >
            <Input.TextArea rows={3} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
