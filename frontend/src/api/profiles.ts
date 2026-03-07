import api from './client';
import type { SearchProfile } from '../types';

export const fetchProfiles = async (): Promise<SearchProfile[]> => {
  const { data } = await api.get<SearchProfile[]>('/profiles');
  return data;
};

export const createProfile = async (body: Omit<SearchProfile, 'id' | 'createdAt'>): Promise<SearchProfile> => {
  const { data } = await api.post<SearchProfile>('/profiles', body);
  return data;
};

export const updateProfile = async (id: string, body: Partial<SearchProfile>): Promise<SearchProfile> => {
  const { data } = await api.put<SearchProfile>(`/profiles/${id}`, body);
  return data;
};

export const deleteProfile = async (id: string): Promise<void> => {
  await api.delete(`/profiles/${id}`);
};

export const toggleProfile = async (id: string): Promise<SearchProfile> => {
  const { data } = await api.patch<SearchProfile>(`/profiles/${id}/toggle`);
  return data;
};
