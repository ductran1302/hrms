import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { App as AntApp, Button, Form, Input, Modal, Popconfirm, Select, Tag } from 'antd';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { errorMessage } from '../api/client';
import {
  createDepartment,
  deactivateDepartment,
  listDepartments,
  updateDepartment,
} from '../api/departments';
import { searchEmployees } from '../api/employees';
import type { Department, Employee } from '../api/types';
import { useAuth } from '../auth/AuthContext';

interface FormValues {
  code: string;
  name: string;
  parentId?: string;
  managerId?: string;
}

export default function PhongBanPage() {
  const { message } = AntApp.useApp();
  const auth = useAuth();
  const actionRef = useRef<ActionType>(null);
  const [form] = Form.useForm<FormValues>();
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Department | null>(null);
  const [saving, setSaving] = useState(false);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [employees, setEmployees] = useState<Employee[]>([]);

  const canWrite = auth.hasRole('HR_ADMIN');

  const reloadMeta = () => {
    listDepartments()
      .then(setDepartments)
      .catch(() => setDepartments([]));
    searchEmployees({ size: 500, status: 'ACTIVE' })
      .then((page) => setEmployees(page.items))
      .catch(() => setEmployees([]));
  };

  useEffect(reloadMeta, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (record: Department) => {
    setEditing(record);
    form.setFieldsValue({
      code: record.code,
      name: record.name,
      parentId: record.parentId ?? undefined,
      managerId: record.managerId ?? undefined,
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await updateDepartment(editing.id, {
          code: values.code,
          name: values.name,
          parentId: values.parentId,
          managerId: values.managerId,
        });
        message.success(`Đã cập nhật ${values.name}`);
      } else {
        await createDepartment(values);
        message.success(`Đã tạo phòng ban ${values.code}`);
      }
      setModalOpen(false);
      reloadMeta();
      actionRef.current?.reload();
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  const deactivate = async (record: Department) => {
    try {
      await deactivateDepartment(record.id);
      message.success(`Đã vô hiệu ${record.name}`);
      reloadMeta();
      actionRef.current?.reload();
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const deptName = (id: string | null) =>
    id ? departments.find((d) => d.id === id)?.name ?? '—' : '—';

  const columns: ProColumns<Department>[] = [
    { title: 'Mã phòng', dataIndex: 'code', width: 110 },
    { title: 'Tên phòng ban', dataIndex: 'name', width: 220, ellipsis: true },
    {
      title: 'Phòng cha',
      dataIndex: 'parentId',
      width: 160,
      ellipsis: true,
      render: (_, record) => deptName(record.parentId),
    },
    { title: 'Trưởng phòng', dataIndex: 'managerName', width: 170, ellipsis: true },
    {
      title: 'Nhân viên',
      dataIndex: 'employeeCount',
      width: 100,
      align: 'right',
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 110,
      render: (_, record) =>
        record.status === 'ACTIVE' ? (
          <Tag color="green">Hoạt động</Tag>
        ) : (
          <Tag color="red">Vô hiệu</Tag>
        ),
    },
    {
      title: 'Thao tác',
      valueType: 'option',
      width: 150,
      render: (_, record) => {
        const actions: ReactNode[] = [];
        if (canWrite) {
          actions.push(
            <Button key="edit" type="link" size="small" onClick={() => openEdit(record)}>
              Sửa
            </Button>,
          );
          if (record.status === 'ACTIVE') {
            actions.push(
              <Popconfirm
                key="off"
                title="Vô hiệu phòng ban?"
                description="Chặn khi còn phòng ban con. Nhân viên không bị ảnh hưởng."
                onConfirm={() => deactivate(record)}
              >
                <Button type="link" size="small" danger>
                  Vô hiệu
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
      <ProTable<Department>
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        headerTitle="Phòng ban"
        search={false}
        pagination={false}
        options={{ density: false, setting: false }}
        toolBarRender={() => [
          canWrite ? (
            <Button key="create" type="primary" onClick={openCreate}>
              Thêm phòng ban
            </Button>
          ) : null,
        ]}
        request={async () => {
          try {
            const data = await listDepartments();
            return { data, success: true };
          } catch (error) {
            message.error(errorMessage(error));
            return { data: [], success: false };
          }
        }}
      />

      <Modal
        title={editing ? `Sửa phòng ban — ${editing.code}` : 'Thêm phòng ban'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        okText="Lưu"
        confirmLoading={saving}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="code"
            label="Mã phòng"
            rules={[{ required: true, message: 'Nhập mã phòng' }, { max: 20 }]}
          >
            <Input placeholder="ENG" />
          </Form.Item>
          <Form.Item
            name="name"
            label="Tên phòng ban"
            rules={[{ required: true, message: 'Nhập tên phòng ban' }, { max: 100 }]}
          >
            <Input placeholder="Kỹ thuật" />
          </Form.Item>
          <Form.Item
            name="parentId"
            label="Phòng cha"
            extra={editing ? 'Bỏ trống = cấp cao nhất (root)' : undefined}
          >
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="—"
              options={departments
                .filter((d) => d.status === 'ACTIVE' && d.id !== editing?.id)
                .map((d) => ({ value: d.id, label: d.name }))}
            />
          </Form.Item>
          <Form.Item name="managerId" label="Trưởng phòng">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="—"
              options={employees.map((e) => ({
                value: e.id,
                label: `${e.employeeCode} — ${e.fullName}`,
              }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
