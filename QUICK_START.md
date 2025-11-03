# Quick Start Guide

This is a condensed guide to get you running quickly. For detailed explanations, see [SETUP_GUIDE.md](SETUP_GUIDE.md).

## ⚠️ CRITICAL: Enable Discord Intents First!

**The bot REQUIRES privileged intents to be enabled in Discord Developer Portal!**

1. Go to https://discord.com/developers/applications → Your Bot → "Bot" section
2. Enable **"MESSAGE CONTENT INTENT"** ✅
3. Enable **"SERVER MEMBERS INTENT"** ✅
4. Save changes
5. Re-invite the bot if needed

**If you see error `CloseCode 4014` or "Disallowed intents", this is the problem!**

## ⚠️ Common Issues Found

Based on your current setup, here are the most likely issues:

### Issue 1: Database Schema Mismatch
Your `config.json` uses `"schema": "public"` but the default is `"repbot_schema"`. The bot will work with either, but for consistency, consider changing to `"repbot_schema"`.

### Issue 2: Docker Compose Profiles
The `dev.docker-compose.yml` requires profile flags. You must use `--profile` flags when starting services.

### Issue 3: Service Name Mismatch
Your config uses `"host": "pgdatabase_unpersisted"` which matches `dev.docker-compose.yml`. Make sure you're using the correct docker-compose file.

## 🚀 Quick Start (Development)

### Step 1: Start Database
```bash
cd docker
docker-compose -f dev.docker-compose.yml --profile unpersisted up -d pgdatabase_unpersisted
```

Wait 15 seconds for PostgreSQL to initialize.

### Step 2: Verify Config
Check `docker/config/config.json`:
- ✅ Token is set (not empty)
- ✅ Database host matches: `pgdatabase_unpersisted`
- ✅ Database name matches: `postgres`
- ✅ Credentials match docker-compose: `root` / `changeme`

### Step 3: Start Bot
```bash
docker-compose -f dev.docker-compose.yml --profile app up --build
```

### Step 4: Watch Logs
In another terminal:
```bash
cd docker
docker-compose -f dev.docker-compose.yml logs -f app
```

Look for:
- ✅ "Creating connection pool."
- ✅ "Configuring Query Configuration"
- ✅ "Creating DAOs"
- ✅ Bot logged in successfully

## 🚀 Quick Start (Production)

### Step 1: Update Config
Change `docker/config/config.json`:
```json
"database": {
  "host": "database",      // Changed from pgdatabase_unpersisted
  "database": "repbot",    // Changed from postgres
  ...
}
```

### Step 2: Start Services
```bash
cd docker
docker-compose up -d database
sleep 15
docker-compose up
```

## 🔧 Configuration Fixes

If you're having issues, check these in `docker/config/config.json`:

### Required Settings:
```json
{
  "baseSettings": {
    "token": "YOUR_BOT_TOKEN_HERE",           // ⚠️ Must be valid
    "botOwner": [YOUR_DISCORD_USER_ID],       // ⚠️ Your user ID
    "botGuild": YOUR_GUILD_ID,                // Optional but recommended
    "privateSupportChannel": 0
  },
  "database": {
    "host": "pgdatabase_unpersisted",         // ⚠️ Must match docker service name
    "port": "5432",
    "database": "postgres",                   // ⚠️ Must match POSTGRES_DB in docker-compose
    "schema": "public",                       // Can be "public" or "repbot_schema"
    "user": "root",                           // ⚠️ Must match POSTGRES_USER
    "password": "changeme",                   // ⚠️ Must match POSTGRES_PASSWORD
    "poolSize": 5
  }
}
```

## 🐛 Quick Troubleshooting

### Bot won't start
1. Check if database is running: `docker-compose ps`
2. Check logs: `docker-compose logs app`
3. Verify config JSON is valid: `cat docker/config/config.json | python3 -m json.tool`

### Database connection fails
1. Wait 15 seconds after starting database
2. Verify host name matches docker service name
3. Test connection: `docker-compose exec database psql -U root -d repbot`

### Bot token invalid
1. Check token in Discord Developer Portal
2. Enable "Message Content Intent" and "Server Members Intent"
3. Re-invite bot after enabling intents

## 📋 Checklist Before Starting

- [ ] Docker is installed and running
- [ ] `docker/config/config.json` exists and is valid JSON
- [ ] Bot token is set in config.json
- [ ] Your Discord User ID is in `botOwner` array
- [ ] Database host matches docker-compose service name
- [ ] Database credentials match docker-compose environment variables
- [ ] Bot has intents enabled in Discord Developer Portal
- [ ] Bot is invited to your Discord server

## 📚 Next Steps

After the bot starts:
1. Run `/setup` command in Discord
2. Add manager roles in Server Settings -> Integrations
3. Configure channels with `/channel add`
4. Optionally scan existing messages with `/scan`

## 🆘 Still Having Issues?

1. Read [TROUBLESHOOTING.md](docs/troubleshooting.md)
2. Check [SETUP_GUIDE.md](docs/setup_guide.md) for detailed steps

