#!/bin/bash

# Apofeoz Work Manager Bot - Deployment Script
# This script automates the deployment process

set -e

echo "🚀 Apofeoz Work Manager Bot - Deployment Script"
echo "=============================================="

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker first."
    echo "   Run: curl -fsSL https://get.docker.com -o get-docker.sh && sh get-docker.sh"
    exit 1
fi

# Check if Docker Compose is installed
if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose is not installed. Please install Docker Compose first."
    echo "   Run: sudo curl -L \"https://github.com/docker/compose/releases/latest/download/docker-compose-\$(uname -s)-\$(uname -m)\" -o /usr/local/bin/docker-compose"
    exit 1
fi

# Check if .env file exists
if [ ! -f ".env" ]; then
    echo "❌ .env file not found. Please create it first:"
    echo "   cp env.example .env"
    echo "   nano .env"
    exit 1
fi

# Check if TELEGRAM_BOT_TOKEN is set
if ! grep -q "TELEGRAM_BOT_TOKEN=" .env || grep -q "TELEGRAM_BOT_TOKEN=your_telegram_bot_token_here" .env; then
    echo "❌ TELEGRAM_BOT_TOKEN not set in .env file."
    echo "   Please edit .env and add your bot token."
    exit 1
fi

echo "✅ Prerequisites check passed"

# Create required directories
echo "📁 Creating directories..."
mkdir -p data logs reports
chmod 755 data logs reports

# Stop existing containers if running
echo "🛑 Stopping existing containers..."
docker-compose down 2>/dev/null || true

# Build and start the bot
echo "🔨 Building and starting the bot..."
docker-compose up -d --build

# Wait a moment for the bot to start
echo "⏳ Waiting for bot to start..."
sleep 10

# Check if the bot is running
if docker-compose ps | grep -q "Up"; then
    echo "✅ Bot deployed successfully!"
    echo ""
    echo "📊 Status:"
    docker-compose ps
    echo ""
    echo "📝 To view logs: docker-compose logs -f"
    echo "🛑 To stop: docker-compose down"
    echo "🔄 To restart: docker-compose restart"
    echo ""
    echo "🎉 Your bot is now running! Test it by sending /start to your bot in Telegram."
else
    echo "❌ Bot failed to start. Check logs:"
    docker-compose logs
    exit 1
fi
