import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import {
  createAdaptation,
  fetchVacancyText,
  type AdaptationResponse,
} from '../api/adaptation';
import AdaptationDiff from '../components/AdaptationDiff';
import CandidateSkills from '../components/CandidateSkills';

function errorDetail(error: unknown): string {
  if (isAxiosError(error) && error.response?.data?.detail) {
    return error.response.data.detail as string;
  }
  return error instanceof Error ? error.message : 'Unknown error';
}

export default function Adapt() {
  const [url, setUrl] = useState('');
  const [vacancyText, setVacancyText] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [adaptation, setAdaptation] = useState<AdaptationResponse | null>(null);

  const fetchMutation = useMutation({
    mutationFn: () => fetchVacancyText(url),
    onSuccess: text => setVacancyText(text),
  });

  const adaptMutation = useMutation({
    mutationFn: () => createAdaptation(file!, vacancyText),
    onSuccess: setAdaptation,
  });

  const reset = () => {
    setAdaptation(null);
    adaptMutation.reset();
  };

  if (adaptation) {
    return <AdaptationDiff adaptation={adaptation} onReset={reset} />;
  }

  return (
    <div className="max-w-3xl space-y-6">
      <h1 className="text-xl font-semibold">Resume Adaptation</h1>

      <div className="space-y-2">
        <label className="text-sm text-gray-400">Vacancy URL</label>
        <div className="flex gap-2">
          <input
            type="url"
            value={url}
            onChange={e => setUrl(e.target.value)}
            placeholder="https://..."
            className="flex-1 bg-gray-900 border border-gray-800 rounded px-3 py-2 text-sm focus:outline-none focus:border-blue-600"
          />
          <button
            onClick={() => fetchMutation.mutate()}
            disabled={!url || fetchMutation.isPending}
            className="px-4 py-2 text-sm rounded bg-gray-800 hover:bg-gray-700 disabled:opacity-50 transition-colors"
          >
            {fetchMutation.isPending ? 'Fetching…' : 'Fetch'}
          </button>
        </div>
        {fetchMutation.isError && (
          <p className="text-sm text-yellow-400">
            {errorDetail(fetchMutation.error)}
          </p>
        )}
      </div>

      <div className="space-y-2">
        <label className="text-sm text-gray-400">Vacancy text</label>
        <textarea
          value={vacancyText}
          onChange={e => setVacancyText(e.target.value)}
          rows={10}
          placeholder="Paste the vacancy description or fetch it from the URL above"
          className="w-full bg-gray-900 border border-gray-800 rounded px-3 py-2 text-sm focus:outline-none focus:border-blue-600"
        />
      </div>

      <div className="space-y-2">
        <label className="text-sm text-gray-400">Resume (.docx)</label>
        <input
          type="file"
          accept=".docx"
          onChange={e => setFile(e.target.files?.[0] ?? null)}
          className="block text-sm text-gray-400 file:mr-3 file:px-4 file:py-1.5 file:rounded file:border-0 file:bg-gray-800 file:text-gray-200 file:text-sm hover:file:bg-gray-700"
        />
      </div>

      <div className="space-y-2">
        <button
          onClick={() => adaptMutation.mutate()}
          disabled={!file || !vacancyText.trim() || adaptMutation.isPending}
          className="px-6 py-2 rounded bg-blue-600 hover:bg-blue-500 disabled:bg-gray-700 disabled:text-gray-500 text-white font-medium transition-colors"
        >
          {adaptMutation.isPending ? 'Adapting… (~30 s)' : 'Adapt resume'}
        </button>
        {adaptMutation.isError && (
          <p className="text-sm text-red-400">{errorDetail(adaptMutation.error)}</p>
        )}
      </div>

      <div className="border-t border-gray-800 pt-6">
        <CandidateSkills />
      </div>
    </div>
  );
}
