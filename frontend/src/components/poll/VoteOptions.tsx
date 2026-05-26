import React, { useState } from 'react';
import { Poll } from '../../types/poll';
import { CastVoteRequest } from '../../types/poll';
import { Button } from '../common/Button';

interface VoteOptionsProps {
  poll: Poll;
  onSubmit: (req: CastVoteRequest) => Promise<void>;
  isSubmitting: boolean;
}

export const VoteOptions: React.FC<VoteOptionsProps> = ({ poll, onSubmit, isSubmitting }) => {
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [freeText, setFreeText] = useState('');

  const isSingle = poll.pollType === 'SINGLE_CHOICE';
  const isMulti  = poll.pollType === 'MULTI_CHOICE';
  const isFree   = poll.pollType === 'FREE_TEXT';

  const toggleOption = (id: string) => {
    if (isSingle) {
      setSelectedIds([id]);
      return;
    }
    setSelectedIds((prev) => {
      if (prev.includes(id)) return prev.filter((x) => x !== id);
      if (prev.length >= poll.maxChoices) return prev; // cap at maxChoices
      return [...prev, id];
    });
  };

  const canSubmit =
    (isFree && freeText.trim().length > 0) ||
    (!isFree && selectedIds.length > 0);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    await onSubmit(
      isFree
        ? { freeText: freeText.trim() }
        : { optionIds: selectedIds }
    );
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-3">
      {/* ---- Choice polls ---- */}
      {!isFree &&
        poll.options.map((opt) => {
          const checked = selectedIds.includes(opt.id);
          return (
            <button
              key={opt.id}
              type="button"
              onClick={() => toggleOption(opt.id)}
              className={`w-full text-left rounded-xl border-2 px-4 py-3 transition-all duration-150 flex items-center gap-3 ${
                checked
                  ? 'border-blue-500 bg-blue-50'
                  : 'border-gray-200 hover:border-blue-300 hover:bg-gray-50'
              }`}
            >
              {/* Indicator */}
              <span
                className={`shrink-0 w-5 h-5 border-2 flex items-center justify-center transition-colors ${
                  isSingle ? 'rounded-full' : 'rounded'
                } ${
                  checked
                    ? 'border-blue-500 bg-blue-500'
                    : 'border-gray-300 bg-white'
                }`}
              >
                {checked && (
                  <svg className="w-3 h-3 text-white" fill="currentColor" viewBox="0 0 20 20">
                    <path
                      fillRule="evenodd"
                      d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                      clipRule="evenodd"
                    />
                  </svg>
                )}
              </span>
              <span className={`text-sm font-medium ${checked ? 'text-blue-800' : 'text-gray-700'}`}>
                {opt.optionText}
              </span>
            </button>
          );
        })}

      {/* Multi-choice hint */}
      {isMulti && (
        <p className="text-xs text-gray-500 pl-1">
          Select up to {poll.maxChoices} option{poll.maxChoices > 1 ? 's' : ''} &nbsp;·&nbsp;
          <span className={selectedIds.length >= poll.maxChoices ? 'text-orange-500 font-medium' : ''}>
            {selectedIds.length} / {poll.maxChoices} selected
          </span>
        </p>
      )}

      {/* ---- Free-text poll ---- */}
      {isFree && (
        <textarea
          rows={4}
          placeholder="Share your thoughts…"
          value={freeText}
          onChange={(e) => setFreeText(e.target.value)}
          maxLength={1000}
          className="w-full rounded-xl border-2 border-gray-200 px-4 py-3 text-sm text-gray-800 placeholder-gray-400 focus:outline-none focus:border-blue-400 resize-none transition-colors"
        />
      )}

      <Button
        type="submit"
        className="w-full"
        isLoading={isSubmitting}
        disabled={!canSubmit}
      >
        Submit Vote
      </Button>
    </form>
  );
};
