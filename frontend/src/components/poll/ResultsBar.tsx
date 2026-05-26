import React from 'react';

interface ResultsBarProps {
  optionText: string;
  voteCount: number;
  percentage: number;
  isLeading: boolean;
  isSelected?: boolean;
}

export const ResultsBar: React.FC<ResultsBarProps> = ({
  optionText,
  voteCount,
  percentage,
  isLeading,
  isSelected = false,
}) => (
  <div
    className={`rounded-xl overflow-hidden border transition-colors ${
      isLeading ? 'border-blue-300' : 'border-gray-200'
    } ${isSelected ? 'ring-2 ring-blue-400 ring-offset-1' : ''}`}
  >
    <div className="relative px-4 py-3">
      {/* Animated fill bar */}
      <div
        className={`absolute inset-0 transition-all duration-700 ease-out ${
          isLeading ? 'bg-blue-100' : 'bg-gray-50'
        }`}
        style={{ width: `${percentage}%` }}
      />
      {/* Content */}
      <div className="relative flex items-center justify-between gap-4">
        <div className="flex items-center gap-2 min-w-0">
          {isSelected && (
            <span className="shrink-0 w-4 h-4 rounded-full bg-blue-500 flex items-center justify-center">
              <svg className="w-2.5 h-2.5 text-white" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
              </svg>
            </span>
          )}
          <span className={`text-sm font-medium truncate ${isLeading ? 'text-blue-900' : 'text-gray-700'}`}>
            {optionText}
          </span>
        </div>
        <div className="shrink-0 flex items-center gap-2">
          <span className={`text-sm font-bold ${isLeading ? 'text-blue-700' : 'text-gray-500'}`}>
            {percentage.toFixed(1)}%
          </span>
        </div>
      </div>
    </div>
    <div className="px-4 pb-2 text-xs text-gray-400">
      {voteCount.toLocaleString()} {voteCount === 1 ? 'vote' : 'votes'}
    </div>
  </div>
);
