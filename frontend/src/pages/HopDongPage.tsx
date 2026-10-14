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
import { useTranslation } from 'react-i18next';
import { errorMessage } from '../api/client';
import { createContract, listContracts, terminateContract, updateContract } from '../api/contracts';
import { searchEmployees } from '../api/employees';
import type { Contract, ContractStatusValue, ContractTypeValue, Employee } from '../api/types';
import { useAuth } from '../auth/AuthContext';

const TYPE_KEYS: Record<ContractTypeValue, string> = {
  PROBATION: 'contract.type.PROBATION',
  FULL_TIME: 'contract.type.FULL_TIME',
  PART_TIME: 'contract.type.PART_TIME',
  INTERN: 'contract.type.INTERN',
  OTHER: 'contract.type.OTHER',
};

const STATUS_META: Record<ContractStatusValue, { key: string; color: string }> = {
  ACTIVE: { key: 'contract.status.ACTIVE', color: 'green' },
  EXPIRED: { key: 'contract.status.EXPIRED', color: 'orange' },
  TERMINATED: { key: 'contract.status.TERMINATED', color: 'red' },
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
  const { t } = useTranslation();
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
        message.success(t('contract.updated'));
      } else {
        if (!values.employeeId) {
          message.error(t('contract.selectEmployee'));
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
        message.success(t('contract.created'));
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
      message.success(t('contract.terminated'));
      actionRef.current?.reload();
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const columns: ProColumns<Contract>[] = [
    {
      title: t('contract.employee'),
      width: 220,
      ellipsis: true,
      render: (_, record) => `${record.employeeCode ?? '?'} — ${record.employeeName ?? '?'}`,
    },
    {
      title: t('contract.contractTypeShort'),
      dataIndex: 'contractType',
      width: 120,
      render: (_, record) => t(TYPE_KEYS[record.contractType] ?? record.contractType),
    },
    {
      title: t('contract.startDate'),
      dataIndex: 'startDate',
      width: 115,
      render: (_, record) => dayjs(record.startDate).format('DD/MM/YYYY'),
    },
    {
      title: t('contract.endDate'),
      dataIndex: 'endDate',
      width: 115,
      render: (_, record) => (record.endDate ? dayjs(record.endDate).format('DD/MM/YYYY') : '—'),
    },
    { title: t('contract.positionShort'), dataIndex: 'position', width: 140, ellipsis: true },
    {
      title: t('contract.salary'),
      dataIndex: 'salary',
      width: 140,
      align: 'right',
      render: (_, record) =>
        record.salary == null ? (
          <Typography.Text type="secondary" italic>
            {t('contract.salaryHidden')}
          </Typography.Text>
        ) : (
          vnd(record.salary)
        ),
    },
    {
      title: t('contract.statusTitle'),
      dataIndex: 'status',
      width: 110,
      render: (_, record) => (
        <Tag color={STATUS_META[record.status].color}>{t(STATUS_META[record.status].key)}</Tag>
      ),
    },
    {
      title: t('actions'),
      valueType: 'option',
      width: 140,
      render: (_, record) => {
        const actions: ReactNode[] = [];
        if (canWrite) {
          actions.push(
            <Button key="edit" type="link" size="small" onClick={() => openEdit(record)}>
              {t('edit')}
            </Button>,
          );
          if (record.status !== 'TERMINATED') {
            actions.push(
              <Popconfirm
                key="terminate"
                title={t('contract.terminateConfirm')}
                onConfirm={() => terminate(record)}
              >
                <Button type="link" size="small" danger>
                  {t('contract.terminate')}
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
        headerTitle={t('contract.title')}
        search={false}
        pagination={{ showSizeChanger: true, defaultPageSize: 20 }}
        options={{ density: false, setting: false }}
        toolBarRender={() => [
          <Select
            key="emp"
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder={t('contract.employee')}
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
              {t('contract.add')}
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
        title={editing ? t('contract.editTitle') : t('contract.add')}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        okText={t('save')}
        confirmLoading={saving}
        destroyOnClose
        width={620}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          {!editing && (
            <Form.Item
              name="employeeId"
              label={t('contract.employee')}
              rules={[{ required: true, message: t('contract.selectEmployee') }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                placeholder={t('contract.selectEmployee')}
                options={employees
                  .filter((e) => e.status !== 'TERMINATED')
                  .map((e) => ({ value: e.id, label: `${e.employeeCode} — ${e.fullName}` }))}
              />
            </Form.Item>
          )}
          <Form.Item
            name="contractType"
            label={t('contract.contractType')}
            rules={[{ required: true }]}
          >
            <Select
              options={Object.entries(TYPE_KEYS).map(([value, key]) => ({
                value,
                label: t(key),
              }))}
            />
          </Form.Item>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
            <Form.Item
              name="startDate"
              label={t('contract.startDate')}
              rules={[{ required: true, message: t('contract.selectStartDate') }]}
            >
              <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" />
            </Form.Item>
            <Form.Item name="endDate" label={t('contract.endDate')}>
              <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" />
            </Form.Item>
            <Form.Item name="signDate" label={t('contract.signDate')}>
              <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" />
            </Form.Item>
          </div>
          <Form.Item name="position" label={t('contract.position')} rules={[{ max: 100 }]}>
            <Input />
          </Form.Item>
          <Form.Item
            name="salary"
            label={t('contract.salary')}
            rules={[{ type: 'number', min: 0, message: t('contract.salaryNotNegative') }]}
          >
            <InputNumber style={{ width: '100%' }} min={0} step={100000} />
          </Form.Item>
          {editing && (
            <Form.Item name="status" label={t('contract.statusTitle')}>
              <Select
                options={Object.entries(STATUS_META).map(([value, meta]) => ({
                  value,
                  label: t(meta.key),
                }))}
              />
            </Form.Item>
          )}
          <Form.Item name="note" label={t('contract.note')} rules={[{ max: 500 }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
