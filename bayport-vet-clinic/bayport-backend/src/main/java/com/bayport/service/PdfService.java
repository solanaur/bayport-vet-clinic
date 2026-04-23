package com.bayport.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.bayport.dto.ReportSummary;
import com.bayport.entity.Pet;
import com.bayport.entity.Prescription;
import com.bayport.util.MoneyUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PdfService {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("MMMM d, yyyy hh:mm a");
    
    private final String uploadDir;
    
    public PdfService(@Value("${bayport.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = (uploadDir == null || uploadDir.isBlank()) ? "uploads" : uploadDir;
    }

    /**
     * Builds the prescription PDF for multiple products. Uses a font/encoding that supports the
     * Philippine Peso sign (₱) so currency renders correctly.
     */
    public byte[] buildPrescriptionPdf(List<Prescription> prescriptions) {
        if (prescriptions == null || prescriptions.isEmpty()) {
            throw new IllegalArgumentException("Prescriptions list cannot be null or empty");
        }

        // Use first prescription for pet/owner/date/prescriber info
        Prescription first = prescriptions.get(0);
        if (first.getPet() == null || first.getPet().trim().isEmpty()) {
            throw new IllegalArgumentException("Prescription must have a pet name");
        }
        if (first.getOwner() == null || first.getOwner().trim().isEmpty()) {
            throw new IllegalArgumentException("Prescription must have an owner name");
        }

        try {
            Document document = new Document(PageSize.A4, 72, 72, 72, 72);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new PrescriptionFooterEvent());

            document.open();
            document.addTitle("Prescription");

            BaseFont base;
            try {
                base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception e) {
                base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);
            }

            Font header = new Font(base, 16, Font.BOLD, Color.BLACK);
            Font sub = new Font(base, 10, Font.NORMAL, Color.GRAY);
            Font label = new Font(base, 11, Font.BOLD);
            Font value = new Font(base, 11, Font.NORMAL);

            addClinicHeader(document, header, sub);
            addPrescriptionDetailsMultiple(document, prescriptions, label, value, sub);
            addDoctorSignature(document, first, label, value);

            document.close();
            return baos.toByteArray();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid prescription data: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Legacy method for single prescription (backwards compatibility).
     */
    public byte[] buildPrescriptionPdf(Prescription prescription) {
        return buildPrescriptionPdf(java.util.Collections.singletonList(prescription));
    }

    /**
     * Adds the clinic header to the document.
     */
    private void addClinicHeader(Document document, Font header, Font sub) throws DocumentException {
        Paragraph clinic = new Paragraph("Bayport Veterinary Clinic", header);
        clinic.setAlignment(Element.ALIGN_CENTER);
        clinic.setSpacingAfter(5);
        document.add(clinic);

        Paragraph address = new Paragraph(
                "322 Quirino Avenue, Brgy. Don Galo, Parañaque City  •  0968 633 2940",
                sub
        );
        address.setAlignment(Element.ALIGN_CENTER);
        address.setSpacingAfter(15);
        document.add(address);

        document.add(Chunk.NEWLINE);
        Paragraph title = new Paragraph("PRESCRIPTION", header);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(5);
        document.add(title);

        Paragraph generated = new Paragraph("Generated: " + TS.format(LocalDateTime.now()), sub);
        generated.setAlignment(Element.ALIGN_CENTER);
        generated.setSpacingAfter(15);
        document.add(generated);
    }
    
    /**
     * Adds clinic header for reports (without prescription title).
     */
    private void addReportHeader(Document document, Font header, Font sub) throws DocumentException {
        Paragraph clinic = new Paragraph("Bayport Veterinary Clinic", header);
        clinic.setAlignment(Element.ALIGN_CENTER);
        clinic.setSpacingAfter(5);
        document.add(clinic);

        Paragraph address = new Paragraph(
                "322 Quirino Avenue, Brgy. Don Galo, Parañaque City  •  0968 633 2940",
                sub
        );
        address.setAlignment(Element.ALIGN_CENTER);
        address.setSpacingAfter(15);
        document.add(address);
    }

    /**
     * Adds prescription details in a formatted table.
     */
    private void addPrescriptionDetails(Document document,
                                      Prescription prescription,
                                      Font label,
                                      Font value,
                                      Font sub) throws DocumentException {
        // Delegate to multiple products method
        addPrescriptionDetailsMultiple(document, java.util.Collections.singletonList(prescription), label, value, sub);
    }

    /**
     * Adds prescription details for multiple products in a formatted table.
     * Shows prescribed medication names (pricing at POS).
     */
    private void addPrescriptionDetailsMultiple(Document document,
                                               List<Prescription> prescriptions,
                                               Font label,
                                               Font value,
                                               Font sub) throws DocumentException {
        if (prescriptions == null || prescriptions.isEmpty()) {
            return;
        }

        // Use first prescription for pet/owner/date info
        Prescription first = prescriptions.get(0);
        
        // Pet and Owner Information
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{1, 2});
        infoTable.setSpacingBefore(10);
        infoTable.setSpacingAfter(15);
        
        addRow(infoTable, "Pet:", nullToEmpty(first.getPet()), label, value);
        addRow(infoTable, "Owner:", nullToEmpty(first.getOwner()), label, value);
        addRow(infoTable, "Veterinarian:", nullToEmpty(first.getPrescriber()), label, value);
        addRow(infoTable, "Date:", first.getDate() != null ? first.getDate().toString() : "", label, value);
        document.add(infoTable);

        document.add(Chunk.NEWLINE);

        // Medication names only — dosage/directions/notes not used on Rx record.
        PdfPTable productsTable = new PdfPTable(1);
        productsTable.setWidthPercentage(100);
        productsTable.setSpacingBefore(5);
        productsTable.setSpacingAfter(10);
        productsTable.setHeaderRows(1);

        PdfPCell medHeader = new PdfPCell(new Phrase("Medication", label));
        medHeader.setBackgroundColor(new Color(240, 240, 240));
        medHeader.setPadding(8);
        productsTable.addCell(medHeader);

        for (Prescription rx : prescriptions) {
            PdfPCell drugCell = new PdfPCell(new Phrase(nullToEmpty(rx.getDrug()), value));
            drugCell.setPadding(6);
            productsTable.addCell(drugCell);
        }

        document.add(productsTable);

        Paragraph payNote = new Paragraph(
                "Pricing, payment, and inventory are handled at the clinic Point of Sale (front desk).",
                sub);
        payNote.setSpacingBefore(8);
        document.add(payNote);
    }

    /**
     * Adds doctor signature section - centered with vet name.
     */
    private void addDoctorSignature(Document document,
                                    Prescription prescription,
                                    Font label,
                                    Font value) throws DocumentException {

        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);

        String prescriber = nullToEmpty(prescription.getPrescriber());
        if (prescriber.isEmpty()) {
            prescriber = "Attending Veterinarian";
        }

        // Center the signature line
        Paragraph signatureLine = new Paragraph("______________________________", value);
        signatureLine.setAlignment(Element.ALIGN_CENTER);
        signatureLine.setSpacingBefore(20);
        document.add(signatureLine);

        // Center the vet name (VET signature, not ADMIN)
        Paragraph doctorName = new Paragraph(prescriber, value);
        doctorName.setAlignment(Element.ALIGN_CENTER);
        doctorName.setSpacingBefore(5);
        document.add(doctorName);
        
        // Add "Veterinarian" label below name
        Paragraph vetLabel = new Paragraph("Veterinarian", label);
        vetLabel.setAlignment(Element.ALIGN_CENTER);
        vetLabel.setSpacingBefore(2);
        document.add(vetLabel);
    }

    /**
     * Builds a printable summary PDF for the given report summary. Adds a footer
     * with "Prepared by: <name>" and "Page X of Y" on each page.
     */
    public byte[] buildSummaryPdf(ReportSummary summary, String preparedBy) {
        String prepared = (preparedBy == null || preparedBy.isBlank()) ? "Admin" : preparedBy;

        try {
            // 1 inch margins on all sides (72 points = 1 inch)
            Document document = new Document(PageSize.A4, 72, 72, 72, 72);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new SummaryFooterEvent(prepared));

            document.open();
            document.addTitle("Clinic Summary");

            BaseFont base;
            try {
                base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception e) {
                base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);
            }

            Font header = new Font(base, 16, Font.BOLD, Color.BLACK);
            Font sub = new Font(base, 10, Font.NORMAL, Color.GRAY);
            Font label = new Font(base, 11, Font.BOLD);
            Font value = new Font(base, 11, Font.NORMAL);

            // Clinic header
            addReportHeader(document, header, sub);
            
            // Title - centered and labeled
            Paragraph title = new Paragraph(
                    "Summary Report",
                    header
            );
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(10);
            document.add(title);

            // Period information - clearly labeled
            String period = summary.period != null ? summary.period : "day";
            String from = summary.from != null ? summary.from : "";
            String to = summary.to != null ? summary.to : "";
            Paragraph periodLabel = new Paragraph(
                    String.format("Period: %s", period.toUpperCase()),
                    label
            );
            periodLabel.setSpacingAfter(3);
            document.add(periodLabel);
            
            Paragraph dateRange = new Paragraph(
                    String.format("From: %s  To: %s", from, to),
                    value
            );
            dateRange.setSpacingAfter(10);
            document.add(dateRange);
            
            document.add(Chunk.NEWLINE);

            // Metrics section - clearly labeled and organized
            Paragraph metricsTitle = new Paragraph("Summary Metrics", label);
            metricsTitle.setSpacingAfter(8);
            document.add(metricsTitle);
            
            PdfPTable metrics = new PdfPTable(2);
            metrics.setWidthPercentage(100);
            metrics.setWidths(new float[]{1, 1});
            metrics.setSpacingBefore(5);
            metrics.setSpacingAfter(10);
            
            addRow(metrics, "Appointments Done:",
                    String.valueOf(summary.appointmentsDone), label, value);
            addRow(metrics, "Prescriptions Dispensed:",
                    String.valueOf(summary.prescriptionsDispensed), label, value);
            addRow(metrics, "New Patients Added:",
                    String.valueOf(summary.petsAdded), label, value);
            addRow(metrics, "POS procedures (lines):",
                    MoneyUtils.formatPeso(summary.posProcedureRevenue), label, value);
            addRow(metrics, "POS products (lines):",
                    MoneyUtils.formatPeso(summary.posProductRevenue), label, value);
            addRow(metrics, "Total POS sales (period):",
                    MoneyUtils.formatPeso(summary.totalProfit), label, value);
            document.add(metrics);

            document.add(Chunk.NEWLINE);

            addPosSaleLinesSection(document, label, value, "Procedures (POS)",
                    summary.posProcedureLines, summary.posProcedureRevenue);
            addPosSaleLinesSection(document, label, value, "Products (POS)",
                    summary.posProductLines, summary.posProductRevenue);
            
            // New Patients section - clearly labeled
            if (summary.newPatients != null && !summary.newPatients.isEmpty()) {
                Paragraph newPatientsTitle = new Paragraph("New Patients", label);
                newPatientsTitle.setSpacingBefore(10);
                newPatientsTitle.setSpacingAfter(8);
                document.add(newPatientsTitle);
                
                PdfPTable newPatientsTable = new PdfPTable(3);
                newPatientsTable.setWidthPercentage(100);
                newPatientsTable.setWidths(new float[]{1.5f, 2f, 2f});
                newPatientsTable.setSpacingBefore(5);
                newPatientsTable.setSpacingAfter(10);
                newPatientsTable.setHeaderRows(1); // Repeat header on new pages
                
                // Header cells with proper styling
                PdfPCell dateAddedHeader = new PdfPCell(new Phrase("Date Added", label));
                dateAddedHeader.setBackgroundColor(new Color(240, 240, 240));
                dateAddedHeader.setPadding(8);
                newPatientsTable.addCell(dateAddedHeader);
                
                PdfPCell petNameHeader = new PdfPCell(new Phrase("Pet Name", label));
                petNameHeader.setBackgroundColor(new Color(240, 240, 240));
                petNameHeader.setPadding(8);
                newPatientsTable.addCell(petNameHeader);
                
                PdfPCell ownerNameHeader = new PdfPCell(new Phrase("Owner Name", label));
                ownerNameHeader.setBackgroundColor(new Color(240, 240, 240));
                ownerNameHeader.setPadding(8);
                newPatientsTable.addCell(ownerNameHeader);
                
                // Data rows with proper cell padding
                for (ReportSummary.NewPatient np : summary.newPatients) {
                    PdfPCell dateCell = new PdfPCell(new Phrase(nullToEmpty(np.addedAt), value));
                    dateCell.setPadding(6);
                    newPatientsTable.addCell(dateCell);
                    
                    PdfPCell petCell = new PdfPCell(new Phrase(nullToEmpty(np.petName), value));
                    petCell.setPadding(6);
                    newPatientsTable.addCell(petCell);
                    
                    PdfPCell ownerCell = new PdfPCell(new Phrase(nullToEmpty(np.ownerName), value));
                    ownerCell.setPadding(6);
                    newPatientsTable.addCell(ownerCell);
                }
                
                document.add(newPatientsTable);
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate summary PDF", e);
        }
    }

    private void addRow(PdfPTable table, String key, String val, Font label, Font value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(key, label));
        labelCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(val == null ? "" : val, value));
        valueCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(valueCell);
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(cell);
    }

    private String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private void addPosSaleLinesSection(Document document, Font label, Font value, String title,
                                        List<ReportSummary.PosSaleLineRow> rows,
                                        BigDecimal subtotal) throws DocumentException {
        Paragraph sectionTitle = new Paragraph(title, label);
        sectionTitle.setSpacingBefore(10);
        sectionTitle.setSpacingAfter(8);
        document.add(sectionTitle);

        BigDecimal st = subtotal != null ? subtotal : BigDecimal.ZERO;

        if (rows == null || rows.isEmpty()) {
            document.add(new Paragraph("No line items in this period.", value));
            Paragraph sub = new Paragraph("Subtotal: " + MoneyUtils.formatPeso(st), label);
            sub.setSpacingAfter(10);
            document.add(sub);
            return;
        }

        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.4f, 1.2f, 2f, 1f, 0.7f, 1f, 1.1f});
        table.setSpacingBefore(5);
        table.setSpacingAfter(8);
        table.setHeaderRows(1);

        String[] headers = {"Date", "Pet", "Item", "SKU", "Qty", "Unit", "Line"};
        for (String h : headers) {
            PdfPCell hc = new PdfPCell(new Phrase(h, label));
            hc.setBackgroundColor(new Color(240, 240, 240));
            hc.setPadding(8);
            table.addCell(hc);
        }

        for (ReportSummary.PosSaleLineRow r : rows) {
            table.addCell(paddedPhraseCell(nullToEmpty(r.date), value));
            table.addCell(paddedPhraseCell(nullToEmpty(r.petName), value));
            table.addCell(paddedPhraseCell(nullToEmpty(r.itemName), value));
            table.addCell(paddedPhraseCell(nullToEmpty(r.sku), value));
            table.addCell(paddedPhraseCell(String.valueOf(r.quantity), value));
            table.addCell(paddedPhraseCell(MoneyUtils.formatPeso(r.unitPrice), value));
            table.addCell(paddedPhraseCell(MoneyUtils.formatPeso(r.lineTotal), value));
        }

        document.add(table);
        Paragraph sub = new Paragraph("Subtotal: " + MoneyUtils.formatPeso(st), label);
        sub.setSpacingAfter(10);
        document.add(sub);
    }

    private static PdfPCell paddedPhraseCell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setPadding(6);
        return c;
    }

    /**
     * Footer for prescription PDF with "X of Y" format.
     * Uses a two-pass approach to ensure accurate page count.
     */
    private static class PrescriptionFooterEvent extends PdfPageEventHelper {
        private PdfTemplate total;
        private BaseFont baseFont;

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            try {
                this.baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception e) {
                try {
                    this.baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false);
                } catch (Exception ignored) {
                    this.baseFont = null;
                }
            }
            total = writer.getDirectContent().createTemplate(30, 16);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            int pageNumber = writer.getCurrentPageNumber();
            Phrase footer = new Phrase(String.format("%d of ", pageNumber));
            float x = (document.left() + document.right()) / 2;
            float y = document.bottom() - 10;
            ColumnText.showTextAligned(
                    writer.getDirectContent(),
                    Element.ALIGN_CENTER,
                    footer,
                    x, y, 0
            );

            PdfContentByte canvas = writer.getDirectContent();
            canvas.addTemplate(total, x + 20, y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            try {
                // Get the actual final page number after document is closed
                int totalPages = writer.getCurrentPageNumber() - 1; // Subtract 1 because page numbers are 1-indexed
                if (totalPages < 1) totalPages = 1; // Ensure at least 1 page
                
                total.beginText();
                if (baseFont != null) {
                    total.setFontAndSize(baseFont, 10);
                } else {
                    BaseFont font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);
                    total.setFontAndSize(font, 10);
                }
                total.showText(String.valueOf(totalPages));
                total.endText();
            } catch (Exception e) {
                // If error, try to write at least something
                try {
                    total.beginText();
                    BaseFont font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);
                    total.setFontAndSize(font, 10);
                    total.showText(String.valueOf(writer.getCurrentPageNumber() - 1));
                    total.endText();
                } catch (Exception ignored) {
                    // swallow
                }
            }
        }
    }

    /**
     * Page event for the summary PDF with:
     *   "Prepared by: <name>" and "Date and Time Generated: <timestamp>" on the left,
     *   "Page X of Y" on the right.
     */
    private static class SummaryFooterEvent extends PdfPageEventHelper {
        private final String preparedBy;
        private final String generatedDateTime;
        private PdfTemplate total;
        private BaseFont baseFont;

        SummaryFooterEvent(String preparedBy) {
            this.preparedBy = preparedBy;
            this.generatedDateTime = TS.format(LocalDateTime.now());
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            try {
                this.baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception e) {
                try {
                    this.baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false);
                } catch (Exception ignored) {
                    this.baseFont = null;
                }
            }
            total = writer.getDirectContent().createTemplate(40, 16);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            int pageNumber = writer.getCurrentPageNumber();

            float leftX = document.left();
            float rightX = document.right();
            float y = document.bottom() - 10;

            // Use a standard Font that doesn't depend on baseFont
            Font footerFont = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY);

            // Left side: Prepared by and Date/Time
            Phrase left = new Phrase(
                    "Prepared by: " + preparedBy + " | Date and Time Generated: " + generatedDateTime,
                    footerFont
            );
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, left, leftX, y, 0);

            // Right side: Page number in "X of Y" format (e.g., "1 of 1" or "1 of 5")
            Phrase right = new Phrase(pageNumber + " of ", footerFont);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, right, rightX - 20, y, 0);

            cb.addTemplate(total, rightX - 20, y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            try {
                // Get the actual final page number after document is closed
                int totalPages = writer.getCurrentPageNumber() - 1; // Subtract 1 because page numbers are 1-indexed
                if (totalPages < 1) totalPages = 1; // Ensure at least 1 page
                
                total.beginText();
                if (baseFont != null) {
                    total.setFontAndSize(baseFont, 9);
                } else {
                    BaseFont font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);
                    total.setFontAndSize(font, 9);
                }
                total.showText(String.valueOf(totalPages));
                total.endText();
            } catch (Exception e) {
                // If error, try to write at least something
                try {
                    total.beginText();
                    BaseFont font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);
                    total.setFontAndSize(font, 9);
                    int totalPages = writer.getCurrentPageNumber() - 1;
                    if (totalPages < 1) totalPages = 1;
                    total.showText(String.valueOf(totalPages));
                    total.endText();
                } catch (Exception ignored) {
                    // swallow
                }
            }
        }
    }
    
    /**
     * Builds a pet profile PDF with photo and all details.
     */
    public byte[] buildPetProfilePdf(Pet pet) {
        if (pet == null) {
            throw new IllegalArgumentException("Pet cannot be null");
        }
        
        try {
            Document document = new Document(PageSize.A4, 72, 72, 72, 72);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new PetProfileFooterEvent());
            
            document.open();
            document.addTitle("Pet Profile");
            
            BaseFont base;
            try {
                base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception e) {
                base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);
            }
            
            Font header = new Font(base, 16, Font.BOLD, Color.BLACK);
            Font sub = new Font(base, 10, Font.NORMAL, Color.GRAY);
            Font label = new Font(base, 11, Font.BOLD);
            Font value = new Font(base, 11, Font.NORMAL);
            Font title = new Font(base, 18, Font.BOLD, Color.BLACK);
            
            // Clinic header
            addReportHeader(document, header, sub);
            
            // Title
            Paragraph titlePara = new Paragraph("PET PROFILE", title);
            titlePara.setAlignment(Element.ALIGN_CENTER);
            titlePara.setSpacingAfter(20);
            document.add(titlePara);
            
            // Pet photo and info - cleaner layout
            PdfPTable mainTable = new PdfPTable(2);
            mainTable.setWidthPercentage(100);
            mainTable.setWidths(new float[]{1.5f, 2.5f});
            mainTable.setSpacingBefore(15);
            mainTable.setSpacingAfter(15);
            
            // Photo cell - larger and better positioned
            PdfPCell photoCell = new PdfPCell();
            photoCell.setPadding(15);
            photoCell.setBorder(Rectangle.BOX);
            photoCell.setBorderColor(new Color(200, 200, 200));
            photoCell.setBorderWidth(1.5f);
            photoCell.setFixedHeight(180);
            photoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            
            if (pet.getPhoto() != null && !pet.getPhoto().trim().isEmpty()) {
                try {
                    String photoPath = pet.getPhoto();
                    // Remove leading /uploads/ if present, we'll add it back with proper path
                    if (photoPath.startsWith("/uploads/")) {
                        photoPath = photoPath.substring("/uploads/".length());
                    } else if (photoPath.startsWith("uploads/")) {
                        photoPath = photoPath.substring("uploads/".length());
                    }
                    
                    // Try multiple possible paths
                    String[] possiblePaths = {
                        uploadDir + File.separator + photoPath,
                        "uploads" + File.separator + photoPath,
                        photoPath,
                        System.getProperty("user.dir") + File.separator + uploadDir + File.separator + photoPath,
                        new File(uploadDir).getAbsolutePath() + File.separator + photoPath
                    };
                    
                    Image img = null;
                    Exception lastException = null;
                    for (String path : possiblePaths) {
                        try {
                            File photoFile = new File(path);
                            if (photoFile.exists() && photoFile.isFile() && photoFile.canRead()) {
                                img = Image.getInstance(photoFile.getAbsolutePath());
                                break;
                            }
                        } catch (Exception e) {
                            lastException = e;
                            // Try next path
                            continue;
                        }
                    }
                    
                    if (img != null) {
                        // Scale to fit nicely in the cell - larger size
                        img.scaleToFit(170, 170);
                        img.setAlignment(Image.ALIGN_CENTER);
                        photoCell.addElement(img);
                    } else {
                        // Try URL if it's a URL
                        try {
                            if (pet.getPhoto().startsWith("http://") || pet.getPhoto().startsWith("https://")) {
                                img = Image.getInstance(new java.net.URL(pet.getPhoto()));
                                img.scaleToFit(170, 170);
                                img.setAlignment(Image.ALIGN_CENTER);
                                photoCell.addElement(img);
                            } else {
                                throw new Exception("Photo file not found: " + (lastException != null ? lastException.getMessage() : ""));
                            }
                        } catch (Exception urlEx) {
                            Paragraph noPhoto = new Paragraph("Photo\nNot Available", value);
                            noPhoto.setAlignment(Element.ALIGN_CENTER);
                            photoCell.addElement(noPhoto);
                        }
                    }
                } catch (Exception e) {
                    Paragraph noPhoto = new Paragraph("Photo\nNot Available", value);
                    noPhoto.setAlignment(Element.ALIGN_CENTER);
                    photoCell.addElement(noPhoto);
                }
            } else {
                Paragraph noPhoto = new Paragraph("No Photo", value);
                noPhoto.setAlignment(Element.ALIGN_CENTER);
                photoCell.addElement(noPhoto);
            }
            mainTable.addCell(photoCell);
            
            // Info cell - better organized
            PdfPCell infoCell = new PdfPCell();
            infoCell.setPadding(15);
            infoCell.setBorder(Rectangle.NO_BORDER);
            infoCell.setVerticalAlignment(Element.ALIGN_TOP);
            
            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);
            infoTable.setWidths(new float[]{0.8f, 2.2f});
            infoTable.setSpacingBefore(0);
            infoTable.setSpacingAfter(0);
            
            // Add rows with better spacing
            addRow(infoTable, "Name:", nullToEmpty(pet.getName()), label, value);
            addRow(infoTable, "Species:", nullToEmpty(pet.getSpecies()), label, value);
            addRow(infoTable, "Breed:", nullToEmpty(pet.getBreed()), label, value);
            addRow(infoTable, "Gender:", nullToEmpty(pet.getGender()), label, value);
            addRow(infoTable, "Age:", pet.getAge() != null ? pet.getAge() + " years" : "N/A", label, value);
            addRow(infoTable, "Microchip:", nullToEmpty(pet.getMicrochip()), label, value);
            addRow(infoTable, "Owner:", nullToEmpty(pet.getOwner()), label, value);
            addRow(infoTable, "Address:", nullToEmpty(pet.getAddress()), label, value);
            addRow(infoTable, "Federation:", nullToEmpty(pet.getFederation()), label, value);
            if (pet.getCreatedAt() != null) {
                addRow(infoTable, "Registered:", pet.getCreatedAt().toLocalDate().toString(), label, value);
            }
            
            infoCell.addElement(infoTable);
            mainTable.addCell(infoCell);
            
            document.add(mainTable);
            
            // Procedures section if any - cleaner layout
            if (pet.getProcedures() != null && !pet.getProcedures().isEmpty()) {
                document.add(Chunk.NEWLINE);
                Paragraph procTitle = new Paragraph("PROCEDURES HISTORY", new Font(base, 14, Font.BOLD, Color.BLACK));
                procTitle.setSpacingBefore(20);
                procTitle.setSpacingAfter(12);
                document.add(procTitle);
                
                PdfPTable procTable = new PdfPTable(4);
                procTable.setWidthPercentage(100);
                procTable.setWidths(new float[]{1.8f, 2.2f, 2f, 1.5f});
                procTable.setHeaderRows(1);
                procTable.setSpacingBefore(5);
                procTable.setSpacingAfter(10);
                
                PdfPCell dateHeader = new PdfPCell(new Phrase("Date", label));
                dateHeader.setBackgroundColor(new Color(230, 230, 230));
                dateHeader.setPadding(8);
                dateHeader.setBorderColor(Color.GRAY);
                procTable.addCell(dateHeader);
                
                PdfPCell nameHeader = new PdfPCell(new Phrase("Procedure", label));
                nameHeader.setBackgroundColor(new Color(230, 230, 230));
                nameHeader.setPadding(8);
                nameHeader.setBorderColor(Color.GRAY);
                procTable.addCell(nameHeader);
                
                PdfPCell vetHeader = new PdfPCell(new Phrase("Veterinarian", label));
                vetHeader.setBackgroundColor(new Color(230, 230, 230));
                vetHeader.setPadding(8);
                vetHeader.setBorderColor(Color.GRAY);
                procTable.addCell(vetHeader);
                
                PdfPCell costHeader = new PdfPCell(new Phrase("Cost", label));
                costHeader.setBackgroundColor(new Color(230, 230, 230));
                costHeader.setPadding(8);
                costHeader.setHorizontalAlignment(Element.ALIGN_RIGHT);
                costHeader.setBorderColor(Color.GRAY);
                procTable.addCell(costHeader);
                
                for (com.bayport.entity.Procedure proc : pet.getProcedures()) {
                    String dateStr = proc.getPerformedAt() != null ? proc.getPerformedAt().toString() : "";
                    PdfPCell dateCell = new PdfPCell(new Phrase(dateStr, value));
                    dateCell.setPadding(7);
                    dateCell.setBorderColor(Color.LIGHT_GRAY);
                    procTable.addCell(dateCell);
                    
                    PdfPCell nameCell = new PdfPCell(new Phrase(nullToEmpty(proc.getName()), value));
                    nameCell.setPadding(7);
                    nameCell.setBorderColor(Color.LIGHT_GRAY);
                    procTable.addCell(nameCell);
                    
                    PdfPCell vetCell = new PdfPCell(new Phrase(nullToEmpty(proc.getVet()), value));
                    vetCell.setPadding(7);
                    vetCell.setBorderColor(Color.LIGHT_GRAY);
                    procTable.addCell(vetCell);
                    
                    String costStr = proc.getCost() != null ? MoneyUtils.formatPeso(proc.getCost()) : "₱0.00";
                    PdfPCell costCell = new PdfPCell(new Phrase(costStr, value));
                    costCell.setPadding(7);
                    costCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    costCell.setBorderColor(Color.LIGHT_GRAY);
                    procTable.addCell(costCell);
                }
                
                document.add(procTable);
            }
            
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate pet profile PDF: " + e.getMessage(), e);
        }
    }
    
    /**
     * Builds a PDF report of all pets in the clinic.
     */
    public byte[] buildAllPetsPdf(List<Pet> pets) {
        if (pets == null || pets.isEmpty()) {
            throw new IllegalArgumentException("Pets list cannot be null or empty");
        }
        
        try {
            Document document = new Document(PageSize.A4, 72, 72, 72, 72);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new AllPetsFooterEvent());
            
            document.open();
            document.addTitle("All Pets Report");
            
            BaseFont base;
            try {
                base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception e) {
                base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);
            }
            
            Font header = new Font(base, 16, Font.BOLD, Color.BLACK);
            Font sub = new Font(base, 10, Font.NORMAL, Color.GRAY);
            Font label = new Font(base, 11, Font.BOLD);
            Font value = new Font(base, 10, Font.NORMAL);
            Font title = new Font(base, 18, Font.BOLD, Color.BLACK);
            
            // Clinic header
            addReportHeader(document, header, sub);
            
            // Title
            Paragraph titlePara = new Paragraph("ALL PETS REPORT", title);
            titlePara.setAlignment(Element.ALIGN_CENTER);
            titlePara.setSpacingAfter(5);
            document.add(titlePara);
            
            Paragraph datePara = new Paragraph("Generated: " + TS.format(LocalDateTime.now()), sub);
            datePara.setAlignment(Element.ALIGN_CENTER);
            datePara.setSpacingAfter(15);
            document.add(datePara);
            
            Paragraph countPara = new Paragraph("Total Pets: " + pets.size(), label);
            countPara.setAlignment(Element.ALIGN_CENTER);
            countPara.setSpacingAfter(15);
            document.add(countPara);
            
            document.add(Chunk.NEWLINE);
            
            // Pets table
            PdfPTable petsTable = new PdfPTable(7);
            petsTable.setWidthPercentage(100);
            petsTable.setWidths(new float[]{1.5f, 1.2f, 1.2f, 0.8f, 0.8f, 1.5f, 1f});
            petsTable.setHeaderRows(1);
            petsTable.setSpacingBefore(5);
            
            // Headers
            String[] headers = {"Name", "Species", "Breed", "Gender", "Age", "Owner", "Registered"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, label));
                cell.setBackgroundColor(new Color(240, 240, 240));
                cell.setPadding(6);
                petsTable.addCell(cell);
            }
            
            // Data rows
            for (Pet pet : pets) {
                petsTable.addCell(new Phrase(nullToEmpty(pet.getName()), value));
                petsTable.addCell(new Phrase(nullToEmpty(pet.getSpecies()), value));
                petsTable.addCell(new Phrase(nullToEmpty(pet.getBreed()), value));
                petsTable.addCell(new Phrase(nullToEmpty(pet.getGender()), value));
                petsTable.addCell(new Phrase(pet.getAge() != null ? pet.getAge().toString() : "", value));
                petsTable.addCell(new Phrase(nullToEmpty(pet.getOwner()), value));
                petsTable.addCell(new Phrase(pet.getCreatedAt() != null ? pet.getCreatedAt().toLocalDate().toString() : "", value));
            }
            
            document.add(petsTable);
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate all pets PDF: " + e.getMessage(), e);
        }
    }
    
    /**
     * Footer event for pet profile PDF.
     */
    private static class PetProfileFooterEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            int pageNumber = writer.getCurrentPageNumber();
            
            float y = document.bottom() - 10;
            Font footerFont = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY);
            
            Phrase footer = new Phrase("Page " + pageNumber, footerFont);
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, footer, 
                (document.left() + document.right()) / 2, y, 0);
        }
    }
    
    /**
     * Footer event for all pets PDF.
     */
    private static class AllPetsFooterEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            int pageNumber = writer.getCurrentPageNumber();
            
            float y = document.bottom() - 10;
            Font footerFont = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY);
            
            String footerText = "Generated: " + TS.format(LocalDateTime.now()) + " | Page " + pageNumber;
            Phrase footer = new Phrase(footerText, footerFont);
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, footer, 
                (document.left() + document.right()) / 2, y, 0);
        }
    }
}
