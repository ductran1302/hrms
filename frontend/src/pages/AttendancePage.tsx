import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { App as AntApp, Button, Tag } from 'antd';
import dayjs from 'dayjs';
import { useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { listAttendanceEmployees } from '../api/attendance';
import { errorMessage } from '../api/client';
import type { AttendanceEmployee } from '../api/types';

const STATUS_COLOR: Record<string, string> = {
  ACTIVE: 'green',
  PROBATION: 'orange',
  ON_LEAVE: 'blue',
  TERMINATED: 'red',
};

const STATUS_KEY: Record<string, string> = {
  ACTIVE: 'status.ACTIVE',
  PROBATION: 'status.PROBATION',
  ON_LEAVE: 'status.ON_LEAVE',
  TERMINATED: 'status.TERMINATED',
};

export default function AttendancePage() {
  const { t } = useTranslation();
  const { message } = AntApp.useApp();
  const actionRef = useRef<ActionType>(null);

  const columns: ProColumns<AttendanceEmployee>[] = [
    { title: t('attendance.employeeCode'), dataIndex: 'employeeCode', width: 110 },
    { title: t('attendance.fullName'), dataIndex: 'fullName', width: 200, ellipsis: true },
    { title: t('attendance.email'), dataIndex: 'email', width: 220, ellipsis: true },
    {
      title: t('attendance.department'),
      dataIndex: 'departmentName',
      width: 140,
      ellipsis: true,
    },
    {
      title: t('attendance.position'),
      dataIndex: 'position',
      width: 150,
      ellipsis: true,
    },
    {
      title: t('attendance.statusTitle'),
      dataIndex: 'status',
      width: 120,
      render: (_, record) => (
        <Tag color={STATUS_COLOR[record.status] ?? 'default'}>
          {STATUS_KEY[record.status] ? t(STATUS_KEY[record.status]) : record.status}
        </Tag>
      ),
    },
    {
      title: t('attendance.hiredAt'),
      dataIndex: 'hiredAt',
      width: 130,
      render: (_, record) => (record.hiredAt ? dayjs(record.hiredAt).format('DD/MM/YYYY') : '—'),
    },
    {
      title: t('attendance.terminatedAt'),
      dataIndex: 'terminatedAt',
      width: 130,
      render: (_, record) => (record.terminatedAt ? dayjs(record.terminatedAt).format('DD/MM/YYYY') : '—'),
    },
  ];

  return (
    <ProTable<AttendanceEmployee>
      rowKey="employeeId"
      actionRef={actionRef}
      columns={columns}
      headerTitle={t('attendance.title')}
      search={false}
      pagination={false}
      options={{ density: false, setting: false }}
      toolBarRender={() => [
        <Button key="reload" onClick={() => actionRef.current?.reload()}>
          {t('refresh')}
        </Button>,
      ]}
      request={async () => {
        try {
          const data = await listAttendanceEmployees();
          return { data, success: true };
        } catch (error) {
          message.error(errorMessage(error));
          return { data: [], success: false };
        }
      }}
    />
  );
}
