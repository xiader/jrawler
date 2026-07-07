import { useMemo, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import type { AdaptationResponse } from '../api/adaptation';
import { downloadAdapted } from '../api/adaptation';
import { diffWords, type DiffPart } from '../lib/wordDiff';

interface Props {
  adaptation: AdaptationResponse;
  onReset: () => void;
}

function DiffText({ parts, changedClass }: { parts: DiffPart[]; changedClass: string }) {
  return (
    <p className="text-sm leading-relaxed whitespace-pre-wrap">
      {parts.map((part, i) => (
        <span key={i} className={part.changed ? changedClass : undefined}>
          {i > 0 ? ' ' : ''}
          {part.text}
        </span>
      ))}
    </p>
  );
}

export default function AdaptationDiff({ adaptation, onReset }: Props) {
  const [accepted, setAccepted] = useState<Set<number>>(
    () => new Set(adaptation.edits.map(e => e.paragraphIndex)),
  );

  const diffs = useMemo(
    () => adaptation.edits.map(e => ({ edit: e, diff: diffWords(e.original, e.proposed) })),
    [adaptation.edits],
  );

  const downloadMutation = useMutation({
    mutationFn: () => downloadAdapted(adaptation.adaptationId, [...accepted]),
    onSuccess: blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'resume-adapted.docx';
      a.click();
      URL.revokeObjectURL(url);
    },
  });

  const toggle = (index: number) => {
    setAccepted(prev => {
      const next = new Set(prev);
      if (next.has(index)) {
        next.delete(index);
      } else {
        next.add(index);
      }
      return next;
    });
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold">
          Правки <span className="text-gray-400 text-sm">принято {accepted.size} из {adaptation.edits.length}</span>
        </h2>
        <div className="flex gap-2">
          <button
            onClick={onReset}
            className="px-4 py-1.5 text-sm rounded border border-gray-700 text-gray-300 hover:bg-gray-800 transition-colors"
          >
            Начать заново
          </button>
          <button
            onClick={() => downloadMutation.mutate()}
            disabled={downloadMutation.isPending}
            className="px-4 py-1.5 text-sm rounded bg-blue-600 hover:bg-blue-500 disabled:bg-gray-700 disabled:text-gray-500 text-white font-medium transition-colors"
          >
            {downloadMutation.isPending ? 'Готовим…' : 'Скачать docx'}
          </button>
        </div>
      </div>

      {downloadMutation.isError && (
        <p className="text-sm text-red-400">
          Не удалось скачать: {(downloadMutation.error as Error).message}. Возможно, сессия истекла — начни заново.
        </p>
      )}

      {adaptation.edits.length === 0 && (
        <p className="text-gray-400">LLM не предложил правок — резюме уже хорошо подходит под вакансию.</p>
      )}

      <div className="space-y-4">
        {diffs.map(({ edit, diff }) => (
          <label
            key={edit.paragraphIndex}
            className={`block rounded-lg border p-4 cursor-pointer transition-colors ${
              accepted.has(edit.paragraphIndex)
                ? 'border-blue-600 bg-gray-900'
                : 'border-gray-800 bg-gray-900/50 opacity-60'
            }`}
          >
            <div className="flex items-start gap-3">
              <input
                type="checkbox"
                checked={accepted.has(edit.paragraphIndex)}
                onChange={() => toggle(edit.paragraphIndex)}
                className="mt-1 accent-blue-600"
              />
              <div className="grid md:grid-cols-2 gap-4 flex-1">
                <div>
                  <div className="text-xs uppercase text-gray-500 mb-1">Было</div>
                  <DiffText parts={diff.originalParts} changedClass="bg-red-900/60 text-red-200 rounded px-0.5" />
                </div>
                <div>
                  <div className="text-xs uppercase text-gray-500 mb-1">Стало</div>
                  <DiffText parts={diff.proposedParts} changedClass="bg-green-900/60 text-green-200 rounded px-0.5" />
                </div>
              </div>
            </div>
          </label>
        ))}
      </div>

      {adaptation.suggestions.length > 0 && (
        <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
          <h3 className="text-sm font-semibold text-gray-300 mb-2">Подсказки — чего не хватает в резюме</h3>
          <ul className="list-disc list-inside space-y-1 text-sm text-gray-400">
            {adaptation.suggestions.map((s, i) => (
              <li key={i}>{s}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
