import api from './client';
import type { Company } from '../types';

export const fetchCompanies = async (): Promise<Company[]> => {
  const { data } = await api.get<Company[]>('/companies');
  return data;
};

export const createCompany = async (body: Omit<Company, 'id' | 'lastCrawledAt'>): Promise<Company> => {
  const { data } = await api.post<Company>('/companies', body);
  return data;
};

export const updateCompany = async (id: string, body: Partial<Company>): Promise<Company> => {
  const { data } = await api.put<Company>(`/companies/${id}`, body);
  return data;
};

export const deleteCompany = async (id: string): Promise<void> => {
  await api.delete(`/companies/${id}`);
};
