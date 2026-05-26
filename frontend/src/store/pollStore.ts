import { create } from 'zustand';
import { Poll } from '../types/poll';

interface PollState {
  polls: Poll[];
  selectedPoll: Poll | null;
  voteCounts: Record<string, Record<string, number>>;
  totalPages: number;
  currentPage: number;
  isLoading: boolean;
  error: string | null;
  setPolls: (polls: Poll[], totalPages: number, currentPage: number) => void;
  setSelectedPoll: (poll: Poll | null) => void;
  updateVoteCounts: (pollId: string, counts: Record<string, number>) => void;
  setLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
}

export const usePollStore = create<PollState>((set) => ({
  polls: [],
  selectedPoll: null,
  voteCounts: {},
  totalPages: 0,
  currentPage: 0,
  isLoading: false,
  error: null,
  setPolls: (polls, totalPages, currentPage) => set({ polls, totalPages, currentPage }),
  setSelectedPoll: (poll) => set({ selectedPoll: poll }),
  updateVoteCounts: (pollId, counts) =>
    set((state) => ({
      voteCounts: { ...state.voteCounts, [pollId]: counts },
    })),
  setLoading: (isLoading) => set({ isLoading }),
  setError: (error) => set({ error }),
}));
