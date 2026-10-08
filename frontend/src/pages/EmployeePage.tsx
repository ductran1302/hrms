import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import {
  App as AntApp,
  Button,
  DatePicker,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Tag,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { listDepartments } from '../api/departments';
import { createEmployee, searchEmployees, terminateEmployee, updateEmployee } from '../api/employees';
import type { Department, Employee, EmployeeUpdate } from '../api/types';
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
  departmentId?: string;
  position?: string;
  managerId?: string;
  status?: string;
  hiredAt?: Dayjs | null;
  birthDate?: Dayjs | null;
  gender?: string;
  phone?: string;
  nationalId?: string;
  address?: string;
}

export default function EmployeePage() {
  const { message } = AntApp.useApp();
  const auth = useAuth();
  const navigate = useNavigate();
  const actionRef = useRef<ActionType>(null);
  const [form] = Form.useForm<FormValues>();
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Employee | null>(null);
  const [saving, setSaving] = useState(false);
  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined);
  const [deptFilter, setDeptFilter] = useState<string | undefined>(undefined);
  const [keyword, setKeyword] = useState('');
  const [departments, setDepartments] = useState<Department[]>([]);
  const [managerOptions, setManagerOptions] = useState<Employee[]>([]);

  const canWrite = auth.hasRole('HR_ADMIN');

  useEffect(() => {
    listDepartments()
      .then((data) => setDepartments(data.filter((d) => d.status === 'ACTIVE')))
      .catch(() => setDepartments([]));
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ hiredAt: dayjs() });
    setModalOpen(true);
    void searchEmployees({ size: 500 })
      .then((page) => setManagerOptions(page.items))
      .catch(() => setManagerOptions([]));
  };

  const openEdit = (record: Employee) => {
    setEditing(record);
    form.setFieldsValue({
      employeeCode: record.employeeCode,
      fullName: record.fullName,
      email: record.email ?? undefined,
      departmentId: record.departmentId ?? undefined,
      position: record.position ?? undefined,
      managerId: record.managerId ?? undefined,
      status: record.status,
      hiredAt: record.hiredAt ? dayjs(record.hiredAt) : null,
      birthDate: record.birthDate ? dayjs(record.birthDate) : null,
      gender: record.gender ?? undefined,
      phone: record.phone ?? undefined,
      nationalId: record.nationalId ?? undefined,
      address: record.address ?? undefined,
    });
    setModalOpen(true);
    void searchEmployees({ size: 500 })
      .then((page) => setManagerOptions(page.items.filter((e) => e.id !== record.id)))
      .catch(() => setManagerOptions([]));
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        const patch: EmployeeUpdate = {};
        if (values.fullName !== editing.fullName) patch.fullName = values.fullName;
        if ((values.email ?? null) !== editing.email) patch.email = values.email;
        if ((values.departmentId ?? null) !== editing.departmentId) {
          if (values.departmentId) patch.departmentId = values.departmentId;
          else patch.clearDepartment = true;
        }
        if ((values.position ?? null) !== editing.position) patch.position = values.position;
        if ((values.managerId ?? null) !== editing.managerId) {
          if (values.managerId) patch.managerId = values.managerId;
          else patch.clearManager = true;
        }
        if (values.status && values.status !== editing.status) patch.status = values.status;
        const birthDate = values.birthDate ? values.birthDate.format('YYYY-MM-DD') : null;
        if (birthDate !== editing.birthDate) patch.birthDate = birthDate ?? undefined;
        if ((values.gender ?? null) !== editing.gender) patch.gender = values.gender;
        if ((values.phone ?? null) !== editing.phone) patch.phone = values.phone;
        if ((values.nationalId ?? null) !== editing.nationalId) patch.nationalId = values.nationalId;
        if ((values.address ?? null) !== editing.address) patch.address = values.address;
        if (Object.keys(patch).length === 0) {
          message.info('Không có thay đổi nào');
          setModalOpen(false);
          return;
        }
        await updateEmployee(editing.id, patch);
        message.success(`Đã cập nhật ${values.fullName}`);
      } else {
        await createEmployee({
          employeeCode: values.employeeCode,
          fullName: values.fullName,
          email: values.email,
          departmentId: values.departmentId,
          position: values.position,
          managerId: values.managerId,
          hiredAt: values.hiredAt ? values.hiredAt.toISOString() : undefined,
          birthDate: values.birthDate ? values.birthDate.format('YYYY-MM-DD') : undefined,
          gender: values.gender,
          phone: values.phone,
          nationalId: values.nationalId,
          address: values.address,
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
    { title: 'Họ và tên', dataIndex: 'fullName', width: 190, ellipsis: true },
    { title: 'Email', dataIndex: 'email', width: 200, ellipsis: true },
    { title: 'Phòng ban', dataIndex: 'departmentName', width: 130, ellipsis: true },
    { title: 'Chức danh', dataIndex: 'position', width: 140, ellipsis: true },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 115,
      render: (_, record) => (
        <Tag color={STATUS_META[record.status].color}>{STATUS_META[record.status].label}</Tag>
      ),
    },
    {
      title: 'Ngày vào làm',
      dataIndex: 'hiredAt',
      width: 125,
      render: (_, record) => (record.hiredAt ? dayjs(record.hiredAt).format('DD/MM/YYYY') : '—'),
    },
    {
      title: 'Thao tác',
      valueType: 'option',
      width: 190,
      render: (_, record) => {
        const actions: ReactNode[] = [
          <Button key="profile" type="link" size="small" onClick={() => navigate(`/nhan-su/${record.id}`)}>
            Hồ sơ
          </Button>,
        ];
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
        pagination={{ showSizeChanger: true, defaultPageSize: 20 }}
        options={{ density: false, setting: false }}
        toolBarRender={() => [
          <Input.Search
            key="q"
            allowClear
            placeholder="Mã / tên / email"
            style={{ width: 220 }}
            onSearch={(value) => {
              setKeyword(value);
              actionRef.current?.reload();
            }}
          />,
          <Select
            key="dept"
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="Phòng ban"
            style={{ width: 170 }}
            value={deptFilter}
            onChange={(value) => {
              setDeptFilter(value);
              actionRef.current?.reload();
            }}
            options={departments.map((d) => ({ value: d.id, label: d.name }))}
          />,
          <Select
            key="status"
            allowClear
            placeholder="Trạng thái"
            style={{ width: 150 }}
            value={statusFilter}
            onChange={(value) => {
              setStatusFilter(value);
              actionRef.current?.reload();
            }}
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
        request={async (params) => {
          try {
            const page = await searchEmployees({
              page: (params.current ?? 1) - 1,
              size: params.pageSize ?? 20,
              status: statusFilter,
              departmentId: deptFilter,
              q: keyword,
            });
            return { data: page.items, total: page.total, success: true };
          } catch (error) {
            message.error(errorMessage(error));
            return { data: [], total: 0, success: false };
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
        width={640}
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
          <Form.Item
            name="departmentId"
            label="Phòng ban"
            extra={editing ? 'Bỏ trống = gỡ khỏi phòng ban' : undefined}
          >
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="Chọn phòng ban"
              options={departments.map((d) => ({ value: d.id, label: d.name }))}
            />
          </Form.Item>
          <Form.Item name="position" label="Chức danh" rules={[{ max: 100 }]}>
            <Input placeholder="Backend Developer" />
          </Form.Item>
          <Form.Item
            name="managerId"
            label="Quản lý trực tiếp"
            extra={editing ? 'Bỏ trống = không có quản lý (gốc sơ đồ)' : undefined}
          >
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="Chọn nhân viên"
              options={managerOptions.map((e) => ({
                value: e.id,
                label: `${e.employeeCode} — ${e.fullName}`,
              }))}
            />
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
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
            <Form.Item name="birthDate" label="Ngày sinh">
              <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" />
            </Form.Item>
            <Form.Item name="gender" label="Giới tính">
              <Select
                allowClear
                placeholder="—"
                options={[
                  { value: 'MALE', label: 'Nam' },
                  { value: 'FEMALE', label: 'Nữ' },
                  { value: 'OTHER', label: 'Khác' },
                ]}
              />
            </Form.Item>
            <Form.Item name="phone" label="SĐT" rules={[{ max: 20 }]}>
              <Input placeholder="09xx" />
            </Form.Item>
          </div>
          <Form.Item name="nationalId" label="Số CCCD/CMND" rules={[{ max: 20 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="address" label="Địa chỉ" rules={[{ max: 300 }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
