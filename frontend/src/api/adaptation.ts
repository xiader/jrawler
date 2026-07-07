import { isAxiosError } from 'axios';
import api from './client';

export interface AdaptationEdit {
  paragraphIndex: number;
  original: string;
  proposed: string;
}

export interface AdaptationResponse {
  adaptationId: string;
  edits: AdaptationEdit[];
  suggestions: string[];
}

export const fetchVacancyText = async (url: string): Promise<string> => {
  const { data } = await api.post<{ text: string }>('/resume-adaptation/fetch-vacancy', { url });
  return data.text;
};

export const createAdaptation = async (
  resume: File,
  vacancyText: string,
): Promise<AdaptationResponse> => {
  const form = new FormData();
  form.append('resume', resume);
  form.append('vacancyText', vacancyText);
  const { data } = await api.post<AdaptationResponse>('/resume-adaptation', form);
  return data;
};

const parseProblemDetail = (text: string): string | undefined => {
  try {
    const problem = JSON.parse(text) as { detail?: string };
    return problem.detail;
  } catch {
    return undefined;
  }
};

export const downloadAdapted = async (id: string, acceptedIndexes: number[]): Promise<Blob> => {
  try {
    const { data } = await api.post<Blob>(
      `/resume-adaptation/${id}/download`,
      { acceptedIndexes },
      { responseType: 'blob' },
    );
    return data;
  } catch (error) {
    if (isAxiosError(error) && error.response?.data instanceof Blob) {
      const text = await error.response.data.text();
      const detail = parseProblemDetail(text);
      if (detail) throw new Error(detail);
    }
    throw error;
  }
};
