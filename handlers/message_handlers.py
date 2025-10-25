"""
Message handlers for the Telegram Bot
"""

import logging
from telegram import Update
from telegram.ext import ContextTypes

logger = logging.getLogger(__name__)

class MessageHandlers:
    """Class containing all message handlers"""
    
    @staticmethod
    async def handle_text_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle non-command text messages"""
        user_message = update.message.text
        user = update.effective_user
        
        # Simple response logic
        response = f"👋 Hi {user.first_name}! You said: '{user_message}'\n\nI'm still learning, but I can echo messages using /echo command!"
        
        await update.message.reply_text(response)
        logger.info(f"User {user.id} sent message: {user_message[:50]}...")
    
    @staticmethod
    async def handle_photo_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle photo messages"""
        user = update.effective_user
        photo = update.message.photo[-1]  # Get the largest photo
        
        response = f"📸 Nice photo, {user.first_name}! I received your image.\n\nPhoto ID: {photo.file_id}"
        
        await update.message.reply_text(response)
        logger.info(f"User {user.id} sent a photo")
    
    @staticmethod
    async def handle_document_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle document messages"""
        user = update.effective_user
        document = update.message.document
        
        response = f"📄 Thanks for the document, {user.first_name}!\n\nDocument: {document.file_name}\nSize: {document.file_size} bytes"
        
        await update.message.reply_text(response)
        logger.info(f"User {user.id} sent a document: {document.file_name}")
    
    @staticmethod
    async def handle_voice_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle voice messages"""
        user = update.effective_user
        voice = update.message.voice
        
        response = f"🎤 Voice message received, {user.first_name}!\n\nDuration: {voice.duration} seconds"
        
        await update.message.reply_text(response)
        logger.info(f"User {user.id} sent a voice message")
    
    @staticmethod
    async def handle_sticker_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle sticker messages"""
        user = update.effective_user
        sticker = update.message.sticker
        
        response = f"😄 Cool sticker, {user.first_name}! I like it!\n\nSticker set: {sticker.set_name}"
        
        await update.message.reply_text(response)
        logger.info(f"User {user.id} sent a sticker")
