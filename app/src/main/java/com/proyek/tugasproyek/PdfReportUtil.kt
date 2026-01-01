package com.proyek.tugasproyek.pdf

import android.content.Context
import java.io.File
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment

object PdfReportUtil {

    fun generateWeeklyReport(
        context: Context,
        userName: String,
        totalCalories: Int
    ): File {

        val file = File(
            context.getExternalFilesDir(null),
            "laporan_pola_makan.pdf"
        )

        val writer = PdfWriter(file)
        val pdf = PdfDocument(writer)
        val document = Document(pdf)

        document.add(
            Paragraph("LAPORAN POLA MAKAN MINGGUAN")
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(16f)
        )

        document.add(Paragraph("\nNama User : $userName"))
        document.add(Paragraph("Total Kalori Mingguan : $totalCalories kkal"))

        document.close()
        return file
    }
}
