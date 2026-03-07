import { useState, type KeyboardEvent } from 'react';

interface Props {
  value: string[];
  onChange: (tags: string[]) => void;
  placeholder?: string;
  color?: string;
}

export default function TagInput({ value, onChange, placeholder = 'Add tag...', color = 'blue' }: Props) {
  const [input, setInput] = useState('');

  const add = () => {
    const tag = input.trim().toLowerCase();
    if (tag && !value.includes(tag)) {
      onChange([...value, tag]);
    }
    setInput('');
  };

  const remove = (tag: string) => onChange(value.filter(t => t !== tag));

  const onKey = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault();
      add();
    } else if (e.key === 'Backspace' && !input && value.length) {
      remove(value[value.length - 1]);
    }
  };

  const tagClass =
    color === 'green'
      ? 'bg-green-900 text-green-300'
      : color === 'red'
      ? 'bg-red-900 text-red-300'
      : 'bg-blue-900 text-blue-300';

  return (
    <div className="flex flex-wrap gap-1 p-2 bg-gray-800 border border-gray-700 rounded min-h-9">
      {value.map(tag => (
        <span key={tag} className={`flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium ${tagClass}`}>
          {tag}
          <button type="button" onClick={() => remove(tag)} className="hover:opacity-70 leading-none">×</button>
        </span>
      ))}
      <input
        value={input}
        onChange={e => setInput(e.target.value)}
        onKeyDown={onKey}
        onBlur={add}
        placeholder={value.length === 0 ? placeholder : ''}
        className="flex-1 min-w-24 bg-transparent text-sm text-gray-100 placeholder-gray-500 outline-none"
      />
    </div>
  );
}
