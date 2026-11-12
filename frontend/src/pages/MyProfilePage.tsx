import { EditOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  Descriptions,
  Empty,
  Form,
  Input,
  Modal,
  Skeleton,
  Tag,
  Typography,
} from 'antd';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { errorMessage } from '../api/client';
import { getMyEmployee, updateMyProfile } from '../api/employees';
import type { Employee, EmployeeSelfUpdate } from '../api/types';

const STATUS_COLOR: Record<Employee['status'], string> = {
  ACTIVE: 'green',
  PROBATION: 'orange',
  ON_LEAVE: 'blue',
  TERMINATED: 'red',
};

/** Hồ sơ của chính mình — mọi role đều xem/sửa được các trường cá nhân an toàn. */
export default function MyProfilePage() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const [employee, setEmployee] = useState<Employee | null>(null);
  const [loading, setLoading] = useState(true);
  const [editOpen, setEditOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<EmployeeSelfUpdate>();

  const load = useCallback(() => {
    setLoading(true);
    getMyEmployee()
      .then(setEmployee)
      .catch((error) => message.error(errorMessage(error)))
      .finally(() => setLoading(false));
  }, [message]);

  useEffect(() => {
    load();
  }, [load]);

  const openEdit = () => {
    if (!employee) return;
    form.setFieldsValue({
      phone: employee.phone ?? '',
      address: employee.address ?? '',
      educationLevel: employee.educationLevel ?? '',
      skills: employee.skills ?? '',
      emergencyContactName: employee.emergencyContactName ?? '',
      emergencyContactPhone: employee.emergencyContactPhone ?? '',
      emergencyContactRelation: employee.emergencyContactRelation ?? '',
    });
    setEditOpen(true);
  };

  const handleFinish = async (values: EmployeeSelfUpdate) => {
    if (!employee) return;
    const payload: EmployeeSelfUpdate = {};
    const addIfChanged = (field: keyof EmployeeSelfUpdate, raw: string | undefined) => {
      const next = (raw ?? '').trim();
      if (next !== (employee[field] ?? '')) {
        payload[field] = next;
      }
    };
    addIfChanged('phone', values.phone);
    addIfChanged('address', values.address);
    addIfChanged('educationLevel', values.educationLevel);
    addIfChanged('skills', values.skills);
    addIfChanged('emergencyContactName', values.emergencyContactName);
    addIfChanged('emergencyContactPhone', values.emergencyContactPhone);
    addIfChanged('emergencyContactRelation', values.emergencyContactRelation);

    if (Object.keys(payload).length === 0) {
      setEditOpen(false);
      return;
    }

    setSaving(true);
    try {
      const updated = await updateMyProfile(payload);
      setEmployee(updated);
      message.success(t('profile.edit.saved'));
      setEditOpen(false);
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <Card>
        <Skeleton active paragraph={{ rows: 8 }} />
      </Card>
    );
  }
  if (!employee) {
    return <Empty description={t('profile.myNotFound')} />;
  }

  return (
    <Card
      title={t('profile.myTitle')}
      extra={
        <Button type="primary" icon={<EditOutlined />} onClick={openEdit}>
          {t('profile.edit.button')}
        </Button>
      }
    >
      <Descriptions bordered column={2} size="middle">
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
          {employee.gender ? t(`gender.${employee.gender}`) : '—'}
        </Descriptions.Item>
        <Descriptions.Item label={t('profile.info.nationalId')}>
          {employee.nationalId ?? '—'}
        </Descriptions.Item>
        <Descriptions.Item label={t('profile.info.address')}>
          {employee.address ?? '—'}
        </Descriptions.Item>
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
        <Descriptions.Item label={t('profile.info.position')}>
          {employee.position ?? '—'}
        </Descriptions.Item>
        <Descriptions.Item label={t('profile.info.hiredAt')}>
          {employee.hiredAt ? dayjs(employee.hiredAt).format('DD/MM/YYYY') : '—'}
        </Descriptions.Item>
        <Descriptions.Item label={t('profile.info.status')}>
          <Tag color={STATUS_COLOR[employee.status]}>{t(`status.${employee.status}`)}</Tag>
        </Descriptions.Item>
      </Descriptions>

      <Modal
        open={editOpen}
        title={t('profile.edit.title')}
        okText={t('save')}
        cancelText={t('cancel')}
        confirmLoading={saving}
        onOk={() => form.submit()}
        onCancel={() => setEditOpen(false)}
        destroyOnClose
      >
        <Typography.Paragraph type="secondary">
          {t('profile.edit.hint')}
        </Typography.Paragraph>
        <Form form={form} layout="vertical" onFinish={handleFinish}>
          <Form.Item name="phone" label={t('profile.info.phone')}>
            <Input maxLength={20} />
          </Form.Item>
          <Form.Item name="address" label={t('profile.info.address')}>
            <Input maxLength={300} />
          </Form.Item>
          <Form.Item name="educationLevel" label={t('profile.info.education')}>
            <Input maxLength={50} />
          </Form.Item>
          <Form.Item name="skills" label={t('profile.info.skills')}>
            <Input.TextArea rows={3} maxLength={1000} showCount />
          </Form.Item>
          <Form.Item name="emergencyContactName" label={t('profile.edit.contactName')}>
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item name="emergencyContactPhone" label={t('profile.edit.contactPhone')}>
            <Input maxLength={20} />
          </Form.Item>
          <Form.Item name="emergencyContactRelation" label={t('profile.edit.contactRelation')}>
            <Input maxLength={50} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
