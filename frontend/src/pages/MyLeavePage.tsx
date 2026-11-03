import {
  CalendarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  GiftOutlined,
  HourglassOutlined,
  InfoCircleOutlined,
  RightOutlined,
  SwapOutlined,
  ToolOutlined,
  TrophyOutlined,
  UserOutlined,
} from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  Col,
  Empty,
  Modal,
  Row,
  Select,
  Skeleton,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { ReactNode } from 'react';
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { errorMessage } from '../api/client';
import { getMyEmployee } from '../api/employees';
import { getLeaveQuota } from '../api/leaveQuotas';
import type { Employee, LeaveEntitlement, LeaveQuota, LeaveSourceValue } from '../api/types';

const { Text, Title } = Typography;

/** Thứ tự dòng trong bảng chi tiết (đúng thứ tự cộng tổng). */
const SOURCES: LeaveSourceValue[] = [
  'BASE',
  'SENIORITY',
  'POSITION',
  'BONUS',
  'CARRYOVER',
  'ADJUSTMENT',
];

const SOURCE_META: Record<LeaveSourceValue, { color: string; bg: string; icon: ReactNode }> = {
  BASE: { color: '#1677ff', bg: '#e6f4ff', icon: <CalendarOutlined /> },
  SENIORITY: { color: '#722ed1', bg: '#f9f0ff', icon: <TrophyOutlined /> },
  POSITION: { color: '#52c41a', bg: '#f6ffed', icon: <UserOutlined /> },
  BONUS: { color: '#fa8c16', bg: '#fff7e6', icon: <GiftOutlined /> },
  CARRYOVER: { color: '#13c2c2', bg: '#e6fffb', icon: <SwapOutlined /> },
  ADJUSTMENT: { color: '#f5222d', bg: '#fff1f0', icon: <ToolOutlined /> },
};

/** Cửa sổ "sắp hết hạn": chưa hết hạn ∧ expiresAt ≤ hôm nay + 30 ngày. */
const EXPIRING_WINDOW_DAYS = 30;

type SourceRow = {
  source: LeaveSourceValue;
  total: number;
  used: number;
  remaining: number;
  /** Hạn sớm nhất chưa hết hạn của nguồn (null = dòng cũ không giới hạn). */
  expiry: string | null;
};

function isoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function slashDate(iso: string): string {
  const [y, m, d] = iso.split('-');
  return `${d}/${m}/${y}`;
}

/** Range năm TC: start = y-mm-dd (từ fyStart của response), end = start + 1 năm − 1 ngày (cùng rule backend). */
function fyRange(year: number, month: number, day: number): { from: string; to: string } {
  const fmt = (dt: Date) =>
    `${String(dt.getUTCDate()).padStart(2, '0')}/${String(dt.getUTCMonth() + 1).padStart(2, '0')}/${dt.getUTCFullYear()}`;
  const start = new Date(Date.UTC(year, month - 1, day));
  const end = new Date(Date.UTC(year, month - 1, day));
  end.setUTCFullYear(end.getUTCFullYear() + 1);
  end.setUTCDate(end.getUTCDate() - 1);
  return { from: fmt(start), to: fmt(end) };
}

function KpiCard({
  icon,
  iconBg,
  iconColor,
  label,
  value,
  hint,
  caption,
}: {
  icon: ReactNode;
  iconBg: string;
  iconColor: string;
  label: ReactNode;
  value: ReactNode;
  hint?: ReactNode;
  caption?: ReactNode;
}) {
  return (
    <div className="org-stat">
      <div className="org-stat-icon" style={{ background: iconBg, color: iconColor }}>
        {icon}
      </div>
      <div className="org-stat-body">
        <div className="org-stat-label">{label}</div>
        <div className="org-stat-row">
          <div className="org-stat-value">{value}</div>
          {(hint || caption) && (
            <div className="org-stat-hint">
              {hint && <span className="org-stat-delta">{hint}</span>}
              {caption && <span className="org-stat-caption">{caption}</span>}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default function MyLeavePage() {
  const { t, i18n } = useTranslation();
  const { message } = App.useApp();
  const locale = i18n.language.startsWith('vi') ? 'vi-VN' : 'en-US';
  const currentYear = new Date().getFullYear();
  const [year, setYear] = useState(currentYear);
  const [employee, setEmployee] = useState<Employee | null>(null);
  const [quota, setQuota] = useState<LeaveQuota | null>(null);
  const [loading, setLoading] = useState(true);
  const [detailSource, setDetailSource] = useState<LeaveSourceValue | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    getMyEmployee()
      .then((emp) => {
        if (cancelled) return;
        setEmployee(emp);
        return getLeaveQuota(emp.id, year).then((q) => {
          if (!cancelled) setQuota(q);
        });
      })
      .catch((e) => {
        if (!cancelled) {
          setEmployee(null);
          setQuota(null);
          if (e?.response?.status !== 404) message.error(errorMessage(e));
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [year, message]);

  const fmtDays = (n: number) => n.toLocaleString(locale, { maximumFractionDigits: 4 });
  const fmtPct = (n: number) =>
    `(${n.toLocaleString(locale, { minimumFractionDigits: 1, maximumFractionDigits: 1 })}%)`;
  const fmtDateTime = (v: string) => new Date(v).toLocaleString(locale);

  /** Dòng chưa hết hạn — đúng bộ dòng backend cộng vào Tổng/Còn lại (gồm BONUS spill). */
  const activeEnts = useMemo(() => quota?.entitlements.filter((e) => !e.expired) ?? [], [quota]);

  /** Tổng theo nguồn — ẩn dòng = 0 (BASE luôn hiện); đã dùng = 0 tới khi có module xin nghỉ. */
  const sourceRows = useMemo<SourceRow[]>(() => {
    if (!quota) return [];
    return SOURCES.flatMap((source) => {
      const rows = quota.entitlements.filter((e) => e.source === source && !e.expired);
      const total = rows.reduce((sum, e) => sum + e.days, 0);
      if (source !== 'BASE' && total === 0) return [];
      const expiries = rows
        .map((e) => e.expiresAt)
        .filter((v): v is string => !!v)
        .sort();
      return [{ source, total, used: 0, remaining: total, expiry: expiries[0] ?? null }];
    });
  }, [quota]);

  const expiring = useMemo(() => {
    const horizon = new Date();
    horizon.setDate(horizon.getDate() + EXPIRING_WINDOW_DAYS);
    const hIso = isoDate(horizon);
    return activeEnts
      .filter((e) => e.expiresAt !== null && e.expiresAt <= hIso)
      .sort((a, b) => (a.expiresAt ?? '').localeCompare(b.expiresAt ?? ''));
  }, [activeEnts]);

  const expiringDays = expiring.reduce((sum, e) => sum + e.days, 0);
  const nextExpiry = expiring[0]?.expiresAt ?? null;

  /** {month, day} của năm TC — derive từ response để label mọi năm trong Select. */
  const fyPattern = useMemo(() => {
    if (!quota?.fyStart) return null;
    const [, m, d] = quota.fyStart.split('-').map(Number);
    return { m, d };
  }, [quota?.fyStart]);

  const yearOptions = useMemo(
    () =>
      Array.from({ length: 5 }, (_, i) => currentYear - 2 + i).map((y) => ({
        value: y,
        label: fyPattern
          ? t('leave.my.fiscalYear', { year: y, ...fyRange(y, fyPattern.m, fyPattern.d) })
          : String(y),
      })),
    [fyPattern, t, currentYear],
  );

  /** Ngày dữ liệu được cập nhật = calculatedAt mới nhất (fallback: hôm nay). */
  const updatedIso = useMemo(() => {
    const stamps = (quota?.entitlements ?? [])
      .map((e) => e.calculatedAt?.slice(0, 10))
      .filter((v): v is string => !!v)
      .sort();
    return stamps.length > 0 ? stamps[stamps.length - 1] : isoDate(new Date());
  }, [quota]);

  const usedDays = 0;
  const usedPct = quota && quota.totalDays !== 0 ? (usedDays / quota.totalDays) * 100 : 0;
  const remainingPct = quota && quota.totalDays !== 0 ? (quota.remainingDays / quota.totalDays) * 100 : 0;

  const detailColumns: ColumnsType<LeaveEntitlement> = [
    {
      title: t('leave.my.historyCol.date'),
      dataIndex: 'createdAt',
      render: (v: string) => fmtDateTime(v),
    },
    {
      title: t('leave.my.historyCol.days'),
      dataIndex: 'days',
      align: 'right',
      render: (v: number) => (v > 0 ? `+${fmtDays(v)}` : fmtDays(v)),
    },
    {
      title: t('leave.action.reason'),
      dataIndex: 'reason',
      ellipsis: true,
      render: (v: string | null) => v ?? '—',
    },
    {
      title: t('leave.action.expiresAt'),
      dataIndex: 'expiresAt',
      render: (v: string | null, row) => (
        <Space size={4}>
          <span>{v ? slashDate(v) : '—'}</span>
          {row.expired && <Tag color="red">{t('leave.action.expiredTag')}</Tag>}
          {row.year !== year && <Tag>{t('leave.action.fromYear', { year: row.year })}</Tag>}
        </Space>
      ),
    },
  ];

  const tableColumns: ColumnsType<SourceRow> = [
    {
      title: t('leave.my.col.type'),
      dataIndex: 'source',
      render: (_: unknown, row: SourceRow) => {
        const meta = SOURCE_META[row.source];
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div
              style={{
                width: 36,
                height: 36,
                borderRadius: 8,
                background: meta.bg,
                color: meta.color,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 16,
              }}
            >
              {meta.icon}
            </div>
            <div>
              <div style={{ fontWeight: 600 }}>{t(`leave.source.${row.source}`)}</div>
              <div className="leave-my-hint" style={{ fontSize: 12 }}>
                {t(`leave.my.sourceHint.${row.source}`)}
              </div>
            </div>
          </div>
        );
      },
    },
    {
      title: t('leave.my.col.total'),
      dataIndex: 'total',
      align: 'right',
      render: (v: number) => <Text strong>{fmtDays(v)}</Text>,
    },
    { title: t('leave.my.col.used'), dataIndex: 'used', align: 'right', render: fmtDays },
    {
      title: t('leave.my.col.remaining'),
      dataIndex: 'remaining',
      align: 'right',
      render: (v: number) => <Text strong>{fmtDays(v)}</Text>,
    },
    {
      title: t('leave.my.col.expiry'),
      dataIndex: 'expiry',
      render: (v: string | null) =>
        v ? (
          <div>
            <div>{slashDate(v)}</div>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {v === quota?.fyEnd
                ? t('leave.my.expiryByFy')
                : t('leave.my.expiryCustom', { date: slashDate(v) })}
            </Text>
          </div>
        ) : (
          <Text type="secondary">—</Text>
        ),
    },
    {
      title: t('leave.my.col.action'),
      render: (_: unknown, row: SourceRow) => (
        <Button size="small" onClick={() => setDetailSource(row.source)}>
          {t('leave.my.viewDetail')} <RightOutlined />
        </Button>
      ),
    },
  ];

  const historyColumns: ColumnsType<Record<string, never>> = [
    { title: t('leave.my.historyCol.date'), dataIndex: 'date' },
    { title: t('leave.my.historyCol.type'), dataIndex: 'type' },
    { title: t('leave.my.historyCol.leaveType'), dataIndex: 'leaveType' },
    { title: t('leave.my.historyCol.days'), dataIndex: 'days', align: 'right' },
    { title: t('leave.my.historyCol.note'), dataIndex: 'note' },
  ];

  const breakdown = quota
    ? sourceRows.map((r) => `${t(`leave.source.${r.source}`)}: ${fmtDays(r.total)}`).join(' · ')
    : '';

  return (
    <div>
      <div className="leave-my-header">
        <div>
          <Title level={4} style={{ marginTop: 0, marginBottom: 4 }}>
            {t('leave.my.title')}
          </Title>
          <Text type="secondary">{t('leave.my.subtitle')}</Text>
        </div>
        <Select value={year} onChange={setYear} options={yearOptions} style={{ width: 360 }} />
      </div>

      {loading ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : !employee ? (
        <Empty description={t('leave.my.noEmployee')} />
      ) : !quota ? (
        <Empty description={t('leave.empty')} />
      ) : (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <div className="org-stats">
            <KpiCard
              icon={<CalendarOutlined />}
              iconBg="#e6f4ff"
              iconColor="#1677ff"
              label={t('leave.my.total')}
              value={
                <>
                  {fmtDays(quota.totalDays)}{' '}
                  <span style={{ fontSize: 14, fontWeight: 500 }}>
                    {t('leave.my.dayUnit', { count: quota.totalDays })}
                  </span>
                  <Tooltip title={breakdown}>
                    <InfoCircleOutlined
                      className="leave-my-hint"
                      style={{ fontSize: 14, marginLeft: 6 }}
                    />
                  </Tooltip>
                </>
              }
              caption={t('leave.my.policyCaption')}
            />
            <KpiCard
              icon={<CheckCircleOutlined />}
              iconBg="#f6ffed"
              iconColor="#52c41a"
              label={
                <>
                  {t('leave.my.used')}{' '}
                  <Tooltip title={t('leave.my.usedHint')}>
                    <InfoCircleOutlined className="leave-my-hint" style={{ fontSize: 12 }} />
                  </Tooltip>
                </>
              }
              value={
                <>
                  {fmtDays(usedDays)}{' '}
                  <span style={{ fontSize: 14, fontWeight: 500 }}>
                    {t('leave.my.dayUnit', { count: usedDays })}
                  </span>
                </>
              }
              hint={fmtPct(usedPct)}
            />
            <KpiCard
              icon={<ClockCircleOutlined />}
              iconBg="#e6f4ff"
              iconColor="#1677ff"
              label={t('leave.my.remaining')}
              value={
                <>
                  {fmtDays(quota.remainingDays)}{' '}
                  <span style={{ fontSize: 14, fontWeight: 500 }}>
                    {t('leave.my.dayUnit', { count: quota.remainingDays })}
                  </span>
                </>
              }
              hint={fmtPct(remainingPct)}
            />
            <KpiCard
              icon={<HourglassOutlined />}
              iconBg="#fff7e6"
              iconColor="#fa8c16"
              label={t('leave.my.expiring')}
              value={
                <span style={expiringDays > 0 ? { color: '#fa8c16' } : undefined}>
                  {fmtDays(expiringDays)}{' '}
                  <span style={{ fontSize: 14, fontWeight: 500 }}>
                    {t('leave.my.dayUnit', { count: expiringDays })}
                  </span>
                </span>
              }
              caption={
                nextExpiry
                  ? t('leave.my.expiringCaption', { date: slashDate(nextExpiry) })
                  : t('leave.my.expiringNone')
              }
            />
          </div>

          <Row gutter={[16, 16]}>
            <Col xs={24} xl={17}>
              <Card title={t('leave.my.detailTitle')}>
                <Table<SourceRow>
                  rowKey="source"
                  columns={tableColumns}
                  dataSource={sourceRows}
                  pagination={false}
                />
              </Card>
            </Col>
            <Col xs={24} xl={7}>
              <Card>
                {expiring.length === 0 ? (
                  <div className="leave-my-expiring-empty">
                    <div className="leave-my-expiring-icon">
                      <CalendarOutlined />
                    </div>
                    <Text strong style={{ fontSize: 15 }}
                    >
                      {t('leave.my.expiringEmptyTitle')}
                    </Text>
                    <Text type="secondary" style={{ fontSize: 13 }}>
                      {t('leave.my.expiringEmptyDesc')}
                    </Text>
                  </div>
                ) : (
                  <Space direction="vertical" size={0} style={{ width: '100%' }}>
                    <Text strong style={{ display: 'block', marginBottom: 8 }}>
                      {t('leave.my.expiringTitle')}
                    </Text>
                    {expiring.map((e) => (
                      <div key={e.id} className="leave-my-expiring-item">
                        <Space size={6}>
                          <span
                            style={{
                              color: SOURCE_META[e.source].color,
                              background: SOURCE_META[e.source].bg,
                              borderRadius: 6,
                              padding: '2px 6px',
                              display: 'inline-flex',
                            }}
                          >
                            {SOURCE_META[e.source].icon}
                          </span>
                          <span>{t(`leave.source.${e.source}`)}</span>
                          {e.year !== year && (
                            <Tag>{t('leave.action.fromYear', { year: e.year })}</Tag>
                          )}
                        </Space>
                        <Space size={10}>
                          <Text type="secondary">{e.expiresAt ? slashDate(e.expiresAt) : '—'}</Text>
                          <Text strong>
                            {fmtDays(e.days)} {t('leave.my.dayUnit', { count: e.days })}
                          </Text>
                        </Space>
                      </div>
                    ))}
                  </Space>
                )}
                <div className="leave-my-updated">
                  <InfoCircleOutlined style={{ marginTop: 2 }} />
                  <span>{t('leave.my.updatedAt', { date: slashDate(updatedIso) })}</span>
                </div>
              </Card>
            </Col>
          </Row>

          <Card title={t('leave.my.historyTitle')}>
            <Table<Record<string, never>>
              rowKey={() => 'none'}
              columns={historyColumns}
              dataSource={[]}
              pagination={false}
              locale={{
                emptyText: (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={t('leave.my.historyEmpty')}
                  />
                ),
              }}
            />
          </Card>
        </Space>
      )}

      <Modal
        title={t('leave.action.entitlements', {
          source: detailSource ? t(`leave.source.${detailSource}`) : '',
        })}
        open={detailSource !== null}
        footer={null}
        width={780}
        onCancel={() => setDetailSource(null)}
      >
        <Table<LeaveEntitlement>
          rowKey="id"
          size="small"
          columns={detailColumns}
          dataSource={(quota?.entitlements ?? []).filter((e) => e.source === detailSource)}
          pagination={false}
        />
      </Modal>
    </div>
  );
}
