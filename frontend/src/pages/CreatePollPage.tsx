import React from 'react';
import { CreatePollForm } from '../components/poll/CreatePollForm';

const CreatePollPage: React.FC = () => (
  <div className="max-w-xl mx-auto">
    <div className="mb-6">
      <h1 className="text-2xl font-bold text-gray-900">Create a Poll</h1>
      <p className="text-gray-500 text-sm mt-1">Ask anything and collect real-time votes</p>
    </div>
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-8">
      <CreatePollForm />
    </div>
  </div>
);

export default CreatePollPage;
