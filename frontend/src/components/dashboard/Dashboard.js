import React, { useState, useEffect } from 'react';
import { Container, Row, Col, Card, Button, Alert } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import { PieChart, Pie, Cell, ResponsiveContainer, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend } from 'recharts';
import portfolioService from '../../services/portfolio.service';
import { useAuth } from '../../context/AuthContext';

const Dashboard = () => {
    const [portfolios, setPortfolios] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const { user } = useAuth();

    useEffect(() => {
        fetchPortfolios();
    }, []);

    const fetchPortfolios = async () => {
        try {
            setLoading(true);
            const data = await portfolioService.getPortfolios();
            setPortfolios(data);
        } catch (err) {
            setError('Failed to load portfolios');
        } finally {
            setLoading(false);
        }
    };

    const calculateTotalValue = () => {
        return portfolios.reduce((total, portfolio) => total + (portfolio.totalValue || 0), 0);
    };

    const calculateTotalReturn = () => {
        return portfolios.reduce((total, portfolio) => total + (portfolio.totalReturn || 0), 0);
    };

    const getPortfolioChartData = () => {
        return portfolios.map(portfolio => ({
            name: portfolio.name,
            value: portfolio.totalValue || 0
        }));
    };

    const getTopPerformers = () => {
        const allStocks = [];
        portfolios.forEach(portfolio => {
            if (portfolio.stocks) {
                portfolio.stocks.forEach(stock => {
                    allStocks.push({
                        symbol: stock.symbol,
                        return: stock.returnPercentage || 0
                    });
                });
            }
        });

        return allStocks
            .sort((a, b) => b.return - a.return)
            .slice(0, 5);
    };

    const COLORS = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042', '#8884D8'];

    if (loading) return <Container className="py-4">Loading...</Container>;

    return (
        <Container className="py-4">
            <h1 className="mb-4">Welcome back, {user?.username}!</h1>

            {error && <Alert variant="danger">{error}</Alert>}

            <Row className="mb-4">
                <Col md={4}>
                    <Card className="h-100">
                        <Card.Body>
                            <h5 className="text-muted">Total Portfolio Value</h5>
                            <h2 className="text-primary">${calculateTotalValue().toFixed(2)}</h2>
                        </Card.Body>
                    </Card>
                </Col>
                <Col md={4}>
                    <Card className="h-100">
                        <Card.Body>
                            <h5 className="text-muted">Total Return</h5>
                            <h2 className={calculateTotalReturn() >= 0 ? 'text-success' : 'text-danger'}>
                                ${calculateTotalReturn().toFixed(2)}
                            </h2>
                        </Card.Body>
                    </Card>
                </Col>
                <Col md={4}>
                    <Card className="h-100">
                        <Card.Body>
                            <h5 className="text-muted">Number of Portfolios</h5>
                            <h2>{portfolios.length}</h2>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>

            <Row className="mb-4">
                <Col md={6}>
                    <Card>
                        <Card.Header>
                            <h5>Portfolio Distribution</h5>
                        </Card.Header>
                        <Card.Body>
                            {portfolios.length > 0 ? (
                                <ResponsiveContainer width="100%" height={300}>
                                    <PieChart>
                                        <Pie
                                            data={getPortfolioChartData()}
                                            cx="50%"
                                            cy="50%"
                                            labelLine={false}
                                            label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}
                                            outerRadius={80}
                                            fill="#8884d8"
                                            dataKey="value"
                                        >
                                            {getPortfolioChartData().map((entry, index) => (
                                                <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                                            ))}
                                        </Pie>
                                        <Tooltip formatter={(value) => `$${value.toFixed(2)}`} />
                                    </PieChart>
                                </ResponsiveContainer>
                            ) : (
                                <p className="text-center">No portfolios yet</p>
                            )}
                        </Card.Body>
                    </Card>
                </Col>

                <Col md={6}>
                    <Card>
                        <Card.Header>
                            <h5>Top Performers</h5>
                        </Card.Header>
                        <Card.Body>
                            {getTopPerformers().length > 0 ? (
                                <ResponsiveContainer width="100%" height={300}>
                                    <BarChart data={getTopPerformers()}>
                                        <CartesianGrid strokeDasharray="3 3" />
                                        <XAxis dataKey="symbol" />
                                        <YAxis />
                                        <Tooltip formatter={(value) => `${value.toFixed(2)}%`} />
                                        <Bar dataKey="return" fill="#82ca9d" />
                                    </BarChart>
                                </ResponsiveContainer>
                            ) : (
                                <p className="text-center">No stock data available</p>
                            )}
                        </Card.Body>
                    </Card>
                </Col>
            </Row>

            <Row>
                <Col>
                    <Card>
                        <Card.Header className="d-flex justify-content-between align-items-center">
                            <h5>Quick Actions</h5>
                        </Card.Header>
                        <Card.Body>
                            <div className="d-grid gap-2 d-md-flex">
                                <Button as={Link} to="/portfolios/new" variant="primary">
                                    Create New Portfolio
                                </Button>
                                <Button as={Link} to="/portfolios/upload" variant="success">
                                    Upload from Excel
                                </Button>
                                <Button as={Link} to="/portfolios" variant="outline-primary">
                                    View All Portfolios
                                </Button>
                            </div>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
        </Container>
    );
};

export default Dashboard;