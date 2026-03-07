import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchCompanies, createCompany, updateCompany, toggleCompany, deleteCompany, type CompanyBody } from '../api/companies';
import type { Company } from '../types';

const ATS_TYPES = ['greenhouse', 'lever', 'workday', 'smartrecruiters', 'bamboohr', 'custom'];

const empty: CompanyBody = {
  name: '',
  careerPageUrl: '',
  atsType: 'greenhouse',
  atsCompanyId: '',
  customSelectors: null,
  isActive: true,
};

export default function Companies() {
  const qc = useQueryClient();
  const [editing, setEditing] = useState<Company | null>(null);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState<CompanyBody>(empty);

  const { data: companies = [], isLoading } = useQuery({
    queryKey: ['companies'],
    queryFn: fetchCompanies,
  });

  const createMutation = useMutation({
    mutationFn: createCompany,
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['companies'] }); setCreating(false); setForm(empty); },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: CompanyBody }) => updateCompany(id, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['companies'] }); setEditing(null); },
  });

  const toggleMutation = useMutation({
    mutationFn: toggleCompany,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['companies'] }),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteCompany,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['companies'] }),
  });

  const openEdit = (c: Company) => {
    setEditing(c);
    setForm({ name: c.name, careerPageUrl: c.careerPageUrl, atsType: c.atsType, atsCompanyId: c.atsCompanyId, customSelectors: c.customSelectors, isActive: c.isActive });
  };

  const handleSubmit = () => {
    if (editing) {
      updateMutation.mutate({ id: editing.id, body: form });
    } else {
      createMutation.mutate(form);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Companies</h1>
        <button
          onClick={() => { setCreating(true); setEditing(null); setForm(empty); }}
          className="px-4 py-1.5 bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium rounded"
        >
          + Add Company
        </button>
      </div>

      <div className="bg-gray-900 rounded-lg border border-gray-800 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-800 text-gray-400 text-xs uppercase">
            <tr>
              <th className="px-4 py-2 text-left">Company</th>
              <th className="px-4 py-2 text-left">ATS</th>
              <th className="px-4 py-2 text-left">ATS ID</th>
              <th className="px-4 py-2 text-left">Last Crawled</th>
              <th className="px-4 py-2 text-left">Status</th>
              <th className="px-4 py-2 text-left">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-800">
            {isLoading && (
              <tr><td colSpan={6} className="px-4 py-8 text-center text-gray-500">Loading...</td></tr>
            )}
            {companies.map(c => (
              <tr key={c.id} className="hover:bg-gray-800/30">
                <td className="px-4 py-2.5">
                  <div className="font-medium text-gray-100">{c.name}</div>
                  <a href={c.careerPageUrl} target="_blank" rel="noopener noreferrer" className="text-xs text-blue-400 hover:underline truncate block max-w-xs">
                    {c.careerPageUrl}
                  </a>
                </td>
                <td className="px-4 py-2.5">
                  <span className="px-2 py-0.5 bg-gray-800 text-gray-300 text-xs rounded font-mono">{c.atsType}</span>
                </td>
                <td className="px-4 py-2.5 text-gray-400 text-xs font-mono">{c.atsCompanyId || '—'}</td>
                <td className="px-4 py-2.5 text-gray-500 text-xs">
                  {c.lastCrawledAt ? new Date(c.lastCrawledAt).toLocaleString() : 'Never'}
                </td>
                <td className="px-4 py-2.5">
                  <button
                    onClick={() => toggleMutation.mutate(c.id)}
                    className={`px-2 py-0.5 rounded text-xs font-medium ${c.isActive ? 'bg-green-900 text-green-300' : 'bg-gray-800 text-gray-500'}`}
                  >
                    {c.isActive ? 'Active' : 'Inactive'}
                  </button>
                </td>
                <td className="px-4 py-2.5 flex gap-2">
                  <button onClick={() => openEdit(c)} className="text-xs text-blue-400 hover:underline">Edit</button>
                  <button
                    onClick={() => { if (confirm(`Delete ${c.name}?`)) deleteMutation.mutate(c.id); }}
                    className="text-xs text-red-400 hover:underline"
                  >
                    Delete
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {(creating || editing) && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60">
          <div className="bg-gray-900 border border-gray-800 rounded-lg p-6 w-full max-w-md space-y-4">
            <h2 className="text-lg font-semibold">{editing ? 'Edit Company' : 'Add Company'}</h2>

            <Field label="Name">
              <input className={input} value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} />
            </Field>
            <Field label="Career Page URL">
              <input className={input} value={form.careerPageUrl} onChange={e => setForm(f => ({ ...f, careerPageUrl: e.target.value }))} />
            </Field>
            <Field label="ATS Type">
              <select className={input} value={form.atsType} onChange={e => setForm(f => ({ ...f, atsType: e.target.value }))}>
                {ATS_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </Field>
            <Field label="ATS Company ID">
              <input className={input} value={form.atsCompanyId} onChange={e => setForm(f => ({ ...f, atsCompanyId: e.target.value }))} />
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

const input = 'w-full bg-gray-800 border border-gray-700 text-sm text-gray-100 rounded px-3 py-1.5 outline-none focus:border-blue-500';

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <label className="text-xs text-gray-400">{label}</label>
      {children}
    </div>
  );
}
