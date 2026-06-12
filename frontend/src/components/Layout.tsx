import { NavLink, Outlet } from 'react-router-dom';
import { useEffect, useRef } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchCrawlerStatus, runCrawler } from '../api/sources';

const nav = [
  { to: '/vacancies', label: 'Vacancies' },
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/companies', label: 'Companies' },
  { to: '/profiles', label: 'Profiles' },
  { to: '/settings', label: 'Settings' },
];

export default function Layout() {
  const queryClient = useQueryClient();

  const { data: status } = useQuery({
    queryKey: ['crawler-status'],
    queryFn: fetchCrawlerStatus,
    refetchInterval: query => (query.state.data?.running ? 2000 : 15000),
  });

  const runMutation = useMutation({
    mutationFn: runCrawler,
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['crawler-status'] }),
  });

  const running = runMutation.isPending || (status?.running ?? false);

  // Refresh data once a crawl finishes
  const wasRunning = useRef(false);
  useEffect(() => {
    if (wasRunning.current && !running) {
      queryClient.invalidateQueries();
    }
    wasRunning.current = running;
  }, [running, queryClient]);

  const handleRun = () => runMutation.mutate();

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100 flex flex-col">
      <header className="bg-gray-900 border-b border-gray-800 px-6 py-3 flex items-center gap-6">
        <span className="font-bold text-blue-400 text-lg tracking-tight">Job Crawler</span>
        <nav className="flex gap-1 flex-1">
          {nav.map(({ to, label }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `px-3 py-1.5 rounded text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-blue-600 text-white'
                    : 'text-gray-400 hover:text-gray-100 hover:bg-gray-800'
                }`
              }
            >
              {label}
            </NavLink>
          ))}
        </nav>
        <button
          onClick={handleRun}
          disabled={running}
          className="px-4 py-1.5 bg-blue-600 hover:bg-blue-500 disabled:bg-gray-700 disabled:text-gray-500 text-white text-sm font-medium rounded transition-colors"
        >
          {running ? 'Running...' : 'Run Crawler'}
        </button>
      </header>
      <main className="flex-1 p-6 max-w-screen-xl mx-auto w-full">
        <Outlet />
      </main>
    </div>
  );
}
