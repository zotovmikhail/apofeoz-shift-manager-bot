#!/usr/bin/env python3
"""
Apofeoz Work Manager Bot
A professional construction company work management system.
"""

import os
import sqlite3
from datetime import datetime, timedelta
from typing import Dict, Any
from dotenv import load_dotenv
from telegram import Update, InlineKeyboardButton, InlineKeyboardMarkup
from telegram.ext import Application, CommandHandler, MessageHandler, CallbackQueryHandler, filters, ContextTypes
from utils.logger import setup_logger, log_command, log_message, log_error, log_initialization, log_important_action
from models import DatabaseManager, User, Worker, WorkSession
from report_generator import ReportGenerator

# Load environment variables
load_dotenv()

# Set up logging early to filter out HTTP requests
logger = setup_logger("apofeoz_bot", "INFO")

class ApofeozWorkBot:
    @log_initialization
    def __init__(self):
        self.token = os.getenv('TELEGRAM_BOT_TOKEN')
        
        if not self.token:
            raise ValueError("TELEGRAM_BOT_TOKEN not found in environment variables")
        
        # Initialize database and managers
        self.db_manager = DatabaseManager()
        self.user_manager = User(self.db_manager)
        self.worker_manager = Worker(self.db_manager)
        self.session_manager = WorkSession(self.db_manager)
        self.report_generator = ReportGenerator(self.db_manager.db_path)
        
        
        # Initialize application with proper configuration
        self.application = Application.builder().token(self.token).build()
        self.setup_handlers()
    
    def setup_handlers(self):
        """Set up command and message handlers"""
        # Command handlers
        self.application.add_handler(CommandHandler("start", self.start_command))
        self.application.add_handler(CommandHandler("help", self.help_command))
        
        # Callback query handler for inline keyboards
        self.application.add_handler(CallbackQueryHandler(self.handle_callback))
        
        # Message handler for non-command messages
        self.application.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, self.handle_message))
        
        # Error handler
        self.application.add_error_handler(self.error_handler)
    
    @log_command
    async def start_command(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle /start command - Auto-register users as foremen"""
        user = update.effective_user
        
        # Check if user exists
        existing_user = self.user_manager.get_user(user.id)
        
        if existing_user:
            # User exists, check role and show appropriate menu
            if existing_user['role'] == 'admin':
                await self.show_admin_menu(update, context)
            else:
                await self.show_foreman_menu(update, context)
            return
        
        # Automatically register user as foreman
        success = self.user_manager.add_user(
            telegram_id=user.id,
            first_name=user.first_name,
            username=user.username,
            last_name=user.last_name,
            role='foreman'
        )
        
        if success:
            welcome_message = f"""
🏗️ **Добро пожаловать в Apofeoz Work Manager, {user.first_name}!**

Вы автоматически зарегистрированы как **Бригадир**.

Эта система поможет вам:
✅ Отслеживать рабочие часы
✅ Управлять командой рабочих
✅ Создавать детальные отчеты
✅ Рассчитывать рабочее время и оплату

Готовы начать работу!
            """
            await update.message.reply_text(welcome_message, parse_mode='Markdown')
            # Show foreman menu after registration
            await self.show_foreman_menu(update, context)
        else:
            await update.message.reply_text(
                "❌ Ошибка при регистрации. Попробуйте еще раз."
            )
    
    @log_command
    async def help_command(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle /help command"""
        help_text = """
📚 **Apofeoz Work Manager - Руководство**

**🏗️ Управление бригадирами:**
• `/start` - Главное меню (автоматическая регистрация как бригадир)

**👥 Управление рабочими:**
• Добавить нового рабочего через меню
• Просмотреть всех активных рабочих
• Деактивировать рабочего через меню

**⏰ Управление рабочими сеансами:**
• Начать рабочий сеанс для конкретного рабочего
• Завершить рабочий сеанс и рассчитать часы/оплату
• Просмотреть активные сеансы

**📊 Отчетность:**
• Создать отчет с данными о рабочем времени
• Создать отчет компании с ежедневной активностью всех рабочих

**💡 Советы:**
• Используйте кнопки для удобной навигации
• Все действия выполняются через интерактивное меню
• Отчеты включают детальные таблицы с несколькими вкладками

**🆘 Нужна помощь?**
Если возникнут проблемы, система предоставит полезные сообщения об ошибках.
        """
        await update.message.reply_text(help_text, parse_mode='Markdown')
    
    @log_important_action("register_foreman")
    
    async def show_main_menu(self, update: Update, context: ContextTypes.DEFAULT_TYPE, message: str = None):
        """Show main menu with buttons"""
        keyboard = [
            [InlineKeyboardButton("👷 Меню бригадира", callback_data="foreman_menu")],
            [InlineKeyboardButton("👥 Управление рабочими", callback_data="workers_menu")],
            [InlineKeyboardButton("⏰ Рабочие сеансы", callback_data="sessions_menu")],
            [InlineKeyboardButton("📊 Отчет компании", callback_data="generate_company_report")],
            [InlineKeyboardButton("ℹ️ Помощь", callback_data="help")]
        ]
        
        reply_markup = InlineKeyboardMarkup(keyboard)
        
        if isinstance(update, Update) and update.message:
            await update.message.reply_text(
                message or "🏗️ **Главное меню Apofeoz Work Manager**\n\nВыберите действие:",
                reply_markup=reply_markup,
                parse_mode='Markdown'
            )
        else:
            await update.callback_query.edit_message_text(
                message or "🏗️ **Главное меню Apofeoz Work Manager**\n\nВыберите действие:",
                reply_markup=reply_markup,
                parse_mode='Markdown'
            )
    
    async def show_workers_menu(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Show workers management menu"""
        keyboard = [
            [InlineKeyboardButton("➕ Добавить рабочего", callback_data="add_worker")],
            [InlineKeyboardButton("📋 Список рабочих", callback_data="list_workers")],
            [InlineKeyboardButton("❌ Уволить рабочего", callback_data="deactivate_worker")],
            [InlineKeyboardButton("🔙 Назад", callback_data="main_menu")]
        ]
        
        reply_markup = InlineKeyboardMarkup(keyboard)
        await update.callback_query.edit_message_text(
            "👥 **Управление рабочими**\n\nВыберите действие:",
            reply_markup=reply_markup,
            parse_mode='Markdown'
        )
    
    async def show_sessions_menu(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Show work sessions menu"""
        keyboard = [
            [InlineKeyboardButton("▶️ Начать сеанс", callback_data="start_session")],
            [InlineKeyboardButton("⏹️ Завершить сеанс", callback_data="end_session")],
            [InlineKeyboardButton("📋 Активные сеансы", callback_data="active_sessions")],
            [InlineKeyboardButton("🔙 Назад", callback_data="main_menu")]
        ]
        
        reply_markup = InlineKeyboardMarkup(keyboard)
        await update.callback_query.edit_message_text(
            "⏰ **Рабочие сеансы**\n\nВыберите действие:",
            reply_markup=reply_markup,
            parse_mode='Markdown'
        )
    
    async def show_admin_menu(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Show admin menu with management options"""
        # Handle both callback queries and direct calls
        if hasattr(update, 'callback_query') and update.callback_query:
            user = update.callback_query.from_user
        else:
            user = update.effective_user
            
        keyboard = [
            [InlineKeyboardButton("📊 Отчет компании", callback_data="generate_company_report")],
            [InlineKeyboardButton("👷 Отчет бригадира", callback_data="admin_foreman_report")],
            [InlineKeyboardButton("📅 Отчет по дням", callback_data="admin_daily_report")],
            [InlineKeyboardButton("👷 Управление бригадирами", callback_data="admin_foremen")],
            [InlineKeyboardButton("👥 Управление рабочими", callback_data="admin_workers")]
        ]
        
        message_text = "🔧 **Админ панель**\n\nВыберите раздел для управления:"
        
        if hasattr(update, 'callback_query') and update.callback_query:
            await update.callback_query.edit_message_text(
                message_text,
                reply_markup=InlineKeyboardMarkup(keyboard),
                parse_mode='Markdown'
            )
        else:
            await update.message.reply_text(
                message_text,
                reply_markup=InlineKeyboardMarkup(keyboard),
                parse_mode='Markdown'
            )
    
    async def show_foreman_menu(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Show foreman menu with worker list and status"""
        # Handle both callback queries and direct calls
        if hasattr(update, 'callback_query') and update.callback_query:
            user = update.callback_query.from_user
        else:
            user = update.effective_user
            
        foreman = self.user_manager.get_foreman(user.id)
        workers = self.worker_manager.get_workers_by_foreman(foreman['id'])
        
        if not workers:
            keyboard = [
                [InlineKeyboardButton("➕ Добавить рабочего", callback_data="add_worker")]
            ]
            
            if hasattr(update, 'callback_query') and update.callback_query:
                await update.callback_query.edit_message_text(
                    "👷 **Меню бригадира**\n\nУ вас пока нет рабочих.\n\nДобавьте первого рабочего!",
                    reply_markup=InlineKeyboardMarkup(keyboard)
                )
            else:
                await update.message.reply_text(
                    "👷 **Меню бригадира**\n\nУ вас пока нет рабочих.\n\nДобавьте первого рабочего!",
                    reply_markup=InlineKeyboardMarkup(keyboard),
                    parse_mode='Markdown'
                )
            return
        
        # Get active sessions to check which workers are currently working
        active_sessions = self.session_manager.get_active_sessions(foreman['id'])
        active_worker_ids = {session['worker_id'] for session in active_sessions}
        
        keyboard = []
        for worker in workers:
            position = worker['position'] or 'Должность не указана'
            
            # Check if worker has active session
            if worker['id'] in active_worker_ids:
                # Worker is currently working - show stop button
                keyboard.append([
                    InlineKeyboardButton(
                        f"🟢 {worker['first_name']} {worker['last_name'] or ''} ({position}) - Работает сейчас",
                        callback_data=f"stop_shift_{worker['id']}"
                    )
                ])
            else:
                # Worker is not working - show start button
                keyboard.append([
                    InlineKeyboardButton(
                        f"🔴 {worker['first_name']} {worker['last_name'] or ''} ({position}) - Не работает",
                        callback_data=f"start_shift_{worker['id']}"
                    )
                ])
        
        # Add additional options
        keyboard.append([InlineKeyboardButton("📝 Заметки всех рабочих", callback_data="workers_notes_menu")])
        keyboard.append([InlineKeyboardButton("➕ Добавить рабочего", callback_data="add_worker")])
        
        message_text = "👷 **Меню бригадира**\n\n🟢 - Работает сейчас\n🔴 - Не работает\n\nНажмите на рабочего для управления сменой:"
        
        if hasattr(update, 'callback_query') and update.callback_query:
            await update.callback_query.edit_message_text(
                message_text,
                reply_markup=InlineKeyboardMarkup(keyboard),
                parse_mode='Markdown'
            )
        else:
            await update.message.reply_text(
                message_text,
                reply_markup=InlineKeyboardMarkup(keyboard),
                parse_mode='Markdown'
            )
    
    async def show_admin_foreman_report_menu(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Show list of foremen to generate a specific foreman report"""
        foremen = self.user_manager.get_all_foremen()
        if not foremen:
            await update.callback_query.edit_message_text(
                "👷 **Отчет бригадира**\n\nНет доступных бригадиров для отчета.",
                reply_markup=InlineKeyboardMarkup([[InlineKeyboardButton("🔙 Назад", callback_data="admin_menu")]]),
                parse_mode='Markdown'
            )
            return
        keyboard = []
        for f in foremen:
            name = f"{f['first_name']} {f['last_name'] or ''}".strip()
            keyboard.append([InlineKeyboardButton(name or f"Бригадир #{f['id']}", callback_data=f"admin_show_foreman_report_{f['id']}")])
        keyboard.append([InlineKeyboardButton("🔙 Назад", callback_data="admin_menu")])
        await update.callback_query.edit_message_text(
            "👷 **Отчет бригадира**\n\nВыберите бригадира для генерации отчета:",
            reply_markup=InlineKeyboardMarkup(keyboard),
            parse_mode='Markdown'
        )

    async def handle_admin_foreman_inline_report(self, update: Update, context: ContextTypes.DEFAULT_TYPE, foreman_id: int):
        """Show last day activity for selected foreman as inline text (no file)."""
        # Build data using existing daily report generator (returns last 2 days)
        report_data = self.generate_daily_report(foreman_id)
        if not report_data:
            await update.callback_query.edit_message_text(
                "📊 **Отчет бригадира (последний день)**\n\nНет данных за сегодня.",
                reply_markup=InlineKeyboardMarkup([[InlineKeyboardButton("🔙 Назад", callback_data="admin_foreman_report")]]),
                parse_mode='Markdown'
            )
            return
        # Take latest date only
        latest_date = sorted(report_data.keys(), reverse=True)[0]
        day_data = report_data[latest_date]
        date_str = datetime.strptime(latest_date, '%Y-%m-%d').strftime('%d.%m.%Y')
        lines = ["📊 **Отчет бригадира (последний день)**\n",
                 f"📅 **{date_str}**",
                 "📅 8 часов = 1.0 смена",
                 f"⏱️ Всего часов: {day_data['total_hours']:.1f}",
                 f"📊 Смен: {day_data['total_ratio']:.3f}"]
        for worker in day_data['workers']:
            ratio_emoji = "🟢" if worker['ratio'] >= 1.0 else ("🟡" if worker['ratio'] >= 0.5 else "🔴")
            lines.append(f"{ratio_emoji} {worker['name']} ({worker['position']}): {worker['hours']:.1f}ч = {worker['ratio']:.3f}")
        text = "\n".join(lines)
        await update.callback_query.edit_message_text(
            text,
            reply_markup=InlineKeyboardMarkup([[InlineKeyboardButton("🔙 Назад", callback_data="admin_menu")]]),
            parse_mode='Markdown'
        )

    def generate_all_foremen_last_day_report(self) -> Dict[str, Any]:
        """Aggregate last day with data (within last 2 days) for all foremen grouped by foreman."""
        try:
            with sqlite3.connect(self.db_manager.db_path) as conn:
                cursor = conn.cursor()
                # Pull sessions for the last 2 days (to avoid timezone gaps) and then choose the latest date with data
                cursor.execute("""
                    SELECT 
                        DATE(ws.start_time) as work_date,
                        COALESCE(u.first_name, ''), COALESCE(u.last_name, ''),
                        COALESCE(w.first_name, ''), COALESCE(w.last_name, ''), COALESCE(w.position, ''),
                        SUM(
                            COALESCE(
                                NULLIF(ws.total_hours, 0),
                                (julianday(ws.end_time) - julianday(ws.start_time)) * 24.0
                            )
                        ) as hours
                    FROM work_sessions ws
                    LEFT JOIN users u ON ws.user_id = u.id
                    LEFT JOIN workers w ON ws.worker_id = w.id
                    WHERE ws.end_time IS NOT NULL
                      AND DATE(ws.start_time) >= DATE('now','-1 day')
                    GROUP BY work_date, ws.user_id, ws.worker_id
                    ORDER BY work_date DESC, u.first_name, u.last_name
                """)
                rows = cursor.fetchall()
                if not rows:
                    return None
                # Determine the latest date present in rows
                latest_date = max(r[0] for r in rows)
                # Filter rows to that latest date only
                filtered = [r for r in rows if r[0] == latest_date]
                report: Dict[str, Any] = { 'date': latest_date, 'foremen': {} }
                for _, f_first, f_last, w_first, w_last, position, hours in filtered:
                    foreman_name = f"{f_first} {f_last or ''}".strip()
                    worker_name = f"{w_first} {w_last or ''}".strip()
                    if foreman_name not in report['foremen']:
                        report['foremen'][foreman_name] = {
                            'total_hours': 0.0,
                            'total_ratio': 0.0,
                            'workers': []
                        }
                    ratio = round((hours or 0.0) / 8.0, 3)
                    report['foremen'][foreman_name]['workers'].append({
                        'name': worker_name,
                        'position': position or 'Должность не указана',
                        'hours': hours or 0.0,
                        'ratio': ratio
                    })
                    report['foremen'][foreman_name]['total_hours'] += hours or 0.0
                    report['foremen'][foreman_name]['total_ratio'] += ratio
                return report
        except Exception as e:
            logger.error(f"Error generating all foremen last day report: {e}")
            return None

    async def handle_admin_all_foremen_inline_report(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Show last day activity for all foremen inline (no file)."""
        data = self.generate_all_foremen_last_day_report()
        if not data:
            await update.callback_query.edit_message_text(
                "📊 **Отчет бригадиров (последний день)**\n\nНет данных за сегодня.",
                reply_markup=InlineKeyboardMarkup([[InlineKeyboardButton("🔙 Назад", callback_data="admin_menu")]]),
                parse_mode='Markdown'
            )
            return
        date_str = datetime.strptime(data['date'], '%Y-%m-%d').strftime('%d.%m.%Y')
        lines = [f"📊 **Отчет бригадиров (последний день)**\n\n📅 **{date_str}**", "📅 8 часов = 1.0 смена"]
        # Sort foremen by name for stable output
        for foreman_name in sorted(data['foremen'].keys()):
            f_data = data['foremen'][foreman_name]
            lines.append(f"\n👷 **{foreman_name}**")
            lines.append(f"⏱️ Всего часов: {f_data['total_hours']:.1f}")
            lines.append(f"📊 Смен: {f_data['total_ratio']:.3f}")
            for worker in f_data['workers']:
                ratio_emoji = "🟢" if worker['ratio'] >= 1.0 else ("🟡" if worker['ratio'] >= 0.5 else "🔴")
                lines.append(f"{ratio_emoji} {worker['name']} ({worker['position']}): {worker['hours']:.1f}ч = {worker['ratio']:.3f}")
        text = "\n".join(lines)
        await update.callback_query.edit_message_text(
            text,
            reply_markup=InlineKeyboardMarkup([[InlineKeyboardButton("🔙 Назад", callback_data="admin_menu")]]),
            parse_mode='Markdown'
        )

    
    async def handle_callback(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle button callbacks"""
        query = update.callback_query
        
        try:
            await query.answer()
        except Exception as e:
            # Handle expired callback queries gracefully
            if "Query is too old" in str(e) or "query id is invalid" in str(e):
                logger.warning(f"Expired callback query from user {query.from_user.id}: {e}")
                return
            else:
                raise
        
        data = query.data
        user = query.from_user
        
        # Check if user is registered
        foreman = self.user_manager.get_foreman(user.id)
        if not foreman and data not in ["main_menu", "help"]:
            await query.edit_message_text(
                "❌ Вы не зарегистрированы как бригадир.\n\nИспользуйте `/register` для регистрации."
            )
            return
        
        if data == "main_menu" or data == "foreman_menu":
            await self.show_foreman_menu(update, context)
        elif data == "admin_menu":
            await self.show_admin_menu(update, context)
        elif data == "admin_foreman_report":
            await self.handle_admin_all_foremen_inline_report(update, context)
        elif data == "admin_foremen":
            await self.handle_admin_foremen(update, context)
        elif data == "admin_workers":
            await self.handle_admin_workers(update, context)
        elif data == "workers_menu":
            await self.show_workers_menu(update, context)
        elif data == "sessions_menu":
            await self.show_sessions_menu(update, context)
        elif data == "add_worker":
            await self.handle_add_worker(update, context)
        elif data == "list_workers":
            await self.handle_list_workers(update, context)
        elif data == "deactivate_worker":
            await self.handle_deactivate_worker_menu(update, context)
        elif data == "start_session":
            await self.handle_start_session_menu(update, context)
        elif data == "end_session":
            await self.handle_end_session_menu(update, context)
        elif data == "active_sessions":
            await self.handle_active_sessions(update, context)
        elif data == "generate_report":
            await self.handle_generate_report(update, context)
        elif data.startswith("admin_generate_foreman_report_"):
            # Legacy: keep compatibility, but prefer inline report
            foreman_id = int(data.split("_")[-1])
            await self.handle_admin_foreman_inline_report(update, context, foreman_id)
        elif data.startswith("admin_show_foreman_report_"):
            foreman_id = int(data.split("_")[-1])
            await self.handle_admin_foreman_inline_report(update, context, foreman_id)
        elif data == "generate_company_report":
            await self.handle_generate_company_report(update, context)
        elif data == "help":
            await self.handle_help(update, context)
        elif data.startswith("deactivate_"):
            worker_id = int(data.split("_")[1])
            await self.handle_deactivate_worker_confirm(update, context, worker_id)
        elif data.startswith("start_session_"):
            worker_id = int(data.split("_")[2])
            await self.handle_start_session_confirm(update, context, worker_id)
        elif data.startswith("end_session_"):
            session_id = int(data.split("_")[2])
            await self.handle_end_session_confirm(update, context, session_id)
        elif data.startswith("worker_busy_"):
            worker_id = int(data.split("_")[2])
            await self.handle_worker_busy(update, context, worker_id)
        elif data.startswith("start_shift_"):
            worker_id = int(data.split("_")[2])
            await self.handle_start_shift(update, context, worker_id)
        elif data.startswith("stop_shift_"):
            worker_id = int(data.split("_")[2])
            await self.handle_stop_shift(update, context, worker_id)
        elif data.startswith("admin_fire_foreman_"):
            foreman_id = int(data.split("_")[3])
            await self.handle_admin_fire_foreman_warning(update, context, foreman_id)
        elif data.startswith("admin_confirm_fire_foreman_"):
            foreman_id = int(data.split("_")[4])
            await self.handle_admin_fire_foreman(update, context, foreman_id)
        elif data.startswith("admin_fire_worker_"):
            worker_id = int(data.split("_")[3])
            await self.handle_admin_fire_worker_warning(update, context, worker_id)
        elif data.startswith("admin_confirm_fire_worker_"):
            worker_id = int(data.split("_")[4])
            await self.handle_admin_fire_worker(update, context, worker_id)
        elif data.startswith("worker_notes_"):
            worker_id = int(data.split("_")[2])
            await self.handle_worker_notes(update, context, worker_id)
        elif data.startswith("move_worker_"):
            worker_id = int(data.split("_")[2])
            await self.handle_move_worker_menu(update, context, worker_id)
        elif data.startswith("move_to_foreman_"):
            parts = data.split("_")
            worker_id = int(parts[3])
            foreman_id = int(parts[4])
            await self.handle_move_worker_confirm(update, context, worker_id, foreman_id)
        elif data == "create_new_worker":
            await self.handle_create_new_worker(update, context)
        elif data == "transfer_worker":
            await self.handle_transfer_worker_menu(update, context)
        elif data.startswith("transfer_worker_"):
            worker_id = int(data.split("_")[2])
            await self.handle_transfer_worker_confirm(update, context, worker_id)
        elif data == "workers_notes_menu":
            await self.handle_workers_notes_menu(update, context)
        elif data == "daily_report":
            await self.handle_daily_report(update, context)
        elif data == "admin_daily_report":
            await self.handle_admin_daily_report(update, context)
    
    async def handle_daily_report(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle daily report generation"""
        user = update.callback_query.from_user
        
        # Get foreman info
        foreman = self.user_manager.get_foreman(user.id)
        if not foreman:
            await update.callback_query.edit_message_text(
                "❌ **Ошибка!**\n\nВы не зарегистрированы как бригадир.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")]
                ]),
                parse_mode='Markdown'
            )
            return
        
        # Generate daily report
        report_data = self.generate_daily_report(foreman['id'])
        
        if not report_data:
            await update.callback_query.edit_message_text(
                "📊 **Отчет по дням (последние 2 дня)**\n\nНет данных для отчета.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")]
                ]),
                parse_mode='Markdown'
            )
            return
        
        # Format the report
        report_text = self.format_daily_report(report_data)
        
        await update.callback_query.edit_message_text(
            report_text,
            reply_markup=InlineKeyboardMarkup([
                [InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")]
            ]),
            parse_mode='Markdown'
        )
    
    def generate_daily_report(self, foreman_id: int) -> Dict[str, Any]:
        """Generate daily report data for the last 2 days with 8-hour shift calculations"""
        try:
            with sqlite3.connect(self.db_manager.db_path) as conn:
                cursor = conn.cursor()
                
                # Get work sessions for the last 2 days
                cursor.execute('''
                    SELECT 
                        DATE(ws.start_time) as work_date,
                        w.first_name,
                        w.last_name,
                        w.position,
                        SUM(ws.total_hours) as daily_hours
                    FROM work_sessions ws
                    JOIN workers w ON ws.worker_id = w.id
                    WHERE ws.user_id = ? 
                    AND ws.end_time IS NOT NULL
                    AND DATE(ws.start_time) >= DATE('now', '-1 day')
                    GROUP BY DATE(ws.start_time), ws.worker_id
                    ORDER BY work_date DESC
                ''', (foreman_id,))
                
                sessions = cursor.fetchall()
                
                if not sessions:
                    return None
                
                # Group by date and calculate ratios
                daily_data = {}
                for session in sessions:
                    date, first_name, last_name, position, hours = session
                    worker_name = f"{first_name} {last_name or ''}".strip()
                    
                    if date not in daily_data:
                        daily_data[date] = {
                            'date': date,
                            'workers': [],
                            'total_hours': 0,
                            'total_ratio': 0
                        }
                    
                    # Calculate ratio (hours / 8)
                    ratio = round(hours / 8.0, 3)
                    
                    daily_data[date]['workers'].append({
                        'name': worker_name,
                        'position': position or 'Должность не указана',
                        'hours': hours,
                        'ratio': ratio
                    })
                    
                    daily_data[date]['total_hours'] += hours
                    daily_data[date]['total_ratio'] += ratio
                
                return daily_data
                
        except Exception as e:
            logger.error(f"Error generating daily report: {e}")
            return None
    
    def format_daily_report(self, report_data: Dict[str, Any]) -> str:
        """Format daily report for display"""
        if not report_data:
            return "📊 **Отчет по дням (последние 2 дня)**\n\nНет данных для отчета."
        
        report_lines = ["📊 **Отчет по дням (последние 2 дня)**\n"]
        report_lines.append("📅 **8 часов = 1.0 смена**\n")
        
        # Sort dates in descending order (newest first)
        sorted_dates = sorted(report_data.keys(), reverse=True)
        
        for date in sorted_dates[:2]:  # Show last 2 days
            day_data = report_data[date]
            date_str = datetime.strptime(date, '%Y-%m-%d').strftime('%d.%m.%Y')
            
            report_lines.append(f"\n📅 **{date_str}**")
            report_lines.append(f"⏱️ Всего часов: {day_data['total_hours']:.1f}")
            report_lines.append(f"📊 Смен: {day_data['total_ratio']:.3f}")
            
            for worker in day_data['workers']:
                ratio_emoji = "🟢" if worker['ratio'] >= 1.0 else "🟡" if worker['ratio'] >= 0.5 else "🔴"
                report_lines.append(
                    f"{ratio_emoji} {worker['name']} ({worker['position']}): "
                    f"{worker['hours']:.1f}ч = {worker['ratio']:.3f}"
                )
        
        return "\n".join(report_lines)
    
    async def handle_admin_daily_report(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle admin daily report generation - shows all foremen's data"""
        user = update.callback_query.from_user
        
        # Check if user is admin
        admin = self.user_manager.get_admin(user.id)
        if not admin:
            await update.callback_query.edit_message_text(
                "❌ **Ошибка!**\n\nУ вас нет прав администратора.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="admin_menu")]
                ]),
                parse_mode='Markdown'
            )
            return
        
        # Generate admin daily report (all foremen)
        report_data = self.generate_admin_daily_report()
        
        if not report_data:
            await update.callback_query.edit_message_text(
                "📊 **Отчет по дням (Админ)**\n\nНет данных для отчета.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="admin_menu")]
                ]),
                parse_mode='Markdown'
            )
            return
        
        # Format the admin report
        report_text = self.format_admin_daily_report(report_data)
        
        await update.callback_query.edit_message_text(
            report_text,
            reply_markup=InlineKeyboardMarkup([
                [InlineKeyboardButton("🔙 Назад", callback_data="admin_menu")]
            ]),
            parse_mode='Markdown'
        )
    
    def generate_admin_daily_report(self) -> Dict[str, Any]:
        """Generate daily report data for all foremen with 8-hour shift calculations"""
        try:
            with sqlite3.connect(self.db_manager.db_path) as conn:
                cursor = conn.cursor()
                
                # Get all completed work sessions for all foremen
                cursor.execute('''
                    SELECT 
                        DATE(ws.start_time) as work_date,
                        u.first_name as foreman_name,
                        u.last_name as foreman_last_name,
                        w.first_name,
                        w.last_name,
                        w.position,
                        SUM(ws.total_hours) as daily_hours
                    FROM work_sessions ws
                    JOIN workers w ON ws.worker_id = w.id
                    JOIN users u ON ws.user_id = u.id
                    WHERE ws.end_time IS NOT NULL
                    GROUP BY DATE(ws.start_time), ws.user_id, ws.worker_id
                    ORDER BY work_date DESC
                ''')
                
                sessions = cursor.fetchall()
                
                if not sessions:
                    return None
                
                # Group by date and foreman
                daily_data = {}
                for session in sessions:
                    date, foreman_first, foreman_last, first_name, last_name, position, hours = session
                    foreman_name = f"{foreman_first} {foreman_last or ''}".strip()
                    worker_name = f"{first_name} {last_name or ''}".strip()
                    
                    if date not in daily_data:
                        daily_data[date] = {
                            'date': date,
                            'foremen': {},
                            'total_hours': 0,
                            'total_ratio': 0
                        }
                    
                    if foreman_name not in daily_data[date]['foremen']:
                        daily_data[date]['foremen'][foreman_name] = {
                            'workers': [],
                            'foreman_hours': 0,
                            'foreman_ratio': 0
                        }
                    
                    # Calculate ratio (hours / 8)
                    ratio = round(hours / 8.0, 3)
                    
                    daily_data[date]['foremen'][foreman_name]['workers'].append({
                        'name': worker_name,
                        'position': position or 'Должность не указана',
                        'hours': hours,
                        'ratio': ratio
                    })
                    
                    daily_data[date]['foremen'][foreman_name]['foreman_hours'] += hours
                    daily_data[date]['foremen'][foreman_name]['foreman_ratio'] += ratio
                    daily_data[date]['total_hours'] += hours
                    daily_data[date]['total_ratio'] += ratio
                
                return daily_data
                
        except Exception as e:
            logger.error(f"Error generating admin daily report: {e}")
            return None
    
    def format_admin_daily_report(self, report_data: Dict[str, Any]) -> str:
        """Format admin daily report for display"""
        if not report_data:
            return "📊 **Отчет по дням (Админ)**\n\nНет данных для отчета."
        
        report_lines = ["📊 **Отчет по дням (Админ)**\n"]
        report_lines.append("📅 **8 часов = 1.0 смена**\n")
        
        # Sort dates in descending order (newest first)
        sorted_dates = sorted(report_data.keys(), reverse=True)
        
        for date in sorted_dates[:10]:  # Show last 10 days
            day_data = report_data[date]
            date_str = datetime.strptime(date, '%Y-%m-%d').strftime('%d.%m.%Y')
            
            report_lines.append(f"\n📅 **{date_str}**")
            report_lines.append(f"⏱️ Всего часов: {day_data['total_hours']:.1f}")
            report_lines.append(f"📊 Смен: {day_data['total_ratio']:.3f}")
            
            # Show each foreman's data
            for foreman_name, foreman_data in day_data['foremen'].items():
                report_lines.append(f"\n👷 **{foreman_name}** ({foreman_data['foreman_hours']:.1f}ч = {foreman_data['foreman_ratio']:.3f})")
                
                for worker in foreman_data['workers']:
                    ratio_emoji = "🟢" if worker['ratio'] >= 1.0 else "🟡" if worker['ratio'] >= 0.5 else "🔴"
                    report_lines.append(
                        f"  {ratio_emoji} {worker['name']} ({worker['position']}): "
                        f"{worker['hours']:.1f}ч = {worker['ratio']:.3f}"
                    )
        
        if len(sorted_dates) > 10:
            report_lines.append(f"\n... и еще {len(sorted_dates) - 10} дней")
        
        return "\n".join(report_lines)
    
    async def handle_workers_notes_menu(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle workers notes menu - show all workers with notes buttons"""
        user = update.callback_query.from_user
        
        # Get foreman info
        foreman = self.user_manager.get_foreman(user.id)
        if not foreman:
            await update.callback_query.edit_message_text(
                "❌ **Ошибка!**\n\nВы не зарегистрированы как бригадир.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")]
                ]),
                parse_mode='Markdown'
            )
            return
        
        # Get all workers for this foreman
        workers = self.worker_manager.get_workers_by_foreman(foreman['id'])
        
        if not workers:
            await update.callback_query.edit_message_text(
                "📝 **Заметки рабочих**\n\nУ вас пока нет рабочих.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")]
                ]),
                parse_mode='Markdown'
            )
            return
        
        # Create keyboard with notes buttons for each worker
        keyboard = []
        for worker in workers:
            position = worker['position'] or 'Должность не указана'
            worker_name = f"{worker['first_name']} {worker['last_name'] or ''}"
            
            keyboard.append([
                InlineKeyboardButton(
                    f"📝 {worker_name} ({position})",
                    callback_data=f"worker_notes_{worker['id']}"
                )
            ])
        
        # Add back button
        keyboard.append([InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")])
        
        message_text = "📝 **Заметки рабочих**\n\nВыберите рабочего для просмотра его активности:"
        
        await update.callback_query.edit_message_text(
            message_text,
            reply_markup=InlineKeyboardMarkup(keyboard),
            parse_mode='Markdown'
        )
    
    async def handle_add_worker(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle add worker request"""
        keyboard = [
            [InlineKeyboardButton("➕ Создать нового рабочего", callback_data="create_new_worker")],
            [InlineKeyboardButton("🔄 Переместить рабочего от другого прораба", callback_data="transfer_worker")],
            [InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")]
        ]
        
        await update.callback_query.edit_message_text(
            "👤 **Добавить рабочего**\n\nВыберите способ добавления:",
            reply_markup=InlineKeyboardMarkup(keyboard),
            parse_mode='Markdown'
        )
    
    async def handle_create_new_worker(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle create new worker request"""
        context.user_data['awaiting_input'] = 'add_worker'
        await update.callback_query.edit_message_text(
            "👤 **Создать нового рабочего**\n\n"
            "Отправьте данные рабочего в формате:\n"
            "`Имя Фамилия Должность`\n\n"
            "Пример: `Иван Петров Плотник`\n\n"
            "Используйте кнопку 'Отмена' для возврата.",
            parse_mode='Markdown',
            reply_markup=InlineKeyboardMarkup([[
                InlineKeyboardButton("❌ Отмена", callback_data="add_worker")
            ]])
        )
    
    async def handle_transfer_worker_menu(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle transfer worker menu - show workers from other foremen"""
        user = update.callback_query.from_user
        current_foreman = self.user_manager.get_foreman(user.id)
        
        # Get all workers from other foremen
        all_workers = self.user_manager.get_all_workers()
        other_workers = [w for w in all_workers if w['user_id'] != current_foreman['id']]
        
        if not other_workers:
            await update.callback_query.edit_message_text(
                "🔄 **Переместить рабочего**\n\nНет рабочих у других бригадиров для перемещения.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="add_worker")]
                ]),
                parse_mode='Markdown'
            )
            return
        
        keyboard = []
        for worker in other_workers:
            foreman_name = f"{worker['foreman_first_name']} {worker['foreman_last_name'] or ''}"
            keyboard.append([
                InlineKeyboardButton(
                    f"👤 {worker['first_name']} {worker['last_name'] or ''} ({worker['position'] or 'Должность не указана'}) - Бригадир: {foreman_name}",
                    callback_data=f"transfer_worker_{worker['id']}"
                )
            ])
        
        keyboard.append([InlineKeyboardButton("🔙 Назад", callback_data="add_worker")])
        
        await update.callback_query.edit_message_text(
            "🔄 **Переместить рабочего**\n\nВыберите рабочего для перемещения к вам:",
            reply_markup=InlineKeyboardMarkup(keyboard),
            parse_mode='Markdown'
        )
    
    @log_important_action("transfer_worker")
    async def handle_transfer_worker_confirm(self, update: Update, context: ContextTypes.DEFAULT_TYPE, worker_id: int):
        """Handle worker transfer confirmation"""
        user = update.callback_query.from_user
        current_foreman = self.user_manager.get_foreman(user.id)
        
        # Get worker info
        all_workers = self.user_manager.get_all_workers()
        worker = next((w for w in all_workers if w['id'] == worker_id), None)
        
        if not worker:
            await update.callback_query.edit_message_text(
                "❌ **Ошибка!**\n\nРабочий не найден.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="transfer_worker")]
                ])
            )
            return
        
        # Move the worker to current foreman
        success = self.user_manager.move_worker_to_foreman(worker_id, current_foreman['id'])
        
        if success:
            worker_name = f"{worker['first_name']} {worker['last_name'] or ''}"
            old_foreman_name = f"{worker['foreman_first_name']} {worker['foreman_last_name'] or ''}"
            
            await update.callback_query.edit_message_text(
                f"✅ **Рабочий перемещен!**\n\n"
                f"👤 {worker_name}\n"
                f"👷 Перемещен от бригадира: {old_foreman_name}\n"
                f"📅 Время: {datetime.now().strftime('%H:%M:%S')}",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("👷 Меню бригадира", callback_data="foreman_menu")]
                ]),
                parse_mode='Markdown'
            )
        else:
            await update.callback_query.edit_message_text(
                "❌ **Ошибка перемещения!**\n\nНе удалось переместить рабочего.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="transfer_worker")]
                ])
        )
    
    async def handle_list_workers(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle list workers request"""
        user = update.callback_query.from_user
        foreman = self.user_manager.get_foreman(user.id)
        workers = self.worker_manager.get_workers_by_foreman(foreman['id'])
        
        if not workers:
            await update.callback_query.edit_message_text(
                "📋 **Список рабочих**\n\nУ вас пока нет рабочих.\n\nДобавьте первого рабочего!",
                reply_markup=InlineKeyboardMarkup([[
                    InlineKeyboardButton("🔙 Назад", callback_data="workers_menu")
                ]])
            )
            return
        
        workers_text = "📋 **Список рабочих:**\n\n"
        for worker in workers:
            status = "✅ Активен" if worker['is_active'] else "❌ Неактивен"
            workers_text += f"**ID: {worker['id']}**\n"
            workers_text += f"👤 {worker['first_name']} {worker['last_name'] or ''}\n"
            workers_text += f"💼 {worker['position'] or 'Должность не указана'}\n"
            workers_text += f"📅 {worker['created_at'][:10]}\n"
            workers_text += f"🔸 {status}\n\n"
        
        await update.callback_query.edit_message_text(
            workers_text,
            reply_markup=InlineKeyboardMarkup([[
                InlineKeyboardButton("🔙 Назад", callback_data="workers_menu")
            ]]),
            parse_mode='Markdown'
        )
    
    async def handle_deactivate_worker_menu(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Show worker selection menu for deactivation"""
        user = update.callback_query.from_user
        foreman = self.user_manager.get_foreman(user.id)
        workers = self.worker_manager.get_workers_by_foreman(foreman['id'])
        
        active_workers = [w for w in workers if w['is_active']]
        
        if not active_workers:
            await update.callback_query.edit_message_text(
                "❌ **Деактивировать рабочего**\n\nУ вас нет активных рабочих для деактивации.",
                reply_markup=InlineKeyboardMarkup([[
                    InlineKeyboardButton("🔙 Назад", callback_data="workers_menu")
                ]])
            )
            return
        
        # Get active sessions to check which workers are currently working
        active_sessions = self.session_manager.get_active_sessions(foreman['id'])
        active_worker_ids = {session['worker_id'] for session in active_sessions}
        
        keyboard = []
        for worker in active_workers:
            position = worker['position'] or 'Должность не указана'
            
            # Check if worker has active session
            if worker['id'] in active_worker_ids:
                # Worker has active session - show as disabled
                keyboard.append([
                    InlineKeyboardButton(
                        f"⏸️ {worker['first_name']} {worker['last_name'] or ''} ({position}) - ID: {worker['id']} - Активный сеанс",
                        callback_data=f"deactivate_{worker['id']}"
                    )
                ])
            else:
                # Worker is available for deactivation
                keyboard.append([
                    InlineKeyboardButton(
                        f"❌ {worker['first_name']} {worker['last_name'] or ''} ({position}) - ID: {worker['id']}",
                        callback_data=f"deactivate_{worker['id']}"
                    )
                ])
        
        keyboard.append([InlineKeyboardButton("🔙 Назад", callback_data="workers_menu")])
        
        await update.callback_query.edit_message_text(
            "❌ **Выберите рабочего для деактивации:**\n\n⏸️ - Имеет активный сеанс\n❌ - Доступен для деактивации",
            reply_markup=InlineKeyboardMarkup(keyboard),
            parse_mode='Markdown'
        )
    
    @log_important_action("deactivate_worker")
    async def handle_deactivate_worker_confirm(self, update: Update, context: ContextTypes.DEFAULT_TYPE, worker_id: int):
        """Handle worker deactivation confirmation"""
        user = update.callback_query.from_user
        foreman = self.user_manager.get_foreman(user.id)
        
        # Check if worker has active sessions
        active_sessions = self.session_manager.get_active_sessions(foreman['id'])
        worker_has_active_session = any(session['worker_id'] == worker_id for session in active_sessions)
        
        if worker_has_active_session:
            # Get worker info for better error message
            workers = self.worker_manager.get_workers_by_foreman(foreman['id'])
            worker = next((w for w in workers if w['id'] == worker_id), None)
            
            if worker:
                worker_name = f"{worker['first_name']} {worker['last_name'] or ''}"
                await update.callback_query.edit_message_text(
                    f"⏸️ **Нельзя деактивировать рабочего!**\n\n"
                    f"👤 {worker_name}\n"
                    f"💼 {worker['position'] or 'Должность не указана'}\n"
                    f"❌ Нельзя деактивировать рабочего с активным сеансом.\n\n"
                    f"Сначала завершите активный сеанс этого рабочего.",
                    reply_markup=InlineKeyboardMarkup([
                        [InlineKeyboardButton("📋 Активные сеансы", callback_data="active_sessions")],
                        [InlineKeyboardButton("🏠 Главное меню", callback_data="main_menu")]
                    ]),
                    parse_mode='Markdown'
                )
            else:
                await update.callback_query.edit_message_text(
                    "❌ **Нельзя деактивировать рабочего!**\n\n"
                    "У рабочего есть активный сеанс. Сначала завершите сеанс.",
                    reply_markup=InlineKeyboardMarkup([
                        [InlineKeyboardButton("📋 Активные сеансы", callback_data="active_sessions")],
                        [InlineKeyboardButton("🏠 Главное меню", callback_data="main_menu")]
                    ])
                )
            return
        
        # Proceed with deactivation if no active sessions
        success = self.worker_manager.deactivate_worker(worker_id)
        
        if success:
            await update.callback_query.edit_message_text(
                f"✅ **Рабочий деактивирован!**\n\nРабочий с ID {worker_id} успешно деактивирован.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("📋 Список рабочих", callback_data="list_workers")],
                    [InlineKeyboardButton("🏠 Главное меню", callback_data="main_menu")]
                ]),
                parse_mode='Markdown'
            )
        else:
            await update.callback_query.edit_message_text(
                "❌ **Ошибка деактивации!**\n\nНе удалось деактивировать рабочего.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔄 Попробовать снова", callback_data="deactivate_worker")],
                    [InlineKeyboardButton("🏠 Главное меню", callback_data="main_menu")]
                ])
            )
    
    async def handle_start_session_menu(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Show worker selection menu for starting session"""
        user = update.callback_query.from_user
        foreman = self.user_manager.get_foreman(user.id)
        workers = self.worker_manager.get_workers_by_foreman(foreman['id'])
        
        active_workers = [w for w in workers if w['is_active']]
        
        if not active_workers:
            await update.callback_query.edit_message_text(
                "▶️ **Начать сеанс**\n\nУ вас нет активных рабочих для начала сеанса.",
                reply_markup=InlineKeyboardMarkup([[
                    InlineKeyboardButton("🔙 Назад", callback_data="sessions_menu")
                ]])
            )
            return
        
        # Get active sessions to check which workers are already working
        active_sessions = self.session_manager.get_active_sessions(foreman['id'])
        active_worker_ids = {session['worker_id'] for session in active_sessions}
        
        keyboard = []
        for worker in active_workers:
            # Check if worker already has an active session
            if worker['id'] in active_worker_ids:
                # Worker is already working - show as disabled
                keyboard.append([
                    InlineKeyboardButton(
                        f"⏸️ {worker['first_name']} {worker['last_name'] or ''} (ID: {worker['id']}) - Уже работает",
                        callback_data=f"worker_busy_{worker['id']}"
                    )
                ])
            else:
                # Worker is available - show as available
                keyboard.append([
                    InlineKeyboardButton(
                        f"▶️ {worker['first_name']} {worker['last_name'] or ''} (ID: {worker['id']}) - {worker['position'] or 'Должность не указана'}",
                        callback_data=f"start_session_{worker['id']}"
                    )
                ])
        
        keyboard.append([InlineKeyboardButton("🔙 Назад", callback_data="sessions_menu")])
        
        await update.callback_query.edit_message_text(
            "▶️ **Выберите рабочего для начала сеанса:**\n\n⏸️ - Уже работает\n▶️ - Доступен",
            reply_markup=InlineKeyboardMarkup(keyboard),
            parse_mode='Markdown'
        )
    
    async def handle_worker_busy(self, update: Update, context: ContextTypes.DEFAULT_TYPE, worker_id: int):
        """Handle attempt to start session for worker who is already working"""
        user = update.callback_query.from_user
        foreman = self.user_manager.get_foreman(user.id)
        
        # Get worker info
        workers = self.worker_manager.get_workers_by_foreman(foreman['id'])
        worker = next((w for w in workers if w['id'] == worker_id), None)
        
        if worker:
            worker_name = f"{worker['first_name']} {worker['last_name'] or ''}"
            await update.callback_query.edit_message_text(
                f"⏸️ **Рабочий уже работает!**\n\n"
                f"👤 {worker_name}\n"
                f"💼 {worker['position'] or 'Должность не указана'}\n"
                f"❌ Нельзя начать новый сеанс, пока текущий не завершен.\n\n"
                f"Сначала завершите активный сеанс этого рабочего.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("📋 Активные сеансы", callback_data="active_sessions")],
                    [InlineKeyboardButton("🏠 Главное меню", callback_data="main_menu")]
                ]),
                parse_mode='Markdown'
            )
        else:
            await update.callback_query.edit_message_text(
                "❌ **Ошибка!**\n\nРабочий не найден.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔄 Попробовать снова", callback_data="start_session")],
                    [InlineKeyboardButton("🏠 Главное меню", callback_data="main_menu")]
                ])
            )
    
    @log_important_action("start_shift")
    async def handle_start_shift(self, update: Update, context: ContextTypes.DEFAULT_TYPE, worker_id: int):
        """Handle starting a work shift for a worker"""
        user = update.callback_query.from_user
        foreman = self.user_manager.get_foreman(user.id)
        
        # Get worker info
        workers = self.worker_manager.get_workers_by_foreman(foreman['id'])
        worker = next((w for w in workers if w['id'] == worker_id), None)
        
        if not worker:
            await update.callback_query.edit_message_text(
                "❌ **Ошибка!**\n\nРабочий не найден.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")]
                ])
            )
            return
        
        # Check if worker already has an active session
        active_sessions = self.session_manager.get_active_sessions(foreman['id'])
        worker_has_active_session = any(session['worker_id'] == worker_id for session in active_sessions)
        
        if worker_has_active_session:
            worker_name = f"{worker['first_name']} {worker['last_name'] or ''}"
            await update.callback_query.edit_message_text(
                f"⏸️ **Рабочий уже работает!**\n\n"
                f"👤 {worker_name}\n"
                f"💼 {worker['position'] or 'Должность не указана'}\n"
                f"❌ Нельзя начать новую смену, пока текущая не завершена.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")]
                ]),
                parse_mode='Markdown'
            )
            return
        
        # Start the work session
        session_id = self.session_manager.start_work_session(foreman['id'], worker_id, None)
        
        if session_id:
            # Return to foreman menu with updated status
            await self.show_foreman_menu(update, context)
        else:
            await update.callback_query.edit_message_text(
                "❌ **Ошибка начала смены!**\n\nНе удалось начать смену.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")]
                ]),
                parse_mode='Markdown'
            )
    
    @log_important_action("stop_shift")
    async def handle_stop_shift(self, update: Update, context: ContextTypes.DEFAULT_TYPE, worker_id: int):
        """Handle stopping a work shift for a worker"""
        user = update.callback_query.from_user
        foreman = self.user_manager.get_foreman(user.id)
        
        # Get worker info
        workers = self.worker_manager.get_workers_by_foreman(foreman['id'])
        worker = next((w for w in workers if w['id'] == worker_id), None)
        
        if not worker:
            await update.callback_query.edit_message_text(
                "❌ **Ошибка!**\n\nРабочий не найден.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")]
                ])
            )
            return
        
        # Find active session for this worker
        active_sessions = self.session_manager.get_active_sessions(foreman['id'])
        worker_session = next((session for session in active_sessions if session['worker_id'] == worker_id), None)
        
        if not worker_session:
            worker_name = f"{worker['first_name']} {worker['last_name'] or ''}"
            await update.callback_query.edit_message_text(
                f"❌ **Рабочий не работает!**\n\n"
                f"👤 {worker_name}\n"
                f"💼 {worker['position'] or 'Должность не указана'}\n"
                f"❌ У рабочего нет активной смены для завершения.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")]
                ]),
                parse_mode='Markdown'
            )
            return
        
        # End the work session
        success = self.session_manager.end_work_session(worker_session['id'])
        
        if success:
            
            # Return to foreman menu with updated status
            await self.show_foreman_menu(update, context)
        else:
            await update.callback_query.edit_message_text(
                "❌ **Ошибка завершения смены!**\n\nНе удалось завершить смену.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")]
                ]),
                parse_mode='Markdown'
            )
    
    
    async def handle_admin_foremen(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle admin foremen management"""
        foremen = self.user_manager.get_all_foremen()
        
        if not foremen:
            await update.callback_query.edit_message_text(
                "👷 **Управление бригадирами**\n\nНет активных бригадиров.",
                reply_markup=InlineKeyboardMarkup([[
                    InlineKeyboardButton("🔙 Назад", callback_data="admin_menu")
                ]])
            )
            return
        
        keyboard = []
        for foreman in foremen:
            keyboard.append([
                InlineKeyboardButton(
                    f"👷 {foreman['first_name']} {foreman['last_name'] or ''} (@{foreman['username'] or 'N/A'})",
                    callback_data=f"admin_fire_foreman_{foreman['id']}"
                )
            ])
        
        keyboard.append([InlineKeyboardButton("🔙 Назад", callback_data="admin_menu")])
        
        await update.callback_query.edit_message_text(
            "👷 **Управление бригадирами**\n\nНажмите на бригадира для увольнения:",
            reply_markup=InlineKeyboardMarkup(keyboard),
            parse_mode='Markdown'
        )
    
    async def handle_admin_workers(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle admin workers management"""
        workers = self.user_manager.get_all_workers()
        
        if not workers:
            await update.callback_query.edit_message_text(
                "👥 **Управление рабочими**\n\nНет активных рабочих.",
                reply_markup=InlineKeyboardMarkup([[
                    InlineKeyboardButton("🔙 Назад", callback_data="admin_menu")
                ]])
            )
            return
        
        keyboard = []
        for worker in workers:
            foreman_name = f"{worker['foreman_first_name']} {worker['foreman_last_name'] or ''}"
            keyboard.append([
                InlineKeyboardButton(
                    f"👤 {worker['first_name']} {worker['last_name'] or ''} ({worker['position'] or 'Должность не указана'}) - Бригадир: {foreman_name}",
                    callback_data=f"admin_fire_worker_{worker['id']}"
                )
            ])
        
        keyboard.append([InlineKeyboardButton("🔙 Назад", callback_data="admin_menu")])
        
        await update.callback_query.edit_message_text(
            "👥 **Управление рабочими**\n\nНажмите на рабочего для увольнения:",
            reply_markup=InlineKeyboardMarkup(keyboard),
            parse_mode='Markdown'
        )
    
    @log_important_action("admin_fire_foreman")
    async def handle_admin_fire_foreman(self, update: Update, context: ContextTypes.DEFAULT_TYPE, foreman_id: int):
        """Handle admin firing a foreman"""
        success = self.user_manager.deactivate_user(foreman_id)
        
        if success:
            await update.callback_query.edit_message_text(
                f"✅ **Бригадир уволен!**\n\nБригадир с ID {foreman_id} успешно уволен.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("👷 Управление бригадирами", callback_data="admin_foremen")],
                    [InlineKeyboardButton("🔙 Админ панель", callback_data="admin_menu")]
                ]),
                parse_mode='Markdown'
            )
        else:
            await update.callback_query.edit_message_text(
                "❌ **Ошибка увольнения!**\n\nНе удалось уволить бригадира.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="admin_foremen")]
                ])
            )
    
    @log_important_action("admin_fire_worker")
    async def handle_admin_fire_worker(self, update: Update, context: ContextTypes.DEFAULT_TYPE, worker_id: int):
        """Handle admin firing a worker"""
        # First, stop any active work sessions for this worker
        active_sessions = self.session_manager.get_active_sessions_by_worker(worker_id)
        stopped_sessions = 0
        
        for session in active_sessions:
            success = self.session_manager.end_work_session(session['id'])
            if success:
                stopped_sessions += 1
                logger.info(f"Stopped active session {session['id']} for worker {worker_id}")
        
        # Now deactivate the worker
        success = self.worker_manager.deactivate_worker(worker_id)
        
        if success:
            message = f"✅ **Рабочий уволен!**\n\nРабочий с ID {worker_id} успешно уволен."
            if stopped_sessions > 0:
                message += f"\n\n⏹️ Остановлено активных сеансов: {stopped_sessions}"
            
            await update.callback_query.edit_message_text(
                message,
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("👥 Управление рабочими", callback_data="admin_workers")],
                    [InlineKeyboardButton("🔙 Админ панель", callback_data="admin_menu")]
                ]),
                parse_mode='Markdown'
            )
        else:
            await update.callback_query.edit_message_text(
                "❌ **Ошибка увольнения!**\n\nНе удалось уволить рабочего.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="admin_workers")]
                ])
            )
    
    async def handle_admin_fire_foreman_warning(self, update: Update, context: ContextTypes.DEFAULT_TYPE, foreman_id: int):
        """Show warning screen before firing a foreman"""
        # Get foreman info for display
        foreman = self.user_manager.get_user_by_id(foreman_id)
        if not foreman:
            await update.callback_query.edit_message_text(
                "❌ **Ошибка!**\n\nБригадир не найден.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="admin_foremen")]
                ])
            )
            return
        
        foreman_name = f"{foreman['first_name']} {foreman['last_name'] or ''}".strip()
        
        await update.callback_query.edit_message_text(
            f"⚠️ **ПОДТВЕРЖДЕНИЕ УВОЛЬНЕНИЯ**\n\n"
            f"Вы собираетесь уволить бригадира:\n"
            f"👷 **{foreman_name}**\n"
            f"🆔 ID: {foreman_id}\n\n"
            f"⚠️ **ВНИМАНИЕ!**\n"
            f"• Это действие нельзя отменить\n"
            f"• Бригадир потеряет доступ к системе\n"
            f"• Все его рабочие останутся без бригадира\n\n"
            f"Вы уверены, что хотите уволить этого бригадира?",
            reply_markup=InlineKeyboardMarkup([
                [
                    InlineKeyboardButton("✅ Да, уволить", callback_data=f"admin_confirm_fire_foreman_{foreman_id}"),
                    InlineKeyboardButton("❌ Отмена", callback_data="admin_foremen")
                ]
            ]),
            parse_mode='Markdown'
        )
    
    async def handle_admin_fire_worker_warning(self, update: Update, context: ContextTypes.DEFAULT_TYPE, worker_id: int):
        """Show warning screen before firing a worker"""
        # Get worker info for display
        workers = self.user_manager.get_all_workers()
        worker = next((w for w in workers if w['id'] == worker_id), None)
        
        if not worker:
            await update.callback_query.edit_message_text(
                "❌ **Ошибка!**\n\nРабочий не найден.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="admin_workers")]
                ])
            )
            return
        
        worker_name = f"{worker['first_name']} {worker['last_name'] or ''}".strip()
        foreman_name = f"{worker['foreman_first_name']} {worker['foreman_last_name'] or ''}".strip()
        
        # Check if worker has active sessions
        active_sessions = self.session_manager.get_active_sessions_by_worker(worker_id)
        active_sessions_warning = ""
        if active_sessions:
            active_sessions_warning = f"\n⏹️ **Активные сеансы:** {len(active_sessions)} (будут автоматически остановлены)"
        
        await update.callback_query.edit_message_text(
            f"⚠️ **ПОДТВЕРЖДЕНИЕ УВОЛЬНЕНИЯ**\n\n"
            f"Вы собираетесь уволить рабочего:\n"
            f"👤 **{worker_name}**\n"
            f"💼 Должность: {worker['position'] or 'Не указана'}\n"
            f"👷 Бригадир: {foreman_name}\n"
            f"🆔 ID: {worker_id}{active_sessions_warning}\n\n"
            f"⚠️ **ВНИМАНИЕ!**\n"
            f"• Это действие нельзя отменить\n"
            f"• Рабочий будет удален из системы\n"
            f"• Все активные сеансы будут остановлены\n"
            f"• Все его рабочие сеансы останутся в истории\n\n"
            f"Вы уверены, что хотите уволить этого рабочего?",
            reply_markup=InlineKeyboardMarkup([
                [
                    InlineKeyboardButton("✅ Да, уволить", callback_data=f"admin_confirm_fire_worker_{worker_id}"),
                    InlineKeyboardButton("❌ Отмена", callback_data="admin_workers")
                ]
            ]),
            parse_mode='Markdown'
        )
    
    async def handle_worker_notes(self, update: Update, context: ContextTypes.DEFAULT_TYPE, worker_id: int):
        """Handle worker notes/activity view"""
        user = update.callback_query.from_user
        foreman = self.user_manager.get_foreman(user.id)
        
        # Get worker info
        workers = self.worker_manager.get_workers_by_foreman(foreman['id'])
        worker = next((w for w in workers if w['id'] == worker_id), None)
        
        if not worker:
            await update.callback_query.edit_message_text(
                "❌ **Ошибка!**\n\nРабочий не найден.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")]
                ])
            )
            return
        
        # Get worker activity for last 12 hours
        activities = self.worker_manager.get_worker_activity(worker_id, 12)
        
        worker_name = f"{worker['first_name']} {worker['last_name'] or ''}"
        position = worker['position'] or 'Должность не указана'
        
        if not activities:
            message_text = f"📝 **Заметки рабочего**\n\n👤 {worker_name}\n💼 {position}\n\n⏰ **Активность за последние 12 часов:**\n\nНет активности за этот период."
        else:
            message_text = f"📝 **Заметки рабочего**\n\n👤 {worker_name}\n💼 {position}\n\n⏰ **Активность за последние 12 часов:**\n\n"
            
            for activity in activities:
                start_time = datetime.fromisoformat(activity['start_time']).strftime('%H:%M:%S')
                end_time = datetime.fromisoformat(activity['end_time']).strftime('%H:%M:%S') if activity['end_time'] else 'В процессе'
                duration = f"{activity['total_hours']:.1f}ч" if activity['total_hours'] else 'В процессе'
                notes = activity['notes'] or 'Без описания'
                
                message_text += f"🕐 {start_time} - {end_time} ({duration})\n"
                message_text += f"📝 {notes}\n"
                message_text += f"👷 Бригадир: {activity['foreman_first_name']} {activity['foreman_last_name'] or ''}\n\n"
        
        await update.callback_query.edit_message_text(
            message_text,
            reply_markup=InlineKeyboardMarkup([
                [InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")]
            ]),
            parse_mode='Markdown'
        )
    
    async def handle_move_worker_menu(self, update: Update, context: ContextTypes.DEFAULT_TYPE, worker_id: int):
        """Handle move worker menu - show available foremen"""
        user = update.callback_query.from_user
        current_foreman = self.user_manager.get_foreman(user.id)
        
        # Get worker info
        workers = self.worker_manager.get_workers_by_foreman(current_foreman['id'])
        worker = next((w for w in workers if w['id'] == worker_id), None)
        
        if not worker:
            await update.callback_query.edit_message_text(
                "❌ **Ошибка!**\n\nРабочий не найден.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")]
                ])
            )
            return
        
        # Get all other foremen
        all_foremen = self.user_manager.get_all_foremen()
        other_foremen = [f for f in all_foremen if f['id'] != current_foreman['id']]
        
        if not other_foremen:
            await update.callback_query.edit_message_text(
                f"🔄 **Переместить рабочего**\n\n👤 {worker['first_name']} {worker['last_name'] or ''}\n\nНет других бригадиров для перемещения.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")]
                ]),
                parse_mode='Markdown'
            )
            return
        
        keyboard = []
        for foreman in other_foremen:
            keyboard.append([
                InlineKeyboardButton(
                    f"👷 {foreman['first_name']} {foreman['last_name'] or ''} (@{foreman['username'] or 'N/A'})",
                    callback_data=f"move_to_foreman_{worker_id}_{foreman['id']}"
                )
            ])
        
        keyboard.append([InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")])
        
        worker_name = f"{worker['first_name']} {worker['last_name'] or ''}"
        await update.callback_query.edit_message_text(
            f"🔄 **Переместить рабочего**\n\n👤 {worker_name}\n💼 {worker['position'] or 'Должность не указана'}\n\nВыберите нового бригадира:",
            reply_markup=InlineKeyboardMarkup(keyboard),
            parse_mode='Markdown'
        )
    
    @log_important_action("move_worker")
    async def handle_move_worker_confirm(self, update: Update, context: ContextTypes.DEFAULT_TYPE, worker_id: int, foreman_id: int):
        """Handle worker move confirmation"""
        user = update.callback_query.from_user
        current_foreman = self.user_manager.get_foreman(user.id)
        
        # Get worker info
        workers = self.worker_manager.get_workers_by_foreman(current_foreman['id'])
        worker = next((w for w in workers if w['id'] == worker_id), None)
        
        if not worker:
            await update.callback_query.edit_message_text(
                "❌ **Ошибка!**\n\nРабочий не найден.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")]
                ])
            )
            return
        
        # Get target foreman info
        all_foremen = self.user_manager.get_all_foremen()
        target_foreman = next((f for f in all_foremen if f['id'] == foreman_id), None)
        
        if not target_foreman:
            await update.callback_query.edit_message_text(
                "❌ **Ошибка!**\n\nЦелевой бригадир не найден.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")]
                ])
            )
            return
        
        # Move the worker
        success = self.user_manager.move_worker_to_foreman(worker_id, foreman_id)
        
        if success:
            worker_name = f"{worker['first_name']} {worker['last_name'] or ''}"
            target_foreman_name = f"{target_foreman['first_name']} {target_foreman['last_name'] or ''}"
            
            await update.callback_query.edit_message_text(
                f"✅ **Рабочий перемещен!**\n\n"
                f"👤 {worker_name}\n"
                f"👷 Перемещен к бригадиру: {target_foreman_name}\n"
                f"📅 Время: {datetime.now().strftime('%H:%M:%S')}",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("👷 Меню бригадира", callback_data="foreman_menu")]
                ]),
                parse_mode='Markdown'
            )
        else:
            await update.callback_query.edit_message_text(
                "❌ **Ошибка перемещения!**\n\nНе удалось переместить рабочего.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")]
                ])
            )
    
    
    async def handle_start_session_confirm(self, update: Update, context: ContextTypes.DEFAULT_TYPE, worker_id: int):
        """Handle work session start confirmation"""
        user = update.callback_query.from_user
        foreman = self.user_manager.get_foreman(user.id)
        
        # Store worker_id in context for notes input
        context.user_data['pending_worker_id'] = worker_id
        context.user_data['pending_foreman_id'] = foreman['id']
        
        await update.callback_query.edit_message_text(
            f"📝 **Добавьте описание работы:**\n\nРабочий ID: {worker_id}\n\nОтправьте сообщение с описанием работы (например: 'Копает траншею', 'Кладёт кирпич')\n\nИли отправьте 'Пропустить' для продолжения без описания.",
            reply_markup=InlineKeyboardMarkup([[
                InlineKeyboardButton("🔙 Отмена", callback_data="sessions_menu")
            ]]),
            parse_mode='Markdown'
        )
        
        # Set state to wait for notes
        context.user_data['waiting_for_notes'] = True
    
    async def handle_end_session_menu(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Show session selection menu for ending"""
        user = update.callback_query.from_user
        foreman = self.user_manager.get_foreman(user.id)
        active_sessions = self.session_manager.get_active_sessions(foreman['id'])
        
        if not active_sessions:
            await update.callback_query.edit_message_text(
                "⏹️ **Завершить сеанс**\n\nУ вас нет активных сеансов для завершения.",
                reply_markup=InlineKeyboardMarkup([[
                    InlineKeyboardButton("🔙 Назад", callback_data="sessions_menu")
                ]])
            )
            return
        
        keyboard = []
        for session in active_sessions:
            start_time = datetime.fromisoformat(session['start_time']).strftime('%H:%M:%S')
            position = session.get('position', 'Должность не указана')
            keyboard.append([
                InlineKeyboardButton(
                    f"⏹️ {session['first_name']} {session['last_name'] or ''} ({position}) - {start_time}",
                    callback_data=f"end_session_{session['id']}"
                )
            ])
        
        keyboard.append([InlineKeyboardButton("🔙 Назад", callback_data="sessions_menu")])
        
        await update.callback_query.edit_message_text(
            "⏹️ **Выберите сеанс для завершения:**",
            reply_markup=InlineKeyboardMarkup(keyboard),
            parse_mode='Markdown'
        )
    
    @log_important_action("end_session")
    async def handle_end_session_confirm(self, update: Update, context: ContextTypes.DEFAULT_TYPE, session_id: int):
        """Handle work session end confirmation"""
        success = self.session_manager.end_work_session(session_id)
        
        if success:
            await update.callback_query.edit_message_text(
                f"✅ **Рабочий сеанс завершен!**\n\nСеанс ID: {session_id}\nВремя завершения: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("▶️ Начать новый сеанс", callback_data="start_session")],
                    [InlineKeyboardButton("📋 Активные сеансы", callback_data="active_sessions")],
                    [InlineKeyboardButton("🏠 Главное меню", callback_data="main_menu")]
                ]),
                parse_mode='Markdown'
            )
        else:
            await update.callback_query.edit_message_text(
                "❌ **Ошибка завершения сеанса!**\n\nНе удалось завершить рабочий сеанс.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔄 Попробовать снова", callback_data="end_session")],
                    [InlineKeyboardButton("🏠 Главное меню", callback_data="main_menu")]
                ])
            )
    
    async def handle_active_sessions(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle active sessions display"""
        user = update.callback_query.from_user
        foreman = self.user_manager.get_foreman(user.id)
        active_sessions = self.session_manager.get_active_sessions(foreman['id'])
        
        if not active_sessions:
            await update.callback_query.edit_message_text(
                "📋 **Активные сеансы**\n\nУ вас нет активных сеансов.",
                reply_markup=InlineKeyboardMarkup([[
                    InlineKeyboardButton("🔙 Назад", callback_data="sessions_menu")
                ]])
            )
            return
        
        sessions_text = "📋 **Активные сеансы:**\n\n"
        for session in active_sessions:
            start_time = datetime.fromisoformat(session['start_time']).strftime('%Y-%m-%d %H:%M:%S')
            sessions_text += f"**ID: {session['id']}**\n"
            sessions_text += f"👤 {session['first_name']} {session['last_name'] or ''}\n"
            sessions_text += f"💼 {session.get('position', 'Должность не указана')}\n"
            sessions_text += f"⏰ Начало: {start_time}\n"
            sessions_text += f"📝 {session['notes'] or 'Описание не указано'}\n\n"
        
        await update.callback_query.edit_message_text(
            sessions_text,
            reply_markup=InlineKeyboardMarkup([[
                InlineKeyboardButton("🔙 Назад", callback_data="sessions_menu")
            ]]),
            parse_mode='Markdown'
        )
    
    @log_important_action("generate_report")
    async def handle_generate_report(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle report generation"""
        user = update.callback_query.from_user
        foreman = self.user_manager.get_foreman(user.id)
        
        await update.callback_query.edit_message_text(
            "📊 **Создание отчета...**\n\nПожалуйста, подождите...",
            parse_mode='Markdown'
        )
        
        try:
            report_path = self.report_generator.generate_work_report(foreman['id'])
            
            if report_path and os.path.exists(report_path):
                try:
                    with open(report_path, 'rb') as report_file:
                        await context.bot.send_document(
                            chat_id=user.id,
                            document=report_file,
                            filename=os.path.basename(report_path),
                            caption="📊 **Отчет готов!**\n\nФайл с данными о рабочих сеансах."
                        )
                    
                    # Delete the report file after sending
                    os.remove(report_path)
                    
                    await update.callback_query.edit_message_text(
                        "✅ **Отчет создан!**\n\nОтчет отправлен в чат.",
                        reply_markup=InlineKeyboardMarkup([[
                            InlineKeyboardButton("🔙 Назад", callback_data="admin_menu")
                        ]]),
                        parse_mode='Markdown'
                    )
                except Exception as e:
                    # Clean up file even if sending fails
                    if os.path.exists(report_path):
                        os.remove(report_path)
                    logger.error(f"Error sending report: {e}")
                    raise e
            else:
                await update.callback_query.edit_message_text(
                    "❌ **Ошибка создания отчета!**\n\nНе удалось создать отчет.",
                    reply_markup=InlineKeyboardMarkup([[
                        InlineKeyboardButton("🔙 Назад", callback_data="admin_menu")
                    ]])
                )
        except Exception as e:
            logger.error(f"Error generating report: {e}")
            await update.callback_query.edit_message_text(
                "❌ **Ошибка создания отчета!**\n\nПроизошла ошибка при создании отчета. Попробуйте позже.",
                reply_markup=InlineKeyboardMarkup([[
                    InlineKeyboardButton("🔙 Назад", callback_data="admin_menu")
                ]])
            )
    
    
    @log_important_action("admin_generate_foreman_report")
    async def handle_admin_generate_foreman_report(self, update: Update, context: ContextTypes.DEFAULT_TYPE, foreman_id: int):
        """Handle admin generating report for specific foreman"""
        user = update.callback_query.from_user
        
        await update.callback_query.edit_message_text(
            "📊 **Создание отчета...**\n\nПожалуйста, подождите...",
            parse_mode='Markdown'
        )
        
        try:
            report_path = self.report_generator.generate_work_report(foreman_id)
            
            if report_path and os.path.exists(report_path):
                try:
                    with open(report_path, 'rb') as report_file:
                        await context.bot.send_document(
                            chat_id=user.id,
                            document=report_file,
                            filename=os.path.basename(report_path),
                            caption="📊 **Отчет готов!**\n\nФайл с данными о рабочих сеансах."
                        )
                    
                    # Delete the report file after sending
                    os.remove(report_path)
                    
                    await update.callback_query.edit_message_text(
                        "✅ **Отчет создан!**\n\nОтчет отправлен в чат.",
                        reply_markup=InlineKeyboardMarkup([[
                            InlineKeyboardButton("🔙 Назад", callback_data="admin_menu")
                        ]]),
                        parse_mode='Markdown'
                    )
                except Exception as e:
                    # Clean up file even if sending fails
                    if os.path.exists(report_path):
                        os.remove(report_path)
                    logger.error(f"Error sending report: {e}")
                    raise e
            else:
                await update.callback_query.edit_message_text(
                    "❌ **Ошибка создания отчета!**\n\nНе удалось создать отчет.",
                    reply_markup=InlineKeyboardMarkup([[
                        InlineKeyboardButton("🔙 Назад", callback_data="admin_menu")
                    ]])
                )
        except Exception as e:
            logger.error(f"Error generating report: {e}")
            await update.callback_query.edit_message_text(
                "❌ **Ошибка создания отчета!**\n\nПроизошла ошибка при создании отчета. Попробуйте позже.",
                reply_markup=InlineKeyboardMarkup([[
                    InlineKeyboardButton("🔙 Назад", callback_data="admin_menu")
                ]])
            )
    
    @log_important_action("generate_company_report")
    async def handle_generate_company_report(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle company-wide report generation"""
        user = update.callback_query.from_user
        
        await update.callback_query.edit_message_text(
            "🏢 **Создание отчета компании...**\n\nПожалуйста, подождите...",
            parse_mode='Markdown'
        )
        
        try:
            # Generate company report for the last 30 days
            end_date = datetime.now()
            start_date = end_date - timedelta(days=30)
            
            report_path = self.report_generator.generate_company_daily_report(start_date, end_date)
            
            if report_path and os.path.exists(report_path):
                try:
                    with open(report_path, 'rb') as report_file:
                        await context.bot.send_document(
                            chat_id=user.id,
                            document=report_file,
                            filename=os.path.basename(report_path),
                            caption="🏢 **Отчет компании готов!**\n\nФайл с ежедневной активностью всех рабочих."
                        )
                    
                    # Delete the report file after sending
                    os.remove(report_path)
                    
                    await update.callback_query.edit_message_text(
                        "✅ **Отчет компании создан!**\n\nОтчет за последние 30 дней отправлен в чат.",
                        reply_markup=InlineKeyboardMarkup([[
                            InlineKeyboardButton("🔙 Назад", callback_data="admin_menu")
                        ]]),
                        parse_mode='Markdown'
                    )
                except Exception as e:
                    # Clean up file even if sending fails
                    if os.path.exists(report_path):
                        os.remove(report_path)
                    logger.error(f"Error sending company report: {e}")
                    raise e
            else:
                await update.callback_query.edit_message_text(
                    "❌ **Ошибка создания отчета!**\n\nНе удалось создать отчет компании.",
                    reply_markup=InlineKeyboardMarkup([[
                        InlineKeyboardButton("🔙 Назад", callback_data="admin_menu")
                    ]])
                )
        except Exception as e:
            logger.error(f"Error generating company report: {e}")
            await update.callback_query.edit_message_text(
                "❌ **Ошибка создания отчета!**\n\nПроизошла ошибка при создании отчета компании. Попробуйте позже.",
                reply_markup=InlineKeyboardMarkup([[
                    InlineKeyboardButton("🔙 Назад", callback_data="admin_menu")
                ]])
            )
    
    async def handle_help(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle help request"""
        help_text = """
📚 **Apofeoz Work Manager - Помощь**

**🏗️ Основные функции:**
• Управление рабочими (добавление, деактивация)
• Управление рабочими сеансами (начало/завершение)
• Создание отчетов

**👥 Рабочие:**
• Добавляйте новых рабочих с указанием должности и ставки
• Просматривайте список всех рабочих
• Деактивируйте рабочих при необходимости

**⏰ Рабочие сеансы:**
• Начинайте сеансы для отдельных рабочих
• Завершайте сеансы для расчета времени и оплаты
• Просматривайте активные сеансы

**📊 Отчеты:**
• Создавайте детальные отчеты
• Отчеты включают данные о сеансах, рабочих и статистику

**💡 Советы:**
• Используйте кнопки для удобной навигации
• Все действия выполняются через меню
• Система автоматически рассчитывает часы работы
• При начале сеанса можно добавить описание работы
        """
        
        await update.callback_query.edit_message_text(
            help_text,
            reply_markup=InlineKeyboardMarkup([[
                InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")
            ]]),
            parse_mode='Markdown'
        )
    
    async def handle_message(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle text messages"""
        user = update.effective_user
        text = update.message.text
        
        # Handle notes input for work session
        if context.user_data.get('waiting_for_notes'):
            await self.process_session_notes(update, context, text)
            return
        
        if 'awaiting_input' not in context.user_data:
            await update.message.reply_text(
                "Используйте кнопки в меню для навигации или команду `/start` для открытия главного меню."
            )
            return
        
        awaiting = context.user_data['awaiting_input']
        
        if awaiting == 'add_worker':
            await self.process_add_worker(update, context, text)
        
        # Clear awaiting input
        context.user_data.pop('awaiting_input', None)
    
    @log_important_action("start_session")
    async def process_session_notes(self, update: Update, context: ContextTypes.DEFAULT_TYPE, text: str):
        """Process notes input for work session"""
        user = update.effective_user
        worker_id = context.user_data.get('pending_worker_id')
        foreman_id = context.user_data.get('pending_foreman_id')
        
        # Clear waiting state
        context.user_data.pop('waiting_for_notes', None)
        context.user_data.pop('pending_worker_id', None)
        context.user_data.pop('pending_foreman_id', None)
        
        # Process notes
        notes = None
        if text.strip().lower() != 'пропустить':
            notes = text.strip()
        
        # Start the work session
        session_id = self.session_manager.start_work_session(foreman_id, worker_id, notes)
        
        if session_id:
            notes_text = f"\n📝 Описание: {notes}" if notes else "\n📝 Описание: Не указано"
            await update.message.reply_text(
                f"✅ **Рабочий сеанс начат!**\n\n"
                f"Сеанс ID: {session_id}\n"
                f"Рабочий ID: {worker_id}\n"
                f"Время начала: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}"
                f"{notes_text}",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("⏹️ Завершить сеанс", callback_data="end_session")],
                    [InlineKeyboardButton("📋 Активные сеансы", callback_data="active_sessions")],
                    [InlineKeyboardButton("🏠 Главное меню", callback_data="main_menu")]
                ]),
                parse_mode='Markdown'
            )
        else:
            await update.message.reply_text(
                "❌ **Ошибка начала сеанса!**\n\nНе удалось начать рабочий сеанс.",
                reply_markup=InlineKeyboardMarkup([
                    [InlineKeyboardButton("🔄 Попробовать снова", callback_data="start_session")],
                    [InlineKeyboardButton("🏠 Главное меню", callback_data="main_menu")]
                ])
            )
    
    @log_important_action("add_worker")
    async def process_add_worker(self, update: Update, context: ContextTypes.DEFAULT_TYPE, text: str):
        """Process add worker input"""
        user = update.effective_user
        foreman = self.user_manager.get_foreman(user.id)
        
        parts = text.strip().split()
        if len(parts) < 2:
            await update.message.reply_text(
                "❌ **Неверный формат!**\n\n"
                "Используйте формат: `Имя Фамилия [Должность]`\n"
                "Пример: `Иван Петров Плотник`",
                parse_mode='Markdown'
            )
            return
        
        first_name = parts[0]
        last_name = parts[1] if len(parts) > 1 else None
        position = parts[2] if len(parts) > 2 else None
        
        success = self.worker_manager.add_worker(
            user_id=foreman['id'],
            first_name=first_name,
            last_name=last_name,
            position=position
        )
        
        if success:
            # Return to foreman menu with updated worker list
            await self.show_foreman_menu(update, context)
        else:
            await update.message.reply_text(
                "❌ **Ошибка добавления рабочего!**\n\nПопробуйте еще раз.",
                reply_markup=InlineKeyboardMarkup([[
                    InlineKeyboardButton("🔙 Назад", callback_data="foreman_menu")
                ]])
            )
    
    @log_error
    async def error_handler(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle errors"""
        logger.error(f"Exception while handling an update: {context.error}")
        if update and update.effective_message:
            await update.effective_message.reply_text(
                "❌ Произошла ошибка. Попробуйте еще раз или обратитесь к администратору."
            )
    
    def run(self):
        """Run the bot"""
        logger.info("Starting Apofeoz Work Manager Bot...")
        self.application.run_polling()

def main():
    """Main function"""
    try:
        bot = ApofeozWorkBot()
        bot.run()
    except Exception as e:
        logger.error(f"Failed to start bot: {e}")
        raise

if __name__ == "__main__":
    main()
