import React from 'react';
import { Link } from 'react-router-dom';
import { Poll, PollType } from '../../types/poll';
import { formatRelativeTime } from '../../utils/formatUtils';

interface PollCardProps {
  poll: Poll;
}

const typeConfig: Record<PollType, { label: string; color: string }> = {
  SINGLE_CHOICE: { label: 'Single choice', color: 'bg-blue-100 text-blue-700'   },
  MULTI_CHOICE:  { label: 'Multi choice',  color: 'bg-violet-100 text-violet-700' },
  FREE_TEXT:     { label: 'Open ended',    color: 'bg-amber-100 text-amber-700'  },
};

const statusColors: Record<string, string> = {
  ACTIVE:  'bg-green-100 text-green-700',
  CLOSED:  'bg-gray-100 text-gray-500',
  EXPIRED: 'bg-orange-100 text-orange-600',
};

export const PollCard: React.FC<PollCardProps> = ({ poll }) => {
  const type = typeConfig[poll.pollType] ?? typeConfig.SINGLE_CHOICE;
  const isFreeText = poll.pollType === 'FREE_TEXT';

  return (
    <Link to={`/polls/${poll.id}`} className="block group">
      <div className="bg-white rounded-xl border border-gray-200 p-5 shadow-sm hover:shadow-md hover:border-blue-300 transition-all duration-150 h-full flex flex-col">
        {/* Header row */}
        <div className="flex items-start justify-between gap-2 mb-3">
          <span className={`shrink-0 text-xs font-medium px-2 py-0.5 rounded-full ${type.color}`}>
            {type.label}
          </span>
          <span className={`shrink-0 text-xs font-medium px-2 py-0.5 rounded-full ${statusColors[poll.status]}`}>
            {poll.status}
          </span>
        </div>

        {/* Question */}
        <h3 className="text-base font-semibold text-gray-900 group-hover:text-blue-600 transition-colors line-clamp-2 mb-3">
          {poll.question}
        </h3>

        {/* Options preview */}
        {!isFreeText && poll.options.length > 0 && (
          <div className="space-y-1.5 mb-4 flex-1">
            {poll.options.slice(0, 3).map((opt) => (
              <div key={opt.id} className="flex items-center gap-2 text-sm text-gray-500">
                <span className="w-1.5 h-1.5 rounded-full bg-blue-300 shrink-0" />
                <span className="truncate">{opt.optionText}</span>
              </div>
            ))}
            {poll.options.length > 3 && (
              <p className="text-xs text-gray-400 pl-3.5">+{poll.options.length - 3} more</p>
            )}
          </div>
        )}

        {isFreeText && (
          <p className="text-sm text-gray-400 italic flex-1 mb-4">Open-ended — share your thoughts</p>
        )}

        {/* Footer */}
        <div className="flex items-center justify-between text-xs text-gray-400 pt-2 border-t border-gray-100 mt-auto">
          <span>by <span className="font-medium text-gray-500">{poll.createdBy}</span></span>
          <div className="flex items-center gap-2">
            <span>{poll.totalVotes.toLocaleString()} votes</span>
            <span>·</span>
            <span>{formatRelativeTime(poll.createdAt)}</span>
          </div>
        </div>
      </div>
    </Link>
  );
};
