// API Configuration
const API_BASE = '/api/cache';
const CLUSTER_API = '/api/cluster';
const ACTUATOR_BASE = '/actuator';

// Authentication
const AUTH = btoa('admin:admin');

// Global State
let currentCache = null;
let currentKeys = [];
let filteredKeys = [];
let currentPage = 1;
const keysPerPage = 50;

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    setupNavigation();
    loadDashboard();
    loadCacheSelector();
    setInterval(refreshDashboard, 30000); // Auto-refresh every 30s
});

// Navigation
function setupNavigation() {
    document.querySelectorAll('.nav-item').forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault();
            const view = item.getAttribute('data-view');
            switchView(view);
            
            // Update active nav
            document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
            item.classList.add('active');
        });
    });
}

function switchView(viewName) {
    document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
    const view = document.getElementById(`${viewName}-view`);
    if (view) {
        view.classList.add('active');
        
        // Load view-specific data
        switch(viewName) {
            case 'dashboard':
                loadDashboard();
                break;
            case 'caches':
                loadCacheSelector();
                break;
            case 'cluster':
                loadClusterStatus();
                break;
            case 'metrics':
                loadMetrics();
                break;
        }
    }
}

// API Calls
async function apiCall(endpoint, options = {}) {
    const url = endpoint.startsWith('http') ? endpoint : `${API_BASE}${endpoint}`;
    const defaultOptions = {
        headers: {
            'Authorization': `Basic ${AUTH}`,
            'Content-Type': 'application/json'
        }
    };
    
    try {
        const response = await fetch(url, { ...defaultOptions, ...options });
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        return await response.json();
    } catch (error) {
        console.error('API Error:', error);
        showToast('Error: ' + error.message, 'error');
        throw error;
    }
}

// Dashboard
async function loadDashboard() {
    try {
        const data = await apiCall('');
        updateDashboardStats(data);
        renderCacheList(data.caches || {});
    } catch (error) {
        console.error('Failed to load dashboard:', error);
    }
}

function updateDashboardStats(data) {
    const caches = data.caches || {};
    const cacheNames = Object.keys(caches);
    
    let totalEntries = 0;
    let totalMemory = 0;
    let totalHits = 0;
    let totalMisses = 0;
    
    cacheNames.forEach(name => {
        const stats = caches[name];
        totalEntries += stats.size || 0;
        totalMemory += stats.memoryBytes || 0;
        totalHits += stats.hits || 0;
        totalMisses += stats.misses || 0;
    });
    
    const totalRequests = totalHits + totalMisses;
    const avgHitRatio = totalRequests > 0 ? ((totalHits / totalRequests) * 100).toFixed(1) : 0;
    
    document.getElementById('total-caches').textContent = cacheNames.length;
    document.getElementById('total-entries').textContent = formatNumber(totalEntries);
    document.getElementById('avg-hit-ratio').textContent = avgHitRatio + '%';
    document.getElementById('total-memory').textContent = formatBytes(totalMemory);
}

function renderCacheList(caches) {
    const container = document.getElementById('cache-list');
    container.innerHTML = '';
    
    Object.entries(caches).forEach(([name, stats]) => {
        const item = document.createElement('div');
        item.className = 'cache-item';
        item.onclick = () => {
            switchView('caches');
            document.getElementById('cache-selector').value = name;
            loadCacheKeys();
        };
        
        const hitRatio = (stats.hits + stats.misses) > 0 
            ? ((stats.hits / (stats.hits + stats.misses)) * 100).toFixed(1) 
            : 0;
        
        item.innerHTML = `
            <div class="cache-item-header">
                <span class="cache-item-name">${escapeHtml(name)}</span>
                <span style="color: var(--text-muted); font-size: 14px;">${formatBytes(stats.memoryBytes)}</span>
            </div>
            <div class="cache-item-stats">
                <div class="cache-stat">
                    <div class="cache-stat-label">Entries</div>
                    <div class="cache-stat-value">${formatNumber(stats.size)}</div>
                </div>
                <div class="cache-stat">
                    <div class="cache-stat-label">Hits</div>
                    <div class="cache-stat-value">${formatNumber(stats.hits)}</div>
                </div>
                <div class="cache-stat">
                    <div class="cache-stat-label">Misses</div>
                    <div class="cache-stat-value">${formatNumber(stats.misses)}</div>
                </div>
                <div class="cache-stat">
                    <div class="cache-stat-label">Hit Ratio</div>
                    <div class="cache-stat-value">${hitRatio}%</div>
                </div>
            </div>
        `;
        container.appendChild(item);
    });
}

// Cache Browser
async function loadCacheSelector() {
    try {
        const data = await apiCall('');
        const selector = document.getElementById('cache-selector');
        selector.innerHTML = '<option value="">Select a cache...</option>';
        
        Object.keys(data.caches || {}).forEach(name => {
            const option = document.createElement('option');
            option.value = name;
            option.textContent = name;
            selector.appendChild(option);
        });
        
        selector.addEventListener('change', (e) => {
            if (e.target.value) {
                loadCacheDetails(e.target.value);
            }
        });
    } catch (error) {
        console.error('Failed to load cache selector:', error);
    }
}

async function loadCacheDetails(cacheName) {
    currentCache = cacheName;
    try {
        const [stats, config] = await Promise.all([
            apiCall(`/${cacheName}/stats`),
            apiCall(`/${cacheName}`)
        ]);
        
        renderCacheStats(stats, config);
        loadCacheKeys();
    } catch (error) {
        console.error('Failed to load cache details:', error);
    }
}

function renderCacheStats(stats, config) {
    const container = document.getElementById('cache-stats-content');
    const hitRatio = (stats.hits + stats.misses) > 0 
        ? ((stats.hits / (stats.hits + stats.misses)) * 100).toFixed(2) 
        : 0;
    
    container.innerHTML = `
        <div class="stat-row">
            <span class="stat-row-label">Cache Name</span>
            <span class="stat-row-value">${escapeHtml(stats.cacheName)}</span>
        </div>
        <div class="stat-row">
            <span class="stat-row-label">Total Entries</span>
            <span class="stat-row-value">${formatNumber(stats.size)}</span>
        </div>
        <div class="stat-row">
            <span class="stat-row-label">Memory Usage</span>
            <span class="stat-row-value">${formatBytes(stats.memoryBytes)}</span>
        </div>
        <div class="stat-row">
            <span class="stat-row-label">Cache Hits</span>
            <span class="stat-row-value">${formatNumber(stats.hits)}</span>
        </div>
        <div class="stat-row">
            <span class="stat-row-label">Cache Misses</span>
            <span class="stat-row-value">${formatNumber(stats.misses)}</span>
        </div>
        <div class="stat-row">
            <span class="stat-row-label">Hit Ratio</span>
            <span class="stat-row-value">${hitRatio}%</span>
        </div>
        <div class="stat-row">
            <span class="stat-row-label">Evictions</span>
            <span class="stat-row-value">${formatNumber(stats.evictions)}</span>
        </div>
        <div class="stat-row">
            <span class="stat-row-label">Last Updated</span>
            <span class="stat-row-value">${formatDate(stats.lastUpdated)}</span>
        </div>
    `;
}

async function loadCacheKeys() {
    const cacheName = document.getElementById('cache-selector').value;
    if (!cacheName) {
        showToast('Please select a cache first', 'error');
        return;
    }
    
    currentCache = cacheName;
    currentPage = 1;
    
    try {
        const data = await apiCall(`/${cacheName}/keys?limit=1000`);
        currentKeys = data.keys || [];
        filteredKeys = [...currentKeys];
        renderKeys();
        renderPagination();
    } catch (error) {
        console.error('Failed to load cache keys:', error);
        showToast('Failed to load keys', 'error');
    }
}

function renderKeys() {
    const container = document.getElementById('keys-list');
    container.innerHTML = '';
    
    if (filteredKeys.length === 0) {
        container.innerHTML = '<div style="text-align: center; padding: 40px; color: var(--text-muted);">No keys found</div>';
        return;
    }
    
    const start = (currentPage - 1) * keysPerPage;
    const end = start + keysPerPage;
    const pageKeys = filteredKeys.slice(start, end);
    
    pageKeys.forEach(key => {
        const item = document.createElement('div');
        item.className = 'key-item';
        item.innerHTML = `
            <span class="key-name" onclick="showKeyDetails('${escapeHtml(key)}')">${escapeHtml(key)}</span>
            <div class="key-actions">
                <button class="key-action-btn" onclick="invalidateSingleKey('${escapeHtml(key)}')" title="Invalidate">🗑️</button>
            </div>
        `;
        container.appendChild(item);
    });
}

function filterKeys() {
    const search = document.getElementById('key-search').value.toLowerCase();
    filteredKeys = currentKeys.filter(key => key.toLowerCase().includes(search));
    currentPage = 1;
    renderKeys();
    renderPagination();
}

function renderPagination() {
    const container = document.getElementById('keys-pagination');
    const totalPages = Math.ceil(filteredKeys.length / keysPerPage);
    
    if (totalPages <= 1) {
        container.innerHTML = '';
        return;
    }
    
    let html = '';
    if (currentPage > 1) {
        html += `<button onclick="changePage(${currentPage - 1})">← Prev</button>`;
    }
    
    html += `<span style="padding: 8px 12px;">Page ${currentPage} of ${totalPages}</span>`;
    
    if (currentPage < totalPages) {
        html += `<button onclick="changePage(${currentPage + 1})">Next →</button>`;
    }
    
    container.innerHTML = html;
}

function changePage(page) {
    currentPage = page;
    renderKeys();
    renderPagination();
    document.getElementById('keys-list').scrollTop = 0;
}

// Cluster Status
async function loadClusterStatus() {
    try {
        const url = `${CLUSTER_API}`;
        const response = await fetch(url, {
            headers: {
                'Authorization': `Basic ${AUTH}`,
                'Content-Type': 'application/json'
            }
        });
        
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        
        const data = await response.json();
        renderClusterStatus(data);
    } catch (error) {
        console.error('Failed to load cluster status:', error);
        document.getElementById('cluster-content').innerHTML = `
            <div style="text-align: center; padding: 40px; color: var(--text-muted);">
                <p>Cluster information not available</p>
                <p style="font-size: 12px; margin-top: 8px;">Single node mode or cluster API unavailable</p>
            </div>
        `;
    }
}

function renderClusterStatus(data) {
    const container = document.getElementById('cluster-content');
    
    const nodeId = data.nodeId || 'Unknown';
    const activePeers = data.activePeers || [];
    const knownPeers = data.knownPeers || [];
    const activeCount = data.activePeerCount || activePeers.length;
    
    let html = `
        <div class="cluster-info">
            <div class="info-panel" style="margin-bottom: 24px;">
                <h3 style="margin-bottom: 16px;">Current Node</h3>
                <div class="stat-row">
                    <span class="stat-row-label">Node ID</span>
                    <span class="stat-row-value">${escapeHtml(nodeId)}</span>
                </div>
                <div class="stat-row">
                    <span class="stat-row-label">Active Peers</span>
                    <span class="stat-row-value">${activeCount}</span>
                </div>
                <div class="stat-row">
                    <span class="stat-row-label">Known Peers</span>
                    <span class="stat-row-value">${knownPeers.length}</span>
                </div>
            </div>
            
            <h3 style="margin-bottom: 16px;">Cluster Nodes</h3>
    `;
    
    // Current node
    html += `
        <div class="cluster-node">
            <div class="node-header">
                <span class="node-name">${escapeHtml(nodeId)} (Current)</span>
                <span class="node-status active">Active</span>
            </div>
        </div>
    `;
    
    // Active peers
    if (activePeers.length > 0) {
        activePeers.forEach(peer => {
            if (peer !== nodeId) {
                html += `
                    <div class="cluster-node">
                        <div class="node-header">
                            <span class="node-name">${escapeHtml(peer)}</span>
                            <span class="node-status active">Active</span>
                        </div>
                    </div>
                `;
            }
        });
    } else {
        html += `
            <div style="text-align: center; padding: 20px; color: var(--text-muted);">
                <p>No active peer nodes</p>
                <p style="font-size: 12px; margin-top: 8px;">Running in single-node mode</p>
            </div>
        `;
    }
    
    html += '</div>';
    container.innerHTML = html;
}

// Metrics
async function loadMetrics() {
    try {
        const response = await fetch(`${ACTUATOR_BASE}/prometheus`);
        const text = await response.text();
        renderMetrics(text);
    } catch (error) {
        console.error('Failed to load metrics:', error);
    }
}

function renderMetrics(prometheusText) {
    const container = document.getElementById('metrics-content');
    // Parse and render Prometheus metrics
    container.innerHTML = '<pre style="font-family: monospace; font-size: 12px; overflow-x: auto;">' + escapeHtml(prometheusText) + '</pre>';
}

// Cache Operations
async function invalidateSingleKey(key) {
    if (!confirm(`Are you sure you want to invalidate key "${key}"?`)) {
        return;
    }
    
    try {
        await apiCall(`/${currentCache}/invalidate`, {
            method: 'POST',
            body: JSON.stringify({ key: key })
        });
        showToast(`Key "${key}" invalidated successfully`, 'success');
        loadCacheKeys();
        loadCacheDetails(currentCache);
    } catch (error) {
        showToast('Failed to invalidate key', 'error');
    }
}

async function clearSelectedCache() {
    const cacheName = document.getElementById('cache-selector').value;
    if (!cacheName) {
        showToast('Please select a cache first', 'error');
        return;
    }
    
    if (!confirm(`Are you sure you want to clear all entries in cache "${cacheName}"?`)) {
        return;
    }
    
    try {
        await apiCall(`/${cacheName}/clear`, {
            method: 'POST'
        });
        showToast(`Cache "${cacheName}" cleared successfully`, 'success');
        loadCacheKeys();
        loadCacheDetails(cacheName);
        loadDashboard();
    } catch (error) {
        showToast('Failed to clear cache', 'error');
    }
}

async function showKeyDetails(key) {
    const modal = document.getElementById('key-modal');
    const content = document.getElementById('key-details');
    
    content.innerHTML = '<div class="loading"></div> Loading key details...';
    modal.classList.add('active');
    currentKey = key;
    
    try {
        const data = await apiCall(`/${currentCache}/keys/${encodeURIComponent(key)}`);
        
        let valueDisplay = '';
        if (data.found) {
            const value = data.value;
            if (typeof value === 'object') {
                valueDisplay = `<pre style="background: var(--bg-tertiary); padding: 12px; border-radius: 4px; overflow-x: auto; font-size: 12px;">${escapeHtml(JSON.stringify(value, null, 2))}</pre>`;
            } else {
                valueDisplay = `<div style="font-family: monospace; background: var(--bg-tertiary); padding: 8px; border-radius: 4px;">${escapeHtml(String(value))}</div>`;
            }
        } else {
            valueDisplay = '<div style="color: var(--text-muted); font-style: italic;">Key not found or expired</div>';
        }
        
        content.innerHTML = `
            <div style="margin-bottom: 16px;">
                <strong>Cache:</strong>
                <div style="margin-top: 4px; color: var(--text-secondary);">${escapeHtml(data.cache)}</div>
            </div>
            <div style="margin-bottom: 16px;">
                <strong>Key:</strong>
                <div style="font-family: monospace; background: var(--bg-tertiary); padding: 8px; border-radius: 4px; margin-top: 4px; word-break: break-all;">
                    ${escapeHtml(key)}
                </div>
            </div>
            <div style="margin-bottom: 16px;">
                <strong>Value:</strong>
                <div style="margin-top: 8px;">
                    ${valueDisplay}
                </div>
            </div>
            <div style="margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--border-color);">
                <div style="font-size: 12px; color: var(--text-muted);">
                    Status: ${data.found ? '<span style="color: var(--success-color);">Found</span>' : '<span style="color: var(--danger-color);">Not Found</span>'}
                </div>
            </div>
        `;
    } catch (error) {
        content.innerHTML = `
            <div style="color: var(--danger-color);">
                <strong>Error:</strong> Failed to load key details
                <div style="font-size: 12px; margin-top: 8px; color: var(--text-muted);">${escapeHtml(error.message)}</div>
            </div>
        `;
    }
}

function closeKeyModal() {
    document.getElementById('key-modal').classList.remove('active');
    currentKey = null;
}

async function invalidateKey() {
    if (currentKey && currentCache) {
        await invalidateSingleKey(currentKey);
        closeKeyModal();
    }
}

// Refresh Functions
function refreshDashboard() {
    loadDashboard();
}

function refreshCluster() {
    loadClusterStatus();
}

function refreshMetrics() {
    loadMetrics();
}

// Utility Functions
function formatNumber(num) {
    return new Intl.NumberFormat().format(num);
}

function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

function formatDate(dateString) {
    if (!dateString) return 'N/A';
    const date = new Date(dateString);
    return date.toLocaleString();
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function showToast(message, type = 'success') {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    document.body.appendChild(toast);
    
    setTimeout(() => {
        toast.style.animation = 'slideIn 0.3s ease-out reverse';
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

