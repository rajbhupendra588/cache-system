# Installation Guide

This guide explains how to install and use the Distributed Cache Management System in your project.

## Installation Options

### Option 1: Use as Library (Recommended)

Use the cache system as a dependency in your existing Spring Boot application.

**See [`LIBRARY_USAGE.md`](LIBRARY_USAGE.md) for complete instructions.**

Quick steps:
1. Build and install: `mvn clean install`
2. Add dependency to your `pom.xml`
3. Configure in `application.yml`
4. Inject `CacheService` and use it!

### Option 2: Standalone Application

Run the cache system as a standalone Spring Boot application.

#### Prerequisites

- Java 17 or higher
- Maven 3.6 or higher

#### Build

```bash
git clone <repository-url>
cd cache-system
mvn clean package
```

#### Run

```bash
java -jar target/distributed-cache-system-1.0.0-SNAPSHOT.jar
```

The application will start on port 8080 (configurable).

#### Configuration

Edit `src/main/resources/application.yml` before building, or override with environment variables:

```bash
export SERVER_PORT=8080
export CACHE_SYSTEM_DEFAULT_TTL=PT30M
java -jar target/distributed-cache-system-1.0.0-SNAPSHOT.jar
```

---

## Maven Installation

### Install to Local Repository

```bash
mvn clean install
```

This installs the artifact to `~/.m2/repository/com/cache/distributed-cache-system/1.0.0-SNAPSHOT/`

### Use in Your Project

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>com.cache</groupId>
    <artifactId>distributed-cache-system</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

## Gradle Installation

If using Gradle, add to `build.gradle`:

```gradle
dependencies {
    implementation 'com.cache:distributed-cache-system:1.0.0-SNAPSHOT'
}
```

---

## Docker Installation (Future)

Docker support is planned for future releases.

---

## Verification

After installation, verify the cache system is working:

### Check Dependency

```bash
mvn dependency:tree | grep cache-system
```

Should show:
```
[INFO] +- com.cache:distributed-cache-system:jar:1.0.0-SNAPSHOT:compile
```

### Test in Code

```java
@Autowired
private CacheService cacheService;

@Test
public void testCache() {
    cacheService.put("test", "key1", "value1", Duration.ofMinutes(1));
    Optional<String> value = cacheService.get("test", "key1", String.class);
    assert value.isPresent();
    assert "value1".equals(value.get());
}
```

---

## Next Steps

- **Library Usage**: See [`LIBRARY_USAGE.md`](LIBRARY_USAGE.md)
- **Developer Guide**: See [`DEVELOPER_GUIDE.md`](DEVELOPER_GUIDE.md)
- **Quick Start**: See [`QUICK_START.md`](QUICK_START.md)


