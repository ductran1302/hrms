import { Card, Col, Empty, Row, Select, Skeleton, Space, Statistic, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { errorMessage } from '../api/client';
import { getMyEmployee } from '../api/employees';
import { getLeaveQuota } from '../api/leaveQuotas';
import type { Employee, LeaveEntitlement, LeaveSourceValue } from '../api/types';

const SOURCE_COLOR: Record<LeaveSourceValue, string> = {
  BASE: 'blue',
  SENIORITY: 'geekblue',
  POSITION: 'purple',
  BONUS: 'green',
  CARRYOVER: 'cyan',
  ADJUSTMENT: 'orange',
};

const fmt = (n: number) => n.toLocaleString('vi-VN', { maximumFractionDigits: 4 });

export default function MyLeavePage() {
  const { t } = useTranslation();
  const currentYear = new Date().getFullYear();
  const [year, setYear] = useState(currentYear);
  const [employee, setEmployee] = useState<Employee | null>(null);
  const [quotas, setQuotas] = useState<{
    totalDays: number;
    remainingDays: number;
    entitlements: LeaveEntitlement[];
  } | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    getMyEmployee()
      .then((emp) => {
        if (cancelled) return;
        setEmployee(emp);
        return getLeaveQuota(emp.id, year).then((quota) => {
          if (!cancelled) setQuotas(quota);
        });
      })
      .catch((e) => {
        if (!cancelled) {
          setEmployee(null);
          setQuotas(null);
          if (e?.response?.status !== 404) message.error(errorMessage(e));
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [year]);

  const columns: ColumnsType<LeaveEntitlement> = [
    {
      title: t('leave.action.source'),
      dataIndex: 'source',
      render: (v: LeaveSourceValue) => <Tag color={SOURCE_COLOR[v]}>{t(`leave.source.${v}`)}</Tag>,
    },
    { title: t('leave.action.days'), dataIndex: 'days', align: 'right', render: fmt },
    { title: t('leave.action.reason'), dataIndex: 'reason', ellipsis: true, render: (v: string | null) => v ?? '—' },
    {
      title: t('leave.action.createdAt'),
      dataIndex: 'createdAt',
      render: (v: string) => new Date(v).toLocaleString('vi-VN'),
    },
  ];

  return (
    <div>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        {t('leave.my.title')}
      </Typography.Title>
      <Typography.Text type="secondary">{t('leave.my.subtitle')}</Typography.Text>

      <div style={{ margin: '16px 0' }}>
        <Select
          style={{ width: 110 }}
          value={year}
          onChange={setYear}
          options={Array.from({ length: 5 }, (_, i) => currentYear - 2 + i).map((y) => ({
            value: y,
            label: String(y),
          }))}
        />
      </div>

      {loading ? (
        <Skeleton active paragraph={{ rows: 4 }} />
      ) : !employee ? (
        <Empty description={t('leave.my.noEmployee')} />
      ) : !quotas ? (
        <Empty description={t('leave.empty')} />
      ) : (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Typography.Text strong>
            {employee.employeeCode} — {employee.fullName}
          </Typography.Text>
          <Row gutter={16}>
            <Col span={8}>
              <Card>
                <Statistic title={t('leave.my.total')} value={fmt(quotas.totalDays)} suffix=" ngày" />
              </Card>
            </Col>
            <Col span={8}>
              <Card>
                <Statistic
                  title={t('leave.my.remaining')}
                  value={fmt(quotas.remainingDays)}
                  suffix=" ngày"
                  valueStyle={{ color: '#52c41a' }}
                />
              </Card>
            </Col>
          </Row>
          <Typography.Text strong>{t('leave.my.breakdown')}</Typography.Text>
          <Table<LeaveEntitlement>
            rowKey="id"
            size="small"
            columns={columns}
            dataSource={quotas.entitlements}
            pagination={false}
          />
        </Space>
      )}
    </div>
  );
}
