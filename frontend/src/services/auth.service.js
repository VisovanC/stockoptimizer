import api from './api';

const authService = {
    login: async (username, password) => {
        const response = await api.post('/auth/signin', { username, password });
        if (response.data.token) {
            localStorage.setItem('token', response.data.token);
            localStorage.setItem('user', JSON.stringify({ username }));
        }
        return response.data;
    },

    register: async (username, email, password) => {
        const response = await api.post('/auth/signup', { username, email, password });
        return response.data;
    },

    logout: () => {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
    },

    getCurrentUser: () => {
        return JSON.parse(localStorage.getItem('user'));
    },

    isAuthenticated: () => {
        return !!localStorage.getItem('token');
    }
};

export default authService;