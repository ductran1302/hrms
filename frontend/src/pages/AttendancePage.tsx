import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { App as AntApp, Button, Tag } from 'antd';
import dayjs from 'dayjs';
import { useRef } from 'react';
import { listAttendanceEmployees } from '../api/attendance';
import { errorMessage } from '../api/client';
import type { AttendanceEmployee } from '../api/types';

const STATUS_COLOR: Record<string, string> = {
  ACTIVE: 'green',
  PROBATION: 'orange',
  TERMINATED: 'red',
};

const STATUS_LABEL: Record<string, string> = {
  ACTIVE: 'Đang làm',
  PROBATION: 'Thử việc',
  TERMINATED: 'Đã nghỉ',
};

export default function AttendancePage() {
  const { message } = AntApp.useApp();
  const actionRef = useRef<ActionType>(null);

  const columns: ProColumns<AttendanceEmployee>[] = [
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
        <Tag color={STATUS_COLOR[record.status] ?? 'default'}>
          {STATUS_LABEL[record.status] ?? record.status}
        </Tag>
      ),
    },
    {
      title: 'Ngày vào làm',
      dataIndex: 'hiredAt',
      width: 130,
      render: (_, record) => (record.hiredAt ? dayjs(record.hiredAt).format('DD/MM/YYYY') : '—'),
    },
    {
      title: 'Ngày nghỉ',
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
      headerTitle="Chấm công — dữ liệu projection từ Kafka"
      search={false}
      pagination={false}
      options={{ density: false, setting: false }}
      toolBarRender={() => [
        <Button key="reload" onClick={() => actionRef.current?.reload()}>
          Làm mới
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
