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

  searchStocks: async (query) => {
    try {
      const response = await api.get(`/stocks/search?query=${query}`);
      return response.data;
    } catch (error) {
      return [];
    }
  },

  ensureStockData: async (symbol) => {
    try {
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

          if (!jsonData || jsonData.length === 0) {
            reject(new Error('No data found in Excel file'));
            return;
          }

          const portfolioData = {
            name: file.name.replace(/\.[^/.]+$/, ''),
            description: `Imported from ${file.name}`,
            stocks: jsonData.map(row => {
              const symbol = (row['Symbol'] || row['Ticker'] || row['Stock'] || row['SYMBOL'] || row['symbol'] || '').toString().toUpperCase();
              const companyName = row['Company Name'] || row['Name'] || row['Company'] || row['COMPANY NAME'] || row['name'] || '';
              const shares = parseInt(row['Shares'] || row['Quantity'] || row['QTY'] || row['shares'] || row['SHARES'] || 0);
              const entryPrice = parseFloat(row['Entry Price'] || row['Purchase Price'] || row['Price'] || row['Cost'] || row['PRICE'] || row['price'] || 0);
              const entryDate = row['Entry Date'] || row['Purchase Date'] || row['Date'] || row['DATE'] || new Date().toISOString();

              return {
                symbol: symbol,
                companyName: companyName || symbol + ' Corp.',
                shares: shares,
                entryPrice: entryPrice,
                entryDate: entryDate
              };
            }).filter(stock => stock.symbol && stock.shares > 0 && stock.entryPrice > 0)
          };

          if (portfolioData.stocks.length === 0) {
            reject(new Error('No valid stocks found in Excel file. Please check your data format.'));
            return;
          }

          resolve(portfolioData);
        } catch (error) {
          console.error('Error parsing Excel:', error);
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
  addStockToPortfolio: async (portfolioId, stockData) => {
    stockData.symbol = stockData.symbol.toUpperCase();

    await portfolioService.ensureStockData(stockData.symbol);

    const portfolio = await portfolioService.getPortfolio(portfolioId);

    const updatedStocks = [...portfolio.stocks, stockData];

    return portfolioService.updatePortfolio(portfolioId, {
      ...portfolio,
      stocks: updatedStocks
    });
  },

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