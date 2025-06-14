import api from './api';
import * as XLSX from 'xlsx';

const portfolioService = {
  getPortfolios: async () => {
    const response = await api.get('/portfolios');
    return response.data;
  },

  getPortfolio: async (id) => {
    const response = await api.get(`/portfolios/${id}`);
    return response.data;
  },

  createPortfolio: async (portfolioData) => {
    // Pre-process stocks to ensure they're uppercase and valid
    if (portfolioData.stocks) {
      portfolioData.stocks = portfolioData.stocks.map(stock => ({
        ...stock,
        symbol: stock.symbol.toUpperCase()
      }));
    }

    const response = await api.post('/portfolios', portfolioData);
    return response.data;
  },

  updatePortfolio: async (id, portfolioData) => {
    // Pre-process stocks to ensure they're uppercase
    if (portfolioData.stocks) {
      portfolioData.stocks = portfolioData.stocks.map(stock => ({
        ...stock,
        symbol: stock.symbol.toUpperCase()
      }));
    }

    const response = await api.put(`/portfolios/${id}`, portfolioData);
    return response.data;
  },

  deletePortfolio: async (id) => {
    const response = await api.delete(`/portfolios/${id}`);
    return response.data;
  },

  // Validate a stock symbol
  validateSymbol: async (symbol) => {
    try {
      const response = await api.get(`/stocks/validate/${symbol}`);
      return response.data;
    } catch (error) {
      return {
        valid: false,
        symbol: symbol.toUpperCase(),
        message: 'Unable to validate symbol'
      };
    }
  },

  // Search for stocks
  searchStocks: async (query) => {
    try {
      const response = await api.get(`/stocks/search?query=${query}`);
      return response.data;
    } catch (error) {
      return [];
    }
  },

  // Ensure stock data exists before adding to portfolio
  ensureStockData: async (symbol) => {
    try {
      // Try to collect data for the stock
      const response = await api.post(`/stocks/data/collect/${symbol}`, null, {
        params: { days: 365, forceRefresh: false }
      });
      return response.data;
    } catch (error) {
      console.error('Error ensuring stock data:', error);
      return null;
    }
  },

  parseExcelFile: async (file) => {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();

      reader.onload = (e) => {
        try {
          const data = new Uint8Array(e.target.result);
          const workbook = XLSX.read(data, { type: 'array' });

          const sheetName = workbook.SheetNames[0];
          const worksheet = workbook.Sheets[sheetName];

          const jsonData = XLSX.utils.sheet_to_json(worksheet);

          const portfolioData = {
            name: file.name.replace(/\.[^/.]+$/, ''), // Remove file extension
            description: `Imported from ${file.name}`,
            stocks: jsonData.map(row => ({
              symbol: (row['Symbol'] || row['Ticker'] || row['Stock'] || '').toUpperCase(),
              companyName: row['Company Name'] || row['Name'] || '',
              shares: parseInt(row['Shares'] || row['Quantity'] || 0),
              entryPrice: parseFloat(row['Entry Price'] || row['Purchase Price'] || row['Cost'] || 0),
              entryDate: row['Entry Date'] || row['Purchase Date'] || new Date().toISOString()
            })).filter(stock => stock.symbol && stock.shares > 0)
          };

          resolve(portfolioData);
        } catch (error) {
          reject(new Error('Failed to parse Excel file: ' + error.message));
        }
      };

      reader.onerror = () => reject(new Error('Failed to read file'));
      reader.readAsArrayBuffer(file);
    });
  },

  optimizePortfolio: async (id, riskTolerance = 0.5) => {
    const response = await api.post(`/portfolios/${id}/optimize`, null, {
      params: { riskTolerance }
    });
    return response.data;
  },

  getOptimizationSuggestions: async (id) => {
    const response = await api.get(`/portfolios/${id}/suggestions`);
    return response.data;
  },

  getAIRecommendations: async (id, riskTolerance = 0.5, expandUniverse = false) => {
    const response = await api.get(`/ai/portfolio/${id}/upgrade-recommendations`, {
      params: { riskTolerance, expandUniverse }
    });
    return response.data;
  },

  applyAIRecommendations: async (id, allocations) => {
    const response = await api.post(`/ai/portfolio/${id}/apply-upgrade`, allocations);
    return response.data;
  },

  getMLStatus: async (portfolioId) => {
    try {
      const response = await api.get(`/ml/portfolio/${portfolioId}/status`);
      return response.data;
    } catch (error) {
      // If the endpoint doesn't exist, try the controller pattern
      const response = await api.get(`/portfolio/${portfolioId}/ml/status`);
      return response.data;
    }
  },

  trainMLModels: async (portfolioId, useSampleData = false) => {
    try {
      const response = await api.post(`/ml/portfolio/${portfolioId}/train`, null, {
        params: { useSampleData }
      });
      return response.data;
    } catch (error) {
      // If the endpoint doesn't exist, try the controller pattern
      const response = await api.post(`/portfolio/${portfolioId}/ml/train`, null, {
        params: { useSampleData }
      });
      return response.data;
    }
  },

  getPersonalizedRecommendations: async (portfolioId, riskTolerance = 0.5, expandUniverse = false) => {
    try {
      const response = await api.get(`/ml/portfolio/${portfolioId}/recommendations`, {
        params: { riskTolerance, expandUniverse }
      });
      return response.data;
    } catch (error) {
      // If the endpoint doesn't exist, use the AI portfolio upgrader
      const response = await api.get(`/ai/portfolio/${portfolioId}/upgrade-recommendations`, {
        params: { riskTolerance, expandUniverse }
      });
      return response.data;
    }
  },

  getPortfolioPerformance: async (portfolioId) => {
    const response = await api.get(`/ai/metrics/portfolio/${portfolioId}`);
    return response.data;
  },

  getPortfolioHistory: async (portfolioId) => {
    const response = await api.get(`/portfolio-history/portfolio/${portfolioId}`);
    return response.data;
  },

  getAIRecommendationHistory: async (portfolioId) => {
    const response = await api.get(`/portfolio-history/portfolio/${portfolioId}/ai-recommendations`);
    return response.data;
  },

  // New methods for handling any stock symbol
  addStockToPortfolio: async (portfolioId, stockData) => {
    // Ensure symbol is uppercase
    stockData.symbol = stockData.symbol.toUpperCase();

    // First, ensure we have data for this stock
    await portfolioService.ensureStockData(stockData.symbol);

    // Get the current portfolio
    const portfolio = await portfolioService.getPortfolio(portfolioId);

    // Add the new stock
    const updatedStocks = [...portfolio.stocks, stockData];

    // Update the portfolio
    return portfolioService.updatePortfolio(portfolioId, {
      ...portfolio,
      stocks: updatedStocks
    });
  },

  // Batch collect data for multiple stocks
  collectDataForStocks: async (symbols) => {
    const results = [];

    for (const symbol of symbols) {
      try {
        const result = await api.post(`/stocks/data/collect/${symbol}`, null, {
          params: { days: 365 }
        });
        results.push({ symbol, status: 'success', data: result.data });
      } catch (error) {
        results.push({ symbol, status: 'error', error: error.message });
      }
    }

    return results;
  }
};

export default portfolioService;