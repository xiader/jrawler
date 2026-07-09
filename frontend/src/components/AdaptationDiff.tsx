import { useMemo, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { AdaptationResponse } from '../api/adaptation';
import { downloadAdapted } from '../api/adaptation';
import { addSkill, SKILLS_QUERY_KEY } from '../api/skills';
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
  const [savedKeywords, setSavedKeywords] = useState<Set<string>>(new Set());
  const queryClient = useQueryClient();

  const saveSkillMutation = useMutation({
    mutationFn: (keyword: string) => addSkill(keyword),
    onSuccess: (_skill, keyword) => {
      setSavedKeywords(prev => new Set(prev).add(keyword));
      queryClient.invalidateQueries({ queryKey: SKILLS_QUERY_KEY });
    },
  });

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
          Edits <span className="text-gray-400 text-sm">accepted {accepted.size} of {adaptation.edits.length}</span>
        </h2>
        <div className="flex gap-2">
          <button
            onClick={onReset}
            className="px-4 py-1.5 text-sm rounded border border-gray-700 text-gray-300 hover:bg-gray-800 transition-colors"
          >
            Start over
          </button>
          <button
            onClick={() => downloadMutation.mutate()}
            disabled={downloadMutation.isPending}
            className="px-4 py-1.5 text-sm rounded bg-blue-600 hover:bg-blue-500 disabled:bg-gray-700 disabled:text-gray-500 text-white font-medium transition-colors"
          >
            {downloadMutation.isPending ? 'Preparing…' : 'Download docx'}
          </button>
        </div>
      </div>

      {downloadMutation.isError && (
        <p className="text-sm text-red-400">
          Download failed: {(downloadMutation.error as Error).message}. The session may have expired — start over.
        </p>
      )}

      {adaptation.edits.length === 0 && (
        <p className="text-gray-400">The LLM proposed no edits — the resume already fits this vacancy well.</p>
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
                  <div className="text-xs uppercase text-gray-500 mb-1">Before</div>
                  <DiffText parts={diff.originalParts} changedClass="bg-red-900/60 text-red-200 rounded px-0.5" />
                </div>
                <div>
                  <div className="text-xs uppercase text-gray-500 mb-1">After</div>
                  <DiffText parts={diff.proposedParts} changedClass="bg-green-900/60 text-green-200 rounded px-0.5" />
                </div>
              </div>
            </div>
          </label>
        ))}
      </div>

      {adaptation.suggestions.length > 0 && (
        <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
          <h3 className="text-sm font-semibold text-gray-300 mb-1">Suggestions — what the resume is missing</h3>
          <p className="text-xs text-gray-500 mb-3">
            "I have this" saves the skill to your profile — future adaptations weave it in automatically.
            Only mark skills you can back up in an interview.
          </p>
          <ul className="space-y-2 text-sm text-gray-400">
            {adaptation.suggestions.map(s => (
              <li key={s.keyword} className="flex items-center justify-between gap-3">
                <span>
                  <span className="text-gray-200 font-medium">{s.keyword}</span>
                  {' — '}
                  {s.text}
                </span>
                {savedKeywords.has(s.keyword) ? (
                  <span className="shrink-0 text-xs text-green-400">Saved to skills</span>
                ) : (
                  <button
                    onClick={() => saveSkillMutation.mutate(s.keyword)}
                    disabled={saveSkillMutation.isPending}
                    className="shrink-0 px-3 py-1 text-xs rounded border border-gray-700 text-gray-300 hover:bg-gray-800 disabled:opacity-50 transition-colors"
                  >
                    I have this
                  </button>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
