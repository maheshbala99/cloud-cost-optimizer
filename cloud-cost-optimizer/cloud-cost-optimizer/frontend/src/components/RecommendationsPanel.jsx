import React, { useState } from 'react';
import './RecommendationsPanel.css';

const fmt = (n) => `$${Number(n).toFixed(2)}`;

const TYPE_CONFIG = {
  IDLE_RESOURCE:      { icon: '😴', label: 'Idle Resource',          color: '#dc3545' },
  RIGHT_SIZING:       { icon: '📏', label: 'Right Sizing',           color: '#fd7e14' },
  RESERVED_INSTANCE:  { icon: '🎟️',  label: 'Reserved Instance',     color: '#0d6efd' },
  STORAGE_OPTIMIZATION: { icon: '💾', label: 'Storage Optimization', color: '#6f42c1' },
  SAVINGS_PLAN:       { icon: '💰', label: 'Savings Plan',           color: '#198754' },
};

const PRIORITY_COLOR = { HIGH: '#dc3545', MEDIUM: '#fd7e14', LOW: '#198754' };

const RecommendationsPanel = ({ recommendations }) => {
  const [filter, setFilter] = useState('ALL');

  const types = ['ALL', ...new Set(recommendations.map(r => r.type))];

  const filtered = filter === 'ALL'
    ? recommendations
    : recommendations.filter(r => r.type === filter);

  const totalSavings = filtered.reduce((sum, r) => sum + r.estimatedMonthlySavings, 0);

  if (recommendations.length === 0) {
    return (
      <div className="empty-recs">
        <span>✅</span>
        <p>No optimization recommendations found.</p>
        <small>Your AWS resources appear to be well-optimized!</small>
      </div>
    );
  }

  return (
    <div className="recs-panel">
      <div className="recs-header">
        <div>
          <h3>{recommendations.length} Recommendations</h3>
          <p>Estimated savings: <strong>{fmt(totalSavings)}/month</strong></p>
        </div>
        <div className="type-filter">
          {types.map(t => (
            <button
              key={t}
              className={`filter-btn ${filter === t ? 'active' : ''}`}
              onClick={() => setFilter(t)}
            >
              {t === 'ALL' ? 'All' : TYPE_CONFIG[t]?.label || t}
            </button>
          ))}
        </div>
      </div>

      <div className="recs-list">
        {filtered.map((rec, i) => {
          const config = TYPE_CONFIG[rec.type] || { icon: '🔍', label: rec.type, color: '#666' };
          return (
            <div key={i} className="rec-card">
              <div className="rec-icon" style={{ background: config.color + '20', color: config.color }}>
                {config.icon}
              </div>
              <div className="rec-content">
                <div className="rec-top">
                  <span className="rec-type" style={{ color: config.color }}>{config.label}</span>
                  <span className="rec-resource">{rec.resourceType}: {rec.resourceId}</span>
                  <span
                    className="rec-priority"
                    style={{ background: PRIORITY_COLOR[rec.priority] + '20', color: PRIORITY_COLOR[rec.priority] }}
                  >
                    {rec.priority}
                  </span>
                </div>
                <p className="rec-desc">{rec.description}</p>
              </div>
              <div className="rec-savings">
                <div className="savings-amount">{fmt(rec.estimatedMonthlySavings)}</div>
                <div className="savings-label">/ month</div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default RecommendationsPanel;
