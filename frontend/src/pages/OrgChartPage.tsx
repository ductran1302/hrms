import {
  ApartmentOutlined,
  BankOutlined,
  DownOutlined,
  ExportOutlined,
  IdcardOutlined,
  MinusOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  TeamOutlined,
  UnorderedListOutlined,
  UserOutlined,
} from '@ant-design/icons';
import {
  App,
  Avatar,
  Breadcrumb,
  Button,
  Empty,
  Input,
  Segmented,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { TableProps } from 'antd';
import { toPng } from 'html-to-image';
import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { listDepartments } from '../api/departments';
import { getOrgChart, getOrgChartStats } from '../api/orgchart';
import type { Department, OrgChartNode, OrgChartStats } from '../api/types';
import { useAuth } from '../auth/AuthContext';

type Mode = 'chart' | 'list';

const STATUS_META: Record<string, { key: string; color: string }> = {
  ACTIVE: { key: 'status.ACTIVE', color: 'green' },
  PROBATION: { key: 'status.PROBATION', color: 'orange' },
  ON_LEAVE: { key: 'status.ON_LEAVE', color: 'blue' },
  TERMINATED: { key: 'status.TERMINATED', color: 'red' },
};

const ZOOM_MIN = 50;
const ZOOM_MAX = 150;
const ZOOM_STEP = 10;

/**
 * Icon vị trí trên card sơ đồ — Bank (ngân hàng/HĐQT) chỉ dành cho Chủ tịch;
 * các cấp khác dùng icon riêng theo vai trò.
 */
function deptIcon(position: string | null | undefined): ReactNode {
  const p = (position ?? '').toLowerCase();
  if (p.includes('chủ tịch')) return <BankOutlined />;
  if (
    p.includes('giám đốc') ||
    p.includes('cto') ||
    p.includes('cfo') ||
    p.includes('coo')
  ) {
    return <SafetyCertificateOutlined />;
  }
  if (p.includes('trưởng') || p.includes('quản lý') || p.includes('manager')) {
    return <TeamOutlined />;
  }
  return <IdcardOutlined />;
}

/** Giữ nguyên nhánh chứa node khớp + node khớp (cả nhánh con của nó). */
function filterTree(
  nodes: OrgChartNode[],
  match: (node: OrgChartNode) => boolean,
): OrgChartNode[] {
  const out: OrgChartNode[] = [];
  for (const node of nodes) {
    if (match(node)) {
      out.push(node);
      continue;
    }
    const children = filterTree(node.children, match);
    if (children.length > 0) out.push({ ...node, children });
  }
  return out;
}

function collectOffices(nodes: OrgChartNode[], into: Set<string>): Set<string> {
  for (const node of nodes) {
    if (node.office) into.add(node.office);
    collectOffices(node.children, into);
  }
  return into;
}

interface FlatRow {
  node: OrgChartNode;
  managerName: string | null;
  depth: number;
}

function flatten(
  nodes: OrgChartNode[],
  managerName: string | null,
  depth: number,
  out: FlatRow[],
): FlatRow[] {
  for (const node of nodes) {
    out.push({ node, managerName, depth });
    flatten(node.children, node.fullName, depth + 1, out);
  }
  return out;
}

interface TreeProps {
  collapsed: Set<string>;
  onToggle: (id: string) => void;
}

function OrgLi({ node, collapsed, onToggle }: { node: OrgChartNode } & TreeProps) {
  const { t } = useTranslation();
  const hasChildren = node.children.length > 0;
  const isCollapsed = collapsed.has(node.id);
  const status = STATUS_META[node.status] ?? { key: null, color: 'default' };
  return (
    <li>
      <div
        className={`org-card${hasChildren ? ' org-card--parent' : ''}${
          isCollapsed ? ' is-collapsed' : ''
        }`}
        onClick={hasChildren ? () => onToggle(node.id) : undefined}
        title={
          hasChildren
            ? isCollapsed
              ? t('orgchart.expandBranch')
              : t('orgchart.collapseBranch')
            : undefined
        }
      >
        <div className="org-card-head">
          <Avatar size={44} src={node.photoUrl ?? undefined} icon={<UserOutlined />} />
          <div className="org-card-id">
            <div className="org-card-name">{node.fullName}</div>
            <div className="org-card-code">{node.employeeCode}</div>
          </div>
          {hasChildren && (
            <span className="org-card-chevron">
              <DownOutlined />
            </span>
          )}
        </div>
        <Tag color={status.color} className="org-card-status">
          {status.key ? t(status.key) : node.status}
        </Tag>
        {node.position && <div className="org-card-pos">{node.position}</div>}
        <div className="org-card-dept">
          {deptIcon(node.position)}
          <span>{node.departmentName ?? '—'}</span>
        </div>
        <div className="org-card-foot">
          <span className="org-card-office">{node.office ?? ''}</span>
          {hasChildren && (
            <span className="org-card-count">
              <TeamOutlined />
              {node.children.length}
            </span>
          )}
        </div>
      </div>
      {hasChildren && !isCollapsed && (
        <ul>
          {node.children.map((child) => (
            <OrgLi key={child.id} node={child} collapsed={collapsed} onToggle={onToggle} />
          ))}
        </ul>
      )}
    </li>
  );
}

function StatCard({
  icon,
  label,
  value,
  hint,
  hintClass,
}: {
  icon: ReactNode;
  label: string;
  value: string | number;
  hint: ReactNode;
  hintClass?: string;
}) {
  return (
    <div className="org-stat">
      <div className="org-stat-icon">{icon}</div>
      <div className="org-stat-body">
        <div className="org-stat-label">{label}</div>
        <div className="org-stat-value">{value}</div>
        <div className={`org-stat-hint${hintClass ? ` ${hintClass}` : ''}`}>{hint}</div>
      </div>
    </div>
  );
}

/** Delta 30 ngày: dương "▲ +n", âm "▼ |n|", bằng 0 "– 0" (tránh render "▲ +-3"). */
const fmtDelta = (n: number) => (n > 0 ? `▲ +${n}` : n < 0 ? `▼ ${Math.abs(n)}` : '– 0');

export default function OrgChartPage() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const auth = useAuth();
  const navigate = useNavigate();

  const [roots, setRoots] = useState<OrgChartNode[]>([]);
  const [stats, setStats] = useState<OrgChartStats | null>(null);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [loading, setLoading] = useState(true);
  const [collapsed, setCollapsed] = useState<Set<string>>(() => new Set());
  const [keyword, setKeyword] = useState('');
  const [deptId, setDeptId] = useState<string | undefined>(undefined);
  const [office, setOffice] = useState<string | undefined>(undefined);
  const [mode, setMode] = useState<Mode>('chart');
  const [zoom, setZoom] = useState(100);
  const [exporting, setExporting] = useState(false);
  const exportRef = useRef<HTMLDivElement>(null);

  const canWrite = auth.hasRole('HR_ADMIN');

  useEffect(() => {
    // allSettled: stats fail chỉ ẩn stat cards, tree vẫn hiển thị (không fail-joined)
    Promise.allSettled([getOrgChart(), getOrgChartStats()])
      .then(([treeRes, statsRes]) => {
        if (treeRes.status === 'fulfilled') setRoots(treeRes.value);
        else message.error(errorMessage(treeRes.reason));
        if (statsRes.status === 'fulfilled') setStats(statsRes.value);
      })
      .finally(() => setLoading(false));
    listDepartments()
      .then((data) => setDepartments(data.filter((d) => d.status === 'ACTIVE')))
      .catch(() => setDepartments([]));
  }, [message]);

  const onToggle = (id: string) =>
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const deptName = departments.find((d) => d.id === deptId)?.name;
  const hasFilter = keyword.trim().length > 0 || Boolean(deptId) || Boolean(office);

  const matchNode = (node: OrgChartNode): boolean => {
    const k = keyword.trim().toLowerCase();
    if (
      k &&
      !node.fullName.toLowerCase().includes(k) &&
      !node.employeeCode.toLowerCase().includes(k) &&
      !(node.position ?? '').toLowerCase().includes(k)
    ) {
      return false;
    }
    if (deptName && node.departmentName !== deptName) return false;
    if (office && node.office !== office) return false;
    return true;
  };

  const viewRoots = hasFilter ? filterTree(roots, matchNode) : roots;
  // Đang lọc → tự mở toàn bộ để thấy kết quả
  const effectiveCollapsed = hasFilter ? new Set<string>() : collapsed;
  const officeOptions = [...collectOffices(roots, new Set<string>())]
    .sort()
    .map((value) => ({ value, label: value }));
  const flatRows = flatten(viewRoots, null, 0, []);

  const handleExport = async () => {
    const target = exportRef.current;
    if (!target) return;
    setExporting(true);
    try {
      const dataUrl = await toPng(target, {
        backgroundColor: '#ffffff',
        pixelRatio: 2,
        cacheBust: true,
      });
      const link = document.createElement('a');
      link.download = `so-do-to-chuc-${new Date().toISOString().slice(0, 10)}.png`;
      link.href = dataUrl;
      link.click();
      message.success(t('orgchart.exportDone'));
    } catch {
      message.error(t('orgchart.exportFailed'));
    } finally {
      setExporting(false);
    }
  };

  if (loading) return <Spin style={{ display: 'block', margin: '64px auto' }} />;

  const listColumns: TableProps<FlatRow>['columns'] = [
    {
      title: t('orgchart.columns.name'),
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
      render: (_, row) => (
        <div style={{ paddingLeft: row.depth * 20, display: 'flex', alignItems: 'center', gap: 8 }}>
          <Avatar size="small" src={row.node.photoUrl ?? undefined} icon={<UserOutlined />} />
          <span style={{ fontWeight: 600 }}>{row.node.fullName}</span>
        </div>
      ),
    },
    { title: t('orgchart.columns.code'), dataIndex: 'code', key: 'code', width: 110,
      render: (_, row) => row.node.employeeCode },
    { title: t('orgchart.columns.position'), dataIndex: 'position', key: 'position', width: 210,
      ellipsis: true, render: (_, row) => row.node.position ?? '-' },
    { title: t('orgchart.columns.department'), dataIndex: 'department', key: 'department',
      width: 190, ellipsis: true, render: (_, row) => row.node.departmentName ?? '-' },
    { title: t('orgchart.columns.office'), dataIndex: 'office', key: 'office', width: 150,
      ellipsis: true, render: (_, row) => row.node.office ?? '-' },
    {
      title: t('orgchart.columns.status'),
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (_, row) => {
        const meta = STATUS_META[row.node.status] ?? { key: null, color: 'default' };
        return <Tag color={meta.color}>{meta.key ? t(meta.key) : row.node.status}</Tag>;
      },
    },
    { title: t('orgchart.columns.manager'), dataIndex: 'manager', key: 'manager', width: 180,
      ellipsis: true, render: (_, row) => row.managerName ?? '-' },
    {
      title: t('orgchart.columns.reports'),
      dataIndex: 'reports',
      key: 'reports',
      width: 110,
      align: 'right',
      render: (_, row) => row.node.children.length,
    },
  ];

  return (
    <div className="org-page">
      <div className="org-header">
        <div>
          <Breadcrumb
            items={[
              { title: t('orgchart.breadcrumbStaff') },
              { title: t('orgchart.title') },
            ]}
          />
          <Typography.Title level={2} className="org-title">
            {t('orgchart.title')}
          </Typography.Title>
          <Typography.Text type="secondary">{t('orgchart.subtitle')}</Typography.Text>
        </div>
        <Space>
          <Button
            icon={<ExportOutlined />}
            loading={exporting}
            disabled={mode === 'list' || viewRoots.length === 0}
            onClick={handleExport}
          >
            {exporting ? t('orgchart.exporting') : t('orgchart.export')}
          </Button>
          {canWrite && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => navigate('/employees', { state: { create: true } })}
            >
              {t('orgchart.addEmployee')}
            </Button>
          )}
        </Space>
      </div>

      {stats && (
        <div className="org-stats">
          <StatCard
            icon={<TeamOutlined />}
            label={t('orgchart.stats.totalEmployees')}
            value={stats.totalEmployees}
            hint={
              <>
                <span className="org-stat-delta">{fmtDelta(stats.employeeDelta)}</span>{' '}
                {t('orgchart.stats.vsLastMonth')}
              </>
            }
          />
          <StatCard
            icon={<BankOutlined />}
            label={t('orgchart.stats.departments')}
            value={stats.totalDepartments}
            hint={
              <>
                <span className="org-stat-delta">{fmtDelta(stats.departmentDelta)}</span>{' '}
                {t('orgchart.stats.vsLastMonth')}
              </>
            }
          />
          <StatCard
            icon={<UserOutlined />}
            label={t('orgchart.stats.managers')}
            value={stats.totalManagers}
            hint={
              <>
                <span className="org-stat-delta">{fmtDelta(stats.managerDelta)}</span>{' '}
                {t('orgchart.stats.vsLastMonth')}
              </>
            }
          />
          <StatCard
            icon={<UserOutlined />}
            label={t('orgchart.stats.active')}
            value={stats.activeEmployees}
            hintClass="org-stat-hint--ok"
            hint={
              <>
                <span className="org-stat-delta">{stats.activePercent}%</span>{' '}
                {t('orgchart.stats.activeRatio')}
              </>
            }
          />
        </div>
      )}

      <div className="org-panel">
        <div className="org-toolbar">
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder={t('orgchart.searchPlaceholder')}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{ width: 260 }}
          />
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder={t('orgchart.allDepartments')}
            value={deptId}
            onChange={setDeptId}
            options={departments.map((d) => ({ value: d.id, label: d.name }))}
            style={{ width: 210 }}
          />
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder={t('orgchart.allOffices')}
            value={office}
            onChange={setOffice}
            options={officeOptions}
            style={{ width: 190 }}
          />
          <div className="org-toolbar-right">
            <Segmented
              value={mode}
              onChange={(value) => setMode(value as Mode)}
              options={[
                {
                  value: 'chart',
                  label: t('orgchart.viewChart'),
                  icon: <ApartmentOutlined />,
                },
                {
                  value: 'list',
                  label: t('orgchart.viewList'),
                  icon: <UnorderedListOutlined />,
                },
              ]}
            />
            <div className="org-zoom">
              <Button
                aria-label={t('orgchart.zoomOut')}
                icon={<MinusOutlined />}
                disabled={zoom <= ZOOM_MIN}
                onClick={() => setZoom((z) => Math.max(ZOOM_MIN, z - ZOOM_STEP))}
              />
              <span className="org-zoom-value">{zoom}%</span>
              <Button
                aria-label={t('orgchart.zoomIn')}
                icon={<PlusOutlined />}
                disabled={zoom >= ZOOM_MAX}
                onClick={() => setZoom((z) => Math.min(ZOOM_MAX, z + ZOOM_STEP))}
              />
            </div>
          </div>
        </div>

        <div className="org-chart-scroll" ref={exportRef}>
          {roots.length === 0 ? (
            <Empty description={t('orgchart.empty')} />
          ) : viewRoots.length === 0 ? (
            <Empty description={t('orgchart.emptyFiltered')} />
          ) : mode === 'chart' ? (
            <div className="org-tree-scale" style={{ zoom: zoom / 100 }}>
              <ul className="org-tree">
                {viewRoots.map((root) => (
                  <OrgLi
                    key={root.id}
                    node={root}
                    collapsed={effectiveCollapsed}
                    onToggle={onToggle}
                  />
                ))}
              </ul>
            </div>
          ) : (
            <Table<FlatRow>
              rowKey={(row) => row.node.id}
              columns={listColumns}
              dataSource={flatRows}
              pagination={false}
              size="middle"
              scroll={{ y: 520 }}
            />
          )}
        </div>
      </div>
    </div>
  );
}
