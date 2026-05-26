import { useCallback, useEffect } from 'react';
import { pollService } from '../services/pollService';
import { usePollStore } from '../store/pollStore';
import { CastVoteRequest, CreatePollRequest, PollResults } from '../types/poll';

export const usePolls = (page = 0) => {
  const { polls, totalPages, currentPage, isLoading, error, setPolls, setLoading, setError } =
    usePollStore();

  const fetchPolls = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const paged = await pollService.getActivePolls(page);
      setPolls(paged.content, paged.totalPages, paged.number);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load polls');
    } finally {
      setLoading(false);
    }
  }, [page, setLoading, setError, setPolls]);

  useEffect(() => {
    fetchPolls();
  }, [fetchPolls]);

  const createPoll = async (data: CreatePollRequest) => pollService.createPoll(data);

  return { polls, totalPages, currentPage, isLoading, error, refetch: fetchPolls, createPoll };
};

export const usePoll = (id: string) => {
  const { selectedPoll, isLoading, error, setSelectedPoll, setLoading, setError } = usePollStore();

  const fetchPoll = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const poll = await pollService.getPollById(id);
      setSelectedPoll(poll);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load poll');
    } finally {
      setLoading(false);
    }
  }, [id, setLoading, setError, setSelectedPoll]);

  useEffect(() => {
    fetchPoll();
  }, [fetchPoll]);

  return { poll: selectedPoll, isLoading, error, refetch: fetchPoll };
};

export const useVote = (pollId: string) => {
  const { updateVoteCounts } = usePollStore();

  const castVote = async (req: CastVoteRequest): Promise<PollResults> => {
    const results = await pollService.castVote(pollId, req);
    if (results.optionResults) {
      const counts: Record<string, number> = {};
      results.optionResults.forEach((o) => { counts[o.optionId] = o.voteCount; });
      updateVoteCounts(pollId, counts);
    }
    return results;
  };

  const getResults = async (): Promise<PollResults> => pollService.getResults(pollId);

  return { castVote, getResults };
};
