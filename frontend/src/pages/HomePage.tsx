import React, { useState } from 'react';
import { usePolls } from '../hooks/usePolls';
import { PollList } from '../components/poll/PollList';
import { CreatePollModal } from '../components/poll/CreatePollModal';
import { useAuthStore } from '../store/authStore';
import { Button } from '../components/common/Button';
import { Poll } from '../types/poll';

const HomePage: React.FC = () => {
  const [page, setPage]           = useState(0);
  const [modalOpen, setModalOpen] = useState(false);
  const { polls, isLoading, error, totalPages, currentPage, refetch } = usePolls(page);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const handlePollCreated = (_poll: Poll) => {
    setPage(0);
    refetch();
  };

  return (
    <div>
      {/* Page header */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Active Polls</h1>
          <p className="text-gray-500 text-sm mt-1">Vote and see results update in real time</p>
        </div>
        {isAuthenticated && (
          <Button onClick={() => setModalOpen(true)}>
            + New Poll
          </Button>
        )}
      </div>

      {/* Poll grid */}
      <PollList
        polls={polls}
        isLoading={isLoading}
        error={error}
        totalPages={totalPages}
        currentPage={currentPage}
        onPageChange={setPage}
      />

      {/* Create poll modal */}
      <CreatePollModal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        onCreated={handlePollCreated}
      />
    </div>
  );
};

export default HomePage;
