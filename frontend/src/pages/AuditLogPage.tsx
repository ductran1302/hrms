import { useEffect, useMemo, useState } from 'react';
import { Table, Input, Select, Space, Tag, Button } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useTranslation } from 'react-i18next';
import { searchAuditLogs, searchAttendanceAuditLogs } from '../api/audit';
import { searchAccounts } from '../api/accounts';
import { listAttendanceEmployees } from '../api/attendance';
import { searchEmployees } from '../api/employees';
import type { AuditLog, Account, AttendanceEmployee, PageResponse } from '../api/types';
import { errorMessage } from '../api/client';

export default function AuditLogPage() {
  const { t } = useTranslation();
  const [service, setService] = useState<'identity' | 'attendance'>('identity');
  const [actor, setActor] = useState<string | undefined>();
  const [action, setAction] = useState('');
  const [data, setData] = useState<PageResponse<AuditLog> | null>(null);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(50);

  const [accounts, setAccounts] = useState<Account[]>([]);
  const [employees, setEmployees] = useState<AttendanceEmployee[]>([]);

  const actorOptions = useMemo(() => accounts.map((a) => ({
    value: a.username,
    label: `${a.username} — ${a.fullName}`,
  })), [accounts]);

  const accountMap = useMemo(() => {
    const m = new Map<string, string>();
    accounts.forEach((a) => m.set(a.username, a.fullName));
    return m;
  }, [accounts]);

  const employeeMap = useMemo(() => {
    const m = new Map<string, string>();
    employees.forEach((e) => m.set(e.employeeId, e.fullName));
    // also map by employeeCode for actor display
    return m;
  }, [employees]);

  const load = async () => {
    setLoading(true);
    try {
      const params = { actor: actor || undefined, action: action || undefined, page, size };
      const res = service === 'identity'
        ? await searchAuditLogs(params)
        : await searchAttendanceAuditLogs(params);
      setData(res);
    } catch (e) {
      // eslint-disable-next-line no-console
      console.error(errorMessage(e));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, [service, page, size]);

  useEffect(() => {
    // load accounts for actor autocomplete
    searchAccounts({ size: 500 }).then((r) => setAccounts(r.items)).catch(() => {});
    // load employees for target name resolution (identity)
    if (service === 'identity') {
      searchEmployees({ size: 500 }).then((r) => {
        const mapped: AttendanceEmployee[] = r.items.map((e) => ({
          employeeId: e.id,
          employeeCode: e.employeeCode,
          fullName: e.fullName,
          email: e.email,
          departmentId: e.departmentId,
          departmentName: e.departmentName,
          position: e.position,
          status: e.status,
          hiredAt: e.hiredAt,
          terminatedAt: null,
        }));
        setEmployees(mapped);
      }).catch(() => {});
    } else {
      listAttendanceEmployees().then(setEmployees).catch(() => {});
    }
  }, [service]);

  const searchEmps = async (keyword: string) => {
    try {
      if (service === 'identity') {
        const r = await searchAccounts({ q: keyword || undefined, size: 20 });
        setAccounts(r.items);
      } else {
        const r = await searchAccounts({ q: keyword || undefined, size: 20 });
        setAccounts(r.items);
      }
    } catch (e) {
      // eslint-disable-next-line no-console
      console.error(errorMessage(e));
    }
  };

  const resolveTarget = (row: AuditLog): string => {
    const type = row.targetType ?? '';
    const id = row.targetId ?? '';
    if (type === 'Employee' && id && employeeMap.has(id)) {
      return `Employee: ${employeeMap.get(id)}`;
    }
    if (type === 'Account' && id) {
      return `Account: ${id}`;
    }
    // attendance: try parse employeeId from detail
    if (service === 'attendance' && row.detail) {
      const m = row.detail.match(/employeeId=([0-9a-f-]{36})/i);
      if (m && employeeMap.has(m[1])) {
        return `Employee: ${employeeMap.get(m[1])}`;
      }
    }
    if (!type && !id) return '-';
    return id ? `${type}: ${id}` : type;
  };

  const columns = [
    { title: t('audit.time'), dataIndex: 'occurredAt', width: 180, render: (v: string) => dayjs(v).format('DD/MM/YYYY HH:mm:ss') },
    {
      title: t('audit.actor'),
      dataIndex: 'actor',
      width: 160,
      render: (v: string) => (
        <div>
          <div>{accountMap.get(v) ?? v}</div>
          <div style={{ fontSize: 11, color: '#8c8c8c' }}>{v}</div>
        </div>
      ),
    },
    { title: t('audit.action'), dataIndex: 'action', width: 230 },
    { title: t('audit.target'), dataIndex: 'targetType', width: 260, render: (_: string | null, r: AuditLog) => resolveTarget(r) },
    { title: t('audit.outcome'), dataIndex: 'outcome', width: 100, render: (v: string) => <Tag color={v === 'SUCCESS' ? 'green' : 'red'}>{v === 'SUCCESS' ? t('audit.success') : t('audit.failure')}</Tag> },
    { title: t('audit.detail'), dataIndex: 'detail', ellipsis: true },
    { title: t('audit.ip'), dataIndex: 'clientIp', width: 120 },
  ];

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={12}>
      <Space wrap>
        <Select value={service} onChange={(v) => { setService(v); setPage(0); }} options={[
          { value: 'identity', label: 'Identity' },
          { value: 'attendance', label: 'Attendance' },
        ]} style={{ width: 160 }} />
        <Select
          showSearch
          filterOption={false}
          value={actor}
          onChange={(v) => setActor(v)}
          onSearch={(kw) => void searchEmps(kw)}
          options={actorOptions}
          placeholder={t('audit.actorPlaceholder')}
          style={{ width: 260 }}
          allowClear
        />
        <Input placeholder={t('audit.actionPlaceholder')} value={action} onChange={(e) => setAction(e.target.value)} onPressEnter={() => { setPage(0); void load(); }} style={{ width: 220 }} />
        <Button type="primary" icon={<SearchOutlined />} onClick={() => { setPage(0); void load(); }}>
          {t('audit.search')}
        </Button>
      </Space>
      <Table<AuditLog>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data?.items ?? []}
        pagination={{
          current: page + 1,
          pageSize: size,
          total: data?.total ?? 0,
          onChange: (p, ps) => { setPage(p - 1); setSize(ps); },
        }}
        scroll={{ x: 1200 }}
      />
    </Space>
  );
}