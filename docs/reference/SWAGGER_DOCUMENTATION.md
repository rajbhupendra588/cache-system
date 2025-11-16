# Swagger/OpenAPI Documentation

## 🎯 Overview

The application now includes comprehensive Swagger/OpenAPI documentation using SpringDoc OpenAPI 3.0. This provides interactive API documentation and testing capabilities.

## 📚 Access Points

### Swagger UI
- **Primary URL:** http://localhost:8080/swagger-ui.html
- **Alternative:** http://localhost:8080/swagger-ui/index.html

### OpenAPI JSON Specification
- **URL:** http://localhost:8080/v3/api-docs
- **Format:** OpenAPI 3.0.1 JSON

## 🔐 Authentication

### Swagger UI Access
- Swagger UI is **publicly accessible** (no authentication required)
- This allows anyone to view the API documentation

### API Endpoints
- All API endpoints require **Basic Authentication**
- Default credentials: `admin` / `admin`
- Use the **"Authorize"** button in Swagger UI to set credentials
- Once authorized, all API calls will include authentication headers

## ✨ Features

### Interactive Documentation
- **Try-it-out:** Test API endpoints directly from the browser
- **Request/Response Examples:** See example payloads and responses
- **Schema Definitions:** View detailed data models
- **Parameter Descriptions:** Clear documentation of all parameters

### Organized Endpoints
- **Cache Management Tag:** 7 endpoints for cache operations
- **Cluster Management Tag:** 3 endpoints for cluster coordination

### Documented Endpoints

#### Cache Management
1. **GET /api/cache** - List all caches with statistics
2. **GET /api/cache/{name}** - Get cache details
3. **GET /api/cache/{name}/stats** - Get cache statistics
4. **GET /api/cache/{name}/keys** - List cache keys (with pagination)
5. **GET /api/cache/{name}/keys/{key}** - Get cache value by key
6. **POST /api/cache/{name}/invalidate** - Invalidate cache entries
7. **POST /api/cache/{name}/clear** - Clear entire cache

#### Cluster Management
1. **GET /api/cluster** - Get cluster status
2. **GET /api/cluster/nodes** - List all cluster nodes
3. **GET /api/cluster/nodes/{nodeId}** - Get node details

## 🛠️ Configuration

### Dependencies
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

### Application Configuration (`application.yml`)
```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
    enabled: true
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
    tags-sorter: alpha
    operations-sorter: alpha
    try-it-out-enabled: true
    filter: true
```

### Security Configuration
Swagger UI endpoints are configured to be publicly accessible:
- `/swagger-ui.html`
- `/swagger-ui/**`
- `/v3/api-docs/**`
- `/swagger-resources/**`

## 📝 API Documentation Annotations

### Controller-Level
- `@Tag` - Groups endpoints into logical categories
- `@SecurityRequirement` - Declares authentication requirements

### Method-Level
- `@Operation` - Describes the endpoint operation
- `@ApiResponse` / `@ApiResponses` - Documents response codes
- `@Parameter` - Describes path/query parameters

### Model-Level
- `@Schema` - Documents request/response models

## 🎨 Customization

### API Information
The OpenAPI configuration (`OpenApiConfig.java`) includes:
- **Title:** Distributed Cache Management System API
- **Version:** 1.0.0
- **Description:** Comprehensive API documentation
- **Contact Information:** Support details
- **License:** Internal Use

### Security Scheme
- **Type:** HTTP Basic Authentication
- **Scheme:** basic
- **Description:** Admin credentials required

## 🚀 Usage Examples

### Viewing Documentation
1. Navigate to http://localhost:8080/swagger-ui.html
2. Browse endpoints by tag (Cache Management, Cluster Management)
3. Expand any endpoint to see details

### Testing an Endpoint
1. Click on an endpoint to expand it
2. Click **"Try it out"** button
3. Fill in required parameters
4. Click **"Authorize"** if not already authenticated
5. Click **"Execute"** to send the request
6. View the response below

### Setting Authentication
1. Click the **"Authorize"** button at the top
2. Enter username: `admin`
3. Enter password: `admin`
4. Click **"Authorize"**
5. Click **"Close"**
6. All subsequent requests will include authentication

## 📊 OpenAPI Specification

The OpenAPI 3.0.1 specification includes:
- **Info:** API metadata and version
- **Servers:** Base URL configuration
- **Paths:** All endpoint definitions
- **Components:** Reusable schemas and security schemes
- **Security:** Authentication requirements

## 🔍 Schema Definitions

### InvalidateRequest
```json
{
  "key": "string (optional, max 500)",
  "keys": ["string"] (optional, max 1000 items),
  "prefix": "string (optional, max 500)"
}
```

### CacheStats
```json
{
  "cacheName": "string",
  "hits": "integer",
  "misses": "integer",
  "evictions": "integer",
  "size": "integer",
  "memoryBytes": "integer",
  "hitRatio": "double",
  "lastUpdated": "date-time"
}
```

### CacheConfiguration
- TTL configuration
- Eviction policy (LRU, LFU, TTL_ONLY)
- Max entries
- Memory cap
- Replication mode
- Persistence mode

## 🎯 Benefits

1. **Self-Documenting API:** Always up-to-date with code
2. **Interactive Testing:** Test APIs without external tools
3. **Client Generation:** Generate client SDKs from OpenAPI spec
4. **Team Collaboration:** Shared understanding of API contracts
5. **Onboarding:** New developers can quickly understand the API

## 📖 Additional Resources

- [SpringDoc OpenAPI Documentation](https://springdoc.org/)
- [OpenAPI Specification](https://swagger.io/specification/)
- [Swagger UI Guide](https://swagger.io/tools/swagger-ui/)

---

**Enjoy exploring and testing the API with Swagger!** 🎉

