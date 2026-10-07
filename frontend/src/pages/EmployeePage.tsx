import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { App as AntApp, Button, DatePicker, Form, Input, Modal, Popconfirm, Select, Tag } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { errorMessage } from '../api/client';
import { createEmployee, listEmployees, terminateEmployee, updateEmployee } from '../api/employees';
import type { Employee } from '../api/types';
import { useAuth } from '../auth/AuthContext';

const STATUS_META: Record<Employee['status'], { label: string; color: string }> = {
  ACTIVE: { label: 'Đang làm', color: 'green' },
  PROBATION: { label: 'Thử việc', color: 'orange' },
  TERMINATED: { label: 'Đã nghỉ', color: 'red' },
};

interface FormValues {
  employeeCode: string;
  fullName: string;
  email?: string;
  departmentName?: string;
  position?: string;
  managerId?: string;
  status?: string;
  hiredAt?: Dayjs | null;
}

export default function EmployeePage() {
  const { message } = AntApp.useApp();
  const auth = useAuth();
  const actionRef = useRef<ActionType>(null);
  const firstRequest = useRef(true);
  const [form] = Form.useForm<FormValues>();
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Employee | null>(null);
  const [saving, setSaving] = useState(false);
  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined);

  const canWrite = auth.hasRole('HR_ADMIN');

  useEffect(() => {
    if (firstRequest.current) {
      firstRequest.current = false;
      return;
    }
    actionRef.current?.reload();
  }, [statusFilter]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ hiredAt: dayjs() });
    setModalOpen(true);
  };

  const openEdit = (record: Employee) => {
    setEditing(record);
    form.setFieldsValue({
      employeeCode: record.employeeCode,
      fullName: record.fullName,
      email: record.email ?? undefined,
      departmentName: record.departmentName ?? undefined,
      position: record.position ?? undefined,
      managerId: record.managerId ?? undefined,
      status: record.status,
      hiredAt: record.hiredAt ? dayjs(record.hiredAt) : null,
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await updateEmployee(editing.id, {
          fullName: values.fullName,
          email: values.email,
          departmentName: values.departmentName,
          position: values.position,
          managerId: values.managerId,
          status: values.status,
        });
        message.success(`Đã cập nhật ${values.fullName}`);
      } else {
        await createEmployee({
          employeeCode: values.employeeCode,
          fullName: values.fullName,
          email: values.email,
          departmentName: values.departmentName,
          position: values.position,
          managerId: values.managerId,
          hiredAt: values.hiredAt ? values.hiredAt.toISOString() : undefined,
        });
        message.success(`Đã tạo nhân viên ${values.employeeCode}`);
      }
      setModalOpen(false);
      actionRef.current?.reload();
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  const terminate = async (record: Employee) => {
    try {
      await terminateEmployee(record.id, 'Chấm dứt hợp đồng');
      message.success(`Đã cho ${record.fullName} nghỉ việc`);
      actionRef.current?.reload();
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const columns: ProColumns<Employee>[] = [
    { title: 'Mã NV', dataIndex: 'employeeCode', width: 110 },
    { title: 'Họ và tên', dataIndex: 'fullName', width: 200, ellipsis: true },
    { title: 'Email', dataIndex: 'email', width: 220, ellipsis: true },
    { title: 'Phòng ban', dataIndex: 'departmentName', width: 140, ellipsis: true },
    { title: 'Chức danh', dataIndex: 'position', width: 150, ellipsis: true },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 120,
      render: (_, record) => (
        <Tag color={STATUS_META[record.status].color}>{STATUS_META[record.status].label}</Tag>
      ),
    },
    {
      title: 'Ngày vào làm',
      dataIndex: 'hiredAt',
      width: 130,
      render: (_, record) => (record.hiredAt ? dayjs(record.hiredAt).format('DD/MM/YYYY') : '—'),
    },
    {
      title: 'Thao tác',
      valueType: 'option',
      width: 170,
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
                title="Cho nghỉ việc?"
                description="Nhân viên chuyển trạng thái TERMINATED (nghỉ việc mềm, vẫn giữ lịch sử)."
                onConfirm={() => terminate(record)}
              >
                <Button type="link" size="small" danger>
                  Nghỉ việc
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
      <ProTable<Employee>
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        headerTitle="Danh sách nhân viên"
        search={false}
        pagination={false}
        options={{ density: false, setting: false }}
        toolBarRender={() => [
          <Select
            key="status"
            allowClear
            placeholder="Trạng thái"
            style={{ width: 150 }}
            value={statusFilter}
            onChange={setStatusFilter}
            options={[
              { value: 'ACTIVE', label: 'Đang làm' },
              { value: 'PROBATION', label: 'Thử việc' },
              { value: 'TERMINATED', label: 'Đã nghỉ' },
            ]}
          />,
          canWrite ? (
            <Button key="create" type="primary" onClick={openCreate}>
              Thêm nhân viên
            </Button>
          ) : null,
        ]}
        request={async () => {
          try {
            const data = await listEmployees(statusFilter);
            return { data, success: true };
          } catch (error) {
            message.error(errorMessage(error));
            return { data: [], success: false };
          }
        }}
      />

      <Modal
        title={editing ? `Sửa nhân viên — ${editing.employeeCode}` : 'Thêm nhân viên'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        okText="Lưu"
        confirmLoading={saving}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="employeeCode"
            label="Mã nhân viên"
            rules={[{ required: true, message: 'Nhập mã nhân viên' }, { max: 20 }]}
          >
            <Input disabled={!!editing} placeholder="NV0003" />
          </Form.Item>
          <Form.Item
            name="fullName"
            label="Họ và tên"
            rules={[{ required: true, message: 'Nhập họ và tên' }, { max: 200 }]}
          >
            <Input placeholder="Nguyễn Văn C" />
          </Form.Item>
          <Form.Item name="email" label="Email" rules={[{ type: 'email', message: 'Email không hợp lệ' }, { max: 200 }]}>
            <Input placeholder="cv@example.com" />
          </Form.Item>
          <Form.Item name="departmentName" label="Phòng ban" rules={[{ max: 100 }]}>
            <Input placeholder="Engineering" />
          </Form.Item>
          <Form.Item name="position" label="Chức danh" rules={[{ max: 100 }]}>
            <Input placeholder="Backend Developer" />
          </Form.Item>
          <Form.Item name="managerId" label="Quản lý trực tiếp (mã người dùng Keycloak)" rules={[{ max: 64 }]}>
            <Input placeholder="manager1" />
          </Form.Item>
          <Form.Item name="status" label="Trạng thái" rules={[{ required: !!editing, message: 'Chọn trạng thái' }]}>
            <Select
              disabled={!editing}
              placeholder={editing ? 'Chọn trạng thái' : 'Mặc định: Đang làm'}
              options={[
                { value: 'ACTIVE', label: 'Đang làm' },
                { value: 'PROBATION', label: 'Thử việc' },
              ]}
            />
          </Form.Item>
          <Form.Item name="hiredAt" label="Ngày vào làm">
            <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
