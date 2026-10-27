import axios from 'axios';

const BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080/api';

const client = axios.create({ baseURL: BASE_URL });

export const costApi = {
  getSummary: () => client.get('/costs/summary').then(r => r.data),
  getHistory: (months = 6) => client.get(`/costs/history?months=${months}`).then(r => r.data),
  getRecommendations: () => client.get('/costs/recommendations').then(r => r.data),
};
