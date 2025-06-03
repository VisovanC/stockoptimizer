// src/services/portfolio.service.js
import api from './api';
import * as XLSX from 'xlsx';

const portfolioService = {
  // Get all portfolios for current user
  getPortfolios: async () => {
    const response = await api.get('/portfolios');
    return response.data;
  },

  // Get portfolio by ID
  getPortfolio: async (id) => {
    const response = await api.get(`/portfolios/${id}`);
    return response.data;
  },

  // Create new portfolio
  createPortfolio: async (portfolioData) => {
    const response = await api.post('/portfolios', portfolioData);
    return response.data;
  },

  // Update portfolio
  updatePortfolio: async (id, portfolioData) => {
    const response = await api.put(`/portfolios/${id}`, portfolioData);
    return response.data;
  },

  // Delete portfolio
  deletePortfolio: async (id) => {
    const response = await api.delete(`/portfolios/${id}`);
    return response.data;
  },

  // Upload Excel file and parse portfolio data
  parseExcelFile: async (file) => {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();

      reader.onload = (e) => {
        try {
          const data = new Uint8Array(e.target.result);
          const workbook = XLSX.read(data, { type: 'array' });

          // Assume the first sheet contains portfolio data
          const sheetName = workbook.SheetNames[0];
          const worksheet = workbook.Sheets[sheetName];

          // Convert to JSON
          const jsonData = XLSX.utils.sheet_to_json(worksheet);

          // Transform Excel data to portfolio format
          const portfolioData = {
            name: file.name.replace(/\.[^/.]+$/, ''), // Remove file extension
            description: `Imported from ${file.name}`,
            stocks: jsonData.map(row => ({
              symbol: row['Symbol'] || row['Ticker'] || row['Stock'],
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

  // Optimize portfolio
  optimizePortfolio: async (id, riskTolerance = 0.5) => {
    const response = await api.post(`/portfolios/${id}/optimize`, null, {
      params: { riskTolerance }
    });
    return response.data;
  },

  // Get optimization suggestions
  getOptimizationSuggestions: async (id) => {
    const response = await api.get(`/portfolios/${id}/suggestions`);
    return response.data;
  },

  // Get AI upgrade recommendations
  getAIRecommendations: async (id, riskTolerance = 0.5, expandUniverse = false) => {
    const response = await api.get(`/ai/portfolio/${id}/upgrade-recommendations`, {
      params: { riskTolerance, expandUniverse }
    });
    return response.data;
  },

  // Apply AI recommendations
  applyAIRecommendations: async (id, allocations) => {
    const response = await api.post(`/ai/portfolio/${id}/apply-upgrade`, allocations);
    return response.data;
  }
};

export default portfolioService;