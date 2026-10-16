import { EditOutlined, WarningOutlined } from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { App as AntApp, Button, Form, Input, Modal, Popconfirm, Select, Tag } from 'antd';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
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
import ActionIcon from '../components/ActionIcon';

interface FormValues {
  code: string;
  name: string;
  parentId?: string;
  managerId?: string;
}

export default function DepartmentPage() {
  const { t } = useTranslation();
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
        message.success(t('department.updated', { name: values.name }));
      } else {
        await createDepartment(values);
        message.success(t('department.created', { code: values.code }));
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
      message.success(t('department.deactivated', { name: record.name }));
      reloadMeta();
      actionRef.current?.reload();
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const deptName = (id: string | null) =>
    id ? departments.find((d) => d.id === id)?.name ?? '—' : '—';

  const columns: ProColumns<Department>[] = [
    { title: t('department.code'), dataIndex: 'code', width: 110 },
    { title: t('department.name'), dataIndex: 'name', width: 220, ellipsis: true },
    {
      title: t('department.parent'),
      dataIndex: 'parentId',
      width: 160,
      ellipsis: true,
      render: (_, record) => deptName(record.parentId),
    },
    {
      title: t('department.manager'),
      dataIndex: 'managerName',
      width: 170,
      ellipsis: true,
    },
    {
      title: t('department.employeeCount'),
      dataIndex: 'employeeCount',
      width: 100,
      align: 'right',
    },
    {
      title: t('department.statusTitle'),
      dataIndex: 'status',
      width: 110,
      render: (_, record) =>
        record.status === 'ACTIVE' ? (
          <Tag color="green">{t('department.active')}</Tag>
        ) : (
          <Tag color="red">{t('department.inactive')}</Tag>
        ),
    },
    {
      title: t('actions'),
      valueType: 'option',
      width: 100,
      render: (_, record) => {
        const actions: ReactNode[] = [];
        if (canWrite) {
          actions.push(
            <ActionIcon key="edit" title={t('edit')} color="#1677ff" onClick={() => openEdit(record)}>
              <EditOutlined />
            </ActionIcon>,
          );
          if (record.status === 'ACTIVE') {
            actions.push(
              <Popconfirm
                key="off"
                title={t('department.deactivateConfirm')}
                description={t('department.deactivateDesc')}
                onConfirm={() => deactivate(record)}
              >
                <ActionIcon title={t('department.deactivate')} color="#faad14">
                  <WarningOutlined />
                </ActionIcon>
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
        headerTitle={t('department.title')}
        search={false}
        pagination={false}
        options={{ density: false, setting: false }}
        toolBarRender={() => [
          canWrite ? (
            <Button key="create" type="primary" onClick={openCreate}>
              {t('department.add')}
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
        title={editing ? t('department.editTitle', { code: editing.code }) : t('department.add')}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        okText={t('save')}
        confirmLoading={saving}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="code"
            label={t('department.code')}
            rules={[{ required: true, message: t('department.codeRequired') }, { max: 20 }]}
          >
            <Input placeholder={t('department.codePlaceholder')} />
          </Form.Item>
          <Form.Item
            name="name"
            label={t('department.name')}
            rules={[{ required: true, message: t('department.nameRequired') }, { max: 100 }]}
          >
            <Input placeholder={t('department.namePlaceholder')} />
          </Form.Item>
          <Form.Item
            name="parentId"
            label={t('department.parent')}
            extra={editing ? t('department.parentExtra') : undefined}
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
          <Form.Item name="managerId" label={t('department.manager')}>
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
