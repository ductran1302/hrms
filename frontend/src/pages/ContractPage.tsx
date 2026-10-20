import { EditOutlined, RiseOutlined, SyncOutlined, UploadOutlined, WarningOutlined } from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import {
  App as AntApp,
  Button,
  DatePicker,
  Divider,
  Form,
  Input,
  InputNumber,
  List,
  Modal,
  Popconfirm,
  Select,
  Tag,
  Typography,
  Upload,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { errorMessage } from '../api/client';
import {
  createContract,
  extendContract,
  listContracts,
  replaceContract,
  terminateContract,
  updateContract,
} from '../api/contracts';
import { deleteDocument, downloadDocument, listDocuments, uploadDocument } from '../api/documents';
import { searchEmployees } from '../api/employees';
import type {
  Contract,
  ContractStatusValue,
  ContractTypeValue,
  Employee,
  EmployeeDocument,
} from '../api/types';
import { useAuth } from '../auth/AuthContext';
import ActionIcon from '../components/ActionIcon';

const TYPE_KEYS: Record<ContractTypeValue, string> = {
  PROBATION: 'contract.type.PROBATION',
  FULL_TIME: 'contract.type.FULL_TIME',
  PART_TIME: 'contract.type.PART_TIME',
  INTERN: 'contract.type.INTERN',
  OTHER: 'contract.type.OTHER',
};

const STATUS_META: Record<ContractStatusValue, { key: string; color: string }> = {
  PENDING: { key: 'contract.status.PENDING', color: 'blue' },
  ACTIVE: { key: 'contract.status.ACTIVE', color: 'green' },
  EXPIRED: { key: 'contract.status.EXPIRED', color: 'orange' },
  TERMINATED: { key: 'contract.status.TERMINATED', color: 'red' },
};

const EXPIRING_WINDOW_DAYS = 30;

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

export default function ContractPage() {
  const { t } = useTranslation();
  const { message } = AntApp.useApp();
  const auth = useAuth();
  const actionRef = useRef<ActionType>(null);
  const [form] = Form.useForm<FormValues>();
  /** create | edit | replace — replace = chấm dứt HĐ cũ + ký HĐ mới linked. */
  const [mode, setMode] = useState<'create' | 'edit' | 'replace'>('create');
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Contract | null>(null);
  const [replaceTarget, setReplaceTarget] = useState<Contract | null>(null);
  const [saving, setSaving] = useState(false);
  const [employeeFilter, setEmployeeFilter] = useState<string | undefined>(undefined);
  const [statusFilter, setStatusFilter] = useState<ContractStatusValue | undefined>(undefined);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [expiringCount, setExpiringCount] = useState<number | null>(null);

  const [extendTarget, setExtendTarget] = useState<Contract | null>(null);
  const [extendEndDate, setExtendEndDate] = useState<Dayjs | null>(null);
  const [extendNote, setExtendNote] = useState('');

  const [docs, setDocs] = useState<EmployeeDocument[]>([]);
  const [uploading, setUploading] = useState(false);

  const canWrite = auth.hasRole('ADMIN');

  useEffect(() => {
    searchEmployees({ size: 500 })
      .then((page) => setEmployees(page.items))
      .catch(() => setEmployees([]));
    listContracts()
      .then((all) => {
        const today = dayjs();
        const count = all.filter(
          (c) =>
            c.effectiveStatus === 'IN_EFFECT' &&
            c.endDate &&
            dayjs(c.endDate).diff(today, 'day') <= EXPIRING_WINDOW_DAYS,
        ).length;
        setExpiringCount(count);
      })
      .catch(() => setExpiringCount(null));
  }, []);

  const loadDocs = (contract: Contract) => {
    listDocuments(contract.employeeId, contract.id)
      .then(setDocs)
      .catch(() => setDocs([]));
  };

  const openCreate = () => {
    setMode('create');
    setEditing(null);
    setReplaceTarget(null);
    form.resetFields();
    form.setFieldsValue({ contractType: 'FULL_TIME', startDate: dayjs() });
    setModalOpen(true);
  };

  const openEdit = (record: Contract) => {
    setMode('edit');
    setEditing(record);
    setReplaceTarget(null);
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
    loadDocs(record);
    setModalOpen(true);
  };

  const openReplace = (record: Contract) => {
    setMode('replace');
    setEditing(null);
    setReplaceTarget(record);
    form.resetFields();
    form.setFieldsValue({
      contractType: record.contractType,
      startDate: dayjs(),
      position: record.position ?? undefined,
      signDate: dayjs(),
      note: t('contract.replaceNote', {
        start: dayjs(record.startDate).format('DD/MM/YYYY'),
        end: record.endDate ? dayjs(record.endDate).format('DD/MM/YYYY') : '—',
      }),
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const payload = {
        contractType: values.contractType,
        startDate: values.startDate.format('YYYY-MM-DD'),
        endDate: values.endDate ? values.endDate.format('YYYY-MM-DD') : undefined,
        position: values.position,
        salary: values.salary,
        signDate: values.signDate ? values.signDate.format('YYYY-MM-DD') : undefined,
        note: values.note,
      };
      if (mode === 'edit' && editing) {
        await updateContract(editing.id, { ...payload, status: values.status });
        message.success(t('contract.updated'));
      } else if (mode === 'replace' && replaceTarget) {
        await replaceContract(replaceTarget.id, payload);
        message.success(t('contract.replaced'));
      } else {
        if (!values.employeeId) {
          message.error(t('contract.selectEmployee'));
          setSaving(false);
          return;
        }
        await createContract(values.employeeId, payload);
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

  const submitExtend = async () => {
    if (!extendTarget || !extendEndDate) {
      message.error(t('contract.extendInvalidDate'));
      return;
    }
    setSaving(true);
    try {
      await extendContract(extendTarget.id, {
        endDate: extendEndDate.format('YYYY-MM-DD'),
        note: extendNote.trim() || undefined,
      });
      message.success(t('contract.extended'));
      setExtendTarget(null);
      setExtendNote('');
      actionRef.current?.reload();
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  const terminate = async (record: Contract) => {
    try {
      await terminateContract(record.id, t('contract.terminate'));
      message.success(t('contract.terminated'));
      actionRef.current?.reload();
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const doUpload = async (file: File) => {
    if (!editing) return;
    setUploading(true);
    try {
      await uploadDocument(editing.employeeId, file, 'CONTRACT', undefined, editing.id);
      message.success(t('contract.attachmentUploaded'));
      loadDocs(editing);
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setUploading(false);
    }
  };

  const removeDoc = async (doc: EmployeeDocument) => {
    if (!editing) return;
    try {
      await deleteDocument(editing.employeeId, doc.id);
      message.success(t('contract.attachmentDeleted'));
      loadDocs(editing);
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const statusCell = (record: Contract): ReactNode => {
    const meta = STATUS_META[record.status];
    const tags: ReactNode[] = [
      <Tag key="biz" color={meta.color}>
        {t(meta.key)}
      </Tag>,
    ];
    if (record.effectiveStatus === 'NOT_YET_EFFECTIVE' && record.status === 'ACTIVE') {
      tags.push(
        <Tag key="eff" color="blue">
          {t('contract.effective.NOT_YET_EFFECTIVE')}
        </Tag>,
      );
    }
    if (record.effectiveStatus === 'ENDED') {
      tags.push(
        <Tag key="eff" color="orange">
          {t('contract.effective.ENDED')}
        </Tag>,
      );
    }
    if (
      record.effectiveStatus === 'IN_EFFECT' &&
      record.endDate &&
      dayjs(record.endDate).diff(dayjs(), 'day') <= EXPIRING_WINDOW_DAYS
    ) {
      tags.push(
        <Tag key="soon" color="warning">
          {t('contract.expiringSoon', {
            n: dayjs(record.endDate).diff(dayjs(), 'day'),
          })}
        </Tag>,
      );
    }
    return tags;
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
      width: 200,
      render: (_, record) => statusCell(record),
    },
    {
      title: t('actions'),
      valueType: 'option',
      width: 130,
      render: (_, record) => {
        const actions: ReactNode[] = [];
        if (canWrite) {
          actions.push(
            <ActionIcon key="edit" title={t('edit')} color="#1677ff" onClick={() => openEdit(record)}>
              <EditOutlined />
            </ActionIcon>,
          );
          if (record.status === 'PENDING' || record.status === 'ACTIVE') {
            actions.push(
              <ActionIcon
                key="extend"
                title={t('contract.extend')}
                color="#52c41a"
                onClick={() => {
                  setExtendEndDate(null);
                  setExtendNote('');
                  setExtendTarget(record);
                }}
              >
                <RiseOutlined />
              </ActionIcon>,
            );
          }
          if (record.status !== 'TERMINATED') {
            actions.push(
              <ActionIcon
                key="replace"
                title={t('contract.replace')}
                color="#1677ff"
                onClick={() => openReplace(record)}
              >
                <SyncOutlined />
              </ActionIcon>,
            );
            actions.push(
              <Popconfirm
                key="terminate"
                title={t('contract.terminateConfirm')}
                onConfirm={() => terminate(record)}
              >
                <ActionIcon title={t('contract.terminate')} color="#faad14">
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
      <ProTable<Contract>
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        headerTitle={
          expiringCount != null && expiringCount > 0
            ? `${t('contract.title')} — ${t('contract.expiringBanner', { n: expiringCount })}`
            : t('contract.title')
        }
        search={false}
        pagination={{ showSizeChanger: true, defaultPageSize: 20 }}
        options={{ density: false, setting: false }}
        toolBarRender={() => [
          <Select
            key="status"
            allowClear
            placeholder={t('contract.filter.status')}
            style={{ width: 150 }}
            value={statusFilter}
            onChange={(value) => {
              setStatusFilter(value);
              actionRef.current?.reload();
            }}
            options={Object.entries(STATUS_META).map(([value, meta]) => ({
              value,
              label: t(meta.key),
            }))}
          />,
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
              status: statusFilter,
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
        title={
          mode === 'edit'
            ? t('contract.editTitle')
            : mode === 'replace'
              ? t('contract.replaceTitle')
              : t('contract.add')
        }
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        okText={t('save')}
        confirmLoading={saving}
        destroyOnClose
        width={680}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          {!editing && mode !== 'replace' && (
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
          {mode === 'replace' && replaceTarget && (
            <Form.Item label={t('contract.employee')}>
              <Input disabled value={`${replaceTarget.employeeCode} — ${replaceTarget.employeeName}`} />
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
          {mode === 'edit' && (
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

          {mode === 'edit' && editing && (
            <>
              <Divider orientation="left" plain style={{ marginTop: 0 }}>
                {t('contract.attachments')}
              </Divider>
              <Upload
                showUploadList={false}
                beforeUpload={(file) => {
                  void doUpload(file);
                  return false;
                }}
              >
                <Button icon={<UploadOutlined />} loading={uploading}>
                  {t('contract.uploadAttachment')}
                </Button>
              </Upload>
              <List
                size="small"
                style={{ marginTop: 12 }}
                dataSource={docs}
                locale={{ emptyText: t('contract.attachmentsEmpty') }}
                renderItem={(doc) => (
                  <List.Item
                    actions={[
                      <Button
                        key="dl"
                        type="link"
                        size="small"
                        onClick={() =>
                          void downloadDocument(editing.employeeId, doc.id, doc.fileName)
                        }
                      >
                        {t('contract.download')}
                      </Button>,
                      canWrite ? (
                        <Popconfirm
                          key="del"
                          title={t('contract.deleteAttachmentConfirm')}
                          onConfirm={() => removeDoc(doc)}
                        >
                          <Button type="link" size="small" danger>
                            {t('delete')}
                          </Button>
                        </Popconfirm>
                      ) : null,
                    ]}
                  >
                    <List.Item.Meta
                      title={doc.fileName}
                      description={`${doc.docType} — ${dayjs(doc.uploadedAt).format('DD/MM/YYYY HH:mm')}`}
                    />
                  </List.Item>
                )}
              />
            </>
          )}
        </Form>
      </Modal>

      <Modal
        title={t('contract.extendTitle')}
        open={extendTarget !== null}
        onOk={() => void submitExtend()}
        onCancel={() => setExtendTarget(null)}
        confirmLoading={saving}
        okText={t('contract.extend')}
      >
        <p>
          {extendTarget
            ? `${extendTarget.employeeCode} — ${extendTarget.employeeName} (${t(
                TYPE_KEYS[extendTarget.contractType],
              )})`
            : ''}
        </p>
        <Form layout="vertical">
          <Form.Item label={t('contract.extendNewEndDate')} required>
            <DatePicker
              style={{ width: '100%' }}
              format="DD/MM/YYYY"
              value={extendEndDate}
              onChange={(value) => setExtendEndDate(value)}
            />
          </Form.Item>
          <Form.Item label={t('contract.note')}>
            <Input.TextArea
              rows={2}
              maxLength={500}
              value={extendNote}
              onChange={(e) => setExtendNote(e.target.value)}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
