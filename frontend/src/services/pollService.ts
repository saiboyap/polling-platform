import api from './api';
import { ApiResponse } from '../types/common';
import {
  CastVoteRequest,
  CreatePollRequest,
  PagedResponse,
  Poll,
  PollResults,
} from '../types/poll';

export const pollService = {
  // ---- Polls ----

  getActivePolls: async (page = 0, size = 10): Promise<PagedResponse<Poll>> => {
    const res = await api.get<ApiResponse<PagedResponse<Poll>>>('/polls', { params: { page, size } });
    return res.data.data;
  },

  getPollById: async (id: string): Promise<Poll> => {
    const res = await api.get<ApiResponse<Poll>>(`/polls/${id}`);
    return res.data.data;
  },

  createPoll: async (data: CreatePollRequest): Promise<Poll> => {
    const res = await api.post<ApiResponse<Poll>>('/polls', data);
    return res.data.data;
  },

  closePoll: async (id: string): Promise<void> => {
    await api.patch(`/polls/${id}/close`);
  },

  // ---- Voting & results (Phase 2 endpoints) ----

  castVote: async (pollId: string, data: CastVoteRequest): Promise<PollResults> => {
    const res = await api.post<ApiResponse<PollResults>>(`/polls/${pollId}/vote`, data);
    return res.data.data;
  },

  getResults: async (pollId: string): Promise<PollResults> => {
    const res = await api.get<ApiResponse<PollResults>>(`/polls/${pollId}/results`);
    return res.data.data;
  },
};
