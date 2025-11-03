# Complete Setup Guide for Reputation Bot

This guide will walk you through setting up and running the Reputation Bot from scratch. Follow these steps carefully to avoid common issues.

## ⚠️ CRITICAL: Enable Discord Intents First!

**BEFORE starting the bot, you MUST enable privileged intents in Discord Developer Portal.** The bot will fail to start with error `CloseCode 4014` if these are not enabled.

### Required Steps:

1. Go to https://discord.com/developers/applications
2. Select your bot application
3. Click on **"Bot"** in the left sidebar
4. Scroll down to **"Privileged Gateway Intents"** section
5. Enable these intents (all are REQUIRED):
   - ✅ **MESSAGE CONTENT INTENT** (required - bot needs to read message content)
   - ✅ **SERVER MEMBERS INTENT** (required - bot needs to track server members)
6. Click **"Save Changes"**

**Important:** After enabling intents, you may need to re-invite the bot to your server. Generate a new invite URL with the `bot` and `applications.commands` scopes.

> **Why?** The bot scans messages for thank words (`MESSAGE_CONTENT`), tracks member relationships (`SERVER_MEMBERS`), and monitors voice activity. Without these intents, Discord blocks the bot from accessing this data.

## Prerequisites

Before starting, ensure you have:
- **Docker** and **Docker Compose** installed (for Docker setup)
- OR **Java 18+** and **PostgreSQL** installed (for manual setup)
- A Discord bot token (create one at https://discord.com/developers/applications)
- Your Discord User ID (to set as bot owner)
- **Discord intents enabled** (see above ⚠️)

## Option 1: Docker Setup (Recommended)

### Step 1: Prepare Configuration

1. Navigate to the `docker/config` directory:
   ```bash
   cd docker/config
   ```

2. The bot will create a `config.json` file on first run if it doesn't exist, but you need to configure it before starting.

3. **Important**: Check your `config.json` file. It should have:
   - A valid Discord bot token
   - Correct database credentials matching your docker-compose setup
   - Your Discord user ID in the `botOwner` array

### Step 2: Configure Database Connection

**For production (docker-compose.yml):**
- Database host: `database` (service name in docker-compose)
- Database name: `repbot` (as defined in docker-compose.yml)
- User: `root`
- Password: `changeme`
- Schema: `public` (default)

**For development (dev.docker-compose.yml):**
- Database host: `pgdatabase_unpersisted` (service name in dev.docker-compose.yml)
- Database name: `postgres` (as defined in dev.docker-compose.yml)
- User: `root`
- Password: `changeme`
- Schema: `public` (default)

### Step 3: Fix Configuration File

Your current `config.json` has:
```json
"database" : {
  "host" : "pgdatabase_unpersisted",
  "database" : "postgres",
  ...
}
```

This matches the **dev.docker-compose.yml** setup. For **production docker-compose.yml**, change it to:
```json
"database" : {
  "host" : "database",
  "database" : "repbot",
  ...
}
```

### Step 4: Start the Database

**For development:**
```bash
cd docker
docker-compose -f dev.docker-compose.yml --profile unpersisted up -d pgdatabase_unpersisted
```

**For production:**
```bash
cd docker
docker-compose up -d database
```

Wait a few seconds for PostgreSQL to initialize.

### Step 5: Start the Bot

**For development (builds from source):**
```bash
cd docker
docker-compose -f dev.docker-compose.yml --profile app up --build
```

**For production (uses pre-built image):**
```bash
cd docker
docker-compose up
```

The bot will:
1. Create the database schema automatically (if it doesn't exist)
2. Run all database migrations
3. Connect to Discord
4. Start listening for commands

### Step 6: Verify Setup

1. Check the logs for any errors:
   ```bash
   docker-compose logs -f app
   ```

2. Look for these success messages:
   - "Creating connection pool."
   - "Configuring Query Configuration"
   - "Creating DAOs"
   - Bot successfully logged in to Discord

3. If you see database connection errors, wait a few more seconds and restart the app service.

## Option 2: Manual Setup (Without Docker)

### Step 1: Install Requirements

1. **Java 18 or higher** (check with `java -version`)
2. **PostgreSQL** installed and running
3. Create a database for the bot:
   ```sql
   CREATE DATABASE repbot;
   CREATE USER repbot WITH PASSWORD 'your_password';
   GRANT ALL PRIVILEGES ON DATABASE repbot TO repbot;
   ```

### Step 2: Build the Project

```bash
./gradlew clean shadowJar
```

This creates a JAR file in `build/libs/rep-bot-*-all.jar`

### Step 3: Configure the Bot

1. Create a `config` directory in your project root
2. Create `config/config.json` with this structure:
   ```json
   {
     "baseSettings": {
       "token": "YOUR_BOT_TOKEN_HERE",
       "botOwner": [YOUR_DISCORD_USER_ID],
       "botGuild": YOUR_GUILD_ID,
       "privateSupportChannel": 0
     },
     "database": {
       "host": "localhost",
       "port": "5432",
       "database": "repbot",
       "schema": "public",
       "user": "repbot",
       "password": "your_password",
       "poolSize": 5
     }
   }
   ```

3. Create `config/log4j2.xml` (copy from `docker/config/log4j2.xml` or use the example)

### Step 4: Run the Bot

```bash
java -Dbot.config=config/config.json \
     -Dlog4j2.configurationFile=config/log4j2.xml \
     -Dcjda.localisation.error.name=false \
     -jar build/libs/rep-bot-*-all.jar
```

## Common Issues and Solutions

### Issue 1: "Could not connect to database"

**Causes:**
- Database service not running
- Wrong host/port in config
- Wrong credentials
- Database service not ready yet

**Solutions:**
1. Check if PostgreSQL is running:
   ```bash
   docker-compose ps
   # or
   psql -h localhost -U root -d postgres
   ```

2. Verify database credentials in `config.json` match docker-compose.yml
3. Wait 10-15 seconds after starting the database before starting the bot
4. Check network connectivity (for Docker, ensure services are on the same network)

### Issue 2: "bot.config property is not set"

**Cause:** The `-Dbot.config` system property is missing.

**Solution:** Ensure you're running with the correct JVM arguments:
```bash
java -Dbot.config=config/config.json -jar bot.jar
```

### Issue 3: Configuration file path issues

**Cause:** The bot resolves config path relative to the parent of the current working directory.

**Solution:** 
- Run from the project root directory
- Or use absolute paths in the `-Dbot.config` property
- For Docker, the config is mounted at `/app/config/`, so use `-Dbot.config=config/config.json`

### Issue 4: Database schema doesn't exist

**Cause:** The bot tries to create schema automatically, but might fail if permissions are wrong.

**Solution:** 
- Ensure the database user has CREATE privileges
- The bot will create `repbot_schema` automatically if the default schema is `public`
- Check PostgreSQL logs for permission errors

### Issue 5: Docker compose profiles not working

**Cause:** The `dev.docker-compose.yml` uses profiles that need to be explicitly specified.

**Solution:** Always use the `--profile` flag:
```bash
docker-compose -f dev.docker-compose.yml --profile unpersisted --profile app up
```

### Issue 6: Token authentication fails

**Causes:**
- Invalid bot token
- Bot not added to server
- Missing intents (Message Content Intent, Server Members Intent)

**Solutions:**
1. Verify token at https://discord.com/developers/applications
2. Enable "Message Content Intent" and "Server Members Intent" in Discord Developer Portal
3. Re-invite the bot with proper permissions

### Issue 7: Port already in use

**Cause:** Another service is using port 5432 (PostgreSQL) or 8888 (API).

**Solutions:**
- Change the port in docker-compose.yml
- Or stop the conflicting service

## Configuration Checklist

Before starting the bot, verify:

- [ ] Bot token is set in `config.json`
- [ ] Your Discord User ID is in `botOwner` array
- [ ] Database host matches the Docker service name (for Docker setup)
- [ ] Database name matches what's in docker-compose.yml
- [ ] Database credentials are correct
- [ ] PostgreSQL is running and accessible
- [ ] Bot has necessary intents enabled in Discord Developer Portal
- [ ] Bot is invited to your Discord server with proper permissions

## First Run Steps

After the bot starts successfully:

1. **Verify bot is online** in your Discord server
2. **Run `/setup` command** in your Discord server
3. **Add manager roles** via Server Settings -> Integrations
4. **Configure channels** where reputation can be given
5. **Set up thank words** (defaults are usually fine)
6. **Optional:** Use `/scan` to backfill reputation from existing messages

## Getting Help

If you're still having issues:

1. Check the logs for specific error messages
2. Verify all configuration matches this guide
3. Join the support Discord: https://discord.gg/5DrGmz7pHj
4. Check the FAQ: https://rainbowdashlabs.github.io/reputation-bot/faq

## Quick Reference

**Start development environment:**
```bash
cd docker
docker-compose -f dev.docker-compose.yml --profile unpersisted up -d pgdatabase_unpersisted
docker-compose -f dev.docker-compose.yml --profile app up --build
```

**Start production environment:**
```bash
cd docker
docker-compose up -d database
docker-compose up
```

**View logs:**
```bash
docker-compose logs -f app
```

**Stop everything:**
```bash
docker-compose down
```

