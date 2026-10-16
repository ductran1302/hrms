import {
  CheckOutlined,
  ClockCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  StopOutlined,
} from '@ant-design/icons';
import {
  Button,
  DatePicker,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Segmented,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  TimePicker,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { errorMessage } from '../api/client';
import { searchEmployees } from '../api/employees';
import type { ShiftAssignment, ShiftTypeValue, WorkShift } from '../api/types';
import {
  assignShift,
  createWorkShift,
  deleteWorkShift,
  listShiftAssignments,
  listWorkShifts,
  removeShiftAssignment,
  updateWorkShift,
} from '../api/workShifts';
import { useAuth } from '../auth/AuthContext';
import ActionIcon from '../components/ActionIcon';

const { Text } = Typography;

interface ShiftFormValues {
  name: string;
  shiftType: ShiftTypeValue;
  workRange: [Dayjs, Dayjs];
  breakRange?: [Dayjs, Dayjs];
  workingHours: number;
  breakingHours?: number;
  active: boolean;
}

interface AssignFormValues {
  employeeId: string;
  workShiftId: string;
  effectiveFrom: Dayjs;
}

/** 480 phút → '8:00', 450 → '7:30' (cột hiển thị giờ:phút). */
const fmtDuration = (minutes: number) => {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return m === 0 ? `${h}:00` : `${h}:${String(m).padStart(2, '0')}`;
};

const hm = (time: string | null | undefined) => (time ? time.slice(0, 5) : null);

export default function WorkShiftPage() {
  const { t } = useTranslation();
  const auth = useAuth();
  const canWrite = auth.hasRole('HR_ADMIN');

  const [tab, setTab] = useState('shifts');
  const [q, setQ] = useState('');
  const [assignQ, setAssignQ] = useState('');

  const [shifts, setShifts] = useState<WorkShift[]>([]);
  const [shiftLoading, setShiftLoading] = useState(false);
  const [assignments, setAssignments] = useState<ShiftAssignment[]>([]);
  const [assignLoading, setAssignLoading] = useState(false);

  const [shiftModalOpen, setShiftModalOpen] = useState(false);
  const [editing, setEditing] = useState<WorkShift | null>(null);
  const [shiftForm] = Form.useForm<ShiftFormValues>();

  const [assignOpen, setAssignOpen] = useState(false);
  const [assignForm] = Form.useForm<AssignFormValues>();
  const [empOptions, setEmpOptions] = useState<{ value: string; label: string }[]>([]);
  const watchedShiftType = Form.useWatch('shiftType', shiftForm);

  const loadShifts = useCallback(async () => {
    setShiftLoading(true);
    try {
      setShifts(await listWorkShifts({ q: q || undefined }));
    } catch (e) {
      message.error(errorMessage(e));
    } finally {
      setShiftLoading(false);
    }
  }, [q]);

  const loadAssignments = useCallback(async () => {
    setAssignLoading(true);
    try {
      setAssignments(await listShiftAssignments({ q: assignQ || undefined }));
    } catch (e) {
      message.error(errorMessage(e));
    } finally {
      setAssignLoading(false);
    }
  }, [assignQ]);

  useEffect(() => {
    void loadShifts();
  }, [loadShifts]);

  useEffect(() => {
    void loadAssignments();
  }, [loadAssignments]);

  const searchEmps = async (keyword: string) => {
    try {
      const page = await searchEmployees({ q: keyword || undefined, size: 20 });
      setEmpOptions(page.items.map((e) => ({ value: e.id, label: `${e.employeeCode} — ${e.fullName}` })));
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const openCreate = () => {
    setEditing(null);
    shiftForm.resetFields();
    shiftForm.setFieldsValue({ shiftType: 'FIXED', active: true, breakingHours: 1 });
    setShiftModalOpen(true);
  };

  const openEdit = (shift: WorkShift) => {
    setEditing(shift);
    shiftForm.setFieldsValue({
      name: shift.name,
      shiftType: shift.shiftType,
      workRange: [
        dayjs(`1970-01-01T${shift.startWorkTime}`),
        dayjs(`1970-01-01T${shift.endWorkTime}`),
      ],
      breakRange:
        shift.startBreakTime && shift.endBreakTime
          ? [
              dayjs(`1970-01-01T${shift.startBreakTime}`),
              dayjs(`1970-01-01T${shift.endBreakTime}`),
            ]
          : undefined,
      workingHours: shift.workingTime / 60,
      breakingHours: shift.breakingTime / 60,
      active: shift.active,
    });
    setShiftModalOpen(true);
  };

  const submitShift = async () => {
    const values = await shiftForm.validateFields();
    const body = {
      name: values.name,
      shiftType: values.shiftType,
      startWorkTime: values.workRange[0].format('HH:mm:ss'),
      endWorkTime: values.workRange[1].format('HH:mm:ss'),
      startBreakTime: values.breakRange ? values.breakRange[0].format('HH:mm:ss') : null,
      endBreakTime: values.breakRange ? values.breakRange[1].format('HH:mm:ss') : null,
      workingTime: Math.round(values.workingHours * 60),
      breakingTime: Math.round((values.breakingHours ?? 0) * 60),
      active: values.active,
    };
    try {
      if (editing) {
        await updateWorkShift(editing.id, body);
      } else {
        await createWorkShift(body);
      }
      message.success(t('saved'));
      setShiftModalOpen(false);
      await loadShifts();
      await loadAssignments();
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const toggleActive = async (shift: WorkShift) => {
    try {
      await updateWorkShift(shift.id, { active: !shift.active });
      message.success(t('saved'));
      await loadShifts();
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const removeShift = async (id: string) => {
    try {
      await deleteWorkShift(id);
      message.success(t('deleted'));
      await loadShifts();
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const submitAssign = async () => {
    const values = await assignForm.validateFields();
    try {
      await assignShift({
        employeeId: values.employeeId,
        workShiftId: values.workShiftId,
        effectiveFrom: values.effectiveFrom.format('YYYY-MM-DD'),
      });
      message.success(t('saved'));
      setAssignOpen(false);
      await loadAssignments();
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const removeAssign = async (id: string) => {
    try {
      await removeShiftAssignment(id);
      message.success(t('deleted'));
      await loadAssignments();
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const shiftColumns: ColumnsType<WorkShift> = [
    { title: t('shift.column.name'), dataIndex: 'name', width: 170, ellipsis: true },
    {
      title: t('shift.column.type'),
      dataIndex: 'shiftType',
      width: 95,
      render: (v: ShiftTypeValue) => (
        <Tag color={v === 'FIXED' ? 'blue' : 'orange'}>{t(`shift.type.${v}`)}</Tag>
      ),
    },
    {
      title: t('shift.column.timeRange'),
      width: 110,
      render: (_, s) => (
        <Text code>
          {hm(s.startWorkTime)} - {hm(s.endWorkTime)}
        </Text>
      ),
    },
    {
      title: t('shift.column.breakRange'),
      width: 105,
      render: (_, s) =>
        hm(s.startBreakTime) && hm(s.endBreakTime) ? (
          <Text code>
            {hm(s.startBreakTime)} - {hm(s.endBreakTime)}
          </Text>
        ) : (
          '—'
        ),
    },
    {
      title: t('shift.column.workingTime'),
      dataIndex: 'workingTime',
      width: 90,
      align: 'right',
      render: (v: number) => <Text strong>{fmtDuration(v)}</Text>,
    },
    {
      title: t('shift.column.breakingTime'),
      dataIndex: 'breakingTime',
      width: 90,
      align: 'right',
      render: (v: number) => fmtDuration(v),
    },
    {
      title: t('shift.column.active'),
      dataIndex: 'active',
      width: 90,
      render: (v: boolean) =>
        v ? (
          <Tag color="green">{t('shift.status.active')}</Tag>
        ) : (
          <Tag>{t('shift.status.inactive')}</Tag>
        ),
    },
    {
      title: t('shift.column.updated'),
      dataIndex: 'updatedAt',
      width: 120,
      render: (v: string) => (v ? dayjs(v).format('DD/MM/YYYY') : '—'),
    },
    {
      title: t('actions'),
      width: 130,
      render: (_, s) =>
        canWrite ? (
          <Space>
            <ActionIcon title={t('shift.action.edit')} color="#1677ff" onClick={() => openEdit(s)}>
              <EditOutlined />
            </ActionIcon>
            <ActionIcon
              title={s.active ? t('shift.action.deactivate') : t('shift.action.activate')}
              color="#faad14"
              onClick={() => void toggleActive(s)}
            >
              {s.active ? <StopOutlined /> : <CheckOutlined />}
            </ActionIcon>
            <Popconfirm title={t('shift.action.confirmDelete')} onConfirm={() => void removeShift(s.id)}>
              <ActionIcon title={t('shift.action.delete')} color="#ff4d4f">
                <DeleteOutlined />
              </ActionIcon>
            </Popconfirm>
          </Space>
        ) : null,
    },
  ];

  const assignmentColumns: ColumnsType<ShiftAssignment> = [
    { title: t('shift.column.employeeCode'), dataIndex: 'employeeCode', width: 100 },
    { title: t('shift.column.fullName'), dataIndex: 'fullName', width: 170, ellipsis: true },
    {
      title: t('shift.column.department'),
      dataIndex: 'departmentName',
      width: 140,
      ellipsis: true,
      render: (v: string | null) => v ?? '—',
    },
    { title: t('shift.column.shift'), dataIndex: 'shiftName', width: 170, ellipsis: true },
    {
      title: t('shift.column.type'),
      dataIndex: 'shiftType',
      width: 95,
      render: (v: ShiftTypeValue) => (
        <Tag color={v === 'FIXED' ? 'blue' : 'orange'}>{t(`shift.type.${v}`)}</Tag>
      ),
    },
    {
      title: t('shift.column.effectiveFrom'),
      dataIndex: 'effectiveFrom',
      width: 110,
      render: (v: string) => dayjs(v).format('DD/MM/YYYY'),
    },
    {
      title: t('shift.column.assignedBy'),
      dataIndex: 'assignedBy',
      width: 100,
      render: (v: string | null) => v ?? '—',
    },
    {
      title: t('shift.column.current'),
      dataIndex: 'latest',
      width: 90,
      render: (v: boolean) => (v ? <Tag color="blue">{t('shift.column.current')}</Tag> : null),
    },
    {
      title: t('actions'),
      width: 70,
      render: (_, a) =>
        canWrite && a.latest ? (
          <Popconfirm title={t('shift.action.confirmUnassign')} onConfirm={() => void removeAssign(a.id)}>
            <ActionIcon title={t('shift.action.unassign')} color="#ff4d4f">
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
            {t('shift.title')}
          </Typography.Title>
          <Text type="secondary">{t('shift.subtitle')}</Text>
        </div>

        <Tabs
          activeKey={tab}
          onChange={setTab}
          items={[
            {
              key: 'shifts',
              label: t('shift.tab.shifts'),
              children: (
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  <Space wrap>
                    <Input.Search
                      allowClear
                      placeholder={t('shift.searchPlaceholder')}
                      style={{ width: 260 }}
                      onSearch={setQ}
                    />
                    {canWrite && (
                      <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                        {t('shift.action.create')}
                      </Button>
                    )}
                  </Space>
                  <Table<WorkShift>
                    rowKey="id"
                    size="small"
                    loading={shiftLoading}
                    columns={shiftColumns}
                    dataSource={shifts}
                    scroll={{ x: 1100 }}
                    pagination={{ pageSize: 20, hideOnSinglePage: true }}
                    locale={{
                      emptyText: (
                        <Empty description={t('shift.emptyShifts')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
                      ),
                    }}
                  />
                </Space>
              ),
            },
            {
              key: 'assignments',
              label: t('shift.tab.assignments'),
              children: (
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  <Space wrap>
                    <Input.Search
                      allowClear
                      placeholder={t('shift.searchAssignPlaceholder')}
                      style={{ width: 260 }}
                      onSearch={setAssignQ}
                    />
                    {canWrite && (
                      <Button
                        type="primary"
                        icon={<ClockCircleOutlined />}
                        onClick={() => {
                          assignForm.resetFields();
                          void searchEmps('');
                          setAssignOpen(true);
                        }}
                      >
                        {t('shift.action.assign')}
                      </Button>
                    )}
                  </Space>
                  <Table<ShiftAssignment>
                    rowKey="id"
                    size="small"
                    loading={assignLoading}
                    columns={assignmentColumns}
                    dataSource={assignments}
                    scroll={{ x: 1150 }}
                    pagination={{ pageSize: 20, hideOnSinglePage: true }}
                    locale={{
                      emptyText: (
                        <Empty
                          description={t('shift.emptyAssignments')}
                          image={Empty.PRESENTED_IMAGE_SIMPLE}
                        />
                      ),
                    }}
                  />
                </Space>
              ),
            },
          ]}
        />
      </Space>

      <Modal
        title={editing ? t('shift.action.edit') : t('shift.action.create')}
        open={shiftModalOpen}
        onOk={() => void submitShift()}
        onCancel={() => setShiftModalOpen(false)}
        okText={t('save')}
        cancelText={t('cancel')}
        width={620}
        destroyOnClose
      >
        <Form form={shiftForm} layout="vertical">
          <Form.Item
            name="name"
            label={t('shift.form.name')}
            rules={[{ required: true, message: t('required') }]}
          >
            <Input maxLength={100} placeholder={t('shift.form.namePlaceholder')} />
          </Form.Item>
          <Form.Item name="shiftType" label={t('shift.form.shiftType')}>
            <Segmented
              options={[
                { label: t('shift.type.FIXED'), value: 'FIXED' },
                { label: t('shift.type.FLEXIBLE'), value: 'FLEXIBLE' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="workRange"
            label={t('shift.form.workTime')}
            rules={[{ required: true, message: t('required') }]}
          >
            <TimePicker.RangePicker format="HH:mm:ss" style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="breakRange" label={t('shift.form.breakTime')}>
            <TimePicker.RangePicker
              format="HH:mm:ss"
              allowEmpty={[false, false]}
              style={{ width: '100%' }}
            />
          </Form.Item>
          <Space size={12} style={{ display: 'flex' }}>
            <Form.Item
              name="workingHours"
              label={t('shift.form.workingHours')}
              rules={[{ required: true, message: t('required') }]}
              extra={
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {watchedShiftType === 'FLEXIBLE' ? t('shift.hint.flexible') : t('shift.hint.fixed')}
                </Text>
              }
            >
              <InputNumber min={0.25} max={24} step={0.25} style={{ width: 150 }} />
            </Form.Item>
            <Form.Item name="breakingHours" label={t('shift.form.breakingHours')}>
              <InputNumber min={0} max={6} step={0.25} style={{ width: 150 }} />
            </Form.Item>
            <Form.Item name="active" label={t('shift.form.active')} valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {t('shift.form.minutesNote')}
          </Text>
        </Form>
      </Modal>

      <Modal
        title={t('shift.action.assignTitle')}
        open={assignOpen}
        onOk={() => void submitAssign()}
        onCancel={() => setAssignOpen(false)}
        okText={t('save')}
        cancelText={t('cancel')}
        destroyOnClose
      >
        <Form form={assignForm} layout="vertical">
          <Form.Item
            name="employeeId"
            label={t('shift.assignForm.employee')}
            rules={[{ required: true, message: t('required') }]}
          >
            <Select
              showSearch
              filterOption={false}
              options={empOptions}
              onSearch={(kw) => void searchEmps(kw)}
              placeholder={t('shift.assignForm.employeePlaceholder')}
              notFoundContent={t('shift.assignForm.searchHint')}
            />
          </Form.Item>
          <Form.Item
            name="workShiftId"
            label={t('shift.assignForm.shift')}
            rules={[{ required: true, message: t('required') }]}
          >
            <Select
              options={shifts
                .filter((s) => s.active)
                .map((s) => ({
                  value: s.id,
                  label: `${s.name} (${hm(s.startWorkTime)}-${hm(s.endWorkTime)})`,
                }))}
              placeholder={t('shift.assignForm.shiftPlaceholder')}
            />
          </Form.Item>
          <Form.Item
            name="effectiveFrom"
            label={t('shift.assignForm.effectiveFrom')}
            rules={[{ required: true, message: t('required') }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
