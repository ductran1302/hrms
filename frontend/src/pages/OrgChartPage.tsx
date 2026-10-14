import { UserOutlined } from '@ant-design/icons';
import { App, Avatar, Spin, Tag } from 'antd';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { errorMessage } from '../api/client';
import { getOrgChart } from '../api/orgchart';
import type { OrgChartNode } from '../api/types';

const STATUS_META: Record<string, { key: string; color: string }> = {
  ACTIVE: { key: 'status.ACTIVE', color: 'green' },
  PROBATION: { key: 'status.PROBATION', color: 'orange' },
  ON_LEAVE: { key: 'status.ON_LEAVE', color: 'blue' },
  TERMINATED: { key: 'status.TERMINATED', color: 'red' },
};

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
        className={`org-card${hasChildren ? ' org-card--parent' : ''}`}
        onClick={hasChildren ? () => onToggle(node.id) : undefined}
        title={
          hasChildren
            ? isCollapsed
              ? t('orgchart.expandBranch')
              : t('orgchart.collapseBranch')
            : undefined
        }
      >
        <div className="org-card-avatar">
          {node.photoUrl ? (
            <Avatar size={44} src={node.photoUrl} alt="" />
          ) : (
            <Avatar
              size={44}
              icon={<UserOutlined />}
              style={{ background: '#f0f0f0', color: '#bfbfbf' }}
            />
          )}
        </div>
        <div className="org-card-name">{node.fullName}</div>
        <div className="org-card-sub">
          {node.employeeCode}
          <Tag color={status.color} style={{ marginLeft: 6 }}>
            {status.key ? t(status.key) : node.status}
          </Tag>
        </div>
        {node.position && <div className="org-card-pos">{node.position}</div>}
        {node.departmentName && <div className="org-card-dept">{node.departmentName}</div>}
        {hasChildren && <span className="org-toggle">{isCollapsed ? '+' : '−'}</span>}
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

export default function OrgChartPage() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const [roots, setRoots] = useState<OrgChartNode[]>([]);
  const [loading, setLoading] = useState(true);
  const [collapsed, setCollapsed] = useState<Set<string>>(() => new Set());

  useEffect(() => {
    getOrgChart()
      .then(setRoots)
      .catch((error) => message.error(errorMessage(error)))
      .finally(() => setLoading(false));
  }, [message]);

  const onToggle = (id: string) =>
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  if (loading) return <Spin style={{ display: 'block', margin: '64px auto' }} />;

  if (roots.length === 0) {
    return <div>{t('orgchart.empty')}</div>;
  }

  return (
    <div className="org-chart-scroll">
      <ul className="org-tree">
        {roots.map((root) => (
          <OrgLi key={root.id} node={root} collapsed={collapsed} onToggle={onToggle} />
        ))}
      </ul>
    </div>
  );
}
