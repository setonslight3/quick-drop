package com.quickdrop.desktop.ui

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

object FilePickerHelper {
    /**
     * Opens the native Windows File Picker dialog to select course materials
     * (PDF, PPTX, HTML, HTM, TXT, MD, PNG, JPEG, WebP). Returns the selected java.io.File or null if cancelled.
     */
    fun pickCourseMaterialFile(): File? {
        return try {
            val dialog = FileDialog(null as Frame?, "Select Course Material (PDF, PPTX, HTML, Images, Notes)", FileDialog.LOAD).apply {
                setFilenameFilter { _, name ->
                    val lower = name.lowercase()
                    lower.endsWith(".pdf") || lower.endsWith(".pptx") ||
                        lower.endsWith(".html") || lower.endsWith(".htm") ||
                        lower.endsWith(".txt") || lower.endsWith(".md") ||
                        lower.endsWith(".png") || lower.endsWith(".jpg") ||
                        lower.endsWith(".jpeg") || lower.endsWith(".webp")
                }
                isVisible = true
            }
            val directory = dialog.directory
            val filename = dialog.file
            if (directory != null && filename != null) File(directory, filename) else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Opens the native Windows File Picker dialog to select a Timetable / Schedule file
     * (.html, .htm, .ics, .csv, .tsv, .pdf, .txt, .json, .png, .jpg).
     */
    fun pickTimetableFile(): File? {
        return try {
            val dialog = FileDialog(null as Frame?, "Upload Timetable / Schedule File (HTML, ICS, CSV, PDF, Text)", FileDialog.LOAD).apply {
                setFilenameFilter { _, name ->
                    val lower = name.lowercase()
                    lower.endsWith(".html") || lower.endsWith(".htm") ||
                        lower.endsWith(".ics") || lower.endsWith(".csv") ||
                        lower.endsWith(".tsv") || lower.endsWith(".pdf") ||
                        lower.endsWith(".txt") || lower.endsWith(".json") ||
                        lower.endsWith(".png") || lower.endsWith(".jpg") ||
                        lower.endsWith(".jpeg")
                }
                isVisible = true
            }
            val directory = dialog.directory
            val filename = dialog.file
            if (directory != null && filename != null) File(directory, filename) else null
        } catch (_: Exception) {
            null
        }
    }
}
