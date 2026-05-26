import React from 'react';
import { PollResults } from '../../types/poll';
import { ResultsBar } from './ResultsBar';

interface VoteResultsProps {
  results: PollResults;
  /** Live option counts pushed via WebSocket (keyed by optionId). */
  liveOptionCounts?: Record<string, number>;
}

export const VoteResults: React.FC<VoteResultsProps> = ({ results, liveOptionCounts }) => {
  const isLive = !!liveOptionCounts;

  if (results.pollType === 'FREE_TEXT') {
    const responses = results.freeTextResponses ?? [];
    return (
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <p className="text-sm font-semibold text-gray-700">
            {results.totalResponses} response{results.totalResponses !== 1 ? 's' : ''}
          </p>
        </div>
        {responses.length === 0 ? (
          <p className="text-sm text-gray-400 italic">No responses yet — be the first!</p>
        ) : (
          <div className="space-y-2 max-h-80 overflow-y-auto pr-1">
            {responses.map((text, i) => (
              <div key={i} className="bg-gray-50 rounded-xl px-4 py-3 text-sm text-gray-700 border border-gray-200">
                {text}
              </div>
            ))}
          </div>
        )}
      </div>
    );
  }

  const options = results.optionResults ?? [];
  const total = isLive
    ? Object.values(liveOptionCounts!).reduce((a, b) => a + b, 0)
    : results.totalResponses;

  const maxCount = Math.max(...options.map((o) => {
    const count = liveOptionCounts ? (liveOptionCounts[o.optionId] ?? 0) : o.voteCount;
    return count;
  }), 0);

  return (
    <div className="space-y-2">
      {options.map((opt) => {
        const count = liveOptionCounts ? (liveOptionCounts[opt.optionId] ?? 0) : opt.voteCount;
        const pct   = total > 0 ? Math.round((count * 1000) / total) / 10 : 0;
        const leading = count > 0 && count === maxCount;
        return (
          <ResultsBar
            key={opt.optionId}
            optionText={opt.optionText}
            voteCount={count}
            percentage={pct}
            isLeading={leading}
          />
        );
      })}

      <div className="flex items-center justify-between pt-1">
        <p className="text-xs text-gray-400">{total.toLocaleString()} total votes</p>
        {isLive && (
          <div className="flex items-center gap-1.5 text-xs text-green-600">
            <span className="w-2 h-2 rounded-full bg-green-500 animate-pulse" />
            Live
          </div>
        )}
      </div>
    </div>
  );
};
