package com.prasad.milestamp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "TimestampDB"
        private const val DATABASE_VERSION = 5

        // Table 1: Timestamps
        private const val TABLE_TIMESTAMPS = "timestamps"
        private const val COL_ID = "id"
        private const val COL_DATETIME = "date_time"
        private const val COL_STATUS = "status"

        // Table 2: Activa
        private const val TABLE_ACTIVA = "activa"
        private const val COL_ACTIVA_ID = "id"
        private const val COL_ACTIVA_TEXT = "entry_text"

        // Table 3: Car (grandi10)
        private const val TABLE_CAR = "grandi10"
        private const val COL_CAR_ID = "id"
        private const val COL_CAR_TEXT = "entry_text"

        private const val COL_TIMESTAMP = "created_at"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createTimeTable = ("CREATE TABLE $TABLE_TIMESTAMPS ("
                + "$COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COL_DATETIME TEXT, "
                + "$COL_STATUS TEXT)")

        val createActivaTable = ("CREATE TABLE $TABLE_ACTIVA ("
                + "$COL_ACTIVA_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COL_ACTIVA_TEXT TEXT, "
                + "$COL_TIMESTAMP DATETIME DEFAULT CURRENT_TIMESTAMP)")

        val createCarTable = ("CREATE TABLE $TABLE_CAR ("
                + "$COL_CAR_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COL_CAR_TEXT TEXT, "
                + "$COL_TIMESTAMP DATETIME DEFAULT CURRENT_TIMESTAMP)")

        db?.execSQL(createTimeTable)
        db?.execSQL(createActivaTable)
        db?.execSQL(createCarTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_TIMESTAMPS")
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_ACTIVA")
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_CAR")
        onCreate(db)
    }

    // --- TIMESTAMPS METHODS ---
    fun insertTimestamp(dateTimeStr: String, status: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_DATETIME, dateTimeStr)
            put(COL_STATUS, status)
        }
        val result = db.insert(TABLE_TIMESTAMPS, null, values)
        db.close()
        return result != -1L
    }

    fun getAllTimestamps(): List<String> {
        val list = mutableListOf<String>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_TIMESTAMPS ORDER BY $COL_ID DESC", null)
        if (cursor.moveToFirst()) {
            do {
                val dateTime = cursor.getString(cursor.getColumnIndexOrThrow(COL_DATETIME))
                val status = cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS))
                list.add("[$status] $dateTime")
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    fun clearAllTimestamps(): Int {
        val db = this.writableDatabase
        val rows = db.delete(TABLE_TIMESTAMPS, null, null)
        db.close()
        return rows
    }

    /**
     * Calculates effective time by taking the total span (FIRST IN to LAST OUT)
     * and deducting any internal break times if requested.
     *
     * @param overrideLastOutTime Optional date passed by the UI to force a specific fallback dynamic end time.
     * @param ignoreBreaks If true, breaks will not be tracked, deducted, or appended to the output.
     */
    fun getEffectiveTime(overrideLastOutTime: Date? = null, ignoreBreaks: Boolean = false): String {
        val db = this.readableDatabase
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val fullSdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val currentDateStr = sdf.format(Date())

        // Fetch all logs for the current day sorted chronologically
        val query = "SELECT $COL_DATETIME, $COL_STATUS FROM $TABLE_TIMESTAMPS " +
                "WHERE $COL_DATETIME LIKE '$currentDateStr%' " +
                "ORDER BY $COL_ID ASC"

        val cursor = db.rawQuery(query, null)
        val logs = mutableListOf<Pair<Date, String>>()

        if (cursor.moveToFirst()) {
            do {
                val dateTimeStr = cursor.getString(0)
                val status = cursor.getString(1)
                val parsedDate = try {
                    fullSdf.parse(dateTimeStr)
                } catch (e: Exception) {
                    null
                }
                if (parsedDate != null) {
                    logs.add(Pair(parsedDate, status))
                }
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()

        if (logs.isEmpty()) {
            return "No logs found for today ($currentDateStr)."
        }

        // Find the absolute first 'IN' log
        val firstInLog = logs.firstOrNull { it.second == "IN" }
        if (firstInLog == null) {
            return "Error: No 'IN' log recorded today to establish a starting baseline."
        }
        val firstInTime = firstInLog.first

        // Determine the overall standard Last Out timestamp from the logs
        val lastOutLog = logs.lastOrNull { it.second == "OUT" }

        val finalOutTime: Date
        val logsSummary = StringBuilder()
        logsSummary.append("FIRST IN: ${fullSdf.format(firstInTime)}\n")

        if (overrideLastOutTime != null) {
            finalOutTime = overrideLastOutTime
            logsSummary.append("LAST OUT: ${fullSdf.format(finalOutTime)} (CT)\n")
        } else if (lastOutLog != null) {
            finalOutTime = lastOutLog.first
            logsSummary.append("LAST OUT: ${fullSdf.format(finalOutTime)}\n")
        } else {
            // Fallback if no out log exists and no custom override button was pressed
            finalOutTime = Date()
            logsSummary.append("LAST OUT: ${fullSdf.format(finalOutTime)} (CT)\n")
        }

        // Calculate maximum potential baseline span (LAST OUT - FIRST IN)
        val totalSpanMillis = finalOutTime.time - firstInTime.time
        var totalBreakMillis = 0L

        // Track internal break deductions only if ignoreBreaks is false
        if (!ignoreBreaks) {
            var lastOutTimeForBreak: Date? = null

            for (i in logs.indices) {
                val (logDate, status) = logs[i]

                // Skip entries occurring outside our calculated baseline scope boundaries
                if (logDate.before(firstInTime) || logDate.after(finalOutTime)) continue

                if (status == "OUT") {
                    lastOutTimeForBreak = logDate
                } else if (status == "IN" && lastOutTimeForBreak != null) {
                    // Found a break sequence gap! Deduct time spent between OUT and this next IN
                    if (logDate.after(lastOutTimeForBreak)) {
                        val breakGap = logDate.time - lastOutTimeForBreak.time
                        totalBreakMillis += breakGap
                        logsSummary.append("  -> Break Deducted: ${breakGap / 1000 / 60} mins\n")
                    }
                    lastOutTimeForBreak = null // Reset tracking until next explicit OUT
                }
            }
        }

        // Final absolute calculation deduction
        val effectiveWorkMillis = totalSpanMillis - totalBreakMillis

        if (effectiveWorkMillis < 0 || totalSpanMillis < 0) {
            return "Error: Miscalculated duration. Please check chronological sequence consistency."
        }

        // Helper function to format milliseconds to "Xd Xh Xm Xs" format
        fun formatDuration(millis: Long): String {
            val seconds = (millis / 1000) % 60
            val minutes = (millis / (1000 * 60)) % 60
            val hours = (millis / (1000 * 60 * 60)) % 24
            val days = millis / (1000 * 60 * 60 * 24)

            return buildString {
                if (days > 0) append("${days}d ")
                if (hours > 0 || days > 0) append("${hours}h ")
                if (minutes > 0 || hours > 0 || days > 0) append("${minutes}m ")
                append("${seconds}s")
            }
        }

        val totalDurationStr = formatDuration(totalSpanMillis)
        val effectiveDurationStr = formatDuration(effectiveWorkMillis)

        return buildString {
            append(logsSummary.toString())
            if (!ignoreBreaks) {
                append("Total Break Time: ${totalBreakMillis / 1000 / 60} mins\n")
                append("\nTotal Duration   :  $totalDurationStr")
                append("\nEffective Duration:  $effectiveDurationStr")
            } else {
                // When ignoreBreaks = true (like via standard btnFetch), Total == Effective
                append("\nTotal Duration   :  $totalDurationStr")
            }
        }
    }

    // --- ACTIVA METHODS ---
    fun insertActiva(text: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply { put(COL_ACTIVA_TEXT, text) }
        val result = db.insert(TABLE_ACTIVA, null, values)
        db.close()
        return result != -1L
    }

    fun getAllActivaEntries(): List<String> {
        val list = mutableListOf<String>()
        val db = this.readableDatabase
        val query =
            "SELECT $COL_ACTIVA_TEXT, datetime($COL_TIMESTAMP, 'localtime') as local_time FROM $TABLE_ACTIVA ORDER BY $COL_ACTIVA_ID DESC"
        val cursor = db.rawQuery(query, null)

        if (cursor.moveToFirst()) {
            do {
                val text = cursor.getString(cursor.getColumnIndexOrThrow(COL_ACTIVA_TEXT))
                val timestamp = cursor.getString(cursor.getColumnIndexOrThrow("local_time"))
                list.add("$timestamp --> $text")
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    // --- CAR (GRANDI10) METHODS ---
    fun insertCarEntry(text: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply { put(COL_CAR_TEXT, text) }
        val result = db.insert(TABLE_CAR, null, values)
        db.close()
        return result != -1L
    }

    fun getAllCarEntries(): List<String> {
        val list = mutableListOf<String>()
        val db = this.readableDatabase
        val query =
            "SELECT $COL_CAR_TEXT, datetime($COL_TIMESTAMP, 'localtime') as local_time FROM $TABLE_CAR ORDER BY $COL_CAR_ID DESC"
        val cursor = db.rawQuery(query, null)

        if (cursor.moveToFirst()) {
            do {
                val text = cursor.getString(cursor.getColumnIndexOrThrow(COL_CAR_TEXT))
                val timestamp = cursor.getString(cursor.getColumnIndexOrThrow("local_time"))
                list.add("$timestamp --> $text")
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }
}