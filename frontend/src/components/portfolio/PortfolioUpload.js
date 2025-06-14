import React, { useState, useCallback } from 'react';
import { useDropzone } from 'react-dropzone';
import { Container, Card, Button, Alert, Table, Form, Row, Col } from 'react-bootstrap';
import { Link, useNavigate } from 'react-router-dom';
import portfolioService from '../../services/portfolio.service';
import { toast } from 'react-toastify';

const PortfolioUpload = () => {
    const [uploadedData, setUploadedData] = useState(null);
    const [portfolioName, setPortfolioName] = useState('');
    const [portfolioDescription, setPortfolioDescription] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    const onDrop = useCallback(async (acceptedFiles) => {
        const file = acceptedFiles[0];
        if (!file) return;

        try {
            setError('');
            const data = await portfolioService.parseExcelFile(file);
            setUploadedData(data);
            setPortfolioName(data.name);
            setPortfolioDescription(data.description);
        } catch (err) {
            setError(err.message);
        }
    }, []);

    const { getRootProps, getInputProps, isDragActive } = useDropzone({
        onDrop,
        accept: {
            'application/vnd.ms-excel': ['.xls'],
            'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': ['.xlsx']
        },
        maxFiles: 1
    });

    const handleSubmit = async () => {
        if (!uploadedData) return;

        if (!portfolioName.trim()) {
            setError('Please enter a portfolio name');
            return;
        }

        setLoading(true);
        try {
            const portfolioData = {
                ...uploadedData,
                name: portfolioName,
                description: portfolioDescription || uploadedData.description
            };

            await portfolioService.createPortfolio(portfolioData);
            toast.success('Portfolio created successfully!');
            navigate('/portfolios');
        } catch (err) {
            setError(err.response?.data?.message || 'Failed to create portfolio');
        } finally {
            setLoading(false);
        }
    };

    const handleEditStock = (index, field, value) => {
        const updatedStocks = [...uploadedData.stocks];
        updatedStocks[index] = {
            ...updatedStocks[index],
            [field]: field === 'shares' ? parseInt(value) || 0 :
                field === 'entryPrice' ? parseFloat(value) || 0 :
                    value
        };
        setUploadedData({
            ...uploadedData,
            stocks: updatedStocks
        });
    };

    const handleRemoveStock = (index) => {
        const updatedStocks = uploadedData.stocks.filter((_, i) => i !== index);
        setUploadedData({
            ...uploadedData,
            stocks: updatedStocks
        });
    };

    const handleAddStock = () => {
        const newStock = {
            symbol: '',
            companyName: '',
            shares: 0,
            entryPrice: 0,
            entryDate: new Date().toISOString()
        };
        setUploadedData({
            ...uploadedData,
            stocks: [...uploadedData.stocks, newStock]
        });
    };

    const downloadTemplate = () => {
        const templateData = [
            ['Symbol', 'Company Name', 'Shares', 'Entry Price', 'Entry Date'],
            ['AAPL', 'Apple Inc.', '100', '150.00', '2024-01-15'],
            ['MSFT', 'Microsoft Corporation', '50', '300.00', '2024-02-01'],
            ['GOOGL', 'Alphabet Inc.', '20', '2500.00', '2024-03-01']
        ];

        // Create CSV content
        const csvContent = templateData.map(row => row.join(',')).join('\n');
        const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
        const link = document.createElement('a');
        const url = URL.createObjectURL(blob);
        link.setAttribute('href', url);
        link.setAttribute('download', 'portfolio_template.csv');
        link.style.visibility = 'hidden';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    };

    const calculateTotalValue = () => {
        if (!uploadedData || !uploadedData.stocks) return 0;
        return uploadedData.stocks.reduce((total, stock) =>
            total + (stock.shares * stock.entryPrice), 0
        ).toFixed(2);
    };

    return (
        <Container className="py-4">
            <h2 className="mb-4">Upload Portfolio from Excel</h2>

            <Row className="mb-4">
                <Col>
                    <Card>
                        <Card.Body>
                            <div className="d-flex justify-content-between align-items-center">
                                <div>
                                    <h5>Need a template?</h5>
                                    <p className="mb-0">Download our Excel template to see the required format</p>
                                </div>
                                <Button variant="outline-primary" onClick={downloadTemplate}>
                                    Download Template
                                </Button>
                            </div>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>

            <Card className="mb-4">
                <Card.Body>
                    <div {...getRootProps()} className="border-dashed border-2 p-5 text-center"
                         style={{
                             cursor: 'pointer',
                             borderStyle: 'dashed',
                             backgroundColor: isDragActive ? '#f0f8ff' : 'transparent'
                         }}>
                        <input {...getInputProps()} />
                        {isDragActive ? (
                            <div>
                                <h4>Drop the Excel file here...</h4>
                            </div>
                        ) : (
                            <div>
                                <h4 className="mb-3">📊 Upload Your Portfolio</h4>
                                <p className="mb-2">Drag and drop an Excel file here, or click to select</p>
                                <p className="text-muted small">
                                    Supported formats: .xls, .xlsx<br/>
                                    Required columns: Symbol/Ticker, Shares/Quantity, Entry Price/Purchase Price<br/>
                                    Optional columns: Company Name, Entry Date/Purchase Date
                                </p>
                            </div>
                        )}
                    </div>
                </Card.Body>
            </Card>

            {error && (
                <Alert variant="danger" dismissible onClose={() => setError('')}>
                    {error}
                </Alert>
            )}

            {uploadedData && (
                <Card>
                    <Card.Header>
                        <h5>Portfolio Details</h5>
                    </Card.Header>
                    <Card.Body>
                        <Form className="mb-4">
                            <Row>
                                <Col md={6}>
                                    <Form.Group className="mb-3">
                                        <Form.Label>Portfolio Name *</Form.Label>
                                        <Form.Control
                                            type="text"
                                            value={portfolioName}
                                            onChange={(e) => setPortfolioName(e.target.value)}
                                            placeholder="My Investment Portfolio"
                                            required
                                        />
                                    </Form.Group>
                                </Col>
                                <Col md={6}>
                                    <Form.Group className="mb-3">
                                        <Form.Label>Description</Form.Label>
                                        <Form.Control
                                            type="text"
                                            value={portfolioDescription}
                                            onChange={(e) => setPortfolioDescription(e.target.value)}
                                            placeholder="Portfolio description (optional)"
                                        />
                                    </Form.Group>
                                </Col>
                            </Row>
                        </Form>

                        <div className="d-flex justify-content-between align-items-center mb-3">
                            <h5>Stocks Found: {uploadedData.stocks.length}</h5>
                            <div>
                                <span className="me-3">Total Value: <strong>${calculateTotalValue()}</strong></span>
                                <Button variant="success" size="sm" onClick={handleAddStock}>
                                    Add Stock
                                </Button>
                            </div>
                        </div>

                        {uploadedData.stocks.length > 0 ? (
                            <div className="table-responsive">
                                <Table striped bordered hover>
                                    <thead>
                                    <tr>
                                        <th style={{ width: '15%' }}>Symbol</th>
                                        <th style={{ width: '25%' }}>Company Name</th>
                                        <th style={{ width: '15%' }}>Shares</th>
                                        <th style={{ width: '15%' }}>Entry Price</th>
                                        <th style={{ width: '15%' }}>Total Value</th>
                                        <th style={{ width: '15%' }}>Actions</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {uploadedData.stocks.map((stock, index) => (
                                        <tr key={index}>
                                            <td>
                                                <Form.Control
                                                    type="text"
                                                    value={stock.symbol}
                                                    onChange={(e) => handleEditStock(index, 'symbol', e.target.value.toUpperCase())}
                                                    placeholder="AAPL"
                                                />
                                            </td>
                                            <td>
                                                <Form.Control
                                                    type="text"
                                                    value={stock.companyName || ''}
                                                    onChange={(e) => handleEditStock(index, 'companyName', e.target.value)}
                                                    placeholder="Company Name"
                                                />
                                            </td>
                                            <td>
                                                <Form.Control
                                                    type="number"
                                                    value={stock.shares}
                                                    onChange={(e) => handleEditStock(index, 'shares', e.target.value)}
                                                    min="0"
                                                />
                                            </td>
                                            <td>
                                                <Form.Control
                                                    type="number"
                                                    step="0.01"
                                                    value={stock.entryPrice}
                                                    onChange={(e) => handleEditStock(index, 'entryPrice', e.target.value)}
                                                    min="0"
                                                />
                                            </td>
                                            <td className="text-end">
                                                ${(stock.shares * stock.entryPrice).toFixed(2)}
                                            </td>
                                            <td>
                                                <Button
                                                    variant="danger"
                                                    size="sm"
                                                    onClick={() => handleRemoveStock(index)}
                                                >
                                                    Remove
                                                </Button>
                                            </td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </Table>
                            </div>
                        ) : (
                            <Alert variant="info">
                                No stocks found in the uploaded file. Please check your Excel format.
                            </Alert>
                        )}

                        <div className="d-flex justify-content-between mt-4">
                            <Button variant="secondary" onClick={() => setUploadedData(null)}>
                                Cancel
                            </Button>
                            <Button
                                variant="primary"
                                onClick={handleSubmit}
                                disabled={loading || !portfolioName.trim() || uploadedData.stocks.length === 0}
                            >
                                {loading ? 'Creating Portfolio...' : 'Create Portfolio'}
                            </Button>
                        </div>
                    </Card.Body>
                </Card>
            )}

            <Card className="mt-4">
                <Card.Header>
                    <h5>Excel File Format Examples</h5>
                </Card.Header>
                <Card.Body>
                    <h6>Example 1: Full Format</h6>
                    <Table bordered size="sm" className="mb-4">
                        <thead className="table-light">
                        <tr>
                            <th>Symbol</th>
                            <th>Company Name</th>
                            <th>Shares</th>
                            <th>Entry Price</th>
                            <th>Entry Date</th>
                        </tr>
                        </thead>
                        <tbody>
                        <tr>
                            <td>AAPL</td>
                            <td>Apple Inc.</td>
                            <td>100</td>
                            <td>150.00</td>
                            <td>2024-01-15</td>
                        </tr>
                        <tr>
                            <td>MSFT</td>
                            <td>Microsoft Corporation</td>
                            <td>50</td>
                            <td>300.00</td>
                            <td>2024-02-01</td>
                        </tr>
                        </tbody>
                    </Table>

                    <h6>Example 2: Minimal Format</h6>
                    <Table bordered size="sm" className="mb-4">
                        <thead className="table-light">
                        <tr>
                            <th>Ticker</th>
                            <th>Quantity</th>
                            <th>Purchase Price</th>
                        </tr>
                        </thead>
                        <tbody>
                        <tr>
                            <td>GOOGL</td>
                            <td>20</td>
                            <td>2500.00</td>
                        </tr>
                        <tr>
                            <td>AMZN</td>
                            <td>30</td>
                            <td>3000.00</td>
                        </tr>
                        </tbody>
                    </Table>

                    <Alert variant="info">
                        <strong>Tips:</strong>
                        <ul className="mb-0">
                            <li>The first row must contain column headers</li>
                            <li>Column names are flexible (Symbol/Ticker, Shares/Quantity, etc.)</li>
                            <li>Any stock symbol will work - if not found in market data, sample data will be generated</li>
                            <li>Dates should be in YYYY-MM-DD format if included</li>
                        </ul>
                    </Alert>
                </Card.Body>
            </Card>
        </Container>
    );
};

export default PortfolioUpload;