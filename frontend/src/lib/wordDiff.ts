export interface DiffPart {
  text: string;
  changed: boolean;
}

interface SplitDiff {
  originalParts: DiffPart[];
  proposedParts: DiffPart[];
}

/** Word-level diff via longest common subsequence. */
export function diffWords(original: string, proposed: string): SplitDiff {
  const a = original.split(/\s+/).filter(Boolean);
  const b = proposed.split(/\s+/).filter(Boolean);

  // LCS table
  const lcs: number[][] = Array.from({ length: a.length + 1 }, () =>
    new Array<number>(b.length + 1).fill(0),
  );
  for (let i = a.length - 1; i >= 0; i--) {
    for (let j = b.length - 1; j >= 0; j--) {
      lcs[i][j] = a[i] === b[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
    }
  }

  const originalParts: DiffPart[] = [];
  const proposedParts: DiffPart[] = [];
  const push = (parts: DiffPart[], word: string, changed: boolean) => {
    const last = parts[parts.length - 1];
    if (last && last.changed === changed) {
      last.text += ` ${word}`;
    } else {
      parts.push({ text: word, changed });
    }
  };

  let i = 0;
  let j = 0;
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) {
      push(originalParts, a[i], false);
      push(proposedParts, b[j], false);
      i++;
      j++;
    } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
      push(originalParts, a[i], true);
      i++;
    } else {
      push(proposedParts, b[j], true);
      j++;
    }
  }
  while (i < a.length) push(originalParts, a[i++], true);
  while (j < b.length) push(proposedParts, b[j++], true);

  return { originalParts, proposedParts };
}
