import api from './client';
import type { Source, CrawlLog } from '../types';

export const fetchSources = async (): Promise<Source[]> => {
  const { data } = await api.get<Source[]>('/sources');
  return data;
};

export const toggleSource = async (id: string): Promise<Source> => {
  const { data } = await api.patch<Source>(`/sources/${id}/toggle`);
  return data;
};

export const runCrawler = async (): Promise<void> => {
  await api.post('/crawler/run');
};

export const fetchCrawlerStatus = async (): Promise<{ running: boolean }> => {
  const { data } = await api.get<{ running: boolean }>('/crawler/status');
  return data;
};

export const fetchCrawlLogs = async (sourceId?: string, limit = 50): Promise<CrawlLog[]> => {
  const params: Record<string, string | number> = { limit };
  if (sourceId) params.sourceId = sourceId;
  const { data } = await api.get<CrawlLog[]>('/crawler/logs', { params });
  return data;
};
