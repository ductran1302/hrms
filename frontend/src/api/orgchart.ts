import client from './client';
import type { OrgChartNode, OrgChartStats } from './types';

export function getOrgChart(): Promise<OrgChartNode[]> {
  return client.get<OrgChartNode[]>('/api/v1/org-chart').then((r) => r.data);
}

export function getOrgChartStats(): Promise<OrgChartStats> {
  return client.get<OrgChartStats>('/api/v1/org-chart/stats').then((r) => r.data);
}
