import React, { createContext, useState, useContext, useEffect } from 'react';
import authService from '../services/auth.service';

const AuthContext = createContext(null);

export const useAuth = () => {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth must be used within an AuthProvider');
    }
    return context;
};

export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const currentUser = authService.getCurrentUser();
        if (currentUser && authService.isAuthenticated()) {
            setUser(currentUser);
        }
        setLoading(false);
    }, []);

    const login = async (username, password) => {
        const response = await authService.login(username, password);
        const currentUser = authService.getCurrentUser();
        setUser(currentUser);
        return response;
    };

    const register = async (username, email, password) => {
        const response = await authService.register(username, email, password);
        return response;
    };

    const logout = () => {
        authService.logout();
        setUser(null);
    };

    const value = {
        user,
        login,
        register,
        logout,
        isAuthenticated: authService.isAuthenticated(),
        loading
    };

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};