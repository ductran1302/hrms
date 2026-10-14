import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import {
  App as AntApp,
  AutoComplete,
  Button,
  DatePicker,
  Divider,
  Form,
  Input,
  Modal,
  Select,
  Tag,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { listDepartments } from '../api/departments';
import { createEmployee, searchEmployees, terminateEmployee, updateEmployee } from '../api/employees';
import { assignEmployeeCalendar, listCalendars } from '../api/holidays';
import { listJobTitles } from '../api/jobtitles';
import type { Department, Employee, EmployeeUpdate, HolidayCalendar } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { generateEmployeeCode } from '../utils/employeeCode';

const STATUS_COLOR: Record<Employee['status'], string> = {
  ACTIVE: 'green',
  PROBATION: 'orange',
  ON_LEAVE: 'blue',
  TERMINATED: 'red',
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
  educationLevel?: string;
  skills?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  emergencyContactRelation?: string;
  calendarId?: string;
}

export default function EmployeePage() {
  const { t } = useTranslation();
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
  const [positionFilter, setPositionFilter] = useState<string | undefined>(undefined);
  const [positionOptions, setPositionOptions] = useState<string[]>([]);
  const [keyword, setKeyword] = useState('');
  const [departments, setDepartments] = useState<Department[]>([]);
  const [managerOptions, setManagerOptions] = useState<Employee[]>([]);
  const [jobTitleOptions, setJobTitleOptions] = useState<string[]>([]);
  const [calendars, setCalendars] = useState<HolidayCalendar[]>([]);
  const [terminateTarget, setTerminateTarget] = useState<Employee | null>(null);
  const [terminateReason, setTerminateReason] = useState('');
  const suggestedCodeRef = useRef('');

  const canWrite = auth.hasRole('HR_ADMIN');

  useEffect(() => {
    listDepartments()
      .then((data) => setDepartments(data.filter((d) => d.status === 'ACTIVE')))
      .catch(() => setDepartments([]));
    listJobTitles()
      .then((titles) => setJobTitleOptions(titles.map((jt) => jt.name)))
      .catch(() => setJobTitleOptions([]));
    listCalendars()
      .then((data) => setCalendars(data.filter((c) => c.status === 'ACTIVE')))
      .catch(() => setCalendars([]));
    searchEmployees({ size: 500 })
      .then((page) => {
        const unique = new Set<string>();
        page.items.forEach((e) => {
          if (e.position) unique.add(e.position);
        });
        setPositionOptions([...unique].sort());
      })
      .catch(() => setPositionOptions([]));
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    suggestedCodeRef.current = '';
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
      educationLevel: record.educationLevel ?? undefined,
      skills: record.skills ?? undefined,
      emergencyContactName: record.emergencyContactName ?? undefined,
      emergencyContactPhone: record.emergencyContactPhone ?? undefined,
      emergencyContactRelation: record.emergencyContactRelation ?? undefined,
      calendarId: record.calendarId ?? undefined,
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
        if ((values.educationLevel ?? null) !== editing.educationLevel) {
          patch.educationLevel = values.educationLevel;
        }
        if ((values.skills ?? null) !== editing.skills) patch.skills = values.skills;
        if ((values.emergencyContactName ?? null) !== editing.emergencyContactName) {
          patch.emergencyContactName = values.emergencyContactName;
        }
        if ((values.emergencyContactPhone ?? null) !== editing.emergencyContactPhone) {
          patch.emergencyContactPhone = values.emergencyContactPhone;
        }
        if ((values.emergencyContactRelation ?? null) !== editing.emergencyContactRelation) {
          patch.emergencyContactRelation = values.emergencyContactRelation;
        }
        const calendarChanged = (values.calendarId ?? null) !== editing.calendarId;
        if (Object.keys(patch).length === 0 && !calendarChanged) {
          message.info(t('employee.noChanges'));
          setModalOpen(false);
          return;
        }
        if (Object.keys(patch).length > 0) {
          await updateEmployee(editing.id, patch);
        }
        if (calendarChanged) {
          await assignEmployeeCalendar(editing.id, values.calendarId ?? null);
        }
        message.success(t('employee.updated', { name: values.fullName }));
      } else {
        const created = await createEmployee({
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
        if (values.calendarId) {
          await assignEmployeeCalendar(created.id, values.calendarId);
        }
        message.success(t('employee.created', { code: values.employeeCode }));
      }
      setModalOpen(false);
      actionRef.current?.reload();
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  const terminate = async () => {
    if (!terminateTarget) return;
    try {
      setSaving(true);
      await terminateEmployee(terminateTarget.id, terminateReason.trim() || undefined);
      message.success(t('employee.terminated', { name: terminateTarget.fullName }));
      setTerminateTarget(null);
      setTerminateReason('');
      actionRef.current?.reload();
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  const columns: ProColumns<Employee>[] = [
    { title: t('employee.column.employeeCode'), dataIndex: 'employeeCode', width: 110 },
    { title: t('employee.column.fullName'), dataIndex: 'fullName', width: 190, ellipsis: true },
    { title: t('employee.column.email'), dataIndex: 'email', width: 200, ellipsis: true },
    { title: t('employee.column.department'), dataIndex: 'departmentName', width: 130, ellipsis: true },
    { title: t('employee.column.position'), dataIndex: 'position', width: 140, ellipsis: true },
    {
      title: t('employee.column.status'),
      dataIndex: 'status',
      width: 115,
      render: (_, record) => (
        <Tag color={STATUS_COLOR[record.status]}>{t('status.' + record.status)}</Tag>
      ),
    },
    {
      title: t('employee.column.hiredAt'),
      dataIndex: 'hiredAt',
      width: 125,
      render: (_, record) => (record.hiredAt ? dayjs(record.hiredAt).format('DD/MM/YYYY') : '—'),
    },
    {
      title: t('actions'),
      valueType: 'option',
      width: 190,
      render: (_, record) => {
        const actions: ReactNode[] = [
          <Button key="profile" type="link" size="small" onClick={() => navigate(`/employees/${record.id}`)}>
            {t('employee.profile')}
          </Button>,
        ];
        if (canWrite) {
          actions.push(
            <Button key="edit" type="link" size="small" onClick={() => openEdit(record)}>
              {t('edit')}
            </Button>,
          );
          if (record.status !== 'TERMINATED') {
            actions.push(
              <Button
                key="terminate"
                type="link"
                size="small"
                danger
                onClick={() => {
                  setTerminateReason('');
                  setTerminateTarget(record);
                }}
              >
                {t('employee.terminate.action')}
              </Button>,
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
        headerTitle={t('employee.listTitle')}
        search={false}
        pagination={{ showSizeChanger: true, defaultPageSize: 20 }}
        options={{ density: false, setting: false }}
        toolBarRender={() => [
          <Input.Search
            key="q"
            allowClear
            placeholder={t('employee.filter.keyword')}
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
            placeholder={t('employee.filter.department')}
            style={{ width: 170 }}
            value={deptFilter}
            onChange={(value) => {
              setDeptFilter(value);
              actionRef.current?.reload();
            }}
            options={departments.map((d) => ({ value: d.id, label: d.name }))}
          />,
          <Select
            key="position"
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder={t('employee.filter.position')}
            style={{ width: 170 }}
            value={positionFilter}
            onChange={(value) => {
              setPositionFilter(value);
              actionRef.current?.reload();
            }}
            options={[...new Set([...positionOptions, ...jobTitleOptions])].sort()
              .map((p) => ({ value: p, label: p }))}
          />,
          <Select
            key="status"
            allowClear
            placeholder={t('employee.filter.status')}
            style={{ width: 150 }}
            value={statusFilter}
            onChange={(value) => {
              setStatusFilter(value);
              actionRef.current?.reload();
            }}
            options={[
              { value: 'ACTIVE', label: t('status.ACTIVE') },
              { value: 'PROBATION', label: t('status.PROBATION') },
              { value: 'ON_LEAVE', label: t('status.ON_LEAVE') },
              { value: 'TERMINATED', label: t('status.TERMINATED') },
            ]}
          />,
          canWrite ? (
            <Button key="create" type="primary" onClick={openCreate}>
              {t('employee.add')}
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
              position: positionFilter,
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
        title={
          editing ? t('employee.editTitle', { code: editing.employeeCode }) : t('employee.add')
        }
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        okText={t('save')}
        confirmLoading={saving}
        destroyOnClose
        width={640}
      >
        <Form
          form={form}
          layout="vertical"
          style={{ marginTop: 16 }}
          onValuesChange={(changed) => {
            if (editing || !('fullName' in changed)) return;
            // Gợi ý mã NV (= username đăng nhập) từ họ tên; HR vẫn sửa tay được
            const suggestion = generateEmployeeCode(changed.fullName ?? '');
            const current = form.getFieldValue('employeeCode');
            if (!current || current === suggestedCodeRef.current) {
              suggestedCodeRef.current = suggestion;
              form.setFieldsValue({ employeeCode: suggestion });
            }
          }}
        >
          <Form.Item
            name="employeeCode"
            label={t('employee.form.employeeCode')}
            extra={editing ? undefined : t('employee.form.employeeCodeExtra')}
            rules={[{ max: 20 }]}
          >
            <Input disabled={!!editing} placeholder={t('employee.form.employeeCodePlaceholder')} />
          </Form.Item>
          <Form.Item
            name="fullName"
            label={t('employee.form.fullName')}
            rules={[{ required: true, message: t('employee.form.fullNameRequired') }, { max: 200 }]}
          >
            <Input placeholder={t('employee.form.fullNamePlaceholder')} />
          </Form.Item>
          <Form.Item
            name="email"
            label={t('employee.form.email')}
            rules={[{ type: 'email', message: t('employee.form.emailInvalid') }, { max: 200 }]}
          >
            <Input placeholder={t('employee.form.emailPlaceholder')} />
          </Form.Item>
          <Form.Item
            name="departmentId"
            label={t('employee.form.department')}
            extra={editing ? t('employee.form.departmentExtra') : undefined}
          >
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder={t('employee.form.departmentPlaceholder')}
              options={departments.map((d) => ({ value: d.id, label: d.name }))}
            />
          </Form.Item>
          <Form.Item name="position" label={t('employee.form.position')} rules={[{ max: 100 }]}>
            <AutoComplete
              allowClear
              options={jobTitleOptions.map((p) => ({ value: p }))}
              placeholder={t('employee.form.positionPlaceholder')}
            />
          </Form.Item>
          <Form.Item
            name="managerId"
            label={t('employee.form.manager')}
            extra={editing ? t('employee.form.managerExtra') : undefined}
          >
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder={t('employee.form.managerPlaceholder')}
              options={managerOptions.map((e) => ({
                value: e.id,
                label: `${e.employeeCode} — ${e.fullName}`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="status"
            label={t('employee.form.status')}
            rules={[{ required: !!editing, message: t('employee.form.statusRequired') }]}
          >
            <Select
              disabled={!editing}
              placeholder={
                editing ? t('employee.form.statusPlaceholder') : t('employee.form.statusDefault')
              }
              options={[
                { value: 'ACTIVE', label: t('status.ACTIVE') },
                { value: 'PROBATION', label: t('status.PROBATION') },
                { value: 'ON_LEAVE', label: t('status.ON_LEAVE') },
              ]}
            />
          </Form.Item>
          <Form.Item name="hiredAt" label={t('employee.form.hiredAt')}>
            <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" />
          </Form.Item>
          <Form.Item name="calendarId" label={t('employee.form.calendarId')}>
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder={t('employee.form.calendarPlaceholder')}
              options={calendars.map((c) => ({ value: c.id, label: `${c.code} — ${c.name}` }))}
            />
          </Form.Item>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
            <Form.Item name="birthDate" label={t('employee.form.birthDate')}>
              <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" />
            </Form.Item>
            <Form.Item name="gender" label={t('employee.form.gender')}>
              <Select
                allowClear
                placeholder={t('employee.form.selectPlaceholder')}
                options={[
                  { value: 'MALE', label: t('gender.MALE') },
                  { value: 'FEMALE', label: t('gender.FEMALE') },
                  { value: 'OTHER', label: t('gender.OTHER') },
                ]}
              />
            </Form.Item>
            <Form.Item name="phone" label={t('employee.form.phone')} rules={[{ max: 20 }]}>
              <Input placeholder={t('employee.form.phonePlaceholder')} />
            </Form.Item>
          </div>
          <Form.Item name="nationalId" label={t('employee.form.nationalId')} rules={[{ max: 20 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="address" label={t('employee.form.address')} rules={[{ max: 300 }]}>
            <Input />
          </Form.Item>
          <Divider orientation="left" plain>
            {t('employee.form.additionalInfo')}
          </Divider>
          <Form.Item name="educationLevel" label={t('employee.form.educationLevel')}>
            <Select
              allowClear
              placeholder={t('employee.form.selectPlaceholder')}
              options={[
                { value: 'Trung cấp', label: t('employee.education.intermediate') },
                { value: 'Cao đẳng', label: t('employee.education.college') },
                { value: 'Đại học', label: t('employee.education.university') },
                { value: 'Thạc sĩ', label: t('employee.education.master') },
                { value: 'Tiến sĩ', label: t('employee.education.doctorate') },
              ]}
            />
          </Form.Item>
          <Form.Item name="skills" label={t('employee.form.skills')} rules={[{ max: 1000 }]}>
            <Input.TextArea rows={2} placeholder={t('employee.form.skillsPlaceholder')} />
          </Form.Item>
          <Form.Item
            name="emergencyContactName"
            label={t('employee.form.emergencyName')}
            rules={[{ max: 100 }]}
          >
            <Input />
          </Form.Item>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Form.Item
              name="emergencyContactPhone"
              label={t('employee.form.emergencyPhone')}
              rules={[{ max: 20 }]}
            >
              <Input placeholder={t('employee.form.phonePlaceholder')} />
            </Form.Item>
            <Form.Item
              name="emergencyContactRelation"
              label={t('employee.form.emergencyRelation')}
              rules={[{ max: 50 }]}
            >
              <Input placeholder={t('employee.form.emergencyRelationPlaceholder')} />
            </Form.Item>
          </div>
        </Form>
      </Modal>

      <Modal
        title={t('employee.terminate.title')}
        open={terminateTarget !== null}
        onOk={() => void terminate()}
        onCancel={() => setTerminateTarget(null)}
        confirmLoading={saving}
        okText={t('employee.terminate.action')}
        okButtonProps={{ danger: true }}
      >
        <p>
          {t('employee.terminate.description', { name: terminateTarget?.fullName ?? '' })}
        </p>
        <Input.TextArea
          rows={3}
          maxLength={500}
          value={terminateReason}
          onChange={(e) => setTerminateReason(e.target.value)}
          placeholder={t('employee.terminate.reasonPlaceholder')}
        />
      </Modal>
    </>
  );
}
