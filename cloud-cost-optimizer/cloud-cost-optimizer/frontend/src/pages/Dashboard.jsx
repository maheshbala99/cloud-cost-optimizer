import React, { useState, useEffect } from 'react';
import {
  LineChart, Line, BarChart, Bar, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer
} from 'recharts';
import { costApi } from '../services/api';
import RecommendationsPanel from '../components/RecommendationsPanel';
import './Dashboard.css';

const SERVICE_COLORS = [
  '#0d6efd', '#198754', '#dc3545', '#fd7e14', '#6f42c1',
  '#20c997', '#ffc107', '#0dcaf0', '#6c757d', '#d63384',
];

const fmt = (n) => `$${Number(n).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const StatCard = ({ label, value, subtext, trend }) => (
  <div className="stat-card">
    <div className="stat-label">{label}</div>
    <div className="stat-value">{value}</div>
    {subtext && (
      <div className={`stat-sub ${trend === 'up' ? 'trend-up' : trend === 'down' ? 'trend-down' : ''}`}>
        {subtext}
      </div>
    )}
  </div>
);

const Dashboard = () => {
  const [summary, setSummary] = useState(null);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [activeTab, setActiveTab] = useState('overview');

  useEffect(() => {
    const load = async () => {
      try {
        const [sum, hist] = await Promise.all([
          costApi.getSummary(),
          costApi.getHistory(6),
        ]);
        setSummary(sum);
        setHistory(hist);
      } catch (e) {
        setError('Failed to load cost data. Ensure AWS credentials are configured.');
        console.error(e);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, []);

  if (loading) return <div className="loading"><div className="spinner" />Loading cost data…</div>;
  if (error)   return <div className="error-banner">⚠️ {error}</div>;

  const changeSign = summary.changePercentage >= 0 ? '+' : '';
  const changeTrend = summary.changePercentage >= 0 ? 'up' : 'down';

  return (
    <div className="dashboard">
      {/* Header */}
      <div className="dash-header">
        <div>
          <h1 className="dash-title">☁️ Cloud Cost Optimizer</h1>
          <p className="dash-subtitle">AWS spend analysis &amp; optimization recommendations</p>
        </div>
        <div className="savings-badge">
          💡 {fmt(summary.estimatedMonthlySavings)}/mo potential savings
        </div>
      </div>

      {/* KPI Row */}
      <div className="stat-grid">
        <StatCard
          label="Current Month Spend"
          value={fmt(summary.currentMonthTotal)}
          subtext={`${changeSign}${summary.changePercentage.toFixed(1)}% vs last month`}
          trend={changeTrend}
        />
        <StatCard
          label="Previous Month"
          value={fmt(summary.previousMonthTotal)}
        />
        <StatCard
          label="Potential Savings"
          value={fmt(summary.estimatedMonthlySavings)}
          subtext={`${summary.recommendations.length} recommendations`}
          trend="down"
        />
        <StatCard
          label="Top Service"
          value={summary.serviceBreakdown[0]?.serviceName?.replace('Amazon ', '') || '—'}
          subtext={fmt(summary.serviceBreakdown[0]?.amount || 0)}
        />
      </div>

      {/* Tabs */}
      <div className="tab-bar">
        {['overview', 'services', 'recommendations'].map(tab => (
          <button
            key={tab}
            className={`tab-btn ${activeTab === tab ? 'active' : ''}`}
            onClick={() => setActiveTab(tab)}
          >
            {tab.charAt(0).toUpperCase() + tab.slice(1)}
            {tab === 'recommendations' && summary.recommendations.length > 0 &&
              <span className="tab-badge">{summary.recommendations.length}</span>}
          </button>
        ))}
      </div>

      {/* Overview tab */}
      {activeTab === 'overview' && (
        <div className="charts-grid">
          {/* Daily spend line chart */}
          <div className="chart-card full-width">
            <h3 className="chart-title">Daily Spend — Current Month</h3>
            <ResponsiveContainer width="100%" height={240}>
              <LineChart data={summary.dailyCosts}
                         margin={{ top: 8, right: 24, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="date" tick={{ fontSize: 11 }}
                       tickFormatter={(d) => d.slice(8)} // day number only
                />
                <YAxis tick={{ fontSize: 11 }} tickFormatter={(v) => `$${v}`} />
                <Tooltip formatter={(v) => fmt(v)} labelFormatter={(l) => `Date: ${l}`} />
                <Line type="monotone" dataKey="amount" stroke="#0d6efd"
                      strokeWidth={2} dot={false} name="Spend" />
              </LineChart>
            </ResponsiveContainer>
          </div>

          {/* Monthly history bar chart */}
          <div className="chart-card">
            <h3 className="chart-title">6-Month Trend</h3>
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={history} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="month" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} tickFormatter={(v) => `$${v}`} />
                <Tooltip formatter={(v) => fmt(v)} />
                <Bar dataKey="amount" fill="#0d6efd" radius={[4, 4, 0, 0]} name="Spend" />
              </BarChart>
            </ResponsiveContainer>
          </div>

          {/* Service pie chart */}
          <div className="chart-card">
            <h3 className="chart-title">Spend by Service</h3>
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie
                  data={summary.serviceBreakdown.slice(0, 8)}
                  cx="50%" cy="50%"
                  outerRadius={80}
                  dataKey="amount"
                  nameKey="serviceName"
                  label={({ serviceName, percentage }) =>
                    `${serviceName.replace('Amazon ', '').replace('AWS ', '').slice(0, 10)} ${percentage.toFixed(0)}%`}
                  labelLine={false}
                >
                  {summary.serviceBreakdown.slice(0, 8).map((_, i) => (
                    <Cell key={i} fill={SERVICE_COLORS[i % SERVICE_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip formatter={(v) => fmt(v)} />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* Services breakdown tab */}
      {activeTab === 'services' && (
        <div className="chart-card">
          <h3 className="chart-title">Cost by Service — Current Month</h3>
          <table className="cost-table">
            <thead>
              <tr>
                <th>Service</th>
                <th>Amount</th>
                <th>% of Total</th>
                <th>Bar</th>
              </tr>
            </thead>
            <tbody>
              {summary.serviceBreakdown.map((s, i) => (
                <tr key={i}>
                  <td>{s.serviceName}</td>
                  <td className="amount-col">{fmt(s.amount)}</td>
                  <td>{s.percentage.toFixed(1)}%</td>
                  <td>
                    <div className="bar-cell">
                      <div
                        className="bar-fill"
                        style={{
                          width: `${s.percentage}%`,
                          backgroundColor: SERVICE_COLORS[i % SERVICE_COLORS.length],
                        }}
                      />
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Recommendations tab */}
      {activeTab === 'recommendations' && (
        <RecommendationsPanel recommendations={summary.recommendations} />
      )}
    </div>
  );
};

export default Dashboard;
