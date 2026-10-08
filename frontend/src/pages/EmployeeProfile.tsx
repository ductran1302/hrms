import { ArrowLeftOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  Descriptions,
  Empty,
  Skeleton,
  Table,
  Tabs,
  Tag,
  Timeline,
  Typography,
} from 'antd';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { listContracts } from '../api/contracts';
import { getEmployee, getEmployeeHistory, searchEmployees } from '../api/employees';
import type { Contract, Employee, JobHistoryItem } from '../api/types';

const STATUS_META: Record<Employee['status'], { label: string; color: string }> = {
  ACTIVE: { label: 'Đang làm', color: 'green' },
  PROBATION: { label: 'Thử việc', color: 'orange' },
  TERMINATED: { label: 'Đã nghỉ', color: 'red' },
};

const GENDER_LABEL: Record<string, string> = { MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác' };

const FIELD_LABEL: Record<string, string> = {
  department: 'Phòng ban',
  position: 'Chức danh',
  manager: 'Quản lý trực tiếp',
  status: 'Trạng thái',
  'contract.type': 'Loại hợp đồng',
  'contract.salary': 'Lương',
  'contract.status': 'Trạng thái hợp đồng',
};

export default function EmployeeProfile() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [employee, setEmployee] = useState<Employee | null>(null);
  const [history, setHistory] = useState<JobHistoryItem[]>([]);
  const [contracts, setContracts] = useState<Contract[]>([]);
  const [peers, setPeers] = useState<Employee[]>([]);
  const [loading, setLoading] = useState(true);

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
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setLoading(false);
    }
  }, [id, message]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    searchEmployees({ size: 500 })
      .then((page) => setPeers(page.items))
      .catch(() => setPeers([]));
  }, []);

  if (loading) {
    return (
      <Card>
        <Skeleton active paragraph={{ rows: 8 }} />
      </Card>
    );
  }
  if (!employee) {
    return <Empty description="Không tìm thấy nhân viên" />;
  }

  const infoTab = (
    <Descriptions bordered column={2} size="middle" style={{ marginTop: 8 }}>
      <Descriptions.Item label="Mã nhân viên">{employee.employeeCode}</Descriptions.Item>
      <Descriptions.Item label="Họ và tên">{employee.fullName}</Descriptions.Item>
      <Descriptions.Item label="Email">{employee.email ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="SĐT">{employee.phone ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Ngày sinh">
        {employee.birthDate ? dayjs(employee.birthDate).format('DD/MM/YYYY') : '—'}
      </Descriptions.Item>
      <Descriptions.Item label="Giới tính">
        {employee.gender ? GENDER_LABEL[employee.gender] ?? employee.gender : '—'}
      </Descriptions.Item>
      <Descriptions.Item label="Số CCCD">{employee.nationalId ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Địa chỉ">{employee.address ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Phòng ban">{employee.departmentName ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Chức danh">{employee.position ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Quản lý trực tiếp">
        {employee.managerId
          ? peers.find((p) => p.id === employee.managerId)?.fullName ?? employee.managerId
          : '—'}
      </Descriptions.Item>
      <Descriptions.Item label="Ngày vào làm">
        {employee.hiredAt ? dayjs(employee.hiredAt).format('DD/MM/YYYY') : '—'}
      </Descriptions.Item>
      <Descriptions.Item label="Trạng thái">
        <Tag color={STATUS_META[employee.status].color}>{STATUS_META[employee.status].label}</Tag>
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
          title: 'Loại HĐ',
          dataIndex: 'contractType',
          render: (value: string) =>
            ({
              PROBATION: 'Thử việc',
              FULL_TIME: 'Chính thức',
              PART_TIME: 'Bán thời gian',
              INTERN: 'Thực tập',
              OTHER: 'Khác',
            })[value] ?? value,
        },
        {
          title: 'Từ ngày',
          dataIndex: 'startDate',
          render: (value: string) => dayjs(value).format('DD/MM/YYYY'),
        },
        {
          title: 'Đến ngày',
          dataIndex: 'endDate',
          render: (value: string | null) => (value ? dayjs(value).format('DD/MM/YYYY') : '—'),
        },
        { title: 'Chức danh', dataIndex: 'position', render: (v: string | null) => v ?? '—' },
        {
          title: 'Lương (VND)',
          dataIndex: 'salary',
          render: (value: number | null) =>
            value == null ? (
              <Typography.Text type="secondary" italic>
                Ẩn (HR_ADMIN)
              </Typography.Text>
            ) : (
              new Intl.NumberFormat('vi-VN').format(value)
            ),
        },
        {
          title: 'Trạng thái',
          dataIndex: 'status',
          render: (value: Contract['status']) => (
            <Tag
              color={value === 'ACTIVE' ? 'green' : value === 'EXPIRED' ? 'orange' : 'red'}
            >
              {value === 'ACTIVE' ? 'Hiệu lực' : value === 'EXPIRED' ? 'Hết hạn' : 'Chấm dứt'}
            </Tag>
          ),
        },
      ]}
      locale={{ emptyText: 'Chưa có hợp đồng' }}
    />
  );

  const historyTab = history.length === 0 ? (
    <Empty description="Chưa có thay đổi nào được ghi lại" style={{ marginTop: 24 }} />
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
              {item.actorId ? ` — bởi ${item.actorId}` : ''}
            </Typography.Text>
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
            onClick={() => navigate('/nhan-su')}
            style={{ marginRight: 8 }}
          />
          {employee.fullName}{' '}
          <Typography.Text type="secondary" style={{ fontSize: 14, fontWeight: 400 }}>
            {employee.employeeCode}
          </Typography.Text>
        </span>
      }
      extra={<Tag color={STATUS_META[employee.status].color}>{STATUS_META[employee.status].label}</Tag>}
    >
      <Tabs
        defaultActiveKey="info"
        items={[
          { key: 'info', label: 'Thông tin', children: infoTab },
          { key: 'contract', label: `Hợp đồng (${contracts.length})`, children: contractTab },
          { key: 'history', label: `Lịch sử (${history.length})`, children: historyTab },
        ]}
      />
    </Card>
  );
}
