"""
Excel Report Generator for Apofeoz Work Manager
"""

import pandas as pd
from datetime import datetime, timedelta
import sqlite3
from typing import List, Dict, Any, Optional
import logging
import os

logger = logging.getLogger(__name__)

class ReportGenerator:
    def __init__(self, db_path: str = "data/apofeoz_shifts.db"):
        self.db_path = db_path
    
    def _hours_to_shifts(self, hours: float) -> float:
        """Convert hours to shifts (8 hours = 1 shift)"""
        return hours / 8.0
    
    def generate_work_report(self, foreman_id: int, start_date: datetime = None, 
                            end_date: datetime = None) -> Optional[str]:
        """Generate Excel report for a foreman's work sessions"""
        try:
            # Set default date range if not provided
            if start_date is None:
                start_date = datetime.now() - timedelta(days=30)
            if end_date is None:
                end_date = datetime.now()
            
            # Get work sessions data
            sessions_data = self._get_sessions_data(foreman_id, start_date, end_date)
            if not sessions_data:
                logger.warning(f"No work sessions found for foreman {foreman_id}")
                return None
            
            # Get workers data
            workers_data = self._get_workers_data(foreman_id)
            
            # Create Excel file
            filename = f"work_report_foreman_{foreman_id}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.xlsx"
            filepath = os.path.join("reports", filename)
            
            # Create reports directory if it doesn't exist
            os.makedirs("reports", exist_ok=True)
            
            with pd.ExcelWriter(filepath, engine='openpyxl') as writer:
                # Create detailed sessions sheet
                self._create_sessions_sheet(writer, sessions_data)
                
                # Create workers summary sheet
                if workers_data:
                    self._create_workers_sheet(writer, workers_data)
                
                # Create summary sheet
                self._create_summary_sheet(writer, sessions_data, workers_data)
            
            logger.info(f"Foreman report generated: {filepath}")
            return filepath
            
        except Exception as e:
            logger.error(f"Error generating report: {e}")
            return None
    
    def generate_company_daily_report(self, start_date: datetime = None, 
                                     end_date: datetime = None) -> Optional[str]:
        """Generate comprehensive daily report for company owner showing all workers' activity by day"""
        try:
            # Set default date range if not provided
            if start_date is None:
                start_date = datetime.now() - timedelta(days=30)
            if end_date is None:
                end_date = datetime.now()
            
            # Get daily summary data
            daily_summary = self._get_daily_summary_data(start_date, end_date)
            if not daily_summary:
                logger.warning(f"No work data found for the specified date range")
                return None
            
            # Get detailed sessions data
            sessions_data = self._get_all_sessions_daily_data(start_date, end_date)
            
            # Get all workers data
            workers_data = self._get_all_workers_data()
            
            # Create Excel file
            filename = f"company_daily_report_{start_date.strftime('%Y%m%d')}_{end_date.strftime('%Y%m%d')}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.xlsx"
            filepath = os.path.join("reports", filename)
            
            # Create reports directory if it doesn't exist
            os.makedirs("reports", exist_ok=True)
            
            with pd.ExcelWriter(filepath, engine='openpyxl') as writer:
                # Create daily summary sheet
                self._create_daily_summary_sheet(writer, daily_summary)
                
                # Create detailed sessions sheet
                if sessions_data:
                    self._create_detailed_sessions_sheet(writer, sessions_data)
                
                # Create workers overview sheet
                if workers_data:
                    self._create_workers_overview_sheet(writer, workers_data)
                
                # Create company summary sheet
                self._create_company_summary_sheet(writer, daily_summary, workers_data)
            
            logger.info(f"Company daily report generated: {filepath}")
            return filepath
            
        except Exception as e:
            logger.error(f"Error generating company daily report: {e}")
            return None
    
    def _get_sessions_data(self, foreman_id: int, start_date: datetime, end_date: datetime) -> List[Dict[str, Any]]:
        """Get work sessions data for the report"""
        try:
            with sqlite3.connect(self.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    SELECT 
                        ws.id,
                        ws.worker_id,
                        w.first_name,
                        w.last_name,
                        w.position,
                        ws.start_time,
                        ws.end_time,
                        COALESCE(NULLIF(ws.total_hours, 0), (julianday(ws.end_time) - julianday(ws.start_time)) * 24.0) as total_hours,
                        ws.notes,
                        CASE 
                            WHEN ws.end_time IS NOT NULL 
                            THEN (julianday(ws.end_time) - julianday(ws.start_time)) * 24
                            ELSE NULL 
                        END as calculated_hours
                    FROM work_sessions ws
                    JOIN workers w ON ws.worker_id = w.id
                    WHERE ws.user_id = ? 
                    AND DATE(ws.start_time) BETWEEN DATE(?) AND DATE(?)
                    ORDER BY ws.start_time DESC
                ''', (foreman_id, start_date, end_date))
                
                rows = cursor.fetchall()
                columns = ['session_id', 'worker_id', 'first_name', 'last_name', 'position', 
                          'start_time', 'end_time', 'total_hours', 'notes', 'calculated_hours']
                return [dict(zip(columns, row)) for row in rows]
        except Exception as e:
            logger.error(f"Error getting sessions data: {e}")
            return []
    
    def _get_workers_data(self, foreman_id: int) -> List[Dict[str, Any]]:
        """Get workers data for the report"""
        try:
            with sqlite3.connect(self.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    SELECT 
                        w.id,
                        w.first_name,
                        w.last_name,
                        w.position,
                        w.created_at,
                        w.is_active,
                        COUNT(ws.id) as total_sessions,
                        SUM(CASE WHEN ws.end_time IS NOT NULL THEN COALESCE(NULLIF(ws.total_hours, 0), (julianday(ws.end_time) - julianday(ws.start_time)) * 24.0) ELSE 0 END) as total_hours
                    FROM workers w
                    LEFT JOIN work_sessions ws ON w.id = ws.worker_id
                    WHERE w.user_id = ?
                    GROUP BY w.id
                    ORDER BY w.first_name, w.last_name
                ''', (foreman_id,))
                
                rows = cursor.fetchall()
                columns = ['worker_id', 'first_name', 'last_name', 'position',
                          'created_at', 'is_active', 'total_sessions', 'total_hours']
                return [dict(zip(columns, row)) for row in rows]
        except Exception as e:
            logger.error(f"Error getting workers data: {e}")
            return []
    
    def _get_daily_summary_data(self, start_date: datetime, end_date: datetime) -> List[Dict[str, Any]]:
        """Get daily summary data for all workers"""
        try:
            with sqlite3.connect(self.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    SELECT 
                        DATE(ws.start_time) as work_date,
                        w.first_name,
                        w.last_name,
                        w.position,
                        u.first_name as foreman_first_name,
                        u.last_name as foreman_last_name,
                        COUNT(ws.id) as sessions_count,
                        SUM(COALESCE(NULLIF(ws.total_hours, 0), (julianday(ws.end_time) - julianday(ws.start_time)) * 24.0)) as total_hours,
                        AVG(COALESCE(NULLIF(ws.total_hours, 0), (julianday(ws.end_time) - julianday(ws.start_time)) * 24.0)) as avg_hours_per_session
                    FROM work_sessions ws
                    JOIN workers w ON ws.worker_id = w.id
                    JOIN users u ON ws.user_id = u.id
                    WHERE ws.end_time IS NOT NULL
                    AND DATE(ws.start_time) BETWEEN DATE(?) AND DATE(?)
                    GROUP BY DATE(ws.start_time), w.id
                    ORDER BY DATE(ws.start_time) DESC, w.first_name, w.last_name
                ''', (start_date, end_date))
                
                rows = cursor.fetchall()
                columns = ['work_date', 'first_name', 'last_name', 'position', 
                          'foreman_first_name', 'foreman_last_name', 'sessions_count', 
                          'total_hours', 'avg_hours_per_session']
                return [dict(zip(columns, row)) for row in rows]
        except Exception as e:
            logger.error(f"Error getting daily summary data: {e}")
            return []
    
    def _get_all_sessions_daily_data(self, start_date: datetime, end_date: datetime) -> List[Dict[str, Any]]:
        """Get all work sessions data for company-wide report"""
        try:
            with sqlite3.connect(self.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    SELECT 
                        ws.id,
                        ws.worker_id,
                        w.first_name,
                        w.last_name,
                        w.position,
                        ws.start_time,
                        ws.end_time,
                        COALESCE(NULLIF(ws.total_hours, 0), (julianday(ws.end_time) - julianday(ws.start_time)) * 24.0) as total_hours,
                        ws.notes,
                        u.first_name as foreman_first_name,
                        u.last_name as foreman_last_name,
                        DATE(ws.start_time) as work_date
                    FROM work_sessions ws
                    JOIN workers w ON ws.worker_id = w.id
                    JOIN users u ON ws.user_id = u.id
                    WHERE ws.end_time IS NOT NULL
                    AND DATE(ws.start_time) BETWEEN DATE(?) AND DATE(?)
                    ORDER BY DATE(ws.start_time) DESC, w.first_name, w.last_name
                ''', (start_date, end_date))
                
                rows = cursor.fetchall()
                columns = ['session_id', 'worker_id', 'first_name', 'last_name', 'position',
                          'start_time', 'end_time', 'total_hours', 'notes',
                          'foreman_first_name', 'foreman_last_name', 'work_date']
                return [dict(zip(columns, row)) for row in rows]
        except Exception as e:
            logger.error(f"Error getting all sessions daily data: {e}")
            return []
    
    def _get_all_workers_data(self) -> List[Dict[str, Any]]:
        """Get all workers data for company-wide report"""
        try:
            with sqlite3.connect(self.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    SELECT 
                        w.id,
                        w.first_name,
                        w.last_name,
                        w.position,
                        w.created_at,
                        w.is_active,
                        u.first_name as foreman_first_name,
                        u.last_name as foreman_last_name,
                        COUNT(ws.id) as total_sessions,
                        SUM(CASE WHEN ws.end_time IS NOT NULL THEN COALESCE(NULLIF(ws.total_hours, 0), (julianday(ws.end_time) - julianday(ws.start_time)) * 24.0) ELSE 0 END) as total_hours
                    FROM workers w
                    JOIN users u ON w.user_id = u.id
                    LEFT JOIN work_sessions ws ON w.id = ws.worker_id
                    WHERE w.is_active = 1
                    GROUP BY w.id
                    ORDER BY u.first_name, w.first_name, w.last_name
                ''')
                
                rows = cursor.fetchall()
                columns = ['worker_id', 'first_name', 'last_name', 'position',
                          'created_at', 'is_active', 'foreman_first_name', 'foreman_last_name',
                          'total_sessions', 'total_hours']
                return [dict(zip(columns, row)) for row in rows]
        except Exception as e:
            logger.error(f"Error getting all workers data: {e}")
            return []
    
    def _create_sessions_sheet(self, writer, sessions_data: List[Dict[str, Any]]):
        """Create detailed work sessions sheet"""
        if not sessions_data:
            return
        
        df = pd.DataFrame(sessions_data)
        
        # Format datetime columns
        if 'start_time' in df.columns:
            df['start_time'] = pd.to_datetime(df['start_time']).dt.strftime('%Y-%m-%d %H:%M:%S')
        if 'end_time' in df.columns:
            df['end_time'] = pd.to_datetime(df['end_time']).dt.strftime('%Y-%m-%d %H:%M:%S')
        
        # Format numeric columns
        numeric_columns = ['total_hours', 'calculated_hours']
        for col in numeric_columns:
            if col in df.columns:
                df[col] = pd.to_numeric(df[col], errors='coerce').round(2)
        
        # Rename columns for better readability
        column_mapping = {
            'session_id': 'ID Сеанса',
            'worker_id': 'ID Рабочего',
            'first_name': 'Имя',
            'last_name': 'Фамилия',
            'position': 'Должность',
            'start_time': 'Время Начала',
            'end_time': 'Время Окончания',
            'total_hours': 'Часы',
            'notes': 'Заметки',
            'calculated_hours': 'Рассчитанные Часы'
        }
        
        df = df.rename(columns=column_mapping)
        df.to_excel(writer, sheet_name='Рабочие Сеансы', index=False)
        
        # Auto-adjust column widths
        worksheet = writer.sheets['Рабочие Сеансы']
        for column in worksheet.columns:
            max_length = 0
            column_letter = column[0].column_letter
            for cell in column:
                try:
                    if len(str(cell.value)) > max_length:
                        max_length = len(str(cell.value))
                except:
                    pass
            adjusted_width = min(max_length + 2, 50)
            worksheet.column_dimensions[column_letter].width = adjusted_width
    
    def _create_workers_sheet(self, writer, workers_data: List[Dict[str, Any]]):
        """Create workers summary sheet"""
        if not workers_data:
            return
        
        df = pd.DataFrame(workers_data)
        
        # Format datetime columns
        if 'created_at' in df.columns:
            df['created_at'] = pd.to_datetime(df['created_at']).dt.strftime('%Y-%m-%d')
        
        # Format numeric columns
        numeric_columns = ['total_hours']
        for col in numeric_columns:
            if col in df.columns:
                df[col] = pd.to_numeric(df[col], errors='coerce').round(2)
        
        # Format boolean columns
        if 'is_active' in df.columns:
            df['is_active'] = df['is_active'].map({1: 'Активен', 0: 'Неактивен'})
        
        # Rename columns for better readability
        column_mapping = {
            'worker_id': 'ID Рабочего',
            'first_name': 'Имя',
            'last_name': 'Фамилия',
            'position': 'Должность',
            'created_at': 'Дата Регистрации',
            'is_active': 'Статус',
            'total_sessions': 'Всего Сеансов',
            'total_hours': 'Всего Часов'
        }
        
        df = df.rename(columns=column_mapping)
        df.to_excel(writer, sheet_name='Рабочие', index=False)
        
        # Auto-adjust column widths
        worksheet = writer.sheets['Рабочие']
        for column in worksheet.columns:
            max_length = 0
            column_letter = column[0].column_letter
            for cell in column:
                try:
                    if len(str(cell.value)) > max_length:
                        max_length = len(str(cell.value))
                except:
                    pass
            adjusted_width = min(max_length + 2, 50)
            worksheet.column_dimensions[column_letter].width = adjusted_width
    
    def _create_summary_sheet(self, writer, sessions_data: List[Dict[str, Any]], 
                             workers_data: List[Dict[str, Any]]):
        """Create summary sheet with statistics"""
        summary_data = []
        
        # Calculate summary statistics
        if sessions_data:
            df_sessions = pd.DataFrame(sessions_data)
            
            # Total sessions
            total_sessions = len(df_sessions)
            completed_sessions = len(df_sessions[df_sessions['end_time'].notna()])
            active_sessions = total_sessions - completed_sessions
            
            # Total hours
            total_hours = df_sessions['total_hours'].sum()
            
            # Average session duration
            avg_hours = df_sessions['total_hours'].mean() if completed_sessions > 0 else 0
            
            summary_data.extend([
                {'Метрика': 'Всего рабочих сеансов', 'Значение': total_sessions},
                {'Метрика': 'Завершенных сеансов', 'Значение': completed_sessions},
                {'Метрика': 'Активных сеансов', 'Значение': active_sessions},
                {'Метрика': 'Общее количество часов', 'Значение': f"{total_hours:.2f}"},
                {'Метрика': 'Средняя продолжительность сеанса', 'Значение': f"{avg_hours:.2f} ч"},
            ])
        
        if workers_data:
            df_workers = pd.DataFrame(workers_data)
            active_workers = len(df_workers[df_workers['is_active'] == 1])
            total_workers = len(df_workers)
            
            summary_data.extend([
                {'Метрика': 'Всего рабочих', 'Значение': total_workers},
                {'Метрика': 'Активных рабочих', 'Значение': active_workers},
                {'Метрика': 'Неактивных рабочих', 'Значение': total_workers - active_workers},
            ])
        
        if summary_data:
            df_summary = pd.DataFrame(summary_data)
            df_summary.to_excel(writer, sheet_name='Сводка', index=False)
            
            # Auto-adjust column widths
            worksheet = writer.sheets['Сводка']
            for column in worksheet.columns:
                max_length = 0
                column_letter = column[0].column_letter
                for cell in column:
                    try:
                        if len(str(cell.value)) > max_length:
                            max_length = len(str(cell.value))
                    except:
                        pass
                adjusted_width = min(max_length + 2, 50)
                worksheet.column_dimensions[column_letter].width = adjusted_width
    
    def _create_daily_summary_sheet(self, writer, daily_summary: List[Dict[str, Any]]):
        """Create daily summary sheet showing worker activity by day"""
        if not daily_summary:
            return
        
        df = pd.DataFrame(daily_summary)
        
        # Format numeric columns
        numeric_columns = ['sessions_count', 'total_hours', 'avg_hours_per_session']
        for col in numeric_columns:
            if col in df.columns:
                df[col] = pd.to_numeric(df[col], errors='coerce').round(2)
        
        # Convert hours to shifts for display
        if 'total_hours' in df.columns:
            df['total_shifts'] = df['total_hours'].apply(self._hours_to_shifts).round(3)
        
        # Combine worker names
        if 'first_name' in df.columns and 'last_name' in df.columns:
            df['worker_full_name'] = df['first_name'] + ' ' + df['last_name'].fillna('')
            df['worker_full_name'] = df['worker_full_name'].str.strip()
        
        # Combine foreman names
        if 'foreman_first_name' in df.columns and 'foreman_last_name' in df.columns:
            df['foreman_full_name'] = df['foreman_first_name'] + ' ' + df['foreman_last_name'].fillna('')
            df['foreman_full_name'] = df['foreman_full_name'].str.strip()
        
        # Remove old name columns
        columns_to_drop = ['first_name', 'last_name', 'foreman_first_name', 'foreman_last_name']
        for col in columns_to_drop:
            if col in df.columns:
                df = df.drop(columns=[col])
        
        # Rename columns for better readability
        column_mapping = {
            'work_date': 'Дата',
            'worker_full_name': 'Рабочий',
            'foreman_full_name': 'Бригадир',
            'position': 'Должность',
            'sessions_count': 'Количество Сеансов',
            'total_hours': 'Общее Время (часы)',
            'total_shifts': 'Общее Время (смены)'
        }
        
        df = df.rename(columns=column_mapping)
        
        # Reorder columns: Дата, Рабочий, Бригадир, then others
        column_order = ['Дата', 'Рабочий', 'Бригадир', 'Должность', 'Количество Сеансов', 'Общее Время (часы)', 'Общее Время (смены)']
        df = df.reindex(columns=column_order)
        
        df.to_excel(writer, sheet_name='Ежедневная Сводка', index=False)
        
        # Auto-adjust column widths
        worksheet = writer.sheets['Ежедневная Сводка']
        for column in worksheet.columns:
            max_length = 0
            column_letter = column[0].column_letter
            for cell in column:
                try:
                    if len(str(cell.value)) > max_length:
                        max_length = len(str(cell.value))
                except:
                    pass
            adjusted_width = min(max_length + 2, 50)
            worksheet.column_dimensions[column_letter].width = adjusted_width
    
    def _create_detailed_sessions_sheet(self, writer, sessions_data: List[Dict[str, Any]]):
        """Create detailed sessions sheet for company-wide report"""
        if not sessions_data:
            return
        
        df = pd.DataFrame(sessions_data)
        
        # Format datetime columns
        if 'start_time' in df.columns:
            df['start_time'] = pd.to_datetime(df['start_time']).dt.strftime('%Y-%m-%d %H:%M:%S')
        if 'end_time' in df.columns:
            df['end_time'] = pd.to_datetime(df['end_time']).dt.strftime('%Y-%m-%d %H:%M:%S')
        if 'work_date' in df.columns:
            df['work_date'] = pd.to_datetime(df['work_date']).dt.strftime('%Y-%m-%d')
        
        # Format numeric columns
        numeric_columns = ['total_hours']
        for col in numeric_columns:
            if col in df.columns:
                df[col] = pd.to_numeric(df[col], errors='coerce').round(2)
        
        # Convert hours to shifts for display
        if 'total_hours' in df.columns:
            df['total_shifts'] = df['total_hours'].apply(self._hours_to_shifts).round(3)
        
        # Combine worker names
        if 'first_name' in df.columns and 'last_name' in df.columns:
            df['worker_full_name'] = df['first_name'] + ' ' + df['last_name'].fillna('')
            df['worker_full_name'] = df['worker_full_name'].str.strip()
        
        # Combine foreman names
        if 'foreman_first_name' in df.columns and 'foreman_last_name' in df.columns:
            df['foreman_full_name'] = df['foreman_first_name'] + ' ' + df['foreman_last_name'].fillna('')
            df['foreman_full_name'] = df['foreman_full_name'].str.strip()
        
        # Remove old name columns
        columns_to_drop = ['first_name', 'last_name', 'foreman_first_name', 'foreman_last_name']
        for col in columns_to_drop:
            if col in df.columns:
                df = df.drop(columns=[col])
        
        # Rename columns for better readability
        column_mapping = {
            'session_id': 'ID Сеанса',
            'worker_id': 'ID Рабочего',
            'work_date': 'Дата',
            'worker_full_name': 'Рабочий',
            'foreman_full_name': 'Бригадир',
            'position': 'Должность',
            'start_time': 'Время Начала',
            'end_time': 'Время Окончания',
            'total_hours': 'Часы',
            'total_shifts': 'Смены',
            'notes': 'Заметки'
        }
        
        df = df.rename(columns=column_mapping)
        
        # Reorder columns: ID Сеанса, ID Рабочего, Дата, Рабочий, Бригадир, then others
        column_order = ['ID Сеанса', 'ID Рабочего', 'Дата', 'Рабочий', 'Бригадир', 'Должность', 'Время Начала', 'Время Окончания', 'Часы', 'Смены', 'Заметки']
        df = df.reindex(columns=column_order)
        
        df.to_excel(writer, sheet_name='Детальные Сеансы', index=False)
        
        # Auto-adjust column widths
        worksheet = writer.sheets['Детальные Сеансы']
        for column in worksheet.columns:
            max_length = 0
            column_letter = column[0].column_letter
            for cell in column:
                try:
                    if len(str(cell.value)) > max_length:
                        max_length = len(str(cell.value))
                except:
                    pass
            adjusted_width = min(max_length + 2, 50)
            worksheet.column_dimensions[column_letter].width = adjusted_width
    
    def _create_workers_overview_sheet(self, writer, workers_data: List[Dict[str, Any]]):
        """Create workers overview sheet for company-wide report"""
        if not workers_data:
            return
        
        df = pd.DataFrame(workers_data)
        
        # Format datetime columns
        if 'created_at' in df.columns:
            df['created_at'] = pd.to_datetime(df['created_at']).dt.strftime('%Y-%m-%d')
        
        # Format numeric columns
        numeric_columns = ['total_hours']
        for col in numeric_columns:
            if col in df.columns:
                df[col] = pd.to_numeric(df[col], errors='coerce').round(2)
        
        # Convert hours to shifts for display
        if 'total_hours' in df.columns:
            df['total_shifts'] = df['total_hours'].apply(self._hours_to_shifts).round(3)
        
        # Combine worker names
        if 'first_name' in df.columns and 'last_name' in df.columns:
            df['worker_full_name'] = df['first_name'] + ' ' + df['last_name'].fillna('')
            df['worker_full_name'] = df['worker_full_name'].str.strip()
        
        # Combine foreman names
        if 'foreman_first_name' in df.columns and 'foreman_last_name' in df.columns:
            df['foreman_full_name'] = df['foreman_first_name'] + ' ' + df['foreman_last_name'].fillna('')
            df['foreman_full_name'] = df['foreman_full_name'].str.strip()
        
        # Format boolean columns
        if 'is_active' in df.columns:
            df['is_active'] = df['is_active'].map({1: 'Активен', 0: 'Неактивен'})
        
        # Remove old name columns
        columns_to_drop = ['first_name', 'last_name', 'foreman_first_name', 'foreman_last_name']
        for col in columns_to_drop:
            if col in df.columns:
                df = df.drop(columns=[col])
        
        # Rename columns for better readability
        column_mapping = {
            'worker_id': 'ID Рабочего',
            'worker_full_name': 'Рабочий',
            'foreman_full_name': 'Бригадир',
            'position': 'Должность',
            'created_at': 'Дата Регистрации',
            'is_active': 'Статус',
            'total_sessions': 'Всего Сеансов',
            'total_hours': 'Общее Время (часы)',
            'total_shifts': 'Общее Время (смены)'
        }
        
        df = df.rename(columns=column_mapping)
        
        # Reorder columns: ID Рабочего, Рабочий, Бригадир, then others
        column_order = ['ID Рабочего', 'Рабочий', 'Бригадир', 'Должность', 'Дата Регистрации', 'Статус', 'Всего Сеансов', 'Общее Время (часы)', 'Общее Время (смены)']
        df = df.reindex(columns=column_order)
        
        df.to_excel(writer, sheet_name='Обзор Рабочих', index=False)
        
        # Auto-adjust column widths
        worksheet = writer.sheets['Обзор Рабочих']
        for column in worksheet.columns:
            max_length = 0
            column_letter = column[0].column_letter
            for cell in column:
                try:
                    if len(str(cell.value)) > max_length:
                        max_length = len(str(cell.value))
                except:
                    pass
            adjusted_width = min(max_length + 2, 50)
            worksheet.column_dimensions[column_letter].width = adjusted_width
    
    def _create_company_summary_sheet(self, writer, daily_summary: List[Dict[str, Any]], 
                                     workers_data: List[Dict[str, Any]]):
        """Create company summary sheet with overall statistics"""
        summary_data = []
        
        # Calculate summary statistics
        if daily_summary:
            df_summary = pd.DataFrame(daily_summary)
            
            # Total work days
            unique_dates = df_summary['work_date'].nunique()
            
            # Total sessions and hours
            total_sessions = df_summary['sessions_count'].sum()
            total_hours = df_summary['total_hours'].sum()
            total_shifts = self._hours_to_shifts(total_hours)
            
            # Average hours per day
            avg_hours_per_day = df_summary.groupby('work_date')['total_hours'].sum().mean()
            avg_shifts_per_day = self._hours_to_shifts(avg_hours_per_day)
            
            # Most active workers
            worker_hours = df_summary.groupby(['first_name', 'last_name'])['total_hours'].sum().sort_values(ascending=False)
            most_active_worker = f"{worker_hours.index[0][0]} {worker_hours.index[0][1]}" if len(worker_hours) > 0 else "Нет данных"
            
            summary_data.extend([
                {'Метрика': 'Общее количество рабочих дней', 'Значение': unique_dates},
                {'Метрика': 'Общее количество рабочих сеансов', 'Значение': total_sessions},
                {'Метрика': 'Общее количество рабочих часов', 'Значение': f"{total_hours:.2f}"},
                {'Метрика': 'Общее количество рабочих смен', 'Значение': f"{total_shifts:.3f}"},
                {'Метрика': 'Среднее количество часов в день', 'Значение': f"{avg_hours_per_day:.2f}"},
                {'Метрика': 'Среднее количество смен в день', 'Значение': f"{avg_shifts_per_day:.3f}"},
                {'Метрика': 'Самый активный рабочий', 'Значение': most_active_worker},
            ])
        
        if workers_data:
            df_workers = pd.DataFrame(workers_data)
            active_workers = len(df_workers[df_workers['is_active'] == 1])
            total_workers = len(df_workers)
            
            summary_data.extend([
                {'Метрика': 'Всего рабочих в системе', 'Значение': total_workers},
                {'Метрика': 'Активных рабочих', 'Значение': active_workers},
                {'Метрика': 'Неактивных рабочих', 'Значение': total_workers - active_workers},
            ])
        
        if summary_data:
            df_summary = pd.DataFrame(summary_data)
            df_summary.to_excel(writer, sheet_name='Сводка Компании', index=False)
            
            # Auto-adjust column widths
            worksheet = writer.sheets['Сводка Компании']
            for column in worksheet.columns:
                max_length = 0
                column_letter = column[0].column_letter
                for cell in column:
                    try:
                        if len(str(cell.value)) > max_length:
                            max_length = len(str(cell.value))
                    except:
                        pass
                adjusted_width = min(max_length + 2, 50)
                worksheet.column_dimensions[column_letter].width = adjusted_width
