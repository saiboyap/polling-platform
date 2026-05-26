import React, { useEffect, useRef, useState } from 'react';
import { Poll, PollType } from '../../types/poll';
import { pollService } from '../../services/pollService';
import { Button } from '../common/Button';
import { Input } from '../common/Input';

interface CreatePollModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreated: (poll: Poll) => void;
}

const POLL_TYPES: { value: PollType; label: string; desc: string }[] = [
  { value: 'SINGLE_CHOICE', label: 'Single Choice', desc: 'Pick one option' },
  { value: 'MULTI_CHOICE',  label: 'Multiple Choice', desc: 'Pick several options' },
  { value: 'FREE_TEXT',     label: 'Free Text', desc: 'Open-ended response' },
];

export const CreatePollModal: React.FC<CreatePollModalProps> = ({ isOpen, onClose, onCreated }) => {
  const [pollType, setPollType]   = useState<PollType>('SINGLE_CHOICE');
  const [question, setQuestion]   = useState('');
  const [options, setOptions]     = useState(['', '']);
  const [maxChoices, setMaxChoices] = useState(2);
  const [expiresAt, setExpiresAt] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError]         = useState<string | null>(null);
  const firstInputRef             = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isOpen) {
      setQuestion(''); setOptions(['', '']); setMaxChoices(2);
      setExpiresAt(''); setError(null); setPollType('SINGLE_CHOICE');
      setTimeout(() => firstInputRef.current?.focus(), 50);
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const addOption = () => {
    if (options.length < 10) setOptions((p) => [...p, '']);
  };

  const removeOption = (i: number) => {
    if (options.length > 2) setOptions((p) => p.filter((_, idx) => idx !== i));
  };

  const updateOption = (i: number, v: string) =>
    setOptions((p) => p.map((o, idx) => (idx === i ? v : o)));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const filledOptions = options.map((o) => o.trim()).filter(Boolean);
    if (pollType !== 'FREE_TEXT' && filledOptions.length < 2) {
      setError('Provide at least 2 options');
      return;
    }
    if (pollType === 'MULTI_CHOICE' && maxChoices > filledOptions.length) {
      setError('Max choices cannot exceed the number of options');
      return;
    }

    setIsLoading(true);
    try {
      const poll = await pollService.createPoll({
        question: question.trim(),
        pollType,
        options: pollType !== 'FREE_TEXT' ? filledOptions : undefined,
        maxChoices: pollType === 'MULTI_CHOICE' ? maxChoices : undefined,
        expiresAt: expiresAt || undefined,
      });
      onCreated(poll);
      onClose();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to create poll');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    /* Backdrop */
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between px-6 pt-6 pb-4 border-b border-gray-100">
          <h2 className="text-lg font-bold text-gray-900">Create a Poll</h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 text-2xl leading-none"
            aria-label="Close"
          >
            ×
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-5">
          {/* Poll type tabs */}
          <div>
            <p className="text-sm font-medium text-gray-700 mb-2">Poll type</p>
            <div className="grid grid-cols-3 gap-2">
              {POLL_TYPES.map(({ value, label, desc }) => (
                <button
                  key={value}
                  type="button"
                  onClick={() => setPollType(value)}
                  className={`rounded-xl border-2 p-3 text-left transition-colors ${
                    pollType === value
                      ? 'border-blue-500 bg-blue-50'
                      : 'border-gray-200 hover:border-blue-200'
                  }`}
                >
                  <p className={`text-xs font-semibold ${pollType === value ? 'text-blue-700' : 'text-gray-700'}`}>
                    {label}
                  </p>
                  <p className="text-xs text-gray-400 mt-0.5">{desc}</p>
                </button>
              ))}
            </div>
          </div>

          {/* Question */}
          <Input
            ref={firstInputRef as React.Ref<HTMLInputElement>}
            label="Question"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder="What would you like to ask?"
            required
          />

          {/* Options (hidden for FREE_TEXT) */}
          {pollType !== 'FREE_TEXT' && (
            <div>
              <p className="text-sm font-medium text-gray-700 mb-2">Options</p>
              <div className="space-y-2">
                {options.map((opt, i) => (
                  <div key={i} className="flex gap-2 items-center">
                    <input
                      value={opt}
                      onChange={(e) => updateOption(i, e.target.value)}
                      placeholder={`Option ${i + 1}`}
                      className="flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                    {options.length > 2 && (
                      <button
                        type="button"
                        onClick={() => removeOption(i)}
                        className="text-gray-400 hover:text-red-500 text-xl font-bold w-7 flex-shrink-0"
                      >
                        ×
                      </button>
                    )}
                  </div>
                ))}
              </div>
              {options.length < 10 && (
                <button
                  type="button"
                  onClick={addOption}
                  className="mt-2 text-xs text-blue-600 hover:underline"
                >
                  + Add option
                </button>
              )}
            </div>
          )}

          {/* Max choices (MULTI only) */}
          {pollType === 'MULTI_CHOICE' && (
            <div className="flex items-center gap-3">
              <label className="text-sm font-medium text-gray-700 whitespace-nowrap">
                Max choices per voter
              </label>
              <input
                type="number"
                min={1}
                max={options.filter(Boolean).length || 10}
                value={maxChoices}
                onChange={(e) => setMaxChoices(Number(e.target.value))}
                className="w-20 rounded-lg border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          )}

          {/* Expiry */}
          <Input
            label="Expires at (optional)"
            type="datetime-local"
            value={expiresAt}
            onChange={(e) => setExpiresAt(e.target.value)}
          />

          {error && (
            <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
              {error}
            </p>
          )}

          <div className="flex gap-3 pt-1">
            <Button type="submit" className="flex-1" isLoading={isLoading}>
              Create Poll
            </Button>
            <Button type="button" variant="secondary" onClick={onClose}>
              Cancel
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
