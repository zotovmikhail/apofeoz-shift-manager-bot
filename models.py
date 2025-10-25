"""
Database models for Apofeoz Work Manager Bot
"""

import sqlite3
from datetime import datetime, timedelta
from typing import List, Optional, Dict, Any
import logging

logger = logging.getLogger(__name__)

class DatabaseManager:
    def __init__(self, db_path: str = "apofeoz_shifts.db"):
        self.db_path = db_path
        self.init_database()
    
    def init_database(self):
        """Initialize database with required tables"""
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            
            # Create users table (unified table for all user types)
            cursor.execute('''
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    telegram_id INTEGER UNIQUE NOT NULL,
                    username TEXT,
                    first_name TEXT NOT NULL,
                    last_name TEXT,
                    phone TEXT,
                    role TEXT NOT NULL DEFAULT 'foreman',
                    is_active BOOLEAN DEFAULT 1,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            ''')
            
            # Create workers table
            cursor.execute('''
                CREATE TABLE IF NOT EXISTS workers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    first_name TEXT NOT NULL,
                    last_name TEXT,
                    position TEXT,
                    is_active BOOLEAN DEFAULT 1,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users (id)
                )
            ''')
            
            # Create work_sessions table
            cursor.execute('''
                CREATE TABLE IF NOT EXISTS work_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    worker_id INTEGER NOT NULL,
                    start_time TIMESTAMP NOT NULL,
                    end_time TIMESTAMP,
                    total_hours REAL DEFAULT 0.0,
                    notes TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users (id),
                    FOREIGN KEY (worker_id) REFERENCES workers (id)
                )
            ''')
            
            conn.commit()
            logger.debug("Database initialized successfully")

class User:
    def __init__(self, db_manager: DatabaseManager):
        self.db = db_manager
    
    def add_user(self, telegram_id: int, first_name: str, username: str = None, 
                 last_name: str = None, phone: str = None, role: str = 'foreman') -> bool:
        """Add a new user with specified role"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    INSERT INTO users (telegram_id, username, first_name, last_name, phone, role)
                    VALUES (?, ?, ?, ?, ?, ?)
                ''', (telegram_id, username, first_name, last_name, phone, role))
                conn.commit()
                logger.info(f"User registered: {first_name} (ID: {telegram_id}, Role: {role})")
                return True
        except sqlite3.IntegrityError:
            logger.debug(f"User already exists: {telegram_id}")
            return False
        except Exception as e:
            logger.error(f"Error adding user: {e}")
            return False
    
    def get_user(self, telegram_id: int) -> Optional[Dict[str, Any]]:
        """Get user by telegram ID"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    SELECT * FROM users WHERE telegram_id = ? AND is_active = 1
                ''', (telegram_id,))
                row = cursor.fetchone()
                if row:
                    columns = [description[0] for description in cursor.description]
                    return dict(zip(columns, row))
                return None
        except Exception as e:
            logger.error(f"Error getting user: {e}")
            return None
    
    def get_foreman(self, telegram_id: int) -> Optional[Dict[str, Any]]:
        """Get foreman by telegram ID (for backward compatibility)"""
        user = self.get_user(telegram_id)
        if user and user['role'] in ['foreman', 'admin']:
            return user
        return None
    
    def get_admin(self, telegram_id: int) -> Optional[Dict[str, Any]]:
        """Get admin by telegram ID"""
        user = self.get_user(telegram_id)
        if user and user['role'] == 'admin':
            return user
        return None
    
    def get_user_by_id(self, user_id: int) -> Optional[Dict[str, Any]]:
        """Get user by internal ID"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    SELECT * FROM users WHERE id = ? AND is_active = 1
                ''', (user_id,))
                row = cursor.fetchone()
                if row:
                    columns = [description[0] for description in cursor.description]
                    return dict(zip(columns, row))
                return None
        except Exception as e:
            logger.error(f"Error getting user by ID: {e}")
            return None
    
    def update_user_role(self, telegram_id: int, new_role: str) -> bool:
        """Update user role (admin, foreman, user)"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    UPDATE users SET role = ? WHERE telegram_id = ?
                ''', (new_role, telegram_id))
                conn.commit()
                logger.info(f"User role updated: {telegram_id} -> {new_role}")
                return True
        except Exception as e:
            logger.error(f"Error updating user role: {e}")
            return False
    
    def get_all_users(self) -> List[Dict[str, Any]]:
        """Get all active users"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    SELECT * FROM users WHERE is_active = 1 ORDER BY created_at DESC
                ''')
                rows = cursor.fetchall()
                columns = [description[0] for description in cursor.description]
                return [dict(zip(columns, row)) for row in rows]
        except Exception as e:
            logger.error(f"Error getting all users: {e}")
            return []
    
    def deactivate_user(self, telegram_id: int) -> bool:
        """Deactivate user"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    UPDATE users SET is_active = 0 WHERE telegram_id = ?
                ''', (telegram_id,))
                conn.commit()
                logger.info(f"User deactivated: {telegram_id}")
                return True
        except Exception as e:
            logger.error(f"Error deactivating user: {e}")
            return False
    
    def get_all_workers(self) -> List[Dict[str, Any]]:
        """Get all active workers with their user info"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    SELECT w.*, u.first_name as foreman_first_name, u.last_name as foreman_last_name
                    FROM workers w
                    JOIN users u ON w.user_id = u.id
                    WHERE w.is_active = 1
                    ORDER BY w.first_name, w.last_name
                ''')
                rows = cursor.fetchall()
                columns = [description[0] for description in cursor.description]
                return [dict(zip(columns, row)) for row in rows]
        except Exception as e:
            logger.error(f"Error getting all workers: {e}")
            return []
    
    def move_worker_to_foreman(self, worker_id: int, new_user_id: int) -> bool:
        """Move worker to different user"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    UPDATE workers SET user_id = ? WHERE id = ?
                ''', (new_user_id, worker_id))
                conn.commit()
                logger.info(f"Worker {worker_id} moved to user {new_user_id}")
                return True
        except Exception as e:
            logger.error(f"Error moving worker: {e}")
            return False
    
    def get_all_foremen(self) -> List[Dict[str, Any]]:
        """Get all active foremen"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('SELECT * FROM users WHERE role IN ("foreman", "admin") AND is_active = 1')
                rows = cursor.fetchall()
                columns = [description[0] for description in cursor.description]
                return [dict(zip(columns, row)) for row in rows]
        except Exception as e:
            logger.error(f"Error getting foremen: {e}")
            return []

class Worker:
    def __init__(self, db_manager: DatabaseManager):
        self.db = db_manager
    
    def add_worker(self, user_id: int, first_name: str, last_name: str = None,
                  position: str = None) -> bool:
        """Add a new worker"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    INSERT INTO workers (user_id, first_name, last_name, position)
                    VALUES (?, ?, ?, ?)
                ''', (user_id, first_name, last_name, position))
                conn.commit()
                logger.info(f"Worker added: {first_name} {last_name or ''} (User ID: {user_id})")
                return True
        except Exception as e:
            logger.error(f"Error adding worker: {e}")
            return False
    
    def get_workers_by_user(self, user_id: int) -> List[Dict[str, Any]]:
        """Get all workers for a specific user"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    SELECT * FROM workers WHERE user_id = ? AND is_active = 1
                    ORDER BY first_name, last_name
                ''', (user_id,))
                rows = cursor.fetchall()
                columns = [description[0] for description in cursor.description]
                return [dict(zip(columns, row)) for row in rows]
        except Exception as e:
            logger.error(f"Error getting workers: {e}")
            return []
    
    def get_workers_by_foreman(self, user_id: int) -> List[Dict[str, Any]]:
        """Get all workers for a specific user (backward compatibility)"""
        return self.get_workers_by_user(user_id)
    
    def deactivate_worker(self, worker_id: int) -> bool:
        """Deactivate worker"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    UPDATE workers SET is_active = 0 WHERE id = ?
                ''', (worker_id,))
                conn.commit()
                logger.info(f"Worker deactivated: {worker_id}")
                return True
        except Exception as e:
            logger.error(f"Error deactivating worker: {e}")
            return False
    
    def get_worker_activity(self, worker_id: int, hours: int = 12) -> List[Dict[str, Any]]:
        """Get worker activity for the last N hours"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cutoff_time = datetime.now() - timedelta(hours=hours)
                cursor.execute('''
                    SELECT ws.*, u.first_name as foreman_first_name, u.last_name as foreman_last_name
                    FROM work_sessions ws
                    JOIN users u ON ws.user_id = u.id
                    WHERE ws.worker_id = ? AND ws.start_time >= ?
                    ORDER BY ws.start_time DESC
                ''', (worker_id, cutoff_time))
                rows = cursor.fetchall()
                columns = [description[0] for description in cursor.description]
                return [dict(zip(columns, row)) for row in rows]
        except Exception as e:
            logger.error(f"Error getting worker activity: {e}")
            return []

class WorkSession:
    def __init__(self, db_manager: DatabaseManager):
        self.db = db_manager
    
    def start_work_session(self, user_id: int, worker_id: int, notes: str = None) -> Optional[int]:
        """Start a work session for a worker"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                
                cursor.execute('''
                    INSERT INTO work_sessions (user_id, worker_id, start_time, notes)
                    VALUES (?, ?, ?, ?)
                ''', (user_id, worker_id, datetime.now(), notes))
                session_id = cursor.lastrowid
                conn.commit()
                logger.info(f"Work session started: {session_id} (Worker ID: {worker_id})")
                return session_id
        except Exception as e:
            logger.error(f"Error starting work session: {e}")
            return None
    
    def end_work_session(self, session_id: int) -> bool:
        """End a work session and calculate hours"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                
                # Get session details
                cursor.execute('''
                    SELECT start_time FROM work_sessions WHERE id = ?
                ''', (session_id,))
                result = cursor.fetchone()
                if not result:
                    return False
                
                start_time = result[0]
                end_time = datetime.now()
                
                # Calculate hours
                duration = end_time - datetime.fromisoformat(start_time)
                total_hours = duration.total_seconds() / 3600
                
                cursor.execute('''
                    UPDATE work_sessions 
                    SET end_time = ?, total_hours = ?
                    WHERE id = ?
                ''', (end_time, total_hours, session_id))
                conn.commit()
                logger.info(f"Work session ended: {session_id} (Hours: {total_hours:.2f})")
                return True
        except Exception as e:
            logger.error(f"Error ending work session: {e}")
            return False
    
    def get_active_sessions(self, user_id: int) -> List[Dict[str, Any]]:
        """Get all active work sessions for a foreman"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    SELECT ws.*, w.first_name, w.last_name 
                    FROM work_sessions ws
                    JOIN workers w ON ws.worker_id = w.id
                    WHERE ws.user_id = ? AND ws.end_time IS NULL
                ''', (user_id,))
                rows = cursor.fetchall()
                columns = [description[0] for description in cursor.description]
                return [dict(zip(columns, row)) for row in rows]
        except Exception as e:
            logger.error(f"Error getting active sessions: {e}")
            return []
    
    def get_active_sessions_by_worker(self, worker_id: int) -> List[Dict[str, Any]]:
        """Get all active work sessions for a specific worker"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    SELECT ws.*, w.first_name, w.last_name, u.first_name as foreman_first_name, u.last_name as foreman_last_name
                    FROM work_sessions ws
                    JOIN workers w ON ws.worker_id = w.id
                    JOIN users u ON ws.user_id = u.id
                    WHERE ws.worker_id = ? AND ws.end_time IS NULL
                ''', (worker_id,))
                rows = cursor.fetchall()
                columns = [description[0] for description in cursor.description]
                return [dict(zip(columns, row)) for row in rows]
        except Exception as e:
            logger.error(f"Error getting active sessions for worker: {e}")
            return []
    
    def get_sessions_by_foreman(self, foreman_id: int, start_date: datetime = None, 
                               end_date: datetime = None) -> List[Dict[str, Any]]:
        """Get all work sessions for a foreman within date range"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                
                query = '''
                    SELECT ws.*, w.first_name, w.last_name, w.position
                    FROM work_sessions ws
                    JOIN workers w ON ws.worker_id = w.id
                    WHERE ws.foreman_id = ?
                '''
                params = [foreman_id]
                
                if start_date and end_date:
                    query += ' AND DATE(ws.start_time) BETWEEN DATE(?) AND DATE(?)'
                    params.extend([start_date, end_date])
                
                query += ' ORDER BY ws.start_time DESC'
                
                cursor.execute(query, params)
                rows = cursor.fetchall()
                columns = [description[0] for description in cursor.description]
                return [dict(zip(columns, row)) for row in rows]
        except Exception as e:
            logger.error(f"Error getting sessions: {e}")
            return []

class Admin:
    def __init__(self, db_manager: DatabaseManager):
        self.db = db_manager
    
    def add_admin(self, telegram_id: int, first_name: str, username: str = None, 
                  last_name: str = None) -> bool:
        """Add a new admin"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    INSERT INTO admins (telegram_id, username, first_name, last_name)
                    VALUES (?, ?, ?, ?)
                ''', (telegram_id, username, first_name, last_name))
                conn.commit()
                logger.info(f"Admin registered: {first_name} (ID: {telegram_id})")
                return True
        except sqlite3.IntegrityError:
            logger.debug(f"Admin already exists: {telegram_id}")
            return False
        except Exception as e:
            logger.error(f"Error adding admin: {e}")
            return False
    
    def get_admin(self, telegram_id: int) -> Optional[Dict[str, Any]]:
        """Get admin by telegram ID"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    SELECT * FROM admins WHERE telegram_id = ? AND is_active = 1
                ''', (telegram_id,))
                row = cursor.fetchone()
                if row:
                    columns = [description[0] for description in cursor.description]
                    return dict(zip(columns, row))
                return None
        except Exception as e:
            logger.error(f"Error getting admin: {e}")
            return None
    
    def get_all_admins(self) -> List[Dict[str, Any]]:
        """Get all active admins"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('SELECT * FROM admins WHERE is_active = 1')
                rows = cursor.fetchall()
                columns = [description[0] for description in cursor.description]
                return [dict(zip(columns, row)) for row in rows]
        except Exception as e:
            logger.error(f"Error getting admins: {e}")
            return []
    
    def deactivate_admin(self, admin_id: int) -> bool:
        """Deactivate an admin"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    UPDATE admins SET is_active = 0 WHERE id = ?
                ''', (admin_id,))
                conn.commit()
                logger.info(f"Admin deactivated: {admin_id}")
                return True
        except Exception as e:
            logger.error(f"Error deactivating admin: {e}")
            return False
    
    def get_all_foremen(self) -> List[Dict[str, Any]]:
        """Get all foremen for admin management"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('SELECT * FROM users WHERE role IN ("foreman", "admin") AND is_active = 1')
                rows = cursor.fetchall()
                columns = [description[0] for description in cursor.description]
                return [dict(zip(columns, row)) for row in rows]
        except Exception as e:
            logger.error(f"Error getting foremen: {e}")
            return []
    
    def deactivate_foreman(self, foreman_id: int) -> bool:
        """Deactivate a foreman"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    UPDATE users SET is_active = 0 WHERE id = ?
                ''', (foreman_id,))
                conn.commit()
                logger.info(f"Foreman deactivated: {foreman_id}")
                return True
        except Exception as e:
            logger.error(f"Error deactivating foreman: {e}")
            return False
    
    def get_all_workers(self) -> List[Dict[str, Any]]:
        """Get all workers for admin management"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    SELECT w.*, u.first_name as foreman_first_name, u.last_name as foreman_last_name
                    FROM workers w
                    JOIN users u ON w.user_id = u.id
                    WHERE w.is_active = 1
                    ORDER BY u.first_name, w.first_name
                ''')
                rows = cursor.fetchall()
                columns = [description[0] for description in cursor.description]
                return [dict(zip(columns, row)) for row in rows]
        except Exception as e:
            logger.error(f"Error getting workers: {e}")
            return []
    
    def deactivate_worker_by_admin(self, worker_id: int) -> bool:
        """Deactivate a worker (admin function)"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    UPDATE workers SET is_active = 0 WHERE id = ?
                ''', (worker_id,))
                conn.commit()
                logger.info(f"Worker deactivated by admin: {worker_id}")
                return True
        except Exception as e:
            logger.error(f"Error deactivating worker: {e}")
            return False
    
    def move_worker_to_foreman(self, worker_id: int, new_foreman_id: int) -> bool:
        """Move a worker to a different foreman"""
        try:
            with sqlite3.connect(self.db.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    UPDATE workers SET foreman_id = ? WHERE id = ?
                ''', (new_foreman_id, worker_id))
                conn.commit()
                logger.info(f"Worker {worker_id} moved to foreman {new_foreman_id}")
                return True
        except Exception as e:
            logger.error(f"Error moving worker: {e}")
            return False
