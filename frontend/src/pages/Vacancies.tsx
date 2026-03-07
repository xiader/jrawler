import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchVacancies, updateVacancyStatus, type VacancyFilters } from '../api/vacancies';
import { fetchSources } from '../api/sources';
import { fetchProfiles } from '../api/profiles';
import type { Vacancy, VacancyStatus } from '../types';
import StatusBadge from '../components/StatusBadge';
import ScoreBadge from '../components/ScoreBadge';
import KeywordHighlight from '../components/KeywordHighlight';

const STATUSES: VacancyStatus[] = ['NEW', 'INTERESTED', 'APPLIED', 'INTERVIEW', 'OFFER', 'REJECTED'];

export default function Vacancies() {
  const qc = useQueryClient();
  const [filters, setFilters] = useState<VacancyFilters>({ page: 0, size: 20 });
  const [selected, setSelected] = useState<Vacancy | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['vacancies', filters],
    queryFn: () => fetchVacancies(filters),
  });

  const { data: sources = [] } = useQuery({ queryKey: ['sources'], queryFn: fetchSources });
  const { data: profiles = [] } = useQuery({ queryKey: ['profiles'], queryFn: fetchProfiles });

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: VacancyStatus }) =>
      updateVacancyStatus(id, status),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['vacancies'] }),
  });

  const setFilter = (key: keyof VacancyFilters, value: string | number | undefined) =>
    setFilters(f => ({ ...f, [key]: value, page: 0 }));

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-3 items-end">
        <div className="flex flex-col gap-1">
          <label className="text-xs text-gray-400">Source</label>
          <select
            className="bg-gray-800 border border-gray-700 text-sm text-gray-100 rounded px-2 py-1.5"
            value={filters.sourceId ?? ''}
            onChange={e => setFilter('sourceId', e.target.value || undefined)}
          >
            <option value="">All sources</option>
            {sources.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-xs text-gray-400">Profile</label>
          <select
            className="bg-gray-800 border border-gray-700 text-sm text-gray-100 rounded px-2 py-1.5"
            value={filters.profileId ?? ''}
            onChange={e => setFilter('profileId', e.target.value || undefined)}
          >
            <option value="">All profiles</option>
            {profiles.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-xs text-gray-400">Status</label>
          <select
            className="bg-gray-800 border border-gray-700 text-sm text-gray-100 rounded px-2 py-1.5"
            value={filters.status ?? ''}
            onChange={e => setFilter('status', (e.target.value as VacancyStatus) || undefined)}
          >
            <option value="">All statuses</option>
            {STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-xs text-gray-400">Min Score</label>
          <input
            type="number"
            min={0}
            max={100}
            placeholder="0"
            className="bg-gray-800 border border-gray-700 text-sm text-gray-100 rounded px-2 py-1.5 w-20"
            value={filters.minScore ?? ''}
            onChange={e => setFilter('minScore', e.target.value ? Number(e.target.value) : undefined)}
          />
        </div>
        <div className="ml-auto text-sm text-gray-500">
          {data?.totalElements ?? 0} vacancies
        </div>
      </div>

      <div className="bg-gray-900 rounded-lg border border-gray-800 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-800 text-gray-400 text-xs uppercase">
            <tr>
              <th className="px-4 py-2 text-left">Score</th>
              <th className="px-4 py-2 text-left">Title</th>
              <th className="px-4 py-2 text-left">Company</th>
              <th className="px-4 py-2 text-left">Location</th>
              <th className="px-4 py-2 text-left">Source</th>
              <th className="px-4 py-2 text-left">Status</th>
              <th className="px-4 py-2 text-left">Found</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-800">
            {isLoading && (
              <tr><td colSpan={7} className="px-4 py-8 text-center text-gray-500">Loading...</td></tr>
            )}
            {data?.content.map(v => (
              <tr
                key={v.id}
                className="hover:bg-gray-800/50 cursor-pointer"
                onClick={() => setSelected(v)}
              >
                <td className="px-4 py-2.5"><ScoreBadge score={v.relevanceScore} /></td>
                <td className="px-4 py-2.5 max-w-xs">
                  <div className="font-medium text-gray-100 truncate">
                    <KeywordHighlight text={v.title} keywords={v.matchedKeywords ?? []} />
                  </div>
                  {v.matchedKeywords?.length > 0 && (
                    <div className="flex gap-1 mt-0.5 flex-wrap">
                      {v.matchedKeywords.map(k => (
                        <span key={k} className="text-xs text-yellow-500/80">{k}</span>
                      ))}
                    </div>
                  )}
                </td>
                <td className="px-4 py-2.5 text-gray-300">{v.companyName}</td>
                <td className="px-4 py-2.5 text-gray-400 text-xs">{v.location}</td>
                <td className="px-4 py-2.5 text-gray-400 text-xs">{v.sourceId}</td>
                <td className="px-4 py-2.5">
                  <select
                    className="bg-transparent text-xs border-0 outline-none cursor-pointer"
                    value={v.status}
                    onClick={e => e.stopPropagation()}
                    onChange={e => statusMutation.mutate({ id: v.id, status: e.target.value as VacancyStatus })}
                  >
                    {STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
                  </select>
                </td>
                <td className="px-4 py-2.5 text-gray-500 text-xs whitespace-nowrap">
                  {new Date(v.foundAt).toLocaleDateString()}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {data && data.totalPages > 1 && (
        <div className="flex gap-2 justify-center">
          <button
            disabled={(filters.page ?? 0) === 0}
            onClick={() => setFilters(f => ({ ...f, page: (f.page ?? 1) - 1 }))}
            className="px-3 py-1 rounded bg-gray-800 text-sm disabled:opacity-40 hover:bg-gray-700"
          >
            Prev
          </button>
          <span className="px-3 py-1 text-sm text-gray-400">
            {(filters.page ?? 0) + 1} / {data.totalPages}
          </span>
          <button
            disabled={(filters.page ?? 0) >= data.totalPages - 1}
            onClick={() => setFilters(f => ({ ...f, page: (f.page ?? 0) + 1 }))}
            className="px-3 py-1 rounded bg-gray-800 text-sm disabled:opacity-40 hover:bg-gray-700"
          >
            Next
          </button>
        </div>
      )}

      {selected && (
        <VacancyDrawer
          vacancy={selected}
          onClose={() => setSelected(null)}
          onStatusChange={status => statusMutation.mutate({ id: selected.id, status })}
        />
      )}
    </div>
  );
}

function VacancyDrawer({
  vacancy,
  onClose,
  onStatusChange,
}: {
  vacancy: Vacancy;
  onClose: () => void;
  onStatusChange: (s: VacancyStatus) => void;
}) {
  const STATUSES: VacancyStatus[] = ['NEW', 'INTERESTED', 'APPLIED', 'INTERVIEW', 'OFFER', 'REJECTED'];

  return (
    <div className="fixed inset-0 z-50 flex">
      <div className="flex-1 bg-black/60" onClick={onClose} />
      <div className="w-full max-w-2xl bg-gray-900 border-l border-gray-800 overflow-y-auto p-6 space-y-4">
        <div className="flex items-start justify-between gap-4">
          <h2 className="text-lg font-semibold text-gray-100 leading-tight">
            <KeywordHighlight text={vacancy.title} keywords={vacancy.matchedKeywords ?? []} />
          </h2>
          <button onClick={onClose} className="text-gray-500 hover:text-gray-300 text-xl leading-none">×</button>
        </div>

        <div className="flex flex-wrap gap-3 text-sm text-gray-400">
          <span>{vacancy.companyName}</span>
          <span>·</span>
          <span>{vacancy.location}</span>
          {vacancy.salaryRaw && <><span>·</span><span className="text-green-400">{vacancy.salaryRaw}</span></>}
          {vacancy.remoteType && <><span>·</span><span className="text-blue-400">{vacancy.remoteType}</span></>}
        </div>

        <div className="flex items-center gap-3">
          <StatusBadge status={vacancy.status} />
          <select
            className="bg-gray-800 border border-gray-700 text-sm text-gray-100 rounded px-2 py-1"
            value={vacancy.status}
            onChange={e => onStatusChange(e.target.value as VacancyStatus)}
          >
            {STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
          </select>
          <a
            href={vacancy.url}
            target="_blank"
            rel="noopener noreferrer"
            className="ml-auto px-3 py-1 bg-blue-600 hover:bg-blue-500 text-white text-sm rounded"
          >
            Open Job
          </a>
        </div>

        {vacancy.matchedKeywords?.length > 0 && (
          <div>
            <div className="text-xs text-gray-500 mb-1">Matched keywords</div>
            <div className="flex flex-wrap gap-1">
              {vacancy.matchedKeywords.map(k => (
                <span key={k} className="px-2 py-0.5 bg-yellow-900/50 text-yellow-300 text-xs rounded">{k}</span>
              ))}
            </div>
          </div>
        )}

        {vacancy.description && (
          <div>
            <div className="text-xs text-gray-500 mb-1">Description</div>
            <div className="text-sm text-gray-300 whitespace-pre-wrap leading-relaxed max-h-96 overflow-y-auto">
              <KeywordHighlight text={vacancy.description} keywords={vacancy.matchedKeywords ?? []} />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
