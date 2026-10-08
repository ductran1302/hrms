import { App, Tree } from 'antd';
import type { DataNode } from 'antd/es/tree';
import { useEffect, useState } from 'react';
import { errorMessage } from '../api/client';
import { getOrgChart } from '../api/orgchart';
import type { OrgChartNode } from '../api/types';

const STATUS_TEXT: Record<string, string> = {
  ACTIVE: 'Đang làm',
  PROBATION: 'Thử việc',
  TERMINATED: 'Đã nghỉ',
};

function toNode(node: OrgChartNode): DataNode {
  const meta = [node.position, node.departmentName, STATUS_TEXT[node.status] ?? node.status]
    .filter(Boolean)
    .join(' · ');
  return {
    key: node.id,
    title: (
      <span>
        <strong>{node.fullName}</strong>
        <span style={{ color: '#888', marginLeft: 8, fontSize: 12 }}>
          {node.employeeCode}
          {meta ? ` — ${meta}` : ''}
        </span>
      </span>
    ),
    children: node.children.map(toNode),
  };
}

export default function OrgChartPage() {
  const { message } = App.useApp();
  const [nodes, setNodes] = useState<DataNode[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getOrgChart()
      .then((tree) => setNodes(tree.map(toNode)))
      .catch((error) => message.error(errorMessage(error)))
      .finally(() => setLoading(false));
  }, [message]);

  if (loading) return null;

  if (nodes.length === 0) {
    return <div>Chưa có dữ liệu sơ đồ tổ chức — gán quản lý trực tiếp cho nhân viên.</div>;
  }

  return (
    <div style={{ background: '#fff', padding: 24, borderRadius: 8 }}>
      <Tree defaultExpandAll treeData={nodes} blockNode selectable={false} style={{ fontSize: 14 }} />
    </div>
  );
}
