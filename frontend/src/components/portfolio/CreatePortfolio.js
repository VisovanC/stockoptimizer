import React, { useState, useEffect } from 'react';
import { Container, Card, Form, Button, Table, Row, Col, Alert, InputGroup, ListGroup } from 'react-bootstrap';
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
    const [searchResults, setSearchResults] = useState([]);
    const [searching, setSearching] = useState(false);
    const [showSearchResults, setShowSearchResults] = useState(false);

    useEffect(() => {
        // Hide search results when clicking outside
        const handleClickOutside = () => {
            setShowSearchResults(false);
        };
        document.addEventListener('click', handleClickOutside);
        return () => document.removeEventListener('click', handleClickOutside);
    }, []);

    const handlePortfolioChange = (e) => {
        setPortfolioData({
            ...portfolioData,
            [e.target.name]: e.target.value
        });
    };

    const handleStockChange = (e) => {
        const { name, value } = e.target;

        setCurrentStock({
            ...currentStock,
            [name]: value
        });

        // Search for stocks when typing symbol
        if (name === 'symbol' && value.length > 0) {
            setSearching(true);
            setShowSearchResults(true);
            searchStocks(value);
        } else if (name === 'symbol' && value.length === 0) {
            setSearchResults([]);
            setShowSearchResults(false);
        }
    };

    const searchStocks = async (query) => {
        try {
            const results = await portfolioService.searchStocks(query);
            setSearchResults(results.slice(0, 10)); // Limit to 10 results
        } catch (error) {
            console.error('Error searching stocks:', error);
            setSearchResults([]);
        } finally {
            setSearching(false);
        }
    };

    const selectStock = (stock) => {
        setCurrentStock({
            ...currentStock,
            symbol: stock.symbol,
            companyName: stock.name
        });
        setSearchResults([]);
        setShowSearchResults(false);
    };

    const validateStock = async () => {
        if (!currentStock.symbol) {
            setError('Please enter a stock symbol');
            return false;
        }

        // Try to validate the symbol
        try {
            const validation = await portfolioService.validateSymbol(currentStock.symbol);
            if (!validation.valid) {
                setError(`Invalid stock symbol: ${currentStock.symbol}`);
                return false;
            }

            // Set company name if not already set
            if (!currentStock.companyName && validation.name) {
                setCurrentStock({
                    ...currentStock,
                    companyName: validation.name
                });
            }

            return true;
        } catch (error) {
            // Allow the symbol anyway - data will be fetched when needed
            return true;
        }
    };

    const addStock = async () => {
        if (!currentStock.symbol || !currentStock.shares || !currentStock.entryPrice) {
            setError('Please fill in all stock fields');
            return;
        }

        // Validate the stock symbol
        const isValid = await validateStock();
        if (!isValid) {
            return;
        }

        const newStock = {
            symbol: currentStock.symbol.toUpperCase(),
            companyName: currentStock.companyName || currentStock.symbol.toUpperCase() + ' Corp.',
            shares: parseInt(currentStock.shares),
            entryPrice: parseFloat(currentStock.entryPrice)
        };

        // Check if stock already exists in portfolio
        if (portfolioData.stocks.some(s => s.symbol === newStock.symbol)) {
            setError('Stock already exists in portfolio');
            return;
        }

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

        toast.success(`Added ${newStock.symbol} to portfolio`);
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
            // Ensure data exists for all stocks
            const dataCollectionPromises = portfolioData.stocks.map(stock =>
                portfolioService.ensureStockData(stock.symbol)
            );

            await Promise.all(dataCollectionPromises);

            // Create the portfolio
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
                                        <Form.Group className="mb-3 position-relative">
                                            <Form.Label>Symbol *</Form.Label>
                                            <Form.Control
                                                type="text"
                                                name="symbol"
                                                value={currentStock.symbol}
                                                onChange={handleStockChange}
                                                placeholder="AAPL"
                                                style={{ textTransform: 'uppercase' }}
                                                onClick={(e) => e.stopPropagation()}
                                            />
                                            {showSearchResults && searchResults.length > 0 && (
                                                <ListGroup
                                                    className="position-absolute w-100"
                                                    style={{
                                                        top: '100%',
                                                        zIndex: 1000,
                                                        maxHeight: '200px',
                                                        overflowY: 'auto',
                                                        boxShadow: '0 4px 6px rgba(0,0,0,0.1)'
                                                    }}
                                                    onClick={(e) => e.stopPropagation()}
                                                >
                                                    {searchResults.map((result, idx) => (
                                                        <ListGroup.Item
                                                            key={idx}
                                                            action
                                                            onClick={() => selectStock(result)}
                                                            style={{ cursor: 'pointer' }}
                                                        >
                                                            <strong>{result.symbol}</strong> - {result.name}
                                                        </ListGroup.Item>
                                                    ))}
                                                </ListGroup>
                                            )}
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
                                <small className="text-muted ms-3">
                                    You can add any stock symbol traded on major exchanges
                                </small>
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
                                <li>You can add any stock symbol from major exchanges</li>
                                <li>Stock data will be automatically fetched</li>
                                <li>If Yahoo Finance is unavailable, sample data will be used</li>
                                <li>Start typing a symbol to search for stocks</li>
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