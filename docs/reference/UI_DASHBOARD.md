# Modern Cache Management Dashboard

## 🎨 Overview

A modern, responsive web dashboard for managing and monitoring the distributed cache system. The dashboard provides an intuitive interface for viewing cache statistics, browsing entries, managing clusters, and monitoring performance.

## 🌐 Access

**URL:** http://localhost:8080

**Credentials:** 
- Username: `admin`
- Password: `admin`

> **Note:** Change credentials in production using environment variables `CACHE_ADMIN_USERNAME` and `CACHE_ADMIN_PASSWORD`

## ✨ Features

### 📊 Dashboard View
- **Overview Statistics:**
  - Total caches count
  - Total entries across all caches
  - Average hit ratio
  - Total memory usage
  
- **Cache List:**
  - Visual cards for each cache
  - Quick stats (entries, hits, misses, hit ratio)
  - Click to navigate to cache browser

### 🗂️ Cache Browser
- **Cache Selection:**
  - Dropdown to select any configured cache
  - Real-time statistics panel
  
- **Key Management:**
  - Browse all cache keys
  - Search/filter keys
  - Pagination (50 keys per page)
  - Click key to view details
  - Invalidate individual keys
  - Clear entire cache

- **Key Details Modal:**
  - View cached value
  - JSON formatting for objects
  - Invalidate key directly from modal

### 🌐 Cluster Status
- **Node Information:**
  - Current node ID
  - Active peer count
  - Known peer list
  
- **Cluster Visualization:**
  - List of all cluster nodes
  - Status indicators (Active/Inactive)
  - Current node highlighting

### 📈 Metrics View
- **Performance Metrics:**
  - Prometheus metrics display
  - Real-time performance data
  - System health indicators

## 🎨 Design Features

### Modern Dark Theme
- Professional dark color scheme
- High contrast for readability
- Smooth animations and transitions
- Gradient accents

### Responsive Design
- Desktop-optimized layout
- Mobile-friendly sidebar
- Adaptive grid layouts
- Touch-friendly controls

### User Experience
- Real-time auto-refresh (30s intervals)
- Toast notifications for actions
- Loading indicators
- Smooth page transitions
- Keyboard-friendly navigation

## 🛠️ Technical Details

### Frontend Stack
- **HTML5** - Semantic markup
- **CSS3** - Modern styling with CSS variables
- **Vanilla JavaScript** - No framework dependencies
- **Fetch API** - Modern HTTP client

### API Integration
- RESTful API calls to `/api/cache/**`
- Cluster API integration `/api/cluster`
- Actuator metrics `/actuator/prometheus`
- Basic authentication

### File Structure
```
src/main/resources/static/
├── index.html          # Main dashboard page
├── css/
│   └── dashboard.css  # All styling
└── js/
    └── dashboard.js   # All functionality
```

## 🚀 Usage

### Viewing Cache Statistics
1. Navigate to Dashboard
2. View overview statistics
3. Click on any cache card to browse

### Browsing Cache Keys
1. Go to "Caches" tab
2. Select a cache from dropdown
3. Click "Load Keys"
4. Use search to filter keys
5. Click any key to view details

### Invalidating Cache
- **Single Key:** Click 🗑️ icon next to key
- **All Keys:** Click "Clear Cache" button
- **From Modal:** Click "Invalidate" in key details

### Monitoring Cluster
1. Go to "Cluster" tab
2. View current node and peers
3. Refresh to update status

## 📱 Responsive Breakpoints

- **Desktop:** Full sidebar + main content
- **Tablet (≤1024px):** Single column cache browser
- **Mobile (≤768px):** Collapsed sidebar, stacked layout

## 🎯 Performance

- **Lightweight:** No heavy frameworks
- **Fast Loading:** Optimized CSS/JS
- **Efficient:** Lazy loading of cache data
- **Caching:** Browser caching for static assets

## 🔒 Security

- Basic authentication for API calls
- Static UI resources are public
- API endpoints require admin role
- XSS protection via HTML escaping

## 🎨 Color Scheme

- **Primary:** Indigo (#6366f1)
- **Secondary:** Purple (#8b5cf6)
- **Success:** Green (#10b981)
- **Danger:** Red (#ef4444)
- **Background:** Dark slate (#0f172a)

## 📝 Browser Support

- Chrome/Edge (latest)
- Firefox (latest)
- Safari (latest)
- Mobile browsers

---

**Enjoy your modern cache management dashboard!** 🎉

