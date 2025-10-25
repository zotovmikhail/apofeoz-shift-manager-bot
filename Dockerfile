FROM python:3.11-slim

# Set working directory
WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    gcc \
    g++ \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements first for better caching
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

# Create necessary directories
RUN mkdir -p logs reports data

# Create non-root user
RUN useradd -m -u 1000 botuser

# Set proper permissions for all directories
RUN chown -R botuser:botuser /app && \
    chmod -R 755 /app/logs /app/reports /app/data

USER botuser

# Run the bot
CMD ["python", "bot.py"]
