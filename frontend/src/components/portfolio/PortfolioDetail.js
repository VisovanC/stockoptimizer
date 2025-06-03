import React, { useState, useEffect } from 'react';
import { Container, Card, Table, Button, Row, Col, Badge, Alert, ProgressBar, Modal, Form } from 'react-bootstrap';
import { useParams, useNavigate } from 'react-router-dom';
import { PieChart, Pie, Cell, ResponsiveContainer, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend } from 'recharts';
import portfolioService from '../../services/portfolio.service';
import { toast } from 'react-toastify';

const PortfolioDetail = () => {
    const { id } = useParams();
    const navigate = useNavigate();
    const [portfolio, setPortfolio] = useState(null);
    const [suggestions, setSuggestions] = useState(null);
    const [aiRecommendations, setAiRecommendations] = useState(null);
    const [loading, setLoading] = useState(true);
    const [optimizing, setOptimizing] = useState(false);
    const [showAIModal, setShowAIModal] = useState(false);
    const [riskTolerance, setRiskTolerance] = useState(0.5);
    const [expandUniverse, setExpandUniverse] = useState(false);

    useEffect(() => {
        fetchPortfolioData();
    }, [id]);

    const fetchPortfolioData = async () => {
        try {
            setLoading(true);
            const [portfolioData, suggestionsData] = await Promise.all([
                portfolioService.getPortfolio(id),
                portfolioService.getOptimizationSuggestions(id)
            ]);
            setPortfolio(portfolioData);
            setSuggestions(suggestionsData);
        } catch (err) {
            toast.error('Failed to load portfolio');
        } finally {
            setLoading(false);
        }
    };

    const handleOptimize = async () => {
        try {
            setOptimizing(true);
            await portfolioService.optimizePortfolio(id, riskTolerance);
            toast.success('Portfolio optimization started');
            fetchPortfolioData();
        } catch (err) {
            toast.error('Failed to optimize portfolio');
        } finally {
            setOptimizing(false);
        }
    };

    const handleGetAIRecommendations = async () => {
        try {
            const recommendations = await portfolioService.getAIRecommendations(id, riskTolerance, expandUniverse);
            setAiRecommendations(recommendations);
            setShowAIModal(true);
        } catch (err) {
            toast.error('Failed to get AI recommendations');
        }
    };

    const handleApplyAIRecommendations = async () => {
        try {
            await portfolioService.applyAIRecommendations(id, aiRecommendations.recommendedAllocations);
            toast.success('AI recommendations applied successfully');
            setShowAIModal(false);
            fetchPortfolioData();
        } catch (err) {
            toast.error('Failed to apply AI recommendations');
        }
    };

    const getStockChartData = () => {
        return portfolio.stocks.map(stock => ({
            name: stock.symbol,
            value: stock.currentPrice * stock.shares
        }));
    };

    const COLORS = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042', '#8884D8', '#82ca9d', '#ffc658'];

    if (loading) return <Container className="py-4">Loading portfolio...</Container>;
    if (!portfolio) return <Container className="py-4">Portfolio not found</Container>;

    return (
        <Container className="py-4">
            <Button variant="link" onClick={() => navigate('/portfolios')} className="mb-3">
                ← Back to Portfolios
            </Button>

            <Row className="mb-4">
                <Col>
                    <h2>{portfolio.name}</h2>
                    <p className="text-muted">{portfolio.description}</p>
                </Col>
                <Col xs="auto">
                    <Button
                        variant="primary"
                        onClick={handleOptimize}
                        disabled={optimizing}
                        className="me-2"
                    >
                        {optimizing ? 'Optimizing...' : 'Optimize'}
                    </Button>
                    <Button
                        variant="success"
                        onClick={handleGetAIRecommendations}
                    >
                        Get AI Recommendations
                    </Button>
                </Col>
            </Row>

            <Row className="mb-4">
                <Col md={3}>
                    <Card>
                        <Card.Body>
                            <h6 className="text-muted">Total Value</h6>
                            <h4>${(portfolio.totalValue || 0).toFixed(2)}</h4>
                        </Card.Body>
                    </Card>
                </Col>
                <Col md={3}>
                    <Card>
                        <Card.Body>
                            <h6 className="text-muted">Total Return</h6>
                            <h4 className={portfolio.totalReturnPercentage >= 0 ? 'text-success' : 'text-danger'}>
                                {portfolio.totalReturnPercentage?.toFixed(2)}%
                            </h4>
                        </Card.Body>
                    </Card>
                </Col>
                <Col md={3}>
                    <Card>
                        <Card.Body>
                            <h6 className="text-muted">Risk Score</h6>
                            <h4>{portfolio.riskScore?.toFixed(1) || 'N/A'}</h4>
                            <ProgressBar
                                now={portfolio.riskScore || 0}
                                variant={portfolio.riskScore < 30 ? 'success' : portfolio.riskScore < 70 ? 'warning' : 'danger'}
                            />
                        </Card.Body>
                    </Card>
                </Col>
                <Col md={3}>
                    <Card>
                        <Card.Body>
                            <h6 className="text-muted">Status</h6>
                            <h4>
                                <Badge bg={portfolio.optimizationStatus === 'OPTIMIZED' ? 'success' : 'warning'}>
                                    {portfolio.optimizationStatus}
                                </Badge>
                            </h4>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>

            <Row className="mb-4">
                <Col md={6}>
                    <Card>
                        <Card.Header>Portfolio Allocation</Card.Header>
                        <Card.Body>
                            <ResponsiveContainer width="100%" height={300}>
                                <PieChart>
                                    <Pie
                                        data={getStockChartData()}
                                        cx="50%"
                                        cy="50%"
                                        labelLine={false}
                                        label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}
                                        outerRadius={80}
                                        fill="#8884d8"
                                        dataKey="value"
                                    >
                                        {getStockChartData().map((entry, index) => (
                                            <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                                        ))}
                                    </Pie>
                                    <Tooltip formatter={(value) => `$${value.toFixed(2)}`} />
                                </PieChart>
                            </ResponsiveContainer>
                        </Card.Body>
                    </Card>
                </Col>
                <Col md={6}>
                    <Card>
                        <Card.Header>Optimization Suggestions</Card.Header>
                        <Card.Body>
                            {suggestions && (
                                <>
                                    <div className="mb-3">
                                        <strong>Diversification Score:</strong> {suggestions.diversificationScore?.toFixed(1)}/100
                                        <ProgressBar now={suggestions.diversificationScore} variant="info" className="mt-1" />
                                    </div>
                                    <div className="mb-3">
                                        <strong>Risk Score:</strong> {suggestions.riskScore?.toFixed(1)}/100
                                        <ProgressBar
                                            now={suggestions.riskScore}
                                            variant={suggestions.riskScore < 30 ? 'success' : suggestions.riskScore < 70 ? 'warning' : 'danger'}
                                            className="mt-1"
                                        />
                                    </div>
                                    <div>
                                        <strong>Rebalancing Needed:</strong> {' '}
                                        <Badge bg={suggestions.rebalancingNeeded ? 'warning' : 'success'}>
                                            {suggestions.rebalancingNeeded ? 'Yes' : 'No'}
                                        </Badge>
                                    </div>
                                </>
                            )}
                        </Card.Body>
                    </Card>
                </Col>
            </Row>

            <Card>
                <Card.Header>Holdings</Card.Header>
                <Card.Body>
                    <Table responsive hover>
                        <thead>
                        <tr>
                            <th>Symbol</th>
                            <th>Company</th>
                            <th>Shares</th>
                            <th>Entry Price</th>
                            <th>Current Price</th>
                            <th>Weight</th>
                            <th>Return</th>
                            <th>Suggestion</th>
                        </tr>
                        </thead>
                        <tbody>
                        {portfolio.stocks.map((stock, index) => {
                            const suggestion = suggestions?.stocks?.find(s => s.symbol === stock.symbol);
                            return (
                                <tr key={index}>
                                    <td><strong>{stock.symbol}</strong></td>
                                    <td>{stock.companyName}</td>
                                    <td>{stock.shares}</td>
                                    <td>${stock.entryPrice.toFixed(2)}</td>
                                    <td>${stock.currentPrice?.toFixed(2) || 'N/A'}</td>
                                    <td>{stock.weight?.toFixed(1)}%</td>
                                    <td className={stock.returnPercentage >= 0 ? 'text-success' : 'text-danger'}>
                                        {stock.returnPercentage?.toFixed(2)}%
                                    </td>
                                    <td>
                                        {suggestion && (
                                            <Badge bg={
                                                suggestion.suggestedAction === 'BUY' ? 'success' :
                                                    suggestion.suggestedAction === 'SELL' ? 'danger' :
                                                        'secondary'
                                            }>
                                                {suggestion.suggestedAction}
                                            </Badge>
                                        )}
                                    </td>
                                </tr>
                            );
                        })}
                        </tbody>
                    </Table>
                </Card.Body>
            </Card>

            {/* AI Recommendations Modal */}
            <Modal show={showAIModal} onHide={() => setShowAIModal(false)} size="lg">
                <Modal.Header closeButton>
                    <Modal.Title>AI Portfolio Recommendations</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    {aiRecommendations && (
                        <>
                            <Alert variant="info">
                                <strong>AI Confidence Score:</strong> {aiRecommendations.aiConfidenceScore}%
                            </Alert>

                            <h5>Recommended Allocations</h5>
                            <Table striped bordered>
                                <thead>
                                <tr>
                                    <th>Symbol</th>
                                    <th>Current %</th>
                                    <th>Recommended %</th>
                                    <th>Change</th>
                                </tr>
                                </thead>
                                <tbody>
                                {Object.entries(aiRecommendations.recommendedAllocations).map(([symbol, allocation]) => {
                                    const currentStock = portfolio.stocks.find(s => s.symbol === symbol);
                                    const currentWeight = currentStock?.weight || 0;
                                    const change = (allocation * 100) - currentWeight;
                                    return (
                                        <tr key={symbol}>
                                            <td>{symbol}</td>
                                            <td>{currentWeight.toFixed(1)}%</td>
                                            <td>{(allocation * 100).toFixed(1)}%</td>
                                            <td className={change >= 0 ? 'text-success' : 'text-danger'}>
                                                {change >= 0 ? '+' : ''}{change.toFixed(1)}%
                                            </td>
                                        </tr>
                                    );
                                })}
                                </tbody>
                            </Table>

                            <h5>Expected Performance</h5>
                            <Row>
                                <Col md={4}>
                                    <Card>
                                        <Card.Body>
                                            <h6>Expected Return</h6>
                                            <h4 className="text-success">
                                                {aiRecommendations.expectedPerformance?.expectedAnnualReturn?.toFixed(2)}%
                                            </h4>
                                        </Card.Body>
                                    </Card>
                                </Col>
                                <Col md={4}>
                                    <Card>
                                        <Card.Body>
                                            <h6>Expected Volatility</h6>
                                            <h4>{aiRecommendations.expectedPerformance?.expectedAnnualVolatility?.toFixed(2)}%</h4>
                                        </Card.Body>
                                    </Card>
                                </Col>
                                <Col md={4}>
                                    <Card>
                                        <Card.Body>
                                            <h6>Sharpe Ratio</h6>
                                            <h4>{aiRecommendations.expectedPerformance?.sharpeRatio?.toFixed(2)}</h4>
                                        </Card.Body>
                                    </Card>
                                </Col>
                            </Row>
                        </>
                    )}
                </Modal.Body>
                <Modal.Footer>
                    <Button variant="secondary" onClick={() => setShowAIModal(false)}>
                        Cancel
                    </Button>
                    <Button variant="primary" onClick={handleApplyAIRecommendations}>
                        Apply Recommendations
                    </Button>
                </Modal.Footer>
            </Modal>
        </Container>
    );
};

export default PortfolioDetail;