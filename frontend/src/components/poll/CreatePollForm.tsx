import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { pollService } from '../../services/pollService';
import { Button } from '../common/Button';
import { Input } from '../common/Input';
import type { PollType } from '../../types/poll';

export const CreatePollForm: React.FC = () => {
  const navigate = useNavigate();
  const [question, setQuestion] = useState('');
  const [pollType, setPollType] = useState<PollType>('SINGLE_CHOICE');
  const [options, setOptions] = useState(['', '']);
  const [expiresAt, setExpiresAt] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const addOption = () => {
    if (options.length < 10) setOptions((prev) => [...prev, '']);
  };

  const removeOption = (index: number) => {
    if (options.length > 2) setOptions((prev) => prev.filter((_, i) => i !== index));
  };

  const updateOption = (index: number, value: string) =>
    setOptions((prev) => prev.map((opt, i) => (i === index ? value : opt)));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const filledOptions = options.filter((o) => o.trim());
    if (filledOptions.length < 2) {
      setError('Please provide at least 2 options');
      return;
    }
    setError(null);
    setIsLoading(true);
    try {
      const poll = await pollService.createPoll({
        question,
        pollType,
        options: filledOptions,
        expiresAt: expiresAt || undefined,
      });
      navigate(`/polls/${poll.id}`);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to create poll');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      <div>
        <label className="text-sm font-medium text-gray-700 block mb-1">Poll Type</label>
        <select
          value={pollType}
          onChange={(e) => setPollType(e.target.value as PollType)}
          className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="SINGLE_CHOICE">Single Choice</option>
          <option value="MULTI_CHOICE">Multi Choice</option>
          <option value="FREE_TEXT">Free Text</option>
        </select>
      </div>

      <Input
        label="Poll Question"
        value={question}
        onChange={(e) => setQuestion(e.target.value)}
        placeholder="What would you like to ask?"
        required
        autoFocus
      />

      <div>
        <label className="text-sm font-medium text-gray-700 block mb-2">Options</label>
        <div className="space-y-2">
          {options.map((opt, i) => (
            <div key={i} className="flex gap-2">
              <Input
                value={opt}
                onChange={(e) => updateOption(i, e.target.value)}
                placeholder={`Option ${i + 1}`}
                className="flex-1"
              />
              {options.length > 2 && (
                <button
                  type="button"
                  onClick={() => removeOption(i)}
                  className="text-red-400 hover:text-red-600 text-xl font-bold px-2"
                  aria-label="Remove option"
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
            className="mt-2 text-sm text-blue-600 hover:underline"
          >
            + Add option
          </button>
        )}
      </div>

      <Input
        label="Expires At (optional)"
        type="datetime-local"
        value={expiresAt}
        onChange={(e) => setExpiresAt(e.target.value)}
        helperText="Leave blank for no expiry"
      />

      {error && (
        <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
          {error}
        </p>
      )}

      <div className="flex gap-3">
        <Button type="submit" isLoading={isLoading} className="flex-1">
          Create Poll
        </Button>
        <Button type="button" variant="secondary" onClick={() => navigate('/')}>
          Cancel
        </Button>
      </div>
    </form>
  );
};
