import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchSources, toggleSource, runCrawler, fetchCrawlerStatus } from '../api/sources';
import { useState } from 'react';

export default function Settings() {
  const qc = useQueryClient();
  const [message, setMessage] = useState('');

  const { data: status } = useQuery({
    queryKey: ['crawler-status'],
    queryFn: fetchCrawlerStatus,
    refetchInterval: query => (query.state.data?.running ? 2000 : 15000),
  });

  const { data: sources = [], isLoading } = useQuery({
    queryKey: ['sources'],
    queryFn: fetchSources,
  });

  const toggleMutation = useMutation({
    mutationFn: toggleSource,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['sources'] }),
  });

  const runMutation = useMutation({
    mutationFn: runCrawler,
    onMutate: () => setMessage(''),
    onSuccess: () => setMessage('Crawler started.'),
    onError: () => setMessage('Failed to start crawler.'),
    onSettled: () => qc.invalidateQueries({ queryKey: ['crawler-status'] }),
  });

  const running = runMutation.isPending || (status?.running ?? false);
  const handleRun = () => runMutation.mutate();

  const byPriority = [0, 1, 2].map(p => ({
    label: `P${p}`,
    sources: sources.filter(s => s.priority === p),
  }));

  return (
    <div className="space-y-6 max-w-3xl">
      <h1 className="text-xl font-semibold">Settings</h1>

      <div className="bg-gray-900 border border-gray-800 rounded-lg p-4 space-y-3">
        <h2 className="text-sm font-semibold text-gray-300 uppercase tracking-wide">Crawler</h2>
        <p className="text-sm text-gray-400">Crawls run automatically every hour. You can trigger a manual run below.</p>
        <div className="flex items-center gap-3">
          <button
            onClick={handleRun}
            disabled={running}
            className="px-4 py-1.5 bg-blue-600 hover:bg-blue-500 disabled:bg-gray-700 disabled:text-gray-500 text-white text-sm font-medium rounded transition-colors"
          >
            {running ? 'Running...' : 'Run Now'}
          </button>
          {message && <span className="text-sm text-gray-400">{message}</span>}
        </div>
      </div>

      <div className="bg-gray-900 border border-gray-800 rounded-lg p-4 space-y-4">
        <h2 className="text-sm font-semibold text-gray-300 uppercase tracking-wide">Sources</h2>
        {isLoading && <p className="text-gray-500 text-sm">Loading...</p>}
        {byPriority.map(({ label, sources: srcs }) => srcs.length > 0 && (
          <div key={label} className="space-y-2">
            <div className="text-xs text-gray-500 font-medium">{label}</div>
            <div className="grid gap-2 sm:grid-cols-2">
              {srcs.map(s => (
                <div key={s.id} className="flex items-center justify-between bg-gray-800 rounded px-3 py-2">
                  <div>
                    <div className="text-sm text-gray-200">{s.name}</div>
                    {s.lastCrawledAt && (
                      <div className="text-xs text-gray-500">
                        Last: {new Date(s.lastCrawledAt).toLocaleString()}
                      </div>
                    )}
                  </div>
                  <button
                    onClick={() => toggleMutation.mutate(s.id)}
                    className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors ${
                      s.isEnabled ? 'bg-blue-600' : 'bg-gray-600'
                    }`}
                  >
                    <span
                      className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform ${
                        s.isEnabled ? 'translate-x-4' : 'translate-x-0'
                      }`}
                    />
                  </button>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
