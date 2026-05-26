import React from 'react';
import { RegisterForm } from '../components/auth/RegisterForm';

const RegisterPage: React.FC = () => (
  <div className="max-w-md mx-auto mt-12">
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-8">
      <div className="text-center mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Create an account</h1>
        <p className="text-gray-500 text-sm mt-1">Join PollHub and start polling</p>
      </div>
      <RegisterForm />
    </div>
  </div>
);

export default RegisterPage;
