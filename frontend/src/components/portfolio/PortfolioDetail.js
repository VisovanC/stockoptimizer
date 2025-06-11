import React, { useState, useEffect } from 'react';
import { Container, Card, Table, Button, Row, Col, Badge, Alert, ProgressBar, Modal, Form, Spinner } from 'react-bootstrap';
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

    // ML Training states
    const [mlStatus, setMlStatus] = useState(null);
    const [training, setTraining] = useState(false);
    const [trainingProgress, setTrainingProgress] = useState(null);

    // Safe number formatting functions
    const formatCurrency = (value) => {
        const numValue = parseFloat(value) || 0;
        return numValue.toFixed(2);
    };

    const formatPercentage = (value) => {
        const numValue = parseFloat(value) || 0;
        return numValue.toFixed(2);
    };

    const formatNumber = (value, decimals = 2) => {
        const numValue = parseFloat(value) || 0;
        return numValue.toFixed(decimals);
    };

    useEffect(() => {
        fetchPortfolioData();
        fetchMLStatus();
    }, [id]);

    useEffect(() => {
        // Poll for training progress if training is in progress
        let interval;
        if (training && trainingProgress && trainingProgress.status === 'in_progress') {
            interval = setInterval(() => {
                fetchMLStatus();
            }, 2000); // Check every 2 seconds
        }
        return () => clearInterval(interval);
    }, [training, trainingProgress]);

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

    const fetchMLStatus = async () => {
        try {
            const status = await portfolioService.getMLStatus(id);
            setMlStatus(status);

            if (status.trainingInProgress) {
                setTraining(true);
                setTrainingProgress(status.progress);
            } else {
                setTraining(false);
                if (trainingProgress && trainingProgress.status === 'completed') {
                    toast.success('ML training completed successfully!');
                    setTrainingProgress(null);
                }
            }
        } catch (err) {
            console.error('Failed to fetch ML status:', err);
        }
    };

    const handleTrainModels = async () => {
        try {
            setTraining(true);
            const useSampleData = window.confirm(
                'Would you like to use sample data for training?\n\n' +
                'Click OK for sample data (faster, for testing)\n' +
                'Click Cancel for real market data (slower, more accurate)'
            );

            const response = await portfolioService.trainMLModels(id, useSampleData);

            if (response.status === 'training_started') {
                toast.info(`ML training started for ${response.totalStocks} stocks`);
                // Start polling for progress
                fetchMLStatus();
            } else {
                toast.error('Failed to start ML training');
                setTraining(false);
            }
        } catch (err) {
            toast.error('Failed to start ML training: ' + err.message);
            setTraining(false);
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
        // Check if models are trained
        if (!mlStatus || mlStatus.modelsReady < mlStatus.totalStocks) {
            toast.error('Please train ML models first before getting AI recommendations');
            return;
        }

        try {
            const recommendations = await portfolioService.getPersonalizedRecommendations(
                id, riskTolerance, expandUniverse
            );
            setAiRecommendations(recommendations);
            setShowAIModal(true);
        } catch (err) {
            toast.error('Failed to get AI recommendations: ' + err.message);
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
            value: (parseFloat(stock.currentPrice) || 0) * (parseInt(stock.shares) || 0)
        }));
    };

    const COLORS = ['#FF6500', '#1E3E62', '#0B192C', '#FF8642', '#2E5E92', '#82ca9d', '#ffc658'];

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
                        variant="warning"
                        onClick={handleTrainModels}
                        disabled={training}
                        className="me-2"
                    >
                        {training ? (
                            <>
                                <Spinner animation="border" size="sm" className="me-2" />
                                Training ML Models...
                            </>
                        ) : (
                            'Train ML Models'
                        )}
                    </Button>
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
                        disabled={!mlStatus || mlStatus.modelsReady < mlStatus.totalStocks}
                    >
                        Get AI Recommendations
                    </Button>
                </Col>
            </Row>

            {/* ML Training Progress */}
            {trainingProgress && trainingProgress.status === 'in_progress' && (
                <Alert variant="info" className="mb-4">
                    <h6>ML Training Progress</h6>
                    <ProgressBar
                        now={trainingProgress.percentComplete}
                        label={`${Math.round(trainingProgress.percentComplete)}%`}
                        animated
                        striped
                    />
                    <small className="d-block mt-2">
                        {trainingProgress.currentStep} ({trainingProgress.completedStocks}/{trainingProgress.totalStocks} stocks)
                    </small>
                    {trainingProgress.trainedSymbols && trainingProgress.trainedSymbols.length > 0 && (
                        <small className="d-block mt-1">
                            Completed: {trainingProgress.trainedSymbols.join(', ')}
                        </small>
                    )}
                </Alert>
            )}

            {/* ML Status Card */}
            <Card className="mb-4">
                <Card.Header>
                    <div className="d-flex justify-content-between align-items-center">
                        <span>ML Model Status</span>
                        <Button
                            variant="link"
                            size="sm"
                            onClick={fetchMLStatus}
                            disabled={training}
                        >
                            Refresh
                        </Button>
                    </div>
                </Card.Header>
                <Card.Body>
                    {mlStatus ? (
                        <Row>
                            <Col md={6}>
                                <p><strong>Models Trained:</strong> {mlStatus.modelsReady}/{mlStatus.totalStocks}</p>
                                <ProgressBar
                                    now={(mlStatus.modelsReady / mlStatus.totalStocks) * 100}
                                    variant={mlStatus.modelsReady === mlStatus.totalStocks ? 'success' : 'warning'}
                                />
                                {mlStatus.modelsReady === mlStatus.totalStocks && (
                                    <Alert variant="success" className="mt-2">
                                        All models trained! AI recommendations are available.
                                    </Alert>
                                )}
                            </Col>
                            <Col md={6}>
                                <p><strong>Stock Models:</strong></p>
                                {Object.entries(mlStatus.models).map(([symbol, modelInfo]) => (
                                    <Badge
                                        key={symbol}
                                        bg={modelInfo.exists ? 'success' : 'secondary'}
                                        className="me-1"
                                        title={modelInfo.exists ?
                                            `Trained on ${modelInfo.trainingDate}` :
                                            'Not trained yet'
                                        }
                                    >
                                        {symbol} {modelInfo.exists ? '(Ready)' : '(Not trained)'}
                                    </Badge>
                                ))}
                            </Col>
                        </Row>
                    ) : (
                        <p className="text-muted">Loading ML status...</p>
                    )}
                </Card.Body>
            </Card>

            <Row className="mb-4">
                <Col md={3}>
                    <Card>
                        <Card.Body>
                            <h6 className="text-muted">Total Value</h6>
                            <h4>${formatCurrency(portfolio.totalValue)}</h4>
                        </Card.Body>
                    </Card>
                </Col>
                <Col md={3}>
                    <Card>
                        <Card.Body>
                            <h6 className="text-muted">Total Return</h6>
                            <h4 className={parseFloat(portfolio.totalReturnPercentage || 0) >= 0 ? 'text-success' : 'text-danger'}>
                                {formatPercentage(portfolio.totalReturnPercentage)}%
                            </h4>
                        </Card.Body>
                    </Card>
                </Col>
                <Col md={3}>
                    <Card>
                        <Card.Body>
                            <h6 className="text-muted">Risk Score</h6>
                            <h4>{formatNumber(portfolio.riskScore, 1) || 'N/A'}</h4>
                            <ProgressBar
                                now={parseFloat(portfolio.riskScore) || 0}
                                variant={parseFloat(portfolio.riskScore) < 30 ? 'success' : parseFloat(portfolio.riskScore) < 70 ? 'warning' : 'danger'}
                            />
                        </Card.Body>
                    </Card>
                </Col>
                <Col md={3}>
                    <Card>
                        <Card.Body>
                            <h6 className="text-muted">Status</h6>
                            <h4>
                                <Badge bg={
                                    portfolio.optimizationStatus === 'OPTIMIZED' ? 'success' :
                                        portfolio.optimizationStatus === 'UPGRADED_WITH_AI' ? 'primary' :
                                            'warning'
                                }>
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
                                    <Tooltip formatter={(value) => `$${formatCurrency(value)}`} />
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
                                        <strong>Diversification Score:</strong> {formatNumber(suggestions.diversificationScore, 1)}/100
                                        <ProgressBar now={parseFloat(suggestions.diversificationScore) || 0} variant="info" className="mt-1" />
                                    </div>
                                    <div className="mb-3">
                                        <strong>Risk Score:</strong> {formatNumber(suggestions.riskScore, 1)}/100
                                        <ProgressBar
                                            now={parseFloat(suggestions.riskScore) || 0}
                                            variant={parseFloat(suggestions.riskScore) < 30 ? 'success' : parseFloat(suggestions.riskScore) < 70 ? 'warning' : 'danger'}
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
                            const returnPercentage = parseFloat(stock.returnPercentage) || 0;
                            return (
                                <tr key={index}>
                                    <td><strong>{stock.symbol}</strong></td>
                                    <td>{stock.companyName}</td>
                                    <td>{stock.shares}</td>
                                    <td>${formatCurrency(stock.entryPrice)}</td>
                                    <td>${formatCurrency(stock.currentPrice) || 'N/A'}</td>
                                    <td>{formatNumber(stock.weight, 1)}%</td>
                                    <td className={returnPercentage >= 0 ? 'text-success' : 'text-danger'}>
                                        {formatPercentage(stock.returnPercentage)}%
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
                    {/* Risk Tolerance Slider */}
                    <Form.Group className="mb-4">
                        <Form.Label>
                            Risk Tolerance: <strong>{riskTolerance < 0.33 ? 'Conservative' : riskTolerance < 0.67 ? 'Moderate' : 'Aggressive'}</strong>
                        </Form.Label>
                        <Form.Range
                            value={riskTolerance}
                            onChange={(e) => setRiskTolerance(parseFloat(e.target.value))}
                            min="0"
                            max="1"
                            step="0.1"
                        />
                        <div className="d-flex justify-content-between text-muted small">
                            <span>Conservative</span>
                            <span>Moderate</span>
                            <span>Aggressive</span>
                        </div>
                    </Form.Group>

                    <Form.Group className="mb-4">
                        <Form.Check
                            type="checkbox"
                            label="Expand universe (consider new stocks)"
                            checked={expandUniverse}
                            onChange={(e) => setExpandUniverse(e.target.checked)}
                        />
                    </Form.Group>

                    {aiRecommendations && (
                        <>
                            <Alert variant="info">
                                <strong>AI Confidence Score:</strong> {formatNumber(aiRecommendations.aiConfidenceScore, 1)}%
                            </Alert>

                            <h5>Recommended Allocations</h5>
                            <Table striped bordered size="sm">
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
                                    const currentWeight = parseFloat(currentStock?.weight) || 0;
                                    const recommendedWeight = parseFloat(allocation) * 100;
                                    const change = recommendedWeight - currentWeight;
                                    return (
                                        <tr key={symbol}>
                                            <td>{symbol}</td>
                                            <td>{formatNumber(currentWeight, 1)}%</td>
                                            <td>{formatNumber(recommendedWeight, 1)}%</td>
                                            <td className={change >= 0 ? 'text-success' : 'text-danger'}>
                                                {change >= 0 ? '+' : ''}{formatNumber(change, 1)}%
                                            </td>
                                        </tr>
                                    );
                                })}
                                </tbody>
                            </Table>

                            <h5 className="mt-4">Expected Performance</h5>
                            <Row>
                                <Col md={4}>
                                    <Card>
                                        <Card.Body className="text-center">
                                            <h6>Expected Return</h6>
                                            <h4 className="text-success">
                                                {formatPercentage(aiRecommendations.expectedPerformance?.expectedAnnualReturn)}%
                                            </h4>
                                        </Card.Body>
                                    </Card>
                                </Col>
                                <Col md={4}>
                                    <Card>
                                        <Card.Body className="text-center">
                                            <h6>Expected Volatility</h6>
                                            <h4>{formatPercentage(aiRecommendations.expectedPerformance?.expectedAnnualVolatility)}%</h4>
                                        </Card.Body>
                                    </Card>
                                </Col>
                                <Col md={4}>
                                    <Card>
                                        <Card.Body className="text-center">
                                            <h6>Sharpe Ratio</h6>
                                            <h4>{formatNumber(aiRecommendations.expectedPerformance?.sharpeRatio)}</h4>
                                        </Card.Body>
                                    </Card>
                                </Col>
                            </Row>

                            {aiRecommendations.recommendedActions && (
                                <>
                                    <h5 className="mt-4">Recommended Actions</h5>
                                    <Table size="sm">
                                        <thead>
                                        <tr>
                                            <th>Action</th>
                                            <th>Symbol</th>
                                            <th>Shares</th>
                                            <th>Reason</th>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        {aiRecommendations.recommendedActions
                                            .filter(action => action.action !== 'HOLD')
                                            .map((action, idx) => (
                                                <tr key={idx}>
                                                    <td>
                                                        <Badge bg={
                                                            action.action === 'BUY' ? 'success' :
                                                                action.action === 'SELL' ? 'danger' : 'secondary'
                                                        }>
                                                            {action.action}
                                                        </Badge>
                                                    </td>
                                                    <td>{action.symbol}</td>
                                                    <td>
                                                        {action.action === 'BUY' ?
                                                            `Buy ${action.targetShares - action.currentShares}` :
                                                            `Sell ${action.currentShares - action.targetShares}`
                                                        }
                                                    </td>
                                                    <td>{action.reason}</td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </Table>
                                </>
                            )}
                        </>
                    )}

                    {!aiRecommendations && (
                        <div className="text-center py-4">
                            <Button
                                variant="primary"
                                onClick={handleGetAIRecommendations}
                                disabled={!mlStatus || mlStatus.modelsReady < mlStatus.totalStocks}
                            >
                                Generate AI Recommendations
                            </Button>
                        </div>
                    )}
                </Modal.Body>
                <Modal.Footer>
                    <Button variant="secondary" onClick={() => setShowAIModal(false)}>
                        Close
                    </Button>
                    {aiRecommendations && (
                        <Button variant="primary" onClick={handleApplyAIRecommendations}>
                            Apply Recommendations
                        </Button>
                    )}
                </Modal.Footer>
            </Modal>
        </Container>
    );
};

export default PortfolioDetail;