import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { usePoll, useVote } from '../hooks/usePolls';
import { useRealtimeVotes } from '../hooks/useRealtimeVotes';
import { usePollStore } from '../store/pollStore';
import { useAuthStore } from '../store/authStore';
import { pollService } from '../services/pollService';
import { VoteOptions } from '../components/poll/VoteOptions';
import { VoteResults } from '../components/poll/VoteResults';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { Button } from '../components/common/Button';
import { formatDate } from '../utils/formatUtils';
import { PollResults, PollType, CastVoteRequest } from '../types/poll';

const pollTypeBadge: Record<PollType, { label: string; cls: string }> = {
  SINGLE_CHOICE: { label: 'Single choice',  cls: 'bg-blue-100 text-blue-700'    },
  MULTI_CHOICE:  { label: 'Multi choice',   cls: 'bg-violet-100 text-violet-700' },
  FREE_TEXT:     { label: 'Open ended',     cls: 'bg-amber-100 text-amber-700'   },
};

const PollDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const { poll, isLoading, error, refetch } = usePoll(id!);
  const { castVote }                        = useVote(id!);
  const { voteCounts }                      = usePollStore();
  const { isAuthenticated, user }           = useAuthStore();

  const [hasVoted, setHasVoted]     = useState(false);
  const [isVoting, setIsVoting]     = useState(false);
  const [voteError, setVoteError]   = useState<string | null>(null);
  const [results, setResults]       = useState<PollResults | null>(null);

  const { isConnected, connectionType } = useRealtimeVotes(id ?? null);

  useEffect(() => {
    if (!id) return;
    pollService.getResults(id).then(setResults).catch(() => {});
  }, [id]);

  const liveOptionCounts = id ? voteCounts[id] : undefined;

  const handleVote = async (req: CastVoteRequest) => {
    if (!isAuthenticated) { navigate('/login'); return; }
    setVoteError(null);
    setIsVoting(true);
    try {
      const res = await castVote(req);
      setResults(res);
      setHasVoted(true);
      await refetch();
      const freshResults = await pollService.getResults(id!);
      setResults(freshResults);
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 409) {
        setVoteError('You have already voted on this poll!');
        setHasVoted(true);
        try {
          const freshResults = await pollService.getResults(id!);
          setResults(freshResults);
        } catch { /* ignore secondary failure */ }
      } else {
        setVoteError(err instanceof Error ? err.message : 'Failed to submit vote');
      }
    } finally {
      setIsVoting(false);
    }
  };

  const handleClose = async () => {
    try { await pollService.closePoll(id!); await refetch(); }
    catch { /* silently refresh */ }
  };

  if (isLoading) return <LoadingSpinner className="py-20" size="lg" />;
  if (error || !poll) {
    return (
      <div className="text-center py-20">
        <p className="text-red-500 mb-4">{error ?? 'Poll not found'}</p>
        <Button variant="secondary" onClick={() => navigate('/')}>Back to polls</Button>
      </div>
    );
  }

  const isOwner    = user?.username === poll.createdBy;
  const pollActive = poll.status === 'ACTIVE';
  const badge      = pollTypeBadge[poll.pollType] ?? pollTypeBadge.SINGLE_CHOICE;
  const showVoting = pollActive && !hasVoted && isAuthenticated;
  const showResults = hasVoted || !pollActive || results !== null;

  return (
    <div className="max-w-2xl mx-auto">
      <button
        onClick={() => navigate(-1)}
        className="text-sm text-gray-500 hover:text-gray-700 mb-4 flex items-center gap-1"
      >
        ← Back
      </button>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-8 space-y-6">
        {/* Poll header */}
        <div>
          <div className="flex flex-wrap items-center gap-2 mb-3">
            <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${badge.cls}`}>{badge.label}</span>
            <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${
              poll.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
              poll.status === 'CLOSED' ? 'bg-gray-100 text-gray-500' :
              'bg-orange-100 text-orange-600'
            }`}>
              {poll.status}
            </span>
            {poll.pollType === 'MULTI_CHOICE' && (
              <span className="text-xs text-gray-400">up to {poll.maxChoices} choices</span>
            )}
            {/* Real-time connection indicator */}
            {pollActive && (
              <span className={`ml-auto flex items-center gap-1 text-xs font-medium ${
                connectionType === 'websocket' ? 'text-green-600' :
                connectionType === 'sse'       ? 'text-blue-500'  : 'text-gray-400'
              }`}>
                <span className={`w-1.5 h-1.5 rounded-full ${
                  isConnected ? 'animate-pulse bg-current' : 'bg-gray-300'
                }`} />
                {connectionType === 'websocket' ? 'Live' :
                 connectionType === 'sse'       ? 'Live (SSE)' : 'Connecting…'}
              </span>
            )}
          </div>

          <h1 className="text-xl font-bold text-gray-900 leading-snug">{poll.question}</h1>

          <div className="mt-2 flex flex-wrap gap-3 text-xs text-gray-400">
            <span>by <span className="font-medium text-gray-500">{poll.createdBy}</span></span>
            <span>{formatDate(poll.createdAt)}</span>
            {poll.expiresAt && <span>expires {formatDate(poll.expiresAt)}</span>}
          </div>
        </div>

        {/* Voting form */}
        {showVoting && (
          <div>
            <p className="text-sm font-semibold text-gray-700 mb-3">Cast your vote</p>
            {voteError && (
              <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2 mb-3">
                {voteError}
              </p>
            )}
            <VoteOptions poll={poll} onSubmit={handleVote} isSubmitting={isVoting} />
          </div>
        )}

        {/* Login prompt */}
        {pollActive && !isAuthenticated && (
          <p className="text-sm text-center text-gray-500">
            <button onClick={() => navigate('/login')} className="text-blue-600 hover:underline font-medium">
              Log in
            </button>{' '}
            to vote on this poll
          </p>
        )}

        {/* Vote confirmed banner */}
        {hasVoted && (
          <div className="flex items-center gap-2 bg-green-50 border border-green-200 rounded-xl px-4 py-3 text-sm text-green-700 font-medium">
            <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
            </svg>
            Vote recorded! Results update live below.
          </div>
        )}

        {/* Results */}
        {showResults && results && (
          <div>
            <p className="text-sm font-semibold text-gray-700 mb-3">
              {pollActive ? 'Current results' : 'Final results'}
            </p>
            <VoteResults
              results={results}
              liveOptionCounts={liveOptionCounts}
            />
          </div>
        )}

        {/* Owner controls */}
        {isOwner && pollActive && (
          <div className="pt-4 border-t border-gray-100">
            <Button variant="danger" size="sm" onClick={handleClose}>
              Close Poll
            </Button>
          </div>
        )}
      </div>
    </div>
  );
};

export default PollDetailPage;
