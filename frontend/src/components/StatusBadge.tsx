import type { VacancyStatus } from '../types';

const colors: Record<VacancyStatus, string> = {
  NEW: 'bg-blue-900 text-blue-300',
  INTERESTED: 'bg-purple-900 text-purple-300',
  APPLIED: 'bg-yellow-900 text-yellow-300',
  INTERVIEW: 'bg-orange-900 text-orange-300',
  OFFER: 'bg-green-900 text-green-300',
  REJECTED: 'bg-gray-800 text-gray-500',
};

export default function StatusBadge({ status }: { status: VacancyStatus }) {
  return (
    <span className={`px-2 py-0.5 rounded text-xs font-medium ${colors[status]}`}>
      {status}
    </span>
  );
}
