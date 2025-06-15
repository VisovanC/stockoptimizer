import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { ToastContainer } from 'react-toastify';
import 'bootstrap/dist/css/bootstrap.min.css';
import 'react-toastify/dist/ReactToastify.css';

import { AuthProvider } from './context/AuthContext';
import PrivateRoute from './components/auth/PrivateRoute';
import Login from './components/auth/Login';
import Register from './components/auth/Register';
import Dashboard from './components/dashboard/Dashboard';
import PortfolioList from './components/portfolio/PortfolioList';
import PortfolioDetail from './components/portfolio/PortfolioDetail';
import PortfolioUpload from './components/portfolio/PortfolioUpload';
import CreatePortfolio from './components/portfolio/CreatePortfolio';
import Navigation from './components/common/Navigation';

function App() {
  return (
      <Router>
        <AuthProvider>
          <div className="App">
            <Navigation />
            <Routes>
              <Route path="/login" element={<Login />} />
              <Route path="/register" element={<Register />} />
              <Route path="/" element={<Navigate to="/dashboard" replace />} />

              <Route path="/dashboard" element={
                <PrivateRoute>
                  <Dashboard />
                </PrivateRoute>
              } />

              <Route path="/portfolios" element={
                <PrivateRoute>
                  <PortfolioList />
                </PrivateRoute>
              } />

              <Route path="/portfolios/new" element={
                <PrivateRoute>
                  <CreatePortfolio />
                </PrivateRoute>
              } />

              <Route path="/portfolios/upload" element={
                <PrivateRoute>
                  <PortfolioUpload />
                </PrivateRoute>
              } />

              <Route path="/portfolios/:id" element={
                <PrivateRoute>
                  <PortfolioDetail />
                </PrivateRoute>
              } />
            </Routes>
            <ToastContainer position="top-right" />
          </div>
        </AuthProvider>
      </Router>
  );
}

export default App;