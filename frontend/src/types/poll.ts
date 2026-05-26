export type PollStatus = 'ACTIVE' | 'CLOSED' | 'EXPIRED';
export type PollType   = 'SINGLE_CHOICE' | 'MULTI_CHOICE' | 'FREE_TEXT';

export interface PollOption {
  id: string;
  optionText: string;
  voteCount: number;
}

export interface Poll {
  id: string;
  question: string;
  createdBy: string;
  status: PollStatus;
  pollType: PollType;
  maxChoices: number;
  options: PollOption[];
  expiresAt: string | null;
  createdAt: string;
  totalVotes: number;
}

// ---- Create ----

export interface CreatePollRequest {
  question: string;
  pollType: PollType;
  options?: string[];
  maxChoices?: number;
  expiresAt?: string;
}

// ---- Vote ----

export interface CastVoteRequest {
  optionIds?: string[];
  freeText?: string;
}

// ---- Results ----

export interface OptionResult {
  optionId: string;
  optionText: string;
  voteCount: number;
  percentage: number;
}

export interface PollResults {
  pollId: string;
  question: string;
  pollType: PollType;
  status: string;
  maxChoices: number;
  totalResponses: number;
  optionResults: OptionResult[] | null;
  freeTextResponses: string[] | null;
}

// ---- Pagination ----

export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}
