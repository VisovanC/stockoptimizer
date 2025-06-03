import React, { useState, useCallback } from 'react';
import { useDropzone } from 'react-dropzone';
import { Container, Card, Button, Alert, Table, Form } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import portfolioService from '../../services/portfolio.service';
import { toast } from 'react-toastify';

const PortfolioUpload = () => {
    const [uploadedData, setUploadedData] = useState(null);
    const [portfolioName, setPortfolioName] = useState('');
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

        setLoading(true);
        try {
            const portfolioData = {
                ...uploadedData,
                name: portfolioName || uploadedData.name
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
            [field]: field === 'shares' ? parseInt(value) || 0 : parseFloat(value) || 0
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

    return (
        <Container className="py-4">
            <h2 className="mb-4">Upload Portfolio from Excel</h2>

            <Card className="mb-4">
                <Card.Body>
                    <div {...getRootProps()} className="border-dashed border-2 p-5 text-center"
                         style={{ cursor: 'pointer', borderStyle: 'dashed' }}>
                        <input {...getInputProps()} />
                        {isDragActive ? (
                            <p>Drop the Excel file here...</p>
                        ) : (
                            <div>
                                <p className="mb-2">Drag and drop an Excel file here, or click to select</p>
                                <p className="text-muted small">
                                    Supported formats: .xls, .xlsx<br/>
                                    Required columns: Symbol/Ticker, Shares/Quantity, Entry Price/Purchase Price
                                </p>
                            </div>
                        )}
                    </div>
                </Card.Body>
            </Card>

            {error && <Alert variant="danger">{error}</Alert>}

            {uploadedData && (
                <Card>
                    <Card.Header>
                        <Form.Group>
                            <Form.Label>Portfolio Name</Form.Label>
                            <Form.Control
                                type="text"
                                value={portfolioName}
                                onChange={(e) => setPortfolioName(e.target.value)}
                                placeholder="Enter portfolio name"
                            />
                        </Form.Group>
                    </Card.Header>
                    <Card.Body>
                        <h5>Preview: {uploadedData.stocks.length} stocks found</h5>
                        <Table striped bordered hover>
                            <thead>
                            <tr>
                                <th>Symbol</th>
                                <th>Company Name</th>
                                <th>Shares</th>
                                <th>Entry Price</th>
                                <th>Actions</th>
                            </tr>
                            </thead>
                            <tbody>
                            {uploadedData.stocks.map((stock, index) => (
                                <tr key={index}>
                                    <td>
                                        <Form.Control
                                            type="text"
                                            value={stock.symbol}
                                            onChange={(e) => handleEditStock(index, 'symbol', e.target.value)}
                                        />
                                    </td>
                                    <td>
                                        <Form.Control
                                            type="text"
                                            value={stock.companyName}
                                            onChange={(e) => handleEditStock(index, 'companyName', e.target.value)}
                                        />
                                    </td>
                                    <td>
                                        <Form.Control
                                            type="number"
                                            value={stock.shares}
                                            onChange={(e) => handleEditStock(index, 'shares', e.target.value)}
                                        />
                                    </td>
                                    <td>
                                        <Form.Control
                                            type="number"
                                            step="0.01"
                                            value={stock.entryPrice}
                                            onChange={(e) => handleEditStock(index, 'entryPrice', e.target.value)}
                                        />
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

                        <div className="d-flex justify-content-between mt-3">
                            <Button variant="secondary" onClick={() => setUploadedData(null)}>
                                Cancel
                            </Button>
                            <Button
                                variant="primary"
                                onClick={handleSubmit}
                                disabled={loading || !portfolioName}
                            >
                                {loading ? 'Creating Portfolio...' : 'Create Portfolio'}
                            </Button>
                        </div>
                    </Card.Body>
                </Card>
            )}

            <Card className="mt-4">
                <Card.Body>
                    <h5>Excel File Format Example</h5>
                    <Table bordered>
                        <thead>
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
                </Card.Body>
            </Card>
        </Container>
    );
};

export default PortfolioUpload;