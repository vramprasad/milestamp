package com.prasad.milestamp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class CarFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_car, container, false)
        val dbHelper = DatabaseHelper(requireContext())

        val etInput = view.findViewById<EditText>(R.id.etCarInput)
        val btnSave = view.findViewById<Button>(R.id.btnCarSave)
        val btnView = view.findViewById<Button>(R.id.btnCarView)
        val tvDisplay = view.findViewById<TextView>(R.id.tvCarDisplay)

        btnSave.setOnClickListener {
            val inputText = etInput.text.toString().trim()
            if (inputText.isNotEmpty()) {
                if (dbHelper.insertCarEntry(inputText)) {
                    Toast.makeText(context, "Entry saved", Toast.LENGTH_SHORT).show()
                    etInput.text.clear()
                }
            } else {
                Toast.makeText(context, "Enter details", Toast.LENGTH_SHORT).show()
            }
        }

        btnView.setOnClickListener {
            val entries = dbHelper.getAllCarEntries()
            tvDisplay.text =
                if (entries.isEmpty()) "No Car entries found." else entries.joinToString("\n")
        }

        return view
    }
}