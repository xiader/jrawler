export type VacancyStatus = 'NEW' | 'INTERESTED' | 'APPLIED' | 'INTERVIEW' | 'OFFER' | 'REJECTED';
export type RemoteType = 'REMOTE' | 'HYBRID' | 'ON_SITE';

export interface Vacancy {
  id: string;
  externalId: string;
  sourceId: string;
  title: string;
  companyName: string;
  url: string;
  location: string;
  salaryRaw: string;
  remoteType: RemoteType;
  description: string;
  relevanceScore: number;
  matchedKeywords: string[];
  profileId: string;
  status: VacancyStatus;
  foundAt: string;
  createdAt: string;
}

export interface VacanciesPage {
  content: Vacancy[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface Source {
  id: string;
  name: string;
  priority: number;
  isEnabled: boolean;
  lastCrawledAt: string | null;
  lastEtag: string | null;
}

export interface Company {
  id: string;
  name: string;
  careerPageUrl: string;
  atsType: string;
  atsCompanyId: string;
  customSelectors: Record<string, string> | null;
  isActive: boolean;
  lastCrawledAt: string | null;
}

export interface SearchProfile {
  id: string;
  name: string;
  isActive: boolean;
  mustHaveKeywords: string[];
  niceToHaveKeywords: string[];
  excludeKeywords: string[];
  locations: string[];
  remoteTypes: RemoteType[];
  minRelevanceScore: number;
  createdAt: string;
}

export interface CrawlLog {
  id: string;
  sourceId: string;
  startedAt: string;
  finishedAt: string | null;
  vacanciesFound: number;
  vacanciesNew: number;
  error: string | null;
}
