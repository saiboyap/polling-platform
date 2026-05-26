import React from 'react';
import { LoginForm } from '../components/auth/LoginForm';

const LoginPage: React.FC = () => (
  <div className="max-w-md mx-auto mt-12">
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-8">
      <div className="text-center mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Welcome back</h1>
        <p className="text-gray-500 text-sm mt-1">Sign in to your account</p>
      </div>
      <LoginForm />
    </div>
  </div>
);

export default LoginPage;
