import logging
import functools
from typing import Callable, Any
import os

def setup_logger(name: str = __name__, level: str = "INFO") -> logging.Logger:
    """Set up a logger with console and file output"""
    # Create logs directory if it doesn't exist
    os.makedirs("logs", exist_ok=True)
    
    # Filter out verbose HTTP requests BEFORE configuring logging
    logging.getLogger("httpx").setLevel(logging.WARNING)
    logging.getLogger("urllib3").setLevel(logging.WARNING)
    logging.getLogger("requests").setLevel(logging.WARNING)
    
    # Set telegram library to WARNING level to reduce noise
    logging.getLogger("telegram").setLevel(logging.WARNING)
    logging.getLogger("telegram.ext").setLevel(logging.WARNING)
    
    # Set asyncio to WARNING level
    logging.getLogger("asyncio").setLevel(logging.WARNING)
    
    # Configure logging
    logging.basicConfig(
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
        level=getattr(logging, level.upper()),
        handlers=[
            logging.StreamHandler(),  # Console output
            logging.FileHandler(f'logs/bot.log')  # File output
        ]
    )
    
    return logging.getLogger(name)

def log_command(func: Callable) -> Callable:
    """Decorator to automatically log command function calls"""
    @functools.wraps(func)
    async def wrapper(self, update, context):
        user = update.effective_user
        command = func.__name__.replace('_command', '')
        
        # Log command received (only important commands)
        if command in ['start', 'register', 'help']:
            logging.info(f"Command /{command} received from user {user.id} ({user.first_name})")
        
        try:
            # Execute the command
            result = await func(self, update, context)
            
            # Log successful response (only for important commands)
            if command in ['start', 'register', 'help']:
                logging.info(f"Command /{command} response sent to user {user.id}")
            return result
            
        except Exception as e:
            # Log error
            logging.error(f"Error in command /{command} for user {user.id}: {e}")
            raise
    
    return wrapper

def log_message(func: Callable) -> Callable:
    """Decorator to automatically log message handler calls"""
    @functools.wraps(func)
    async def wrapper(self, update, context):
        user = update.effective_user
        message_text = update.message.text[:50] + "..." if len(update.message.text) > 50 else update.message.text
        
        # Only log important messages (not every text input)
        if len(update.message.text) > 10:  # Only log substantial messages
            logging.debug(f"Message received from user {user.id} ({user.first_name}): {message_text}")
        
        try:
            # Execute the message handler
            result = await func(self, update, context)
            
            # Don't log every response to reduce noise
            return result
            
        except Exception as e:
            # Log error
            logging.error(f"Error in message handler for user {user.id}: {e}")
            raise
    
    return wrapper

def log_error(func: Callable) -> Callable:
    """Decorator to automatically log error handler calls"""
    @functools.wraps(func)
    async def wrapper(self, update, context):
        error = context.error
        
        # Log error details
        logging.error(f"Error handler called: {error}")
        if update and update.effective_user:
            logging.error(f"Error occurred for user {update.effective_user.id}")
        
        try:
            # Execute the error handler
            result = await func(self, update, context)
            logging.info("Error handler completed successfully")
            return result
            
        except Exception as e:
            # Log error in error handler
            logging.error(f"Error in error handler: {e}")
            raise
    
    return wrapper

def log_initialization(func: Callable) -> Callable:
    """Decorator to automatically log initialization steps"""
    @functools.wraps(func)
    def wrapper(self, *args, **kwargs):
        func_name = func.__name__
        logging.info(f"Initializing {func_name}...")
        
        try:
            result = func(self, *args, **kwargs)
            logging.info(f"{func_name} initialized successfully")
            return result
            
        except Exception as e:
            logging.error(f"Failed to initialize {func_name}: {e}")
            raise
    
    return wrapper

def log_important_action(action: str):
    """Decorator to log important business actions"""
    def decorator(func: Callable) -> Callable:
        @functools.wraps(func)
        async def wrapper(self, update, context, *args, **kwargs):
            user = update.effective_user
            
            try:
                result = await func(self, update, context, *args, **kwargs)
                logging.info(f"Important action '{action}' completed by user {user.id} ({user.first_name})")
                return result
                
            except Exception as e:
                logging.error(f"Error in important action '{action}' for user {user.id}: {e}")
                raise
        
        return wrapper
    return decorator
