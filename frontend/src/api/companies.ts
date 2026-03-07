import api from './client';
import type { Company } from '../types';

export type CompanyBody = Omit<Company, 'id' | 'lastCrawledAt'>;

export const fetchCompanies = async (): Promise<Company[]> => {
  const { data } = await api.get<Company[]>('/companies');
  return data;
};

export const createCompany = async (body: CompanyBody): Promise<Company> => {
  const { data } = await api.post<Company>('/companies', body);
  return data;
};

export const updateCompany = async (id: string, body: CompanyBody): Promise<Company> => {
  const { data } = await api.put<Company>(`/companies/${id}`, body);
  return data;
};

export const toggleCompany = async (id: string): Promise<Company> => {
  const { data } = await api.patch<Company>(`/companies/${id}/toggle`);
  return data;
};

export const deleteCompany = async (id: string): Promise<void> => {
  await api.delete(`/companies/${id}`);
};
