import client from './client';
import type { OrgChartNode } from './types';

export function getOrgChart(): Promise<OrgChartNode[]> {
  return client.get<OrgChartNode[]>('/api/v1/org-chart').then((r) => r.data);
}
