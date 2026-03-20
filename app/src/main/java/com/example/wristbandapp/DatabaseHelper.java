package com.example.wristbandapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "locations.db";
    private static final int DATABASE_VERSION = 4; // bumped for location_name in logs
    private static final String TABLE_LOCATIONS = "locations";
    private static final String TABLE_ALARMS = "alarms";
    private static final String TABLE_LOCATION_LOGS = "location_logs";
    private static final String TABLE_ALARM_LOGS = "alarm_logs";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_LOCATIONS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT, " +
                "latitude REAL, " +
                "longitude REAL, " +
                "radius_meters REAL)");

        db.execSQL("CREATE TABLE " + TABLE_ALARMS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "label TEXT, " +
                "hour INTEGER, " +
                "minute INTEGER, " +
                "repeat_days TEXT, " +
                "vibration TEXT)");

        db.execSQL("CREATE TABLE " + TABLE_LOCATION_LOGS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "location_id INTEGER, " +
                "location_name TEXT, " +
                "entry_time INTEGER, " +
                "exit_time INTEGER)");

        db.execSQL("CREATE TABLE " + TABLE_ALARM_LOGS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "alarm_id INTEGER, " +
                "trigger_time INTEGER, " +
                "label TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // v1 → v2: just add the alarms table; preserve existing locations
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_ALARMS + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "label TEXT, " +
                    "hour INTEGER, " +
                    "minute INTEGER, " +
                    "repeat_days TEXT, " +
                    "vibration TEXT)");
        }
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_LOCATION_LOGS + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "location_id INTEGER, " +
                    "location_name TEXT, " +
                    "entry_time INTEGER, " +
                    "exit_time INTEGER)");

            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_ALARM_LOGS + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "alarm_id INTEGER, " +
                    "trigger_time INTEGER, " +
                    "label TEXT)");
        }
        if (oldVersion == 3) {
            db.execSQL("ALTER TABLE " + TABLE_LOCATION_LOGS + " ADD COLUMN location_name TEXT");
        }
    }

    // ── Location CRUD ──────────────────────────────────────────

    public long insertLocation(String name, double lat, double lng, float radius) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("latitude", lat);
        values.put("longitude", lng);
        values.put("radius_meters", radius);
        long id = db.insert(TABLE_LOCATIONS, null, values);
        db.close();
        return id;
    }

    public List<LocationItem> getAllLocations() {
        List<LocationItem> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_LOCATIONS, null);
        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                double lat = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
                double lng = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));
                float radius = cursor.getFloat(cursor.getColumnIndexOrThrow("radius_meters"));
                list.add(new LocationItem(id, name, lat, lng, radius));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return list;
    }

    public void deleteLocation(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_LOCATIONS, "id = ?", new String[] { String.valueOf(id) });
        db.close();
    }

    // ── Alarm CRUD ─────────────────────────────────────────────

    public long insertAlarm(String label, int hour, int minute, String repeatDays, String vibration) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("label", label);
        values.put("hour", hour);
        values.put("minute", minute);
        values.put("repeat_days", repeatDays);
        values.put("vibration", vibration);
        long id = db.insert(TABLE_ALARMS, null, values);
        db.close();
        return id;
    }

    public void updateAlarm(int id, String label, int hour, int minute, String repeatDays, String vibration) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("label", label);
        values.put("hour", hour);
        values.put("minute", minute);
        values.put("repeat_days", repeatDays);
        values.put("vibration", vibration);
        db.update(TABLE_ALARMS, values, "id = ?", new String[] { String.valueOf(id) });
        db.close();
    }

    public List<DashboardItem> getAllAlarms() {
        List<DashboardItem> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_ALARMS, null);
        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                String label = cursor.getString(cursor.getColumnIndexOrThrow("label"));
                int hour = cursor.getInt(cursor.getColumnIndexOrThrow("hour"));
                int minute = cursor.getInt(cursor.getColumnIndexOrThrow("minute"));
                String days = cursor.getString(cursor.getColumnIndexOrThrow("repeat_days"));
                String vibration = cursor.getString(cursor.getColumnIndexOrThrow("vibration"));
                list.add(new DashboardItem(id, label, hour, minute, days, vibration));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return list;
    }

    public void deleteAlarm(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_ALARMS, "id = ?", new String[] { String.valueOf(id) });
        db.close();
    }

    // ── Logging Methods ──────────────────────────────────────────

    public long logLocationEntry(long locationId, String locationName, long timeMs) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("location_id", locationId);
        values.put("location_name", locationName);
        values.put("entry_time", timeMs);
        values.put("exit_time", 0); // Active
        return db.insert(TABLE_LOCATION_LOGS, null, values);
    }

    public void logLocationExit(long logId, long timeMs) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("exit_time", timeMs);
        db.update(TABLE_LOCATION_LOGS, values, "id=?", new String[] { String.valueOf(logId) });
        db.close();
    }

    public void logAlarmTrigger(long alarmId, String label, long timeMs) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("alarm_id", alarmId);
        values.put("trigger_time", timeMs);
        values.put("label", label);
        db.insert(TABLE_ALARM_LOGS, null, values);
        db.close();
    }

    // ── Analytics Queries ──────────────────────────────────────

    // 1. Total Alerts in the last 7 days (Combined Alarms + Locations)
    public int getRecentAlertCount() {
        int count = 0;
        long sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c1 = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_LOCATION_LOGS + " WHERE entry_time > ?",
                new String[] { String.valueOf(sevenDaysAgo) });
        if (c1.moveToFirst())
            count += c1.getInt(0);
        c1.close();
        Cursor c2 = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_ALARM_LOGS + " WHERE trigger_time > ?",
                new String[] { String.valueOf(sevenDaysAgo) });
        if (c2.moveToFirst())
            count += c2.getInt(0);
        c2.close();
        db.close();
        return count;
    }

    // 2. Top Locations Visited
    // Returns List of [Location Name, Visit Count string]
    public List<String[]> getTopLocations() {
        List<String[]> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT location_name, COUNT(id) as visit_count " +
                "FROM " + TABLE_LOCATION_LOGS + " " +
                "WHERE location_name IS NOT NULL " +
                "GROUP BY location_name " +
                "ORDER BY visit_count DESC LIMIT 3";
        Cursor cursor = db.rawQuery(query, null);
        if (cursor.moveToFirst()) {
            do {
                String name = cursor.getString(0);
                int count = cursor.getInt(1);
                list.add(new String[] { name, count + " visits" });
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return list;
    }

    // 3. Average Time in Zone
    // Returns List of [Location Name, Average Duration string]
    public List<String[]> getAverageDurations() {
        List<String[]> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        // exit_time > 0 ensures we don't count currently active visits where exit_time
        // is 0
        String query = "SELECT location_name, AVG(exit_time - entry_time) as avg_duration " +
                "FROM " + TABLE_LOCATION_LOGS + " " +
                "WHERE exit_time > 0 AND location_name IS NOT NULL " +
                "GROUP BY location_name " +
                "ORDER BY avg_duration DESC LIMIT 3";
        Cursor cursor = db.rawQuery(query, null);
        if (cursor.moveToFirst()) {
            do {
                String name = cursor.getString(0);
                long avgMs = cursor.getLong(1);
                long mins = (avgMs / 1000) / 60;
                String format;
                if (mins < 60) {
                    format = mins + " min";
                } else {
                    format = (mins / 60) + "h " + (mins % 60) + "m";
                }
                list.add(new String[] { name, format });
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return list;
    }

    // 4. Activity Heatmap (Last 7 days x 24 hours)
    // Returns int[7][24] where index 0 is 6 days ago. inner index is hour of day
    // (0-23).
    public int[][] getWeeklyActivity() {
        int[][] activity = new int[7][24];
        SQLiteDatabase db = this.getReadableDatabase();

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);

        java.util.Calendar eventCal = java.util.Calendar.getInstance();

        for (int i = 6; i >= 0; i--) {
            long startOfDay = cal.getTimeInMillis();
            long endOfDay = startOfDay + (24L * 60 * 60 * 1000) - 1;

            Cursor c1 = db.rawQuery(
                    "SELECT entry_time FROM " + TABLE_LOCATION_LOGS + " WHERE entry_time BETWEEN ? AND ?",
                    new String[] { String.valueOf(startOfDay), String.valueOf(endOfDay) });
            if (c1.moveToFirst()) {
                do {
                    eventCal.setTimeInMillis(c1.getLong(0));
                    int hour = eventCal.get(java.util.Calendar.HOUR_OF_DAY);
                    activity[i][hour]++;
                } while (c1.moveToNext());
            }
            c1.close();

            Cursor c2 = db.rawQuery(
                    "SELECT trigger_time FROM " + TABLE_ALARM_LOGS + " WHERE trigger_time BETWEEN ? AND ?",
                    new String[] { String.valueOf(startOfDay), String.valueOf(endOfDay) });
            if (c2.moveToFirst()) {
                do {
                    eventCal.setTimeInMillis(c2.getLong(0));
                    int hour = eventCal.get(java.util.Calendar.HOUR_OF_DAY);
                    activity[i][hour]++;
                } while (c2.moveToNext());
            }
            c2.close();

            cal.add(java.util.Calendar.DAY_OF_YEAR, -1);
        }
        db.close();
        return activity;
    }

    public void clearAllHistory() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_LOCATION_LOGS, null, null);
        db.delete(TABLE_ALARM_LOGS, null, null);
        db.close();
    }
}
