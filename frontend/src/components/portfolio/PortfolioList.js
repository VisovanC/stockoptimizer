import React, { useState, useEffect } from 'react';
import { Container, Card, Table, Button, Badge, Alert } from 'react-bootstrap';
import { Link, useNavigate } from 'react-router-dom';
import portfolioService from '../../services/portfolio.service';
import { toast } from 'react-toastify';

const PortfolioList = () => {
    const [portfolios, setPortfolios] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const navigate = useNavigate();

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

    const handleDelete = async (id, name) => {
        if (window.confirm(`Are you sure you want to delete portfolio "${name}"?`)) {
            try {
                await portfolioService.deletePortfolio(id);
                toast.success('Portfolio deleted successfully');
                fetchPortfolios();
            } catch (err) {
                toast.error('Failed to delete portfolio');
            }
        }
    };

    const getStatusBadge = (status) => {
        const variants = {
            'OPTIMIZED': 'success',
            'NOT_OPTIMIZED': 'warning',
            'OPTIMIZING': 'info',
            'UPGRADED_WITH_AI': 'primary'
        };
        return <Badge bg={variants[status] || 'secondary'}>{status || 'Unknown'}</Badge>;
    };

    if (loading) return <Container className="py-4">Loading portfolios...</Container>;

    return (
        <Container className="py-4">
            <div className="d-flex justify-content-between align-items-center mb-4">
                <h2>My Portfolios</h2>
                <div>
                    <Button as={Link} to="/portfolios/new" variant="primary" className="me-2">
                        Create New
                    </Button>
                    <Button as={Link} to="/portfolios/upload" variant="success">
                        Upload from Excel
                    </Button>
                </div>
            </div>

            {error && <Alert variant="danger">{error}</Alert>}

            {portfolios.length === 0 ? (
                <Card>
                    <Card.Body className="text-center py-5">
                        <h5>No portfolios yet</h5>
                        <p className="text-muted">Create your first portfolio to get started</p>
                        <div>
                            <Button as={Link} to="/portfolios/new" variant="primary" className="me-2">
                                Create Portfolio
                            </Button>
                            <Button as={Link} to="/portfolios/upload" variant="outline-success">
                                Upload from Excel
                            </Button>
                        </div>
                    </Card.Body>
                </Card>
            ) : (
                <Card>
                    <Card.Body>
                        <Table responsive hover>
                            <thead>
                            <tr>
                                <th>Name</th>
                                <th>Stocks</th>
                                <th>Total Value</th>
                                <th>Return</th>
                                <th>Status</th>
                                <th>AI Enhanced</th>
                                <th>Actions</th>
                            </tr>
                            </thead>
                            <tbody>
                            {portfolios.map(portfolio => (
                                <tr key={portfolio.id}>
                                    <td>
                                        <Link to={`/portfolios/${portfolio.id}`}>
                                            <strong>{portfolio.name}</strong>
                                        </Link>
                                        {portfolio.description && (
                                            <small className="d-block text-muted">{portfolio.description}</small>
                                        )}
                                    </td>
                                    <td>{portfolio.stocks?.length || 0}</td>
                                    <td>${(portfolio.totalValue || 0).toFixed(2)}</td>
                                    <td className={(parseFloat(portfolio.totalReturnPercentage) || 0) >= 0 ? 'text-success' : 'text-danger'}>
                                        {(parseFloat(portfolio.totalReturnPercentage) || 0).toFixed(2)}%
                                    </td>
                                    <td>{getStatusBadge(portfolio.optimizationStatus)}</td>
                                    <td>
                                        {portfolio.hasAiRecommendations ? (
                                            <Badge bg="info">Yes</Badge>
                                        ) : (
                                            <Badge bg="secondary">No</Badge>
                                        )}
                                    </td>
                                    <td>
                                        <Button
                                            variant="primary"
                                            size="sm"
                                            className="me-2"
                                            onClick={() => navigate(`/portfolios/${portfolio.id}`)}
                                        >
                                            View
                                        </Button>
                                        <Button
                                            variant="danger"
                                            size="sm"
                                            onClick={() => handleDelete(portfolio.id, portfolio.name)}
                                        >
                                            Delete
                                        </Button>
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </Table>
                    </Card.Body>
                </Card>
            )}
        </Container>
    );
};

export default PortfolioList;