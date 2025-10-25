# Apofeoz Work Manager Bot - Deployment Guide

This guide will help you deploy the Apofeoz Work Manager Bot on your Ubuntu server using Docker Compose.

## Prerequisites

- Ubuntu 20.04+ server
- Docker and Docker Compose installed
- Telegram Bot Token from [@BotFather](https://t.me/botfather)

## Step 1: Install Docker and Docker Compose

### Install Docker
```bash
# Update package index
sudo apt update

# Install required packages
sudo apt install -y apt-transport-https ca-certificates curl gnupg lsb-release

# Add Docker's official GPG key
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg

# Add Docker repository
echo "deb [arch=amd64 signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io

# Add your user to docker group
sudo usermod -aG docker $USER
```

### Install Docker Compose
```bash
# Download Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose

# Make it executable
sudo chmod +x /usr/local/bin/docker-compose

# Verify installation
docker-compose --version
```

## Step 2: Clone Repository

```bash
# Clone the repository
git clone https://github.com/zotovmikhail/apofeoz-shift-manager-bot.git
cd apofeoz-shift-manager-bot
```

## Step 3: Configure Environment

```bash
# Copy environment template
cp env.example .env

# Edit environment file
nano .env
```

Add your Telegram Bot Token:
```env
TELEGRAM_BOT_TOKEN=your_telegram_bot_token_here
```

## Step 4: Create Required Directories

```bash
# Create directories for persistent data
mkdir -p data logs reports

# Set proper permissions
chmod 755 data logs reports
```

## Step 5: Deploy with Docker Compose

```bash
# Build and start the bot
docker-compose up -d

# Check if the bot is running
docker-compose ps

# View logs
docker-compose logs -f
```

## Step 6: Verify Deployment

1. **Check container status:**
   ```bash
   docker-compose ps
   ```

2. **View bot logs:**
   ```bash
   docker-compose logs -f apofeoz-bot
   ```

3. **Test the bot:**
   - Send `/start` to your bot in Telegram
   - You should receive a welcome message

## Management Commands

### Start the bot
```bash
docker-compose up -d
```

### Stop the bot
```bash
docker-compose down
```

### Restart the bot
```bash
docker-compose restart
```

### View logs
```bash
# All logs
docker-compose logs

# Follow logs in real-time
docker-compose logs -f

# Only bot logs
docker-compose logs -f apofeoz-bot
```

### Update the bot
```bash
# Pull latest changes
git pull origin main

# Rebuild and restart
docker-compose down
docker-compose build --no-cache
docker-compose up -d
```

### Backup data
```bash
# Create backup
tar -czf backup-$(date +%Y%m%d-%H%M%S).tar.gz data/ logs/

# Restore backup
tar -xzf backup-YYYYMMDD-HHMMSS.tar.gz
```

## File Structure

```
apofeoz-shift-manager-bot/
├── bot.py                 # Main bot application
├── models.py              # Database models
├── report_generator.py    # Report generation
├── requirements.txt       # Python dependencies
├── Dockerfile            # Docker image definition
├── docker-compose.yml    # Docker Compose configuration
├── .env                  # Environment variables
├── .gitignore           # Git ignore rules
├── data/                # Database files (persistent)
├── logs/                # Log files (persistent)
└── reports/             # Generated reports (temporary)
```

## Troubleshooting

### Bot not responding
```bash
# Check container status
docker-compose ps

# Check logs for errors
docker-compose logs apofeoz-bot

# Restart the bot
docker-compose restart
```

### Database issues
```bash
# Check database file permissions
ls -la data/

# Fix permissions if needed
sudo chown -R 1000:1000 data/
```

### Memory issues
```bash
# Check container resource usage
docker stats apofeoz-work-manager-bot

# Adjust memory limits in docker-compose.yml if needed
```

### Update bot
```bash
# Pull latest changes
git pull origin main

# Rebuild without cache
docker-compose build --no-cache

# Restart with new image
docker-compose up -d
```

## Security Notes

1. **Keep your `.env` file secure** - never commit it to version control
2. **Regular backups** - backup your `data/` directory regularly
3. **Monitor logs** - check logs regularly for any issues
4. **Update regularly** - keep the bot updated with latest changes

## Support

If you encounter any issues:

1. Check the logs: `docker-compose logs -f`
2. Verify your bot token is correct
3. Ensure all directories have proper permissions
4. Check if the container is running: `docker-compose ps`

## Production Recommendations

1. **Use a reverse proxy** (nginx) if exposing webhooks
2. **Set up log rotation** to prevent disk space issues
3. **Monitor resource usage** with tools like `htop` or `docker stats`
4. **Regular backups** of the `data/` directory
5. **SSL certificates** if using webhooks

## Quick Commands Reference

```bash
# Start bot
docker-compose up -d

# Stop bot
docker-compose down

# View logs
docker-compose logs -f

# Restart bot
docker-compose restart

# Update bot
git pull && docker-compose build --no-cache && docker-compose up -d

# Check status
docker-compose ps

# Backup data
tar -czf backup-$(date +%Y%m%d).tar.gz data/
```
