import React from 'react';
import { Poll } from '../../types/poll';
import { PollCard } from './PollCard';
import { LoadingSpinner } from '../common/LoadingSpinner';
import { Button } from '../common/Button';

interface PollListProps {
  polls: Poll[];
  isLoading: boolean;
  error: string | null;
  totalPages: number;
  currentPage: number;
  onPageChange: (page: number) => void;
}

export const PollList: React.FC<PollListProps> = ({
  polls,
  isLoading,
  error,
  totalPages,
  currentPage,
  onPageChange,
}) => {
  if (isLoading) return <LoadingSpinner className="py-20" size="lg" />;

  if (error) {
    return (
      <div className="text-center py-12">
        <p className="text-red-600 mb-4">{error}</p>
        <Button variant="secondary" onClick={() => onPageChange(0)}>Retry</Button>
      </div>
    );
  }

  if (polls.length === 0) {
    return (
      <div className="text-center py-20 text-gray-500">
        <p className="text-lg font-medium">No polls yet</p>
        <p className="text-sm mt-1">Be the first to create one!</p>
      </div>
    );
  }

  return (
    <div>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {polls.map((poll) => (
          <PollCard key={poll.id} poll={poll} />
        ))}
      </div>

      {totalPages > 1 && (
        <div className="flex justify-center items-center gap-2 mt-8">
          <Button
            variant="secondary"
            size="sm"
            disabled={currentPage === 0}
            onClick={() => onPageChange(currentPage - 1)}
          >
            Previous
          </Button>
          <span className="text-sm text-gray-600">
            Page {currentPage + 1} of {totalPages}
          </span>
          <Button
            variant="secondary"
            size="sm"
            disabled={currentPage >= totalPages - 1}
            onClick={() => onPageChange(currentPage + 1)}
          >
            Next
          </Button>
        </div>
      )}
    </div>
  );
};
