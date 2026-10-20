import { ArrowLeftOutlined, DeleteOutlined, UploadOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  Descriptions,
  Empty,
  Popconfirm,
  Select,
  Skeleton,
  Table,
  Tabs,
  Tag,
  Timeline,
  Typography,
  Upload,
} from 'antd';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useParams } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { listContracts } from '../api/contracts';
import { deleteDocument, downloadDocument, listDocuments, uploadDocument } from '../api/documents';
import { getEmployee, getEmployeeHistory, searchEmployees } from '../api/employees';
import type { Contract, Employee, EmployeeDocument, JobHistoryItem } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import ActionIcon from '../components/ActionIcon';

const STATUS_COLOR: Record<Employee['status'], string> = {
  ACTIVE: 'green',
  PROBATION: 'orange',
  ON_LEAVE: 'blue',
  TERMINATED: 'red',
};

/** Giới hạn upload tài liệu (khớp backend EmployeeDocumentService — 10MB). */
const MAX_DOC_SIZE = 10 * 1024 * 1024;
/** MIME whitelist — chặn file thực thi/script (svg/html cũng bị loại để tránh XSS). */
const ALLOWED_DOC_MIME = new Set([
  'application/pdf',
  'image/jpeg',
  'image/png',
  'image/gif',
  'image/webp',
  'image/bmp',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.ms-excel',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.ms-powerpoint',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  'text/plain',
  'text/csv',
  'application/rtf',
  'application/zip',
  'application/x-zip-compressed',
]);
/** Backup theo extension khi browser không khai báo MIME (file.type rỗng/không chuẩn). */
const ALLOWED_DOC_EXT = new Set([
  'pdf', 'jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp',
  'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx',
  'txt', 'csv', 'rtf', 'zip',
]);

/** true nếu file hợp lệ (MIME hoặc extension khớp whitelist) và ≤ 10MB. */
function isAllowedDocFile(file: File): boolean {
  const mime = (file.type.split(';')[0] ?? '').trim().toLowerCase();
  const ext = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : '';
  const typeOk = (mime !== '' && ALLOWED_DOC_MIME.has(mime)) || ALLOWED_DOC_EXT.has(ext);
  return typeOk && file.size <= MAX_DOC_SIZE;
}

export default function EmployeeProfile() {
  const { t } = useTranslation();
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const auth = useAuth();
  const [employee, setEmployee] = useState<Employee | null>(null);
  const [history, setHistory] = useState<JobHistoryItem[]>([]);
  const [contracts, setContracts] = useState<Contract[]>([]);
  const [peers, setPeers] = useState<Employee[]>([]);
  const [docs, setDocs] = useState<EmployeeDocument[]>([]);
  const [docType, setDocType] = useState<string>('CV');
  const [loading, setLoading] = useState(true);

  const DOC_TYPE_LABEL: Record<string, string> = {
    CV: t('profile.docType.CV'),
    CERTIFICATE: t('profile.docType.CERTIFICATE'),
    ID_CARD: t('profile.docType.ID_CARD'),
    CONTRACT: t('profile.docType.CONTRACT'),
    MEDICAL: t('profile.docType.MEDICAL'),
    OTHER: t('profile.docType.OTHER'),
  };

  const GENDER_LABEL: Record<string, string> = {
    MALE: t('gender.MALE'),
    FEMALE: t('gender.FEMALE'),
    OTHER: t('gender.OTHER'),
  };

  const FIELD_LABEL: Record<string, string> = {
    department: t('profile.field.department'),
    position: t('profile.field.position'),
    manager: t('profile.field.manager'),
    status: t('profile.field.status'),
    'contract.type': t('profile.field.contract.type'),
    'contract.salary': t('profile.field.contract.salary'),
    'contract.status': t('profile.field.contract.status'),
    'contract.endDate': t('profile.field.contract.endDate'),
  };

  const canReadDocs = auth.hasRole('ADMIN', 'GLOBAL_LEAD', 'DEPT_LEAD', 'MANAGER');
  const canWriteDocs = auth.hasRole('ADMIN');

  const loadDocs = useCallback(() => {
    if (!canReadDocs) return Promise.resolve();
    return listDocuments(id).then(setDocs).catch(() => setDocs([]));
  }, [id, canReadDocs]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [emp, hist, cons] = await Promise.all([
        getEmployee(id),
        getEmployeeHistory(id),
        listContracts({ employeeId: id }),
      ]);
      setEmployee(emp);
      setHistory(hist);
      setContracts(cons);
      if (canReadDocs) {
        listDocuments(id).then(setDocs).catch(() => setDocs([]));
      }
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setLoading(false);
    }
  }, [id, message, canReadDocs]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    searchEmployees({ size: 500 })
      .then((page) => setPeers(page.items))
      .catch(() => setPeers([]));
  }, []);

  const documentsTab = (
    <div style={{ marginTop: 8 }}>
      {canWriteDocs && (
        <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
          <Select
            value={docType}
            onChange={setDocType}
            style={{ width: 170 }}
            options={Object.entries(DOC_TYPE_LABEL).map(([value, label]) => ({
              value,
              label,
            }))}
          />
          <Upload
            showUploadList={false}
            beforeUpload={(file) => {
              // Validate phía client trước khi gửi (backend cũng chặn >10MB)
              if (file.size > MAX_DOC_SIZE) {
                message.error(t('profile.docs.fileTooLarge'));
                return false;
              }
              if (!isAllowedDocFile(file)) {
                message.error(t('profile.docs.invalidType'));
                return false;
              }
              uploadDocument(id, file, docType)
                .then(() => {
                  message.success(t('profile.uploaded'));
                  void loadDocs();
                })
                .catch((error) => message.error(errorMessage(error)));
              return false;
            }}
          >
            <Button icon={<UploadOutlined />} type="primary">
              {t('profile.upload')}
            </Button>
          </Upload>
        </div>
      )}
      <Table<EmployeeDocument>
        rowKey="id"
        dataSource={docs}
        pagination={false}
        columns={[
          {
            title: t('profile.docs.fileName'),
            dataIndex: 'fileName',
            render: (value: string, record) => (
              <Typography.Link
                onClick={() =>
                  void downloadDocument(id, record.id, value).catch((error) =>
                    message.error(errorMessage(error)),
                  )
                }
              >
                {value}
              </Typography.Link>
            ),
          },
          {
            title: t('profile.docs.type'),
            dataIndex: 'docType',
            render: (value: string) => DOC_TYPE_LABEL[value] ?? value,
          },
          {
            title: t('profile.docs.size'),
            dataIndex: 'fileSize',
            width: 110,
            render: (value: number) => `${Math.max(1, Math.round(value / 1024))} KB`,
          },
          {
            title: t('profile.docs.note'),
            dataIndex: 'note',
            ellipsis: true,
            render: (v: string | null) => v ?? '—',
          },
          {
            title: t('profile.docs.uploadedAt'),
            dataIndex: 'uploadedAt',
            width: 150,
            render: (value: string) => dayjs(value).format('DD/MM/YYYY HH:mm'),
          },
          ...(canWriteDocs
            ? [
                {
                  title: t('actions'),
                  key: 'action',
                  width: 90,
                  render: (_: unknown, record: EmployeeDocument) => (
                    <Popconfirm
                      title={t('profile.docs.deleteTitle')}
                      description={t('profile.docs.deleteDesc')}
                      onConfirm={() =>
                        deleteDocument(id, record.id)
                          .then(() => {
                            message.success(t('profile.docs.deleted'));
                            void loadDocs();
                          })
                          .catch((error) => message.error(errorMessage(error)))
                      }
                    >
                      <ActionIcon title={t('delete')} color="#ff4d4f">
                        <DeleteOutlined />
                      </ActionIcon>
                    </Popconfirm>
                  ),
                },
              ]
            : []),
        ]}
        locale={{ emptyText: t('profile.docs.empty') }}
      />
    </div>
  );

  if (loading) {
    return (
      <Card>
        <Skeleton active paragraph={{ rows: 8 }} />
      </Card>
    );
  }
  if (!employee) {
    return <Empty description={t('profile.notFound')} />;
  }

  const infoTab = (
    <Descriptions bordered column={2} size="middle" style={{ marginTop: 8 }}>
      <Descriptions.Item label={t('profile.info.employeeCode')}>
        {employee.employeeCode}
      </Descriptions.Item>
      <Descriptions.Item label={t('profile.info.fullName')}>{employee.fullName}</Descriptions.Item>
      <Descriptions.Item label={t('profile.info.email')}>{employee.email ?? '—'}</Descriptions.Item>
      <Descriptions.Item label={t('profile.info.phone')}>{employee.phone ?? '—'}</Descriptions.Item>
      <Descriptions.Item label={t('profile.info.birthDate')}>
        {employee.birthDate ? dayjs(employee.birthDate).format('DD/MM/YYYY') : '—'}
      </Descriptions.Item>
      <Descriptions.Item label={t('profile.info.gender')}>
        {employee.gender ? GENDER_LABEL[employee.gender] ?? employee.gender : '—'}
      </Descriptions.Item>
      <Descriptions.Item label={t('profile.info.nationalId')}>
        {employee.nationalId ?? '—'}
      </Descriptions.Item>
      <Descriptions.Item label={t('profile.info.address')}>{employee.address ?? '—'}</Descriptions.Item>
      <Descriptions.Item label={t('profile.info.office')}>{employee.office ?? '—'}</Descriptions.Item>
      <Descriptions.Item label={t('profile.info.education')}>
        {employee.educationLevel ?? '—'}
      </Descriptions.Item>
      <Descriptions.Item label={t('profile.info.emergencyContact')}>
        {employee.emergencyContactName
          ? `${employee.emergencyContactName} — ${employee.emergencyContactPhone ?? '—'}` +
            (employee.emergencyContactRelation
              ? ` (${employee.emergencyContactRelation})`
              : '')
          : '—'}
      </Descriptions.Item>
      <Descriptions.Item label={t('profile.info.skills')} span={2}>
        {employee.skills ?? '—'}
      </Descriptions.Item>
      <Descriptions.Item label={t('profile.info.department')}>
        {employee.departmentName ?? '—'}
      </Descriptions.Item>
      <Descriptions.Item label={t('profile.info.position')}>{employee.position ?? '—'}</Descriptions.Item>
      <Descriptions.Item label={t('profile.info.manager')}>
        {employee.managerId
          ? peers.find((p) => p.id === employee.managerId)?.fullName ?? employee.managerId
          : '—'}
      </Descriptions.Item>
      <Descriptions.Item label={t('profile.info.hiredAt')}>
        {employee.hiredAt ? dayjs(employee.hiredAt).format('DD/MM/YYYY') : '—'}
      </Descriptions.Item>
      <Descriptions.Item label={t('profile.info.status')}>
        <Tag color={STATUS_COLOR[employee.status]}>{t('status.' + employee.status)}</Tag>
      </Descriptions.Item>
    </Descriptions>
  );

  const contractTab = (
    <Table<Contract>
      rowKey="id"
      style={{ marginTop: 8 }}
      dataSource={contracts}
      pagination={false}
      columns={[
        {
          title: t('profile.contract.type'),
          dataIndex: 'contractType',
          render: (value: string) =>
            ({
              PROBATION: t('profile.contractType.PROBATION'),
              FULL_TIME: t('profile.contractType.FULL_TIME'),
              PART_TIME: t('profile.contractType.PART_TIME'),
              INTERN: t('profile.contractType.INTERN'),
              OTHER: t('profile.contractType.OTHER'),
            })[value] ?? value,
        },
        {
          title: t('profile.contract.startDate'),
          dataIndex: 'startDate',
          render: (value: string) => dayjs(value).format('DD/MM/YYYY'),
        },
        {
          title: t('profile.contract.endDate'),
          dataIndex: 'endDate',
          render: (value: string | null) => (value ? dayjs(value).format('DD/MM/YYYY') : '—'),
        },
        {
          title: t('profile.contract.position'),
          dataIndex: 'position',
          render: (v: string | null) => v ?? '—',
        },
        {
          title: t('profile.contract.salary'),
          dataIndex: 'salary',
          render: (value: number | null) =>
            value == null ? (
              <Typography.Text type="secondary" italic>
                {t('profile.contract.salaryHidden')}
              </Typography.Text>
            ) : (
              new Intl.NumberFormat('vi-VN').format(value)
            ),
        },
        {
          title: t('profile.contract.status'),
          dataIndex: 'status',
          render: (value: Contract['status']) => {
            const meta =
              value === 'ACTIVE'
                ? { color: 'green', key: 'profile.contractStatus.ACTIVE' }
                : value === 'EXPIRED'
                  ? { color: 'orange', key: 'profile.contractStatus.EXPIRED' }
                  : value === 'PENDING'
                    ? { color: 'blue', key: 'profile.contractStatus.PENDING' }
                    : { color: 'red', key: 'profile.contractStatus.TERMINATED' };
            return <Tag color={meta.color}>{t(meta.key)}</Tag>;
          },
        },
      ]}
      locale={{ emptyText: t('profile.contract.empty') }}
    />
  );

  const historyTab = history.length === 0 ? (
    <Empty description={t('profile.history.empty')} style={{ marginTop: 24 }} />
  ) : (
    <Timeline
      style={{ marginTop: 16 }}
      items={history.map((item) => ({
        color: 'blue',
        children: (
          <div>
            <Typography.Text strong>
              {FIELD_LABEL[item.field] ?? item.field}
            </Typography.Text>
            <div>
              <Typography.Text delete type="secondary">
                {item.oldValue ?? '—'}
              </Typography.Text>{' '}
              →{' '}
              <Typography.Text>{item.newValue ?? '—'}</Typography.Text>
            </div>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {dayjs(item.effectiveDate).format('DD/MM/YYYY HH:mm')}
              {item.actorId ? ` — ${t('profile.history.by', { id: item.actorId })}` : ''}
            </Typography.Text>
            {item.reason ? (
              <div>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {t('profile.history.reason')}: {item.reason}
                </Typography.Text>
              </div>
            ) : null}
          </div>
        ),
      }))}
    />
  );

  return (
    <Card
      title={
        <span>
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate('/employees')}
            style={{ marginRight: 8 }}
          />
          {employee.fullName}{' '}
          <Typography.Text type="secondary" style={{ fontSize: 14, fontWeight: 400 }}>
            {employee.employeeCode}
          </Typography.Text>
        </span>
      }
      extra={
        <Tag color={STATUS_COLOR[employee.status]}>{t('status.' + employee.status)}</Tag>
      }
    >
      <Tabs
        defaultActiveKey="info"
        items={[
          { key: 'info', label: t('profile.tabs.info'), children: infoTab },
          {
            key: 'contract',
            label: t('profile.tabs.contracts', { n: contracts.length }),
            children: contractTab,
          },
          ...(canReadDocs
            ? [
                {
                  key: 'documents',
                  label: t('profile.tabs.documents', { n: docs.length }),
                  children: documentsTab,
                },
              ]
            : []),
          {
            key: 'history',
            label: t('profile.tabs.history', { n: history.length }),
            children: historyTab,
          },
        ]}
      />
    </Card>
  );
}
