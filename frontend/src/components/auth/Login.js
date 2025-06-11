import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Container, Form, Button, Card, Alert } from 'react-bootstrap';
import { useAuth } from '../../context/AuthContext';

const Login = () => {
    const [formData, setFormData] = useState({
        username: '',
        password: ''
    });
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();
    const { login } = useAuth();

    const handleChange = (e) => {
        setFormData({
            ...formData,
            [e.target.name]: e.target.value
        });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setLoading(true);

        try {
            await login(formData.username, formData.password);
            navigate('/dashboard');
        } catch (err) {
            setError(err.response?.data?.message || 'Failed to login');
        } finally {
            setLoading(false);
        }
    };

    return (
        <Container className="d-flex align-items-center justify-content-center" style={{
            minHeight: '100vh',
            background: 'linear-gradient(135deg, #0B192C 0%, #1E3E62 100%)'
        }}>
            <div style={{ maxWidth: '400px', width: '100%' }}>
                <Card style={{ borderRadius: '15px', boxShadow: '0 10px 30px rgba(0,0,0,0.2)' }}>
                    <Card.Body style={{ padding: '2.5rem' }}>
                        <h2 className="text-center mb-4" style={{ color: '#0B192C', fontWeight: '700' }}>
                            Welcome Back
                        </h2>
                        <p className="text-center text-muted mb-4">
                            Sign in to your account to continue
                        </p>
                        {error && <Alert variant="danger">{error}</Alert>}
                        <Form onSubmit={handleSubmit}>
                            <Form.Group className="mb-3">
                                <Form.Label>Username</Form.Label>
                                <Form.Control
                                    type="text"
                                    name="username"
                                    value={formData.username}
                                    onChange={handleChange}
                                    required
                                    placeholder="Enter your username"
                                />
                            </Form.Group>

                            <Form.Group className="mb-4">
                                <Form.Label>Password</Form.Label>
                                <Form.Control
                                    type="password"
                                    name="password"
                                    value={formData.password}
                                    onChange={handleChange}
                                    required
                                    placeholder="Enter your password"
                                />
                            </Form.Group>

                            <Button
                                disabled={loading}
                                className="w-100"
                                type="submit"
                                style={{
                                    backgroundColor: '#FF6500',
                                    border: 'none',
                                    padding: '0.75rem',
                                    fontWeight: '600'
                                }}
                            >
                                {loading ? 'Signing in...' : 'Sign In'}
                            </Button>
                        </Form>
                    </Card.Body>
                </Card>
                <div className="w-100 text-center mt-3">
                    <span style={{ color: 'white' }}>Need an account? </span>
                    <Link to="/register" style={{ color: '#FF6500', fontWeight: '600' }}>Sign Up</Link>
                </div>
            </div>
        </Container>
    );
};

export default Login;