export default function ScoreBadge({ score }: { score: number }) {
  const color =
    score >= 70 ? 'text-green-400' : score >= 40 ? 'text-yellow-400' : 'text-gray-500';
  return <span className={`font-mono font-bold text-sm ${color}`}>{score}</span>;
}
