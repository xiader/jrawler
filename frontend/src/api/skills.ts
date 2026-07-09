import api from './client';

export const SKILLS_QUERY_KEY = ['candidate-skills'];

export interface CandidateSkill {
  id: string;
  term: string;
  note: string | null;
  createdAt: string;
}

export const listSkills = async (): Promise<CandidateSkill[]> => {
  const { data } = await api.get<CandidateSkill[]>('/candidate-skills');
  return data;
};

export const addSkill = async (term: string, note?: string): Promise<CandidateSkill> => {
  const { data } = await api.post<CandidateSkill>('/candidate-skills', { term, note });
  return data;
};

export const deleteSkill = async (id: string): Promise<void> => {
  await api.delete(`/candidate-skills/${id}`);
};
