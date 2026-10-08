import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import {
  App as AntApp,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Tag,
  Typography,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { errorMessage } from '../api/client';
import { createContract, listContracts, terminateContract, updateContract } from '../api/contracts';
import { searchEmployees } from '../api/employees';
import type { Contract, ContractStatusValue, ContractTypeValue, Employee } from '../api/types';
import { useAuth } from '../auth/AuthContext';

const TYPE_LABEL: Record<ContractTypeValue, string> = {
  PROBATION: 'Thử việc',
  FULL_TIME: 'Chính thức',
  PART_TIME: 'Bán thời gian',
  INTERN: 'Thực tập',
  OTHER: 'Khác',
};

const STATUS_META: Record<ContractStatusValue, { label: string; color: string }> = {
  ACTIVE: { label: 'Hiệu lực', color: 'green' },
  EXPIRED: { label: 'Hết hạn', color: 'orange' },
  TERMINATED: { label: 'Chấm dứt', color: 'red' },
};

interface FormValues {
  employeeId?: string;
  contractType: ContractTypeValue;
  startDate: Dayjs;
  endDate?: Dayjs | null;
  position?: string;
  salary?: number;
  signDate?: Dayjs | null;
  note?: string;
  status?: ContractStatusValue;
}

const vnd = (value: number | null) =>
  value == null ? null : new Intl.NumberFormat('vi-VN').format(value);

export default function HopDongPage() {
  const { message } = AntApp.useApp();
  const auth = useAuth();
  const actionRef = useRef<ActionType>(null);
  const [form] = Form.useForm<FormValues>();
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Contract | null>(null);
  const [saving, setSaving] = useState(false);
  const [employeeFilter, setEmployeeFilter] = useState<string | undefined>(undefined);
  const [employees, setEmployees] = useState<Employee[]>([]);

  const canWrite = auth.hasRole('HR_ADMIN');

  useEffect(() => {
    searchEmployees({ size: 500 })
      .then((page) => setEmployees(page.items))
      .catch(() => setEmployees([]));
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ contractType: 'FULL_TIME', startDate: dayjs() });
    setModalOpen(true);
  };

  const openEdit = (record: Contract) => {
    setEditing(record);
    form.setFieldsValue({
      contractType: record.contractType,
      startDate: dayjs(record.startDate),
      endDate: record.endDate ? dayjs(record.endDate) : null,
      position: record.position ?? undefined,
      salary: record.salary ?? undefined,
      signDate: record.signDate ? dayjs(record.signDate) : null,
      note: record.note ?? undefined,
      status: record.status,
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await updateContract(editing.id, {
          contractType: values.contractType,
          startDate: values.startDate.format('YYYY-MM-DD'),
          endDate: values.endDate ? values.endDate.format('YYYY-MM-DD') : undefined,
          position: values.position,
          salary: values.salary,
          signDate: values.signDate ? values.signDate.format('YYYY-MM-DD') : undefined,
          note: values.note,
          status: values.status,
        });
        message.success('Đã cập nhật hợp đồng');
      } else {
        if (!values.employeeId) {
          message.error('Chọn nhân viên');
          setSaving(false);
          return;
        }
        await createContract(values.employeeId, {
          contractType: values.contractType,
          startDate: values.startDate.format('YYYY-MM-DD'),
          endDate: values.endDate ? values.endDate.format('YYYY-MM-DD') : undefined,
          position: values.position,
          salary: values.salary,
          signDate: values.signDate ? values.signDate.format('YYYY-MM-DD') : undefined,
          note: values.note,
        });
        message.success('Đã tạo hợp đồng');
      }
      setModalOpen(false);
      actionRef.current?.reload();
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  const terminate = async (record: Contract) => {
    try {
      await terminateContract(record.id);
      message.success('Đã chấm dứt hợp đồng');
      actionRef.current?.reload();
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const columns: ProColumns<Contract>[] = [
    {
      title: 'Nhân viên',
      width: 220,
      ellipsis: true,
      render: (_, record) => `${record.employeeCode ?? '?'} — ${record.employeeName ?? '?'}`,
    },
    {
      title: 'Loại HĐ',
      dataIndex: 'contractType',
      width: 120,
      render: (_, record) => TYPE_LABEL[record.contractType] ?? record.contractType,
    },
    {
      title: 'Từ ngày',
      dataIndex: 'startDate',
      width: 115,
      render: (_, record) => dayjs(record.startDate).format('DD/MM/YYYY'),
    },
    {
      title: 'Đến ngày',
      dataIndex: 'endDate',
      width: 115,
      render: (_, record) => (record.endDate ? dayjs(record.endDate).format('DD/MM/YYYY') : '—'),
    },
    { title: 'Chức danh', dataIndex: 'position', width: 140, ellipsis: true },
    {
      title: 'Lương (VND)',
      dataIndex: 'salary',
      width: 140,
      align: 'right',
      render: (_, record) =>
        record.salary == null ? (
          <Typography.Text type="secondary" italic>
            Ẩn (HR_ADMIN)
          </Typography.Text>
        ) : (
          vnd(record.salary)
        ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 110,
      render: (_, record) => (
        <Tag color={STATUS_META[record.status].color}>{STATUS_META[record.status].label}</Tag>
      ),
    },
    {
      title: 'Thao tác',
      valueType: 'option',
      width: 140,
      render: (_, record) => {
        const actions: ReactNode[] = [];
        if (canWrite) {
          actions.push(
            <Button key="edit" type="link" size="small" onClick={() => openEdit(record)}>
              Sửa
            </Button>,
          );
          if (record.status !== 'TERMINATED') {
            actions.push(
              <Popconfirm
                key="terminate"
                title="Chấm dứt hợp đồng?"
                onConfirm={() => terminate(record)}
              >
                <Button type="link" size="small" danger>
                  Chấm dứt
                </Button>
              </Popconfirm>,
            );
          }
        }
        return actions;
      },
    },
  ];

  return (
    <>
      <ProTable<Contract>
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        headerTitle="Hợp đồng lao động"
        search={false}
        pagination={{ showSizeChanger: true, defaultPageSize: 20 }}
        options={{ density: false, setting: false }}
        toolBarRender={() => [
          <Select
            key="emp"
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="Nhân viên"
            style={{ width: 220 }}
            value={employeeFilter}
            onChange={(value) => {
              setEmployeeFilter(value);
              actionRef.current?.reload();
            }}
            options={employees.map((e) => ({
              value: e.id,
              label: `${e.employeeCode} — ${e.fullName}`,
            }))}
          />,
          canWrite ? (
            <Button key="create" type="primary" onClick={openCreate}>
              Thêm hợp đồng
            </Button>
          ) : null,
        ]}
        request={async (params) => {
          try {
            const data = await listContracts({
              employeeId: employeeFilter,
            });
            const size = params.pageSize ?? 20;
            const current = params.current ?? 1;
            const start = (current - 1) * size;
            return {
              data: data.slice(start, start + size),
              total: data.length,
              success: true,
            };
          } catch (error) {
            message.error(errorMessage(error));
            return { data: [], total: 0, success: false };
          }
        }}
      />

      <Modal
        title={editing ? 'Sửa hợp đồng' : 'Thêm hợp đồng'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        okText="Lưu"
        confirmLoading={saving}
        destroyOnClose
        width={620}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          {!editing && (
            <Form.Item
              name="employeeId"
              label="Nhân viên"
              rules={[{ required: true, message: 'Chọn nhân viên' }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                placeholder="Chọn nhân viên"
                options={employees
                  .filter((e) => e.status !== 'TERMINATED')
                  .map((e) => ({ value: e.id, label: `${e.employeeCode} — ${e.fullName}` }))}
              />
            </Form.Item>
          )}
          <Form.Item name="contractType" label="Loại hợp đồng" rules={[{ required: true }]}>
            <Select
              options={Object.entries(TYPE_LABEL).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
            <Form.Item
              name="startDate"
              label="Từ ngày"
              rules={[{ required: true, message: 'Chọn ngày bắt đầu' }]}
            >
              <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" />
            </Form.Item>
            <Form.Item name="endDate" label="Đến ngày">
              <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" />
            </Form.Item>
            <Form.Item name="signDate" label="Ngày ký">
              <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" />
            </Form.Item>
          </div>
          <Form.Item name="position" label="Chức danh (HĐ)" rules={[{ max: 100 }]}>
            <Input />
          </Form.Item>
          <Form.Item
            name="salary"
            label="Lương (VND)"
            rules={[{ type: 'number', min: 0, message: 'Lương không âm' }]}
          >
            <InputNumber style={{ width: '100%' }} min={0} step={100000} />
          </Form.Item>
          {editing && (
            <Form.Item name="status" label="Trạng thái">
              <Select
                options={Object.entries(STATUS_META).map(([value, meta]) => ({
                  value,
                  label: meta.label,
                }))}
              />
            </Form.Item>
          )}
          <Form.Item name="note" label="Ghi chú" rules={[{ max: 500 }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
