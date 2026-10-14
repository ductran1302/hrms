import client from './client';
import type { JobTitle } from './types';

export function listJobTitles(): Promise<JobTitle[]> {
  return client.get<JobTitle[]>('/api/v1/job-titles').then((r) => r.data);
}
