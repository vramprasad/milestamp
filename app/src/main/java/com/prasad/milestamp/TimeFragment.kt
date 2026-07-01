package com.prasad.milestamp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_time, container, false)
        val dbHelper = DatabaseHelper(requireContext())

        val btnIn = view.findViewById<Button>(R.id.btnIn)
        val btnOut = view.findViewById<Button>(R.id.btnOut)
        val btnFetch = view.findViewById<Button>(R.id.btnFetch)
        val btnFetchCurrentSession =
            view.findViewById<Button>(R.id.btnFetchCurrentSession) // NEW BUTTON
        val btnClear = view.findViewById<Button>(R.id.btnClear)
        val tvDisplay = view.findViewById<TextView>(R.id.tvDisplay)

        fun saveLog(status: String) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val currentDateTime = dateFormat.format(Date())
            if (dbHelper.insertTimestamp(currentDateTime, status)) {
                Toast.makeText(context, "$status Logged: $currentDateTime", Toast.LENGTH_SHORT)
                    .show()
                btnFetch.performClick() // Refresh screen summary layout immediately
            }
        }

        btnIn.setOnClickListener { saveLog("IN") }
        btnOut.setOnClickListener { saveLog("OUT") }

        btnFetch.setOnClickListener {
            val entries = dbHelper.getAllTimestamps()

            if (entries.isEmpty()) {
                tvDisplay.text = "The database is empty."
            } else {
                // Modified: Passing ignoreBreaks = true here to get total overall span
                val effectiveTimeSummary = dbHelper.getEffectiveTime(ignoreBreaks = true)
                val historyText = entries.joinToString("\n")

                tvDisplay.text =
                    "--- SUMMARY ---\n$effectiveTimeSummary\n\n--- HISTORY ---\n$historyText"
            }
        }

        btnClear.setOnClickListener {
            val rows = dbHelper.clearAllTimestamps()
            tvDisplay.text = "Database entries cleared."
            Toast.makeText(context, "Deleted $rows entries", Toast.LENGTH_SHORT).show()
        }

        // New Button (Forces current system runtime time to act as LAST OUT calculation anchor)
        btnFetchCurrentSession.setOnClickListener {
            val entries = dbHelper.getAllTimestamps()

            if (entries.isEmpty()) {
                tvDisplay.text = "The database is empty."
            } else {
                // Pass current instant right into calculation logic engine (unchanged)
                val effectiveTimeSummary = dbHelper.getEffectiveTime(overrideLastOutTime = Date())
                val historyText = entries.joinToString("\n")
                tvDisplay.text =
                    "--- ACTIVE SUMMARY ---\n$effectiveTimeSummary\n\n--- HISTORY ---\n$historyText"
            }
        }

        return view
    }
}