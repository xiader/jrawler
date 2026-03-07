import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchProfiles, createProfile, updateProfile, deleteProfile, toggleProfile } from '../api/profiles';
import type { SearchProfile, RemoteType } from '../types';
import TagInput from '../components/TagInput';

const REMOTE_TYPES: RemoteType[] = ['REMOTE', 'HYBRID', 'ON_SITE'];

const emptyForm = (): Omit<SearchProfile, 'id' | 'createdAt'> => ({
  name: '',
  isActive: true,
  mustHaveKeywords: [],
  niceToHaveKeywords: [],
  excludeKeywords: [],
  locations: [],
  remoteTypes: ['REMOTE'],
  minRelevanceScore: 30,
});

export default function Profiles() {
  const qc = useQueryClient();
  const [editing, setEditing] = useState<SearchProfile | null>(null);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState(emptyForm());

  const { data: profiles = [], isLoading } = useQuery({
    queryKey: ['profiles'],
    queryFn: fetchProfiles,
  });

  const createMutation = useMutation({
    mutationFn: createProfile,
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['profiles'] }); setCreating(false); setForm(emptyForm()); },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: Partial<SearchProfile> }) => updateProfile(id, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['profiles'] }); setEditing(null); },
  });

  const deleteMutation = useMutation({
    mutationFn: deleteProfile,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['profiles'] }),
  });

  const toggleMutation = useMutation({
    mutationFn: toggleProfile,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['profiles'] }),
  });

  const openEdit = (p: SearchProfile) => {
    setEditing(p);
    setForm({ name: p.name, isActive: p.isActive, mustHaveKeywords: p.mustHaveKeywords, niceToHaveKeywords: p.niceToHaveKeywords, excludeKeywords: p.excludeKeywords, locations: p.locations, remoteTypes: p.remoteTypes, minRelevanceScore: p.minRelevanceScore });
    setCreating(false);
  };

  const handleSubmit = () => {
    if (editing) updateMutation.mutate({ id: editing.id, body: form });
    else createMutation.mutate(form);
  };

  const toggleRemote = (rt: RemoteType) => {
    setForm(f => ({
      ...f,
      remoteTypes: f.remoteTypes.includes(rt) ? f.remoteTypes.filter(r => r !== rt) : [...f.remoteTypes, rt],
    }));
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Search Profiles</h1>
        <button
          onClick={() => { setCreating(true); setEditing(null); setForm(emptyForm()); }}
          className="px-4 py-1.5 bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium rounded"
        >
          + New Profile
        </button>
      </div>

      {isLoading && <p className="text-gray-500">Loading...</p>}

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {profiles.map(p => (
          <div key={p.id} className={`bg-gray-900 border rounded-lg p-4 space-y-3 ${p.isActive ? 'border-gray-700' : 'border-gray-800 opacity-60'}`}>
            <div className="flex items-center justify-between">
              <span className="font-semibold text-gray-100">{p.name}</span>
              <button
                onClick={() => toggleMutation.mutate(p.id)}
                className={`px-2 py-0.5 rounded text-xs font-medium ${p.isActive ? 'bg-green-900 text-green-300' : 'bg-gray-800 text-gray-500'}`}
              >
                {p.isActive ? 'Active' : 'Inactive'}
              </button>
            </div>

            <div className="space-y-1 text-xs">
              <KeywordRow label="Must have" keywords={p.mustHaveKeywords} color="blue" />
              <KeywordRow label="Nice to have" keywords={p.niceToHaveKeywords} color="green" />
              <KeywordRow label="Exclude" keywords={p.excludeKeywords} color="red" />
            </div>

            <div className="flex flex-wrap gap-1">
              {p.remoteTypes.map(rt => (
                <span key={rt} className="px-2 py-0.5 bg-blue-900/50 text-blue-300 text-xs rounded">{rt}</span>
              ))}
              <span className="px-2 py-0.5 bg-gray-800 text-gray-400 text-xs rounded">min score: {p.minRelevanceScore}</span>
            </div>

            <div className="flex gap-2 pt-1">
              <button onClick={() => openEdit(p)} className="text-xs text-blue-400 hover:underline">Edit</button>
              <button
                onClick={() => { if (confirm(`Delete "${p.name}"?`)) deleteMutation.mutate(p.id); }}
                className="text-xs text-red-400 hover:underline"
              >
                Delete
              </button>
            </div>
          </div>
        ))}
      </div>

      {(creating || editing) && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60">
          <div className="bg-gray-900 border border-gray-800 rounded-lg p-6 w-full max-w-lg space-y-4 max-h-screen overflow-y-auto">
            <h2 className="text-lg font-semibold">{editing ? 'Edit Profile' : 'New Profile'}</h2>

            <Field label="Name">
              <input className={inp} value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} />
            </Field>

            <Field label="Must Have Keywords">
              <TagInput value={form.mustHaveKeywords} onChange={v => setForm(f => ({ ...f, mustHaveKeywords: v }))} color="blue" placeholder="java, spring..." />
            </Field>
            <Field label="Nice To Have Keywords">
              <TagInput value={form.niceToHaveKeywords} onChange={v => setForm(f => ({ ...f, niceToHaveKeywords: v }))} color="green" placeholder="kafka, docker..." />
            </Field>
            <Field label="Exclude Keywords">
              <TagInput value={form.excludeKeywords} onChange={v => setForm(f => ({ ...f, excludeKeywords: v }))} color="red" placeholder="php, clearance..." />
            </Field>
            <Field label="Locations">
              <TagInput value={form.locations} onChange={v => setForm(f => ({ ...f, locations: v }))} placeholder="Poland, Europe, Remote..." />
            </Field>

            <Field label="Remote Types">
              <div className="flex gap-2">
                {REMOTE_TYPES.map(rt => (
                  <label key={rt} className="flex items-center gap-1 text-sm text-gray-300 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={form.remoteTypes.includes(rt)}
                      onChange={() => toggleRemote(rt)}
                    />
                    {rt}
                  </label>
                ))}
              </div>
            </Field>

            <Field label={`Min Relevance Score: ${form.minRelevanceScore}`}>
              <input
                type="range"
                min={0}
                max={100}
                value={form.minRelevanceScore}
                onChange={e => setForm(f => ({ ...f, minRelevanceScore: Number(e.target.value) }))}
                className="w-full"
              />
            </Field>

            <label className="flex items-center gap-2 text-sm text-gray-300">
              <input type="checkbox" checked={form.isActive} onChange={e => setForm(f => ({ ...f, isActive: e.target.checked }))} />
              Active
            </label>

            <div className="flex gap-2 justify-end pt-2">
              <button onClick={() => { setCreating(false); setEditing(null); }} className="px-4 py-1.5 text-sm text-gray-400 hover:text-gray-100">
                Cancel
              </button>
              <button onClick={handleSubmit} className="px-4 py-1.5 bg-blue-600 hover:bg-blue-500 text-white text-sm rounded">
                {editing ? 'Save' : 'Create'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

const inp = 'w-full bg-gray-800 border border-gray-700 text-sm text-gray-100 rounded px-3 py-1.5 outline-none focus:border-blue-500';

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <label className="text-xs text-gray-400">{label}</label>
      {children}
    </div>
  );
}

function KeywordRow({ label, keywords, color }: { label: string; keywords: string[]; color: 'blue' | 'green' | 'red' }) {
  if (!keywords.length) return null;
  const cls = color === 'blue' ? 'bg-blue-900/40 text-blue-300' : color === 'green' ? 'bg-green-900/40 text-green-300' : 'bg-red-900/40 text-red-300';
  return (
    <div className="flex gap-1 flex-wrap items-center">
      <span className="text-gray-500 w-20 shrink-0">{label}:</span>
      {keywords.map(k => <span key={k} className={`px-1.5 py-0.5 rounded ${cls}`}>{k}</span>)}
    </div>
  );
}
