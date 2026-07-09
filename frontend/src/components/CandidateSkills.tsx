import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { addSkill, deleteSkill, listSkills, SKILLS_QUERY_KEY } from '../api/skills';

export default function CandidateSkills() {
  const [term, setTerm] = useState('');
  const [note, setNote] = useState('');
  const queryClient = useQueryClient();

  const { data: skills = [] } = useQuery({ queryKey: SKILLS_QUERY_KEY, queryFn: listSkills });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: SKILLS_QUERY_KEY });

  const addMutation = useMutation({
    mutationFn: () => addSkill(term.trim(), note.trim() || undefined),
    onSuccess: () => {
      setTerm('');
      setNote('');
      invalidate();
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteSkill(id),
    onSuccess: invalidate,
  });

  return (
    <div className="space-y-3">
      <div>
        <h2 className="text-sm font-semibold text-gray-300">My skills</h2>
        <p className="text-xs text-gray-500">
          Skills you actually have that your resume may not mention (side work, omitted tasks).
          They get woven into every adaptation where the vacancy asks for them.
        </p>
      </div>

      {skills.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {skills.map(skill => (
            <span
              key={skill.id}
              title={skill.note ?? undefined}
              className="inline-flex items-center gap-1.5 rounded-full bg-gray-800 border border-gray-700 px-3 py-1 text-sm text-gray-200"
            >
              {skill.term}
              {skill.note && <span className="text-gray-500" aria-hidden>·</span>}
              <button
                onClick={() => deleteMutation.mutate(skill.id)}
                className="text-gray-500 hover:text-red-400 transition-colors"
                aria-label={`Remove ${skill.term}`}
              >
                ×
              </button>
            </span>
          ))}
        </div>
      )}

      <div className="flex gap-2">
        <input
          value={term}
          onChange={e => setTerm(e.target.value)}
          onKeyDown={e => {
            if (e.key === 'Enter' && term.trim()) addMutation.mutate();
          }}
          placeholder="Skill, e.g. Quarkus"
          className="w-44 bg-gray-900 border border-gray-800 rounded px-3 py-1.5 text-sm focus:outline-none focus:border-blue-600"
        />
        <input
          value={note}
          onChange={e => setNote(e.target.value)}
          onKeyDown={e => {
            if (e.key === 'Enter' && term.trim()) addMutation.mutate();
          }}
          placeholder="Context (optional), e.g. container orchestration on AWS ECS in production"
          className="flex-1 bg-gray-900 border border-gray-800 rounded px-3 py-1.5 text-sm focus:outline-none focus:border-blue-600"
        />
        <button
          onClick={() => addMutation.mutate()}
          disabled={!term.trim() || addMutation.isPending}
          className="px-4 py-1.5 text-sm rounded bg-gray-800 hover:bg-gray-700 disabled:opacity-50 transition-colors"
        >
          Add
        </button>
      </div>
    </div>
  );
}
