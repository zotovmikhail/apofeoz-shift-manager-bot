"""
Command handlers for the Telegram Bot
"""

import logging
from telegram import Update
from telegram.ext import ContextTypes

logger = logging.getLogger(__name__)

class CommandHandlers:
    """Class containing all command handlers"""
    
    @staticmethod
    async def start_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle /start command"""
        user = update.effective_user
        welcome_message = f"""
🤖 Welcome to the Telegram Bot, {user.first_name}!

I'm a simple bot that can help you with various tasks.

Available commands:
/start - Show this welcome message
/help - Show help information
/echo <message> - Echo back your message

Feel free to send me a message or use any of the commands above!
        """
        await update.message.reply_text(welcome_message)
        logger.info(f"User {user.id} ({user.username}) started the bot")
    
    @staticmethod
    async def help_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle /help command"""
        help_text = """
📚 **Bot Help**

**Available Commands:**
• `/start` - Start the bot and see welcome message
• `/help` - Show this help information
• `/echo <message>` - Echo back your message

**Features:**
• Responds to text messages
• Handles basic commands
• Error handling and logging

**Getting Started:**
1. Send `/start` to begin
2. Try sending me a message
3. Use `/echo Hello World` to test the echo feature

For more information, check the project README.
        """
        await update.message.reply_text(help_text, parse_mode='Markdown')
        logger.info(f"User {update.effective_user.id} requested help")
    
    @staticmethod
    async def echo_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle /echo command"""
        if context.args:
            message = ' '.join(context.args)
            await update.message.reply_text(f"🔊 Echo: {message}")
            logger.info(f"User {update.effective_user.id} used echo command: {message}")
        else:
            await update.message.reply_text("Please provide a message to echo. Usage: /echo <message>")
    
    @staticmethod
    async def status_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle /status command - show bot status"""
        status_text = """
📊 **Bot Status**

✅ Bot is running
🟢 All systems operational
📈 Ready to handle requests

**Bot Info:**
• Version: 1.0.0
• Status: Active
• Uptime: Running
        """
        await update.message.reply_text(status_text, parse_mode='Markdown')
        logger.info(f"User {update.effective_user.id} requested status")
