# Troubleshooting Guide

This guide helps you diagnose and fix common issues when setting up the Reputation Bot.

## Quick Diagnostic Checklist

Run through these checks to identify the issue:

### 1. Database Connection Issues

**Symptoms:**
- Error: "Could not connect to database"
- Error: "Connection refused"
- Bot keeps retrying database connection

**Diagnosis Steps:**

1. **Check if database is running:**
   ```bash
   # For Docker
   docker-compose ps
   # Should show database service as "Up"
   
   # For manual setup
   pg_isready -h localhost -p 5432
   ```

2. **Test database connection:**
   ```bash
   # For Docker (dev setup)
   docker-compose -f dev.docker-compose.yml exec pgdatabase_unpersisted psql -U root -d postgres
   
   # For Docker (production)
   docker-compose exec database psql -U root -d repbot
   
   # For manual setup
   psql -h localhost -U your_user -d your_database
   ```

3. **Check database host name:**
   - In Docker: Use the **service name** from docker-compose.yml, not `localhost`
   - For dev: `pgdatabase_unpersisted`
   - For production: `database`
   - For manual: `localhost`

4. **Verify credentials match:**
   - Check `config.json` database section
   - Compare with docker-compose.yml environment variables
   - They must match exactly

**Common Fixes:**
- Start database first, wait 10-15 seconds, then start bot
- Verify service name matches in docker-compose.yml and config.json
- Check network: services must be on same Docker network
- Ensure database is not blocking connections (firewall, pg_hba.conf)

### 2. Configuration File Issues

**Symptoms:**
- Error: "bot.config property is not set"
- Error: "Could not load config file"
- Configuration not being read

**Diagnosis Steps:**

1. **Check if config file exists:**
   ```bash
   ls -la docker/config/config.json
   # Should show the file exists
   ```

2. **Verify config file is valid JSON:**
   ```bash
   cat docker/config/config.json | python3 -m json.tool
   # Should output formatted JSON without errors
   ```

3. **Check config path in JVM arguments:**
   - Docker: Should be `-Dbot.config=config/config.json`
   - Manual: Should be relative to where you run the command, or absolute path

4. **Verify config file permissions:**
   ```bash
   ls -la docker/config/
   # Ensure file is readable
   ```

**Common Fixes:**
- Run from correct directory (project root for manual, docker/ for Docker)
- Use absolute path if relative path doesn't work
- Check JSON syntax (commas, quotes, brackets)
- Ensure file encoding is UTF-8

### 3. Bot Token Issues

**Symptoms:**
- Error: "LoginException"
- Error: "401 Unauthorized"
- Bot doesn't appear online

**Diagnosis Steps:**

1. **Verify token format:**
   - Should be a long string like: `MTIzNzE0ODM0NDY0ODk5NDg2Nw.GuzCM8...`
   - Usually starts with letters/numbers, contains dots and dashes

2. **Check token in Discord Developer Portal:**
   - Go to https://discord.com/developers/applications
   - Select your application
   - Go to "Bot" section
   - Verify token matches (or reset if needed)

3. **Check intents:**
   - "Message Content Intent" must be ENABLED
   - "Server Members Intent" must be ENABLED
   - Without these, bot won't work properly

4. **Verify bot is invited:**
   - Bot must be added to your server
   - Use OAuth2 URL generator in Developer Portal
   - Ensure bot has necessary permissions

**Common Fixes:**
- Enable all privileged intents in Developer Portal
- Re-invite bot after enabling intents
- Reset token if compromised (invalidates old token)
- Check if token has spaces or extra characters

### 4. Docker-Specific Issues

**Symptoms:**
- Services won't start
- "No such service" errors
- Network connection issues

**Diagnosis Steps:**

1. **Check Docker Compose profiles:**
   ```bash
   # Dev setup requires profiles
   docker-compose -f dev.docker-compose.yml --profile unpersisted --profile app ps
   ```

2. **Verify services are on same network:**
   ```bash
   docker network inspect repbot
   # Should show both app and database services
   ```

3. **Check volume mounts:**
   ```bash
   docker-compose exec app ls -la /app/config/
   # Should show config.json file
   ```

4. **Check service dependencies:**
   - Database must start before app
   - Use `depends_on` in docker-compose.yml (already configured)

**Common Fixes:**
- Use correct profile flags: `--profile unpersisted --profile app`
- Ensure docker-compose.yml files are in correct location
- Check Docker daemon is running: `docker ps`
- Rebuild if code changed: `docker-compose up --build`

### 5. Database Schema Issues

**Symptoms:**
- Error: "relation does not exist"
- Error: "schema does not exist"
- Tables missing

**Diagnosis Steps:**

1. **Check if schema was created:**
   ```sql
   -- Connect to database
   \dn
   -- Should list schemas including 'public' or 'repbot_schema'
   ```

2. **Check if migrations ran:**
   ```sql
   SELECT * FROM repbot_schema.repbot_version;
   -- Should show version number (1.37)
   ```

3. **Verify database user permissions:**
   ```sql
   SELECT has_schema_privilege('your_user', 'public', 'CREATE');
   -- Should return 't' (true)
   ```

**Common Fixes:**
- Bot creates schema automatically on first run
- Ensure database user has CREATE privilege
- Check logs for migration errors
- Manually create schema if needed: `CREATE SCHEMA IF NOT EXISTS public;`

### 6. Build Issues

**Symptoms:**
- Gradle build fails
- Missing dependencies
- JAR file not created

**Diagnosis Steps:**

1. **Check Java version:**
   ```bash
   java -version
   # Must be Java 18 or higher
   ```

2. **Check Gradle wrapper:**
   ```bash
   ./gradlew --version
   # Should show Gradle version
   ```

3. **Clean and rebuild:**
   ```bash
   ./gradlew clean
   ./gradlew shadowJar
   ```

**Common Fixes:**
- Install Java 18+ (JDK, not just JRE)
- Use provided Gradle wrapper: `./gradlew` not `gradle`
- Check internet connection (downloads dependencies)
- Clear Gradle cache if needed: `rm -rf ~/.gradle/caches`

## Step-by-Step Debugging

If you're still stuck, follow these steps in order:

### Step 1: Verify Prerequisites
```bash
# Check Docker (if using Docker)
docker --version
docker-compose --version

# Check Java (if manual setup)
java -version  # Must be 18+

# Check PostgreSQL (if manual setup)
psql --version
```

### Step 2: Start Database Only
```bash
# Docker dev
docker-compose -f dev.docker-compose.yml --profile unpersisted up -d pgdatabase_unpersisted

# Docker production
docker-compose up -d database

# Wait 15 seconds
sleep 15

# Test connection
docker-compose exec database psql -U root -d repbot -c "SELECT 1;"
```

### Step 3: Verify Configuration
```bash
# Check config file exists and is valid
cat docker/config/config.json | python3 -m json.tool > /dev/null && echo "JSON valid" || echo "JSON invalid"

# Check database settings match docker-compose
grep -A 5 '"database"' docker/config/config.json
```

### Step 4: Start Bot with Verbose Logging
```bash
# Docker
docker-compose up app

# Watch for specific errors in logs
# Common errors will show stack traces
```

### Step 5: Check Logs for Specific Errors
```bash
# Get last 50 lines of logs
docker-compose logs --tail=50 app

# Follow logs in real-time
docker-compose logs -f app
```

## Error Message Reference

| Error Message | Cause | Solution |
|--------------|-------|----------|
| "Could not connect to database" | Database not running or wrong host | Start database, check host name |
| "bot.config property is not set" | Missing JVM argument | Add `-Dbot.config=config/config.json` |
| "LoginException" | Invalid bot token | Check token in Developer Portal |
| "401 Unauthorized" | Token expired or invalid | Reset token in Developer Portal |
| "No such service" | Wrong docker-compose file or profile | Use correct file and profile flags |
| "relation does not exist" | Schema not created | Check database permissions, restart bot |
| "Connection refused" | Database not accessible | Check network, firewall, host/port |

## Getting More Help

If you've tried everything:

1. **Collect diagnostic information:**
   ```bash
   # Save logs
   docker-compose logs app > bot-logs.txt
   
   # Save config (remove sensitive data first!)
   cp docker/config/config.json config-backup.json
   # Edit config-backup.json to remove token/password
   ```

2. **Check the logs** for the exact error message

3. **Join Discord support:** https://discord.gg/5DrGmz7pHj

4. **Check GitHub issues:** https://github.com/RainbowDashLabs/reputation-bot/issues

5. **Provide these details when asking for help:**
   - Operating system
   - Docker version or Java version
   - Full error message from logs
   - What you've tried so far
   - Configuration (with sensitive data removed)

