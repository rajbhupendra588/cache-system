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
    
    // Store original switchView
    window.originalSwitchView = switchView;
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
    
    // Load cluster info for quick view
    loadClusterQuickView();
}

async function loadClusterQuickView() {
    try {
        const url = `${CLUSTER_API}`;
        const response = await fetch(url, {
            headers: {
                'Authorization': `Basic ${AUTH}`,
                'Content-Type': 'application/json'
            }
        });
        
        if (response.ok) {
            const data = await response.json();
            const activeCount = (data.activePeerCount || 0) + 1; // +1 for current node
            document.getElementById('cluster-nodes-quick').textContent = activeCount;
        } else {
            document.getElementById('cluster-nodes-quick').textContent = '1';
        }
    } catch (error) {
        document.getElementById('cluster-nodes-quick').textContent = '1';
    }
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
let clusterAutoRefreshInterval = null;

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
        document.getElementById('cluster-topology').innerHTML = `
            <div style="text-align: center; padding: 40px; color: var(--text-muted);">
                <p>Cluster information not available</p>
                <p style="font-size: 12px; margin-top: 8px;">Single node mode or cluster API unavailable</p>
            </div>
        `;
    }
}

function renderClusterStatus(data) {
    const nodeId = data.nodeId || 'Unknown';
    const activePeers = data.activePeers || [];
    const knownPeers = data.knownPeers || [];
    const activeCount = data.activePeerCount || activePeers.length;
    const peerHealth = data.peerHealth || {};
    const lastHeartbeatTimes = data.lastHeartbeatTimes || {};
    const consecutiveFailures = data.consecutiveFailures || {};
    
    // Update overview stats
    document.getElementById('current-node-id').textContent = nodeId;
    document.getElementById('active-nodes-count').textContent = activeCount + 1; // +1 for current node
    document.getElementById('known-nodes-count').textContent = knownPeers.length + 1;
    
    // Count healthy nodes
    let healthyCount = 1; // Current node is always healthy
    Object.values(peerHealth).forEach(healthy => {
        if (healthy) healthyCount++;
    });
    document.getElementById('healthy-nodes-count').textContent = healthyCount;
    
    // Render topology
    renderClusterTopology(nodeId, activePeers, knownPeers, peerHealth);
    
    // Render node details
    renderNodeDetails(nodeId, activePeers, knownPeers, peerHealth, lastHeartbeatTimes, consecutiveFailures);
}

function renderClusterTopology(currentNodeId, activePeers, knownPeers, peerHealth) {
    const container = document.getElementById('cluster-topology');
    
    if (knownPeers.length === 0) {
        container.innerHTML = `
            <div class="single-node-view">
                <div class="topology-node current">
                    <div class="node-circle healthy">
                        <div class="node-icon">🖥️</div>
                    </div>
                    <div class="node-label">${escapeHtml(currentNodeId)}</div>
                    <div class="node-badge current-badge">Current</div>
                </div>
                <div class="single-node-message">
                    <p>Running in single-node mode</p>
                    <p class="hint">Configure peers in application.yml to enable clustering</p>
                </div>
            </div>
        `;
        return;
    }
    
    // Create all nodes list (current + peers)
    const allNodes = [currentNodeId, ...knownPeers];
    const activeNodes = new Set([currentNodeId, ...activePeers]);
    
    let html = '<div class="topology-grid">';
    
    allNodes.forEach((node, index) => {
        const isCurrent = node === currentNodeId;
        const isActive = activeNodes.has(node);
        const isHealthy = isCurrent || (peerHealth[node] !== false);
        const nodeClass = isCurrent ? 'current' : (isActive ? 'active' : 'inactive');
        const healthClass = isHealthy ? 'healthy' : 'unhealthy';
        
        // Truncate long node IDs for display
        const displayNodeId = node.length > 20 ? node.substring(0, 17) + '...' : node;
        const fullNodeId = node; // Keep full ID for tooltip
        
        html += `
            <div class="topology-node ${nodeClass}" title="${escapeHtml(fullNodeId)}">
                <div class="node-circle ${healthClass}">
                    <div class="node-icon">${isCurrent ? '🖥️' : '💻'}</div>
                    ${!isHealthy ? '<div class="health-indicator">⚠️</div>' : ''}
                </div>
                <div class="node-label" title="${escapeHtml(fullNodeId)}">${escapeHtml(displayNodeId)}</div>
                ${isCurrent ? '<div class="node-badge current-badge">Current</div>' : ''}
                ${!isActive ? '<div class="node-badge inactive-badge">Inactive</div>' : ''}
                ${!isHealthy ? '<div class="node-badge unhealthy-badge">Unhealthy</div>' : ''}
            </div>
        `;
    });
    
    html += '</div>';
    
    // Add connection lines visualization
    if (allNodes.length > 1) {
        html += '<div class="topology-connections">';
        html += '<div class="connection-hint">Connected nodes exchange heartbeats every 2-5 seconds</div>';
        html += '</div>';
    }
    
    container.innerHTML = html;
}

function renderNodeDetails(currentNodeId, activePeers, knownPeers, peerHealth, lastHeartbeatTimes, consecutiveFailures) {
    const container = document.getElementById('cluster-nodes-list');
    
    const allNodes = [currentNodeId, ...knownPeers];
    const activeNodes = new Set([currentNodeId, ...activePeers]);
    
    let html = '';
    
    allNodes.forEach(node => {
        const isCurrent = node === currentNodeId;
        const isActive = activeNodes.has(node);
        const isHealthy = isCurrent || (peerHealth[node] !== false);
        const lastHeartbeat = lastHeartbeatTimes[node];
        const failures = consecutiveFailures[node] || 0;
        
        const timeSinceHeartbeat = lastHeartbeat ? 
            Math.floor((Date.now() - lastHeartbeat) / 1000) : null;
        
        html += `
            <div class="node-detail-card ${isCurrent ? 'current-node' : ''} ${isActive ? 'active' : 'inactive'}">
                <div class="node-detail-header">
                    <div class="node-detail-title">
                        <span class="node-icon-large">${isCurrent ? '🖥️' : '💻'}</span>
                        <div style="flex: 1; min-width: 0;">
                            <div class="node-detail-name" title="${escapeHtml(node)}">${escapeHtml(node)}</div>
                            ${isCurrent ? '<div class="node-detail-badge current">Current Node</div>' : ''}
                        </div>
                    </div>
                    <div class="node-detail-status">
                        <span class="status-indicator ${isActive ? 'active' : 'inactive'} ${isHealthy ? 'healthy' : 'unhealthy'}"></span>
                        <span class="status-text">${isActive ? (isHealthy ? 'Active & Healthy' : 'Active (Unhealthy)') : 'Inactive'}</span>
                    </div>
                </div>
                
                <div class="node-detail-body">
                    <div class="node-detail-row">
                        <span class="detail-label">Status:</span>
                        <span class="detail-value ${isActive ? 'text-success' : 'text-danger'}">${isActive ? 'Active' : 'Inactive'}</span>
                    </div>
                    ${!isCurrent ? `
                        <div class="node-detail-row">
                            <span class="detail-label">Health:</span>
                            <span class="detail-value ${isHealthy ? 'text-success' : 'text-warning'}">${isHealthy ? 'Healthy' : 'Unhealthy'}</span>
                        </div>
                        ${lastHeartbeat ? `
                            <div class="node-detail-row">
                                <span class="detail-label">Last Heartbeat:</span>
                                <span class="detail-value">${formatTimeAgo(timeSinceHeartbeat)} ago</span>
                            </div>
                        ` : ''}
                        ${failures > 0 ? `
                            <div class="node-detail-row">
                                <span class="detail-label">Consecutive Failures:</span>
                                <span class="detail-value text-warning">${failures}</span>
                            </div>
                        ` : ''}
                    ` : `
                        <div class="node-detail-row">
                            <span class="detail-label">Mode:</span>
                            <span class="detail-value">Current Node</span>
                        </div>
                    `}
                </div>
            </div>
        `;
    });
    
    if (allNodes.length === 0) {
        html = '<div class="no-nodes-message">No cluster nodes configured</div>';
    }
    
    container.innerHTML = html;
}

function formatTimeAgo(seconds) {
    if (seconds < 60) return `${seconds}s`;
    if (seconds < 3600) return `${Math.floor(seconds / 60)}m`;
    return `${Math.floor(seconds / 3600)}h`;
}

function toggleClusterAutoRefresh() {
    const checkbox = document.getElementById('cluster-auto-refresh');
    if (checkbox.checked) {
        if (clusterAutoRefreshInterval) clearInterval(clusterAutoRefreshInterval);
        clusterAutoRefreshInterval = setInterval(refreshCluster, 5000);
    } else {
        if (clusterAutoRefreshInterval) {
            clearInterval(clusterAutoRefreshInterval);
            clusterAutoRefreshInterval = null;
        }
    }
}

// Override switchView to handle cluster auto-refresh
const originalSwitchViewFunc = switchView;
switchView = function(viewName) {
    // Stop any existing auto-refresh
    if (clusterAutoRefreshInterval) {
        clearInterval(clusterAutoRefreshInterval);
        clusterAutoRefreshInterval = null;
    }
    
    // Call original function
    originalSwitchViewFunc(viewName);
    
    // Start auto-refresh for cluster view if enabled
    if (viewName === 'cluster') {
        const checkbox = document.getElementById('cluster-auto-refresh');
        if (checkbox && checkbox.checked) {
            toggleClusterAutoRefresh();
        }
    }
};

// Metrics
let metricsData = {};

async function loadMetrics() {
    try {
        // Load both Prometheus metrics and cache stats
        const [prometheusResponse, cacheResponse] = await Promise.all([
            fetch(`${ACTUATOR_BASE}/prometheus`),
            apiCall('')
        ]);
        
        const prometheusText = await prometheusResponse.text();
        const cacheData = cacheResponse;
        
        parseAndRenderMetrics(prometheusText, cacheData);
    } catch (error) {
        console.error('Failed to load metrics:', error);
        document.getElementById('metrics-content').innerHTML = `
            <div style="text-align: center; padding: 40px; color: var(--text-muted);">
                <p>Failed to load metrics</p>
                <p style="font-size: 12px; margin-top: 8px;">${escapeHtml(error.message)}</p>
            </div>
        `;
    }
}

function parseAndRenderMetrics(prometheusText, cacheData) {
    // Parse Prometheus format
    const metrics = parsePrometheusMetrics(prometheusText);
    metricsData = metrics;
    
    // Update cache filter
    updateMetricsCacheFilter(metrics);
    
    // Render summary
    renderMetricsSummary(metrics, cacheData);
    
    // Render detailed metrics
    renderDetailedMetrics(metrics, cacheData);
}

function parsePrometheusMetrics(text) {
    const metrics = {
        byCache: {},
        system: {}
    };
    
    const lines = text.split('\n');
    for (const line of lines) {
        if (line.trim() === '' || line.startsWith('#')) continue;
        
        // Parse metric line: metric_name{tags} value or metric_name value
        // Handle both with and without tags
        let match = line.match(/^([a-zA-Z_][a-zA-Z0-9_]*)\{([^}]*)\}\s+([0-9.eE+-]+)$/);
        if (!match) {
            // Try without tags
            match = line.match(/^([a-zA-Z_][a-zA-Z0-9_]*)\s+([0-9.eE+-]+)$/);
            if (!match) continue;
        }
        
        const metricName = match[1];
        const tagsStr = match[2] || '';
        const value = match[3] || match[2];
        const valueNum = parseFloat(value);
        
        if (isNaN(valueNum)) continue;
        
        // Parse tags
        const tags = {};
        if (tagsStr) {
            tagsStr.split(',').forEach(tag => {
                const tagMatch = tag.match(/([^=]+)="?([^"]+)"?/);
                if (tagMatch) {
                    tags[tagMatch[1].trim()] = tagMatch[2].trim();
                }
            });
        }
        
        const cacheName = tags.cache || 'system';
        
        // Group by cache
        if (!metrics.byCache[cacheName]) {
            metrics.byCache[cacheName] = {};
        }
        
        // Store metric - handle customcache metrics
        if (metricName.startsWith('customcache_')) {
            // Remove prefix and normalize
            let shortName = metricName.replace('customcache_', '');
            
            // Remove _total suffix for counters
            const isCounter = shortName.endsWith('_total');
            if (isCounter) {
                shortName = shortName.replace(/_total$/, '');
            }
            
            // Convert underscores to dots for consistent display
            const displayName = shortName.replace(/_/g, '.');
            
            // Handle histogram/summary metrics (they have _count, _sum, _max, etc.)
            if (shortName.includes('_count') || shortName.includes('_sum') || 
                shortName.includes('_max') || shortName.includes('_mean')) {
                const baseName = shortName.split('_')[0];
                const baseDisplayName = baseName.replace(/_/g, '.');
                if (!metrics.byCache[cacheName][baseDisplayName]) {
                    metrics.byCache[cacheName][baseDisplayName] = [];
                }
                // Store with type info
                metrics.byCache[cacheName][baseDisplayName].push({
                    name: displayName,
                    value: valueNum,
                    tags: tags,
                    fullName: metricName,
                    type: shortName.includes('_count') ? 'count' : 
                          shortName.includes('_sum') ? 'sum' : 
                          shortName.includes('_max') ? 'max' : 'mean'
                });
            } else {
                if (!metrics.byCache[cacheName][displayName]) {
                    metrics.byCache[cacheName][displayName] = [];
                }
                metrics.byCache[cacheName][displayName].push({
                    name: displayName,
                    value: valueNum,
                    tags: tags,
                    fullName: metricName
                });
            }
        } else {
            // For system metrics, normalize histogram/summary metric names
            // e.g., spring_security_authentications_seconds_count -> spring_security_authentications_seconds
            let normalizedName = metricName;
            let metricType = null;
            
            // Check if it's a histogram/summary metric with suffix
            // Handle patterns like: metric_name_count, metric_name_sum, metric_name_max, etc.
            const suffixPatterns = [
                { suffix: '_count', type: 'count' },
                { suffix: '_sum', type: 'sum' },
                { suffix: '_max', type: 'max' },
                { suffix: '_mean', type: 'mean' },
                { suffix: '_total', type: 'total' },
                { suffix: '_active_count', type: 'count' },
                { suffix: '_duration_sum', type: 'sum' }
            ];
            
            for (const pattern of suffixPatterns) {
                if (metricName.endsWith(pattern.suffix)) {
                    normalizedName = metricName.slice(0, -pattern.suffix.length);
                    metricType = pattern.type;
                    break;
                }
            }
            
            // Ensure normalized name is stored (use lowercase for consistency)
            normalizedName = normalizedName.toLowerCase();
            
            if (!metrics.system[normalizedName]) {
                metrics.system[normalizedName] = [];
            }
            metrics.system[normalizedName].push({
                name: metricName,
                value: valueNum,
                tags: tags,
                fullName: metricName,
                type: metricType
            });
        }
    }
    
    return metrics;
}

function updateMetricsCacheFilter(metrics) {
    const filter = document.getElementById('metrics-cache-filter');
    filter.innerHTML = '<option value="">All Caches</option>';
    
    Object.keys(metrics.byCache).forEach(cacheName => {
        const option = document.createElement('option');
        option.value = cacheName;
        option.textContent = cacheName === 'system' ? 'System Metrics' : cacheName;
        filter.appendChild(option);
    });
}

function renderMetricsSummary(metrics, cacheData) {
    const container = document.getElementById('metrics-summary');
    
    // Calculate totals across all caches
    let totalHits = 0;
    let totalMisses = 0;
    let totalEvictions = 0;
    let totalSize = 0;
    let totalMemory = 0;
    let totalLoadTime = 0;
    let loadCount = 0;
    
    Object.entries(metrics.byCache).forEach(([cacheName, cacheMetrics]) => {
        // Handle normalized metric names (dots format)
        const hits = cacheMetrics.hits;
        const misses = cacheMetrics.misses;
        const evictions = cacheMetrics.evictions;
        const size = cacheMetrics['size.entries'];
        const memory = cacheMetrics['memory.bytes'];
        const loadTime = cacheMetrics['load.time.ms'];
        
        if (hits) {
            totalHits += hits.reduce((sum, m) => sum + m.value, 0);
        }
        if (misses) {
            totalMisses += misses.reduce((sum, m) => sum + m.value, 0);
        }
        if (evictions) {
            totalEvictions += evictions.reduce((sum, m) => sum + m.value, 0);
        }
        if (size) {
            totalSize += size.reduce((sum, m) => sum + m.value, 0);
        }
        if (memory) {
            totalMemory += memory.reduce((sum, m) => sum + m.value, 0);
        }
        if (loadTime) {
            loadTime.forEach(m => {
                if (m.value > 0 && !m.type) { // Only count non-aggregated values
                    totalLoadTime += m.value;
                    loadCount++;
                } else if (m.aggregated) {
                    // Use aggregated value
                    totalLoadTime += m.value * (m.count || 1);
                    loadCount += (m.count || 1);
                }
            });
        }
    });
    
    const totalRequests = totalHits + totalMisses;
    const avgHitRatio = totalRequests > 0 ? ((totalHits / totalRequests) * 100).toFixed(1) : 0;
    const avgLoadTime = loadCount > 0 ? (totalLoadTime / loadCount).toFixed(2) : 0;
    
    container.innerHTML = `
        <div class="metric-summary-card">
            <div class="metric-summary-icon">🎯</div>
            <div class="metric-summary-content">
                <div class="metric-summary-label">Hit Ratio</div>
                <div class="metric-summary-value">${avgHitRatio}%</div>
                <div class="metric-summary-detail">${formatNumber(totalHits)} hits / ${formatNumber(totalRequests)} requests</div>
            </div>
        </div>
        <div class="metric-summary-card">
            <div class="metric-summary-icon">⚡</div>
            <div class="metric-summary-content">
                <div class="metric-summary-label">Avg Load Time</div>
                <div class="metric-summary-value">${avgLoadTime}ms</div>
                <div class="metric-summary-detail">${loadCount > 0 ? formatNumber(loadCount) + ' operations' : 'No load operations'}</div>
            </div>
        </div>
        <div class="metric-summary-card">
            <div class="metric-summary-icon">🗑️</div>
            <div class="metric-summary-content">
                <div class="metric-summary-label">Total Evictions</div>
                <div class="metric-summary-value">${formatNumber(totalEvictions)}</div>
                <div class="metric-summary-detail">Across all caches</div>
            </div>
        </div>
        <div class="metric-summary-card">
            <div class="metric-summary-icon">💾</div>
            <div class="metric-summary-content">
                <div class="metric-summary-label">Total Memory</div>
                <div class="metric-summary-value">${formatBytes(totalMemory)}</div>
                <div class="metric-summary-detail">${formatNumber(totalSize)} entries</div>
            </div>
        </div>
    `;
}

function renderDetailedMetrics(metrics, cacheData) {
    const container = document.getElementById('metrics-content');
    const selectedCache = document.getElementById('metrics-cache-filter').value;
    
    let html = '';
    
    // Filter caches if selected
    const cachesToShow = selectedCache ? 
        (selectedCache === 'system' ? ['system'] : [selectedCache]) : 
        Object.keys(metrics.byCache);
    
    cachesToShow.forEach(cacheName => {
        const cacheMetrics = metrics.byCache[cacheName];
        if (!cacheMetrics || Object.keys(cacheMetrics).length === 0) return;
        
        html += `
            <div class="metrics-cache-section">
                <h3 class="metrics-cache-title">
                    <span class="cache-icon">📦</span>
                    ${cacheName === 'system' ? 'System Metrics' : escapeHtml(cacheName)}
                </h3>
                
                <div class="metrics-grid">
        `;
        
        // Render each metric type
        Object.entries(cacheMetrics).forEach(([metricType, metricArray]) => {
            if (!metricArray || metricArray.length === 0) return;
            
            // For metrics with multiple values (like histogram), aggregate them
            let displayValue = 0;
            let metric = metricArray[0];
            
            if (metricArray.length > 1) {
                // Handle histogram metrics - use mean if available, otherwise sum
                const countMetric = metricArray.find(m => m.type === 'count');
                const sumMetric = metricArray.find(m => m.type === 'sum');
                const maxMetric = metricArray.find(m => m.type === 'max');
                
                if (countMetric && sumMetric) {
                    displayValue = sumMetric.value / countMetric.value; // Average
                    metric = { ...sumMetric, aggregated: true, count: countMetric.value };
                } else if (maxMetric) {
                    displayValue = maxMetric.value;
                    metric = maxMetric;
                } else {
                    // Sum all values
                    displayValue = metricArray.reduce((sum, m) => sum + m.value, 0);
                    metric = metricArray[0];
                }
            } else {
                displayValue = metric.value;
            }
            
            html += renderMetricCard(metricType, displayValue, metric);
        });
        
        html += `
                </div>
            </div>
        `;
    });
    
    // System metrics
    if (!selectedCache || selectedCache === 'system') {
        if (Object.keys(metrics.system).length > 0) {
            html += `
                <div class="metrics-cache-section">
                    <h3 class="metrics-cache-title">
                        <span class="cache-icon">⚙️</span>
                        System Metrics
                    </h3>
                    <div class="metrics-grid">
            `;
            
            // Use a Set to track which metrics we've already rendered
            const renderedMetrics = new Set();
            
            Object.entries(metrics.system).forEach(([metricName, metricArray]) => {
                if (!metricArray || metricArray.length === 0) return;
                
                // Skip if we've already rendered this metric
                if (renderedMetrics.has(metricName)) return;
                renderedMetrics.add(metricName);
                
                // For system metrics, aggregate values with different tags
                // Check if this is a histogram/summary metric
                const hasHistogramTypes = metricArray.some(m => m.type);
                
                if (hasHistogramTypes) {
                    // Group histogram metrics together
                    const countMetrics = metricArray.filter(m => m.type === 'count');
                    const sumMetrics = metricArray.filter(m => m.type === 'sum');
                    const maxMetrics = metricArray.filter(m => m.type === 'max');
                    const meanMetrics = metricArray.filter(m => m.type === 'mean');
                    const totalMetrics = metricArray.filter(m => m.type === 'total');
                    
                    // Aggregate values across different tag combinations
                    const totalCount = countMetrics.reduce((sum, m) => sum + m.value, 0);
                    const totalSum = sumMetrics.reduce((sum, m) => sum + m.value, 0);
                    const maxValue = maxMetrics.length > 0 ? 
                        Math.max(...maxMetrics.map(m => m.value)) : 0;
                    const totalValue = totalMetrics.reduce((sum, m) => sum + m.value, 0);
                    const meanValue = meanMetrics.length > 0 ?
                        meanMetrics.reduce((sum, m) => sum + m.value, 0) / meanMetrics.length : 0;
                    
                    // Calculate average if we have count and sum (most common case)
                    if (totalCount > 0 && totalSum > 0) {
                        const avgValue = totalSum / totalCount;
                        const aggregatedMetric = {
                            name: metricName,
                            value: avgValue,
                            tags: {},
                            fullName: metricName,
                            aggregated: true,
                            count: totalCount,
                            sum: totalSum,
                            max: maxValue > 0 ? maxValue : undefined
                        };
                        html += renderMetricCard(metricName, avgValue, aggregatedMetric);
                    } else if (maxValue > 0) {
                        // Show max value if available
                        html += renderMetricCard(metricName, maxValue, {
                            name: metricName,
                            value: maxValue,
                            tags: {},
                            fullName: metricName,
                            aggregated: maxMetrics.length > 1,
                            instanceCount: maxMetrics.length
                        });
                    } else if (totalCount > 0) {
                        // Show count if available
                        html += renderMetricCard(metricName, totalCount, {
                            name: metricName,
                            value: totalCount,
                            tags: {},
                            fullName: metricName,
                            aggregated: countMetrics.length > 1,
                            instanceCount: countMetrics.length
                        });
                    } else if (totalValue > 0) {
                        // Show total if available
                        html += renderMetricCard(metricName, totalValue, {
                            name: metricName,
                            value: totalValue,
                            tags: {},
                            fullName: metricName,
                            aggregated: totalMetrics.length > 1,
                            instanceCount: totalMetrics.length
                        });
                    } else if (meanValue > 0) {
                        // Show mean if available
                        html += renderMetricCard(metricName, meanValue, {
                            name: metricName,
                            value: meanValue,
                            tags: {},
                            fullName: metricName,
                            aggregated: meanMetrics.length > 1,
                            instanceCount: meanMetrics.length
                        });
                    }
                } else {
                    // For non-histogram metrics, aggregate all values with different tags
                    const totalValue = metricArray.reduce((sum, m) => sum + m.value, 0);
                    const uniqueTags = new Set();
                    metricArray.forEach(m => {
                        const tagStr = JSON.stringify(m.tags || {});
                        uniqueTags.add(tagStr);
                    });
                    
                    // For certain metrics, we might want to show sum, for others we might want average
                    // For now, show sum for counters and totals, but we could enhance this
                    const displayValue = totalValue;
                    
                    // Always render non-histogram metrics (they're already normalized and deduplicated)
                    // Show aggregated value with context about tag combinations
                    const aggregatedMetric = {
                        name: metricName,
                        value: displayValue,
                        tags: {},
                        fullName: metricName,
                        aggregated: uniqueTags.size > 1,
                        instanceCount: uniqueTags.size
                    };
                    
                    html += renderMetricCard(metricName, displayValue, aggregatedMetric);
                }
            });
            
            html += `
                    </div>
                </div>
            `;
        }
    }
    
    if (html === '') {
        html = '<div style="text-align: center; padding: 40px; color: var(--text-muted);">No metrics available</div>';
    }
    
    container.innerHTML = html;
}

function renderMetricCard(metricType, value, metric) {
    const metricInfo = getMetricInfo(metricType);
    const displayValue = formatMetricValue(metricType, value);
    const description = metricInfo.description || metricType;
    const icon = metricInfo.icon || '📊';
    
    // Add additional context if available
    let additionalInfo = '';
    if (metric.aggregated) {
        if (metric.count !== undefined) {
            additionalInfo = `<div class="metric-additional-info">Based on ${formatNumber(metric.count)} operations`;
            if (metric.sum !== undefined) {
                additionalInfo += ` (Total: ${formatMetricValue(metricType, metric.sum)})`;
            }
            if (metric.max !== undefined && metric.max > 0) {
                additionalInfo += ` | Max: ${formatMetricValue(metricType, metric.max)}`;
            }
            additionalInfo += '</div>';
        } else if (metric.instanceCount && metric.instanceCount > 1) {
            additionalInfo = `<div class="metric-additional-info">Aggregated from ${metric.instanceCount} tag combinations</div>`;
        }
    } else if (metric.tags && metric.tags.reason) {
        additionalInfo = `<div class="metric-additional-info">Reason: ${escapeHtml(metric.tags.reason)}</div>`;
    }
    
    return `
        <div class="metric-card">
            <div class="metric-card-header">
                <span class="metric-icon">${icon}</span>
                <div class="metric-title-group">
                    <div class="metric-title">${metricInfo.label || metricType}</div>
                    <div class="metric-description">${description}</div>
                    ${additionalInfo}
                </div>
            </div>
            <div class="metric-card-value">
                ${displayValue}
            </div>
            ${metricInfo.showBar ? `
                <div class="metric-bar-container">
                    <div class="metric-bar" style="width: ${Math.min((value / (metricInfo.max || 100)) * 100, 100)}%"></div>
                </div>
            ` : ''}
        </div>
    `;
}

function getMetricInfo(metricType) {
    // Normalize metric type (handle both dot and underscore formats)
    const normalized = metricType.replace(/_/g, '.').replace(/\.(total|count|sum|max|mean)$/, '');
    
    // Check if it's a Spring Security metric
    if (normalized.includes('spring.security') || normalized.includes('spring_security')) {
        if (normalized.includes('authentications')) {
            return {
                label: 'Authentication Time',
                description: 'Time taken for authentication operations. Lower is better.',
                icon: '🔐',
                showBar: false,
                color: 'primary'
            };
        }
    }
    
    const info = {
        'hits': {
            label: 'Cache Hits',
            description: 'Number of successful cache lookups. Higher is better.',
            icon: '✅',
            showBar: false,
            color: 'success'
        },
        'misses': {
            label: 'Cache Misses',
            description: 'Number of failed cache lookups. Lower is better.',
            icon: '❌',
            showBar: false,
            color: 'warning'
        },
        'evictions': {
            label: 'Evictions',
            description: 'Number of entries removed from cache due to size/memory limits or TTL expiration.',
            icon: '🗑️',
            showBar: false,
            color: 'info'
        },
        'load.time.ms': {
            label: 'Load Time',
            description: 'Average time (in milliseconds) to load data into cache when there\'s a miss.',
            icon: '⏱️',
            showBar: false,
            unit: 'ms',
            color: 'primary'
        },
        'size.entries': {
            label: 'Cache Size',
            description: 'Current number of entries stored in this cache.',
            icon: '📊',
            showBar: false,
            color: 'info'
        },
        'memory.bytes': {
            label: 'Memory Usage',
            description: 'Total memory consumed by cache entries (estimated using JOL).',
            icon: '💾',
            showBar: false,
            color: 'primary'
        }
    };
    
    return info[normalized] || {
        label: metricType.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase()),
        description: 'Performance metric for monitoring cache operations',
        icon: '📈',
        showBar: false,
        color: 'primary'
    };
}

function formatMetricValue(metricType, value) {
    const normalized = metricType.replace(/_/g, '.').replace(/\.total$/, '');
    
    if (normalized === 'memory.bytes' || normalized.includes('memory')) {
        return formatBytes(value);
    } else if (normalized === 'load.time.ms' || normalized.includes('load.time') || normalized.includes('latency')) {
        return `${value.toFixed(2)}ms`;
    } else if (normalized === 'size.entries' || normalized.includes('size')) {
        return formatNumber(Math.round(value));
    } else if (normalized.includes('ratio') || normalized.includes('percent')) {
        return `${(value * 100).toFixed(2)}%`;
    } else if (normalized.includes('rate')) {
        return `${value.toFixed(2)}/s`;
    } else {
        // For counters, show as integer
        return formatNumber(Math.round(value));
    }
}

function filterMetricsByCache() {
    renderDetailedMetrics(metricsData, {});
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

