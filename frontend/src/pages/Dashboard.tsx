import { useQuery } from '@tanstack/react-query';
import { fetchVacancies } from '../api/vacancies';
import { fetchSources, fetchCrawlLogs } from '../api/sources';
import { fetchProfiles } from '../api/profiles';
import type { VacancyStatus } from '../types';

const STATUSES: VacancyStatus[] = ['NEW', 'INTERESTED', 'APPLIED', 'INTERVIEW', 'OFFER', 'REJECTED'];

const statusColors: Record<VacancyStatus, string> = {
  NEW: 'bg-blue-500',
  INTERESTED: 'bg-purple-500',
  APPLIED: 'bg-yellow-500',
  INTERVIEW: 'bg-orange-500',
  OFFER: 'bg-green-500',
  REJECTED: 'bg-gray-600',
};

export default function Dashboard() {
  const { data: allVacancies } = useQuery({
    queryKey: ['vacancies', { size: 1000 }],
    queryFn: () => fetchVacancies({ size: 1000 }),
  });

  const { data: sources = [] } = useQuery({ queryKey: ['sources'], queryFn: fetchSources });
  const { data: profiles = [] } = useQuery({ queryKey: ['profiles'], queryFn: fetchProfiles });
  const { data: logs = [] } = useQuery({ queryKey: ['crawl-logs'], queryFn: () => fetchCrawlLogs(undefined, 20) });

  const vacancies = allVacancies?.content ?? [];
  const total = vacancies.length;

  const byStatus = STATUSES.map(s => ({
    status: s,
    count: vacancies.filter(v => v.status === s).length,
  }));

  const bySource = sources
    .map(s => ({ name: s.name, id: s.id, count: vacancies.filter(v => v.sourceId === s.id).length }))
    .filter(s => s.count > 0)
    .sort((a, b) => b.count - a.count)
    .slice(0, 10);

  const byProfile = profiles
    .map(p => ({ name: p.name, id: p.id, count: vacancies.filter(v => v.profileId === p.id).length }))
    .filter(p => p.count > 0);

  const avgScore = total > 0 ? Math.round(vacancies.reduce((sum, v) => sum + v.relevanceScore, 0) / total) : 0;

  const activeSources = sources.filter(s => s.isEnabled).length;
  const activeProfiles = profiles.filter(p => p.isActive).length;

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-semibold">Dashboard</h1>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard label="Total Vacancies" value={total} />
        <StatCard label="Avg Score" value={avgScore} />
        <StatCard label="Active Sources" value={`${activeSources} / ${sources.length}`} />
        <StatCard label="Active Profiles" value={`${activeProfiles} / ${profiles.length}`} />
      </div>

      <div className="grid md:grid-cols-2 gap-6">
        <div className="bg-gray-900 border border-gray-800 rounded-lg p-4 space-y-3">
          <h2 className="text-sm font-semibold text-gray-300 uppercase tracking-wide">Status Funnel</h2>
          {byStatus.map(({ status, count }) => (
            <div key={status} className="flex items-center gap-3">
              <div className="w-28 text-xs text-gray-400 text-right">{status}</div>
              <div className="flex-1 bg-gray-800 rounded-full h-4 overflow-hidden">
                <div
                  className={`h-full rounded-full transition-all ${statusColors[status]}`}
                  style={{ width: total > 0 ? `${(count / total) * 100}%` : '0%' }}
                />
              </div>
              <div className="w-8 text-right text-xs text-gray-300 font-mono">{count}</div>
            </div>
          ))}
        </div>

        <div className="bg-gray-900 border border-gray-800 rounded-lg p-4 space-y-3">
          <h2 className="text-sm font-semibold text-gray-300 uppercase tracking-wide">Top Sources</h2>
          {bySource.length === 0 && <p className="text-gray-500 text-sm">No data yet</p>}
          {bySource.map(({ name, count }) => {
            const max = bySource[0]?.count || 1;
            return (
              <div key={name} className="flex items-center gap-3">
                <div className="w-32 text-xs text-gray-400 truncate text-right">{name}</div>
                <div className="flex-1 bg-gray-800 rounded-full h-3 overflow-hidden">
                  <div
                    className="h-full bg-blue-600 rounded-full transition-all"
                    style={{ width: `${(count / max) * 100}%` }}
                  />
                </div>
                <div className="w-8 text-right text-xs text-gray-300 font-mono">{count}</div>
              </div>
            );
          })}
        </div>
      </div>

      {byProfile.length > 0 && (
        <div className="bg-gray-900 border border-gray-800 rounded-lg p-4 space-y-3">
          <h2 className="text-sm font-semibold text-gray-300 uppercase tracking-wide">By Profile</h2>
          <div className="flex gap-4 flex-wrap">
            {byProfile.map(({ name, count }) => (
              <div key={name} className="bg-gray-800 rounded-lg px-4 py-3 text-center">
                <div className="text-2xl font-bold text-blue-400">{count}</div>
                <div className="text-xs text-gray-400 mt-1">{name}</div>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="bg-gray-900 border border-gray-800 rounded-lg p-4 space-y-2">
        <h2 className="text-sm font-semibold text-gray-300 uppercase tracking-wide">Recent Crawl Logs</h2>
        <div className="overflow-x-auto">
          <table className="w-full text-xs">
            <thead className="text-gray-500">
              <tr>
                <th className="text-left py-1 pr-4">Source</th>
                <th className="text-left py-1 pr-4">Started</th>
                <th className="text-left py-1 pr-4">Found</th>
                <th className="text-left py-1 pr-4">New</th>
                <th className="text-left py-1">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-800">
              {logs.map(log => (
                <tr key={log.id}>
                  <td className="py-1.5 pr-4 text-gray-300">{log.sourceId}</td>
                  <td className="py-1.5 pr-4 text-gray-400">{new Date(log.startedAt).toLocaleString()}</td>
                  <td className="py-1.5 pr-4 text-gray-300">{log.vacanciesFound}</td>
                  <td className="py-1.5 pr-4 text-green-400">{log.vacanciesNew}</td>
                  <td className="py-1.5">
                    {log.error
                      ? <span className="text-red-400">{log.error.slice(0, 60)}</span>
                      : <span className="text-green-400">OK</span>
                    }
                  </td>
                </tr>
              ))}
              {logs.length === 0 && (
                <tr><td colSpan={5} className="py-4 text-center text-gray-500">No logs yet</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function StatCard({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="bg-gray-900 border border-gray-800 rounded-lg p-4">
      <div className="text-2xl font-bold text-gray-100">{value}</div>
      <div className="text-xs text-gray-500 mt-1">{label}</div>
    </div>
  );
}
