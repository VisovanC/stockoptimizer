import React, { useState } from 'react';
import { Container, Card, Form, Button, Table, Row, Col, Alert } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import portfolioService from '../../services/portfolio.service';
import { toast } from 'react-toastify';

const CreatePortfolio = () => {
    const navigate = useNavigate();
    const [portfolioData, setPortfolioData] = useState({
        name: '',
        description: '',
        stocks: []
    });
    const [currentStock, setCurrentStock] = useState({
        symbol: '',
        companyName: '',
        shares: '',
        entryPrice: ''
    });
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const handlePortfolioChange = (e) => {
        setPortfolioData({
            ...portfolioData,
            [e.target.name]: e.target.value
        });
    };

    const handleStockChange = (e) => {
        setCurrentStock({
            ...currentStock,
            [e.target.name]: e.target.value
        });
    };

    const addStock = () => {
        if (!currentStock.symbol || !currentStock.shares || !currentStock.entryPrice) {
            setError('Please fill in all stock fields');
            return;
        }

        const newStock = {
            symbol: currentStock.symbol.toUpperCase(),
            companyName: currentStock.companyName,
            shares: parseInt(currentStock.shares),
            entryPrice: parseFloat(currentStock.entryPrice)
        };

        setPortfolioData({
            ...portfolioData,
            stocks: [...portfolioData.stocks, newStock]
        });

        // Reset current stock form
        setCurrentStock({
            symbol: '',
            companyName: '',
            shares: '',
            entryPrice: ''
        });
        setError('');
    };

    const removeStock = (index) => {
        const updatedStocks = portfolioData.stocks.filter((_, i) => i !== index);
        setPortfolioData({
            ...portfolioData,
            stocks: updatedStocks
        });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        if (!portfolioData.name) {
            setError('Please enter a portfolio name');
            return;
        }

        if (portfolioData.stocks.length === 0) {
            setError('Please add at least one stock to the portfolio');
            return;
        }

        setLoading(true);
        try {
            await portfolioService.createPortfolio(portfolioData);
            toast.success('Portfolio created successfully!');
            navigate('/portfolios');
        } catch (err) {
            setError(err.response?.data?.message || 'Failed to create portfolio');
        } finally {
            setLoading(false);
        }
    };

    const calculateTotalValue = () => {
        return portfolioData.stocks.reduce((total, stock) =>
            total + (stock.shares * stock.entryPrice), 0
        ).toFixed(2);
    };

    return (
        <Container className="py-4">
            <h2 className="mb-4">Create New Portfolio</h2>

            {error && <Alert variant="danger" dismissible onClose={() => setError('')}>{error}</Alert>}

            <Row>
                <Col md={8}>
                    <Card className="mb-4">
                        <Card.Header>Portfolio Information</Card.Header>
                        <Card.Body>
                            <Form>
                                <Form.Group className="mb-3">
                                    <Form.Label>Portfolio Name *</Form.Label>
                                    <Form.Control
                                        type="text"
                                        name="name"
                                        value={portfolioData.name}
                                        onChange={handlePortfolioChange}
                                        placeholder="My Investment Portfolio"
                                        required
                                    />
                                </Form.Group>

                                <Form.Group className="mb-3">
                                    <Form.Label>Description</Form.Label>
                                    <Form.Control
                                        as="textarea"
                                        rows={3}
                                        name="description"
                                        value={portfolioData.description}
                                        onChange={handlePortfolioChange}
                                        placeholder="Portfolio description (optional)"
                                    />
                                </Form.Group>
                            </Form>
                        </Card.Body>
                    </Card>

                    <Card className="mb-4">
                        <Card.Header>Add Stocks</Card.Header>
                        <Card.Body>
                            <Form>
                                <Row>
                                    <Col md={3}>
                                        <Form.Group className="mb-3">
                                            <Form.Label>Symbol *</Form.Label>
                                            <Form.Control
                                                type="text"
                                                name="symbol"
                                                value={currentStock.symbol}
                                                onChange={handleStockChange}
                                                placeholder="AAPL"
                                                style={{ textTransform: 'uppercase' }}
                                            />
                                        </Form.Group>
                                    </Col>
                                    <Col md={3}>
                                        <Form.Group className="mb-3">
                                            <Form.Label>Company Name</Form.Label>
                                            <Form.Control
                                                type="text"
                                                name="companyName"
                                                value={currentStock.companyName}
                                                onChange={handleStockChange}
                                                placeholder="Apple Inc."
                                            />
                                        </Form.Group>
                                    </Col>
                                    <Col md={3}>
                                        <Form.Group className="mb-3">
                                            <Form.Label>Shares *</Form.Label>
                                            <Form.Control
                                                type="number"
                                                name="shares"
                                                value={currentStock.shares}
                                                onChange={handleStockChange}
                                                placeholder="100"
                                                min="1"
                                            />
                                        </Form.Group>
                                    </Col>
                                    <Col md={3}>
                                        <Form.Group className="mb-3">
                                            <Form.Label>Entry Price *</Form.Label>
                                            <Form.Control
                                                type="number"
                                                name="entryPrice"
                                                value={currentStock.entryPrice}
                                                onChange={handleStockChange}
                                                placeholder="150.00"
                                                step="0.01"
                                                min="0.01"
                                            />
                                        </Form.Group>
                                    </Col>
                                </Row>
                                <Button variant="success" onClick={addStock}>
                                    Add Stock
                                </Button>
                            </Form>
                        </Card.Body>
                    </Card>

                    {portfolioData.stocks.length > 0 && (
                        <Card>
                            <Card.Header>Portfolio Holdings</Card.Header>
                            <Card.Body>
                                <Table striped bordered hover>
                                    <thead>
                                    <tr>
                                        <th>Symbol</th>
                                        <th>Company Name</th>
                                        <th>Shares</th>
                                        <th>Entry Price</th>
                                        <th>Total Value</th>
                                        <th>Actions</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {portfolioData.stocks.map((stock, index) => (
                                        <tr key={index}>
                                            <td>{stock.symbol}</td>
                                            <td>{stock.companyName || '-'}</td>
                                            <td>{stock.shares}</td>
                                            <td>${stock.entryPrice.toFixed(2)}</td>
                                            <td>${(stock.shares * stock.entryPrice).toFixed(2)}</td>
                                            <td>
                                                <Button
                                                    variant="danger"
                                                    size="sm"
                                                    onClick={() => removeStock(index)}
                                                >
                                                    Remove
                                                </Button>
                                            </td>
                                        </tr>
                                    ))}
                                    </tbody>
                                    <tfoot>
                                    <tr>
                                        <th colSpan="4">Total Portfolio Value</th>
                                        <th>${calculateTotalValue()}</th>
                                        <th></th>
                                    </tr>
                                    </tfoot>
                                </Table>
                            </Card.Body>
                        </Card>
                    )}
                </Col>

                <Col md={4}>
                    <Card className="position-sticky" style={{ top: '20px' }}>
                        <Card.Header>Portfolio Summary</Card.Header>
                        <Card.Body>
                            <p><strong>Name:</strong> {portfolioData.name || 'Not set'}</p>
                            <p><strong>Number of Stocks:</strong> {portfolioData.stocks.length}</p>
                            <p><strong>Total Value:</strong> ${calculateTotalValue()}</p>

                            <hr />

                            <div className="d-grid gap-2">
                                <Button
                                    variant="primary"
                                    size="lg"
                                    onClick={handleSubmit}
                                    disabled={loading || portfolioData.stocks.length === 0}
                                >
                                    {loading ? 'Creating Portfolio...' : 'Create Portfolio'}
                                </Button>
                                <Button
                                    variant="outline-secondary"
                                    onClick={() => navigate('/portfolios')}
                                >
                                    Cancel
                                </Button>
                            </div>
                        </Card.Body>
                    </Card>

                    <Card className="mt-3">
                        <Card.Body>
                            <h6>Quick Tips:</h6>
                            <ul className="small">
                                <li>Enter stock symbols in uppercase (e.g., AAPL, MSFT)</li>
                                <li>You can add multiple stocks to diversify your portfolio</li>
                                <li>Entry price is the price at which you bought the stock</li>
                                <li>You can upload an Excel file instead if you have many stocks</li>
                            </ul>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
        </Container>
    );
};

export default CreatePortfolio;