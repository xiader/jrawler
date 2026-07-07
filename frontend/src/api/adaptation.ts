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
  const { data } = await api.post<AdaptationResponse>('/resume-adaptation', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data;
};

export const downloadAdapted = async (id: string, acceptedIndexes: number[]): Promise<Blob> => {
  const { data } = await api.post<Blob>(
    `/resume-adaptation/${id}/download`,
    { acceptedIndexes },
    { responseType: 'blob' },
  );
  return data;
};
