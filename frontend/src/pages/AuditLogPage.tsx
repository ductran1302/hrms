import { useEffect, useState } from 'react';
import { Table, Input, Select, Space, Tag } from 'antd';
import { searchAuditLogs, searchAttendanceAuditLogs } from '../api/audit';
import type { AuditLog, PageResponse } from '../api/types';
import { errorMessage } from '../api/client';


export default function AuditLogPage() {
  const [service, setService] = useState<'identity' | 'attendance'>('identity');
  const [actor, setActor] = useState('');
  const [action, setAction] = useState('');
  const [data, setData] = useState<PageResponse<AuditLog> | null>(null);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(50);

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

  const columns = [
    { title: 'Thời gian', dataIndex: 'occurredAt', width: 180, render: (v: string) => new Date(v).toLocaleString() },
    { title: 'Actor', dataIndex: 'actor', width: 120 },
    { title: 'Action', dataIndex: 'action', width: 220 },
    { title: 'Target', dataIndex: 'targetType', width: 120, render: (v: string | null, r: AuditLog) => v ? `${v}:${r.targetId ?? ''}` : '-' },
    { title: 'Kết quả', dataIndex: 'outcome', width: 90, render: (v: string) => <Tag color={v === 'SUCCESS' ? 'green' : 'red'}>{v}</Tag> },
    { title: 'Detail', dataIndex: 'detail', ellipsis: true },
    { title: 'IP', dataIndex: 'clientIp', width: 120 },
  ];

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={12}>
      <Space wrap>
        <Select value={service} onChange={(v) => { setService(v); setPage(0); }} options={[
          { value: 'identity', label: 'Identity' },
          { value: 'attendance', label: 'Attendance' },
        ]} />
        <Input placeholder="Actor" value={actor} onChange={(e) => setActor(e.target.value)} onPressEnter={() => { setPage(0); void load(); }} style={{ width: 160 }} />
        <Input placeholder="Action" value={action} onChange={(e) => setAction(e.target.value)} onPressEnter={() => { setPage(0); void load(); }} style={{ width: 220 }} />
        <button onClick={() => { setPage(0); void load(); }}>Tìm</button>
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
        scroll={{ x: 1100 }}
      />
    </Space>
  );
}