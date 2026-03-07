import api from './client';
import type { Vacancy, VacanciesPage, VacancyStatus } from '../types';

export interface VacancyFilters {
  sourceId?: string;
  profileId?: string;
  status?: VacancyStatus;
  minScore?: number;
  page?: number;
  size?: number;
}

export const fetchVacancies = async (filters: VacancyFilters = {}): Promise<VacanciesPage> => {
  const params: Record<string, string | number> = {};
  if (filters.sourceId) params.sourceId = filters.sourceId;
  if (filters.profileId) params.profileId = filters.profileId;
  if (filters.status) params.status = filters.status;
  if (filters.minScore !== undefined) params.minScore = filters.minScore;
  params.page = filters.page ?? 0;
  params.size = filters.size ?? 20;
  const { data } = await api.get<VacanciesPage>('/vacancies', { params });
  return data;
};

export const fetchVacancy = async (id: string): Promise<Vacancy> => {
  const { data } = await api.get<Vacancy>(`/vacancies/${id}`);
  return data;
};

export const updateVacancyStatus = async (id: string, status: VacancyStatus): Promise<Vacancy> => {
  const { data } = await api.patch<Vacancy>(`/vacancies/${id}/status`, { status });
  return data;
};
