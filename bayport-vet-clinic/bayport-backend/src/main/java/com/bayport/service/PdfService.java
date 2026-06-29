package com.bayport.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.bayport.dto.ReportDailyPosRow;
import com.bayport.dto.ReportNewPatient;
import com.bayport.dto.ReportSummary;
import com.bayport.dto.ReportTopItemRow;
import com.bayport.dto.VaccinationScheduleItem;
import com.bayport.entity.MedicalRecordType;
import com.bayport.entity.Pet;
import com.bayport.entity.PetMedicalRecord;
import com.bayport.entity.Prescription;
import com.bayport.entity.Procedure;
import com.bayport.entity.Reminder;
import com.bayport.util.MoneyUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PdfService {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("MMMM d, yyyy hh:mm a");
    private static final Color CLINIC_BLUE = new Color(0, 87, 184);
    private static final Color SOFT_BLUE = new Color(235, 243, 255);

    /** Matches printed Bayport prescription pad letterhead. */
    private static final String CLINIC_NAME_LINE = "BAYPORT VETERINARY CLINIC";
    private static final String CLINIC_ADDRESS_LINE = "0383 Quirino, Ave Don Galo, Para\u00f1aque City";
    private static final String CLINIC_PHONE_LINE = "09686332940 / tel no. (02) 82548338";
    private static final String CLINIC_EMAIL_LINE = "bayport.vetclinic@gmail.com";
    
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
            // Physical Rx pad has no page-number footer.

            document.open();
            document.addTitle("Prescription");

            BaseFont base;
            try {
                base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception e) {
                base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);
            }

            BaseFont padBase;
            try {
                padBase = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);
            } catch (Exception e) {
                padBase = base;
            }

            Font clinicTitle = new Font(padBase, 15, Font.BOLD, Color.BLACK);
            Font contactLine = new Font(padBase, 9.5f, Font.NORMAL, Color.BLACK);
            Font fieldLabel = new Font(padBase, 11, Font.NORMAL, Color.BLACK);
            Font fieldValue = new Font(padBase, 11, Font.NORMAL, Color.BLACK);
            Font rxSymbol = new Font(padBase, 48, Font.BOLD, Color.BLACK);
            // IDENTITY_H required so circled medication indices (①②…) render like the on-screen Rx pad.
            Font medFont = new Font(base, 11, Font.NORMAL, Color.BLACK);

            addPrescriptionPadHeader(document, clinicTitle, contactLine);
            addPrescriptionPadPatientBlock(document, first, fieldLabel, fieldValue);
            addPrescriptionPadRxBody(document, prescriptions, rxSymbol, medFont);
            addPrescriptionPadFooter(document, first, fieldLabel, fieldValue, contactLine);

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

    /** Prescription pad letterhead — matches physical pad (logo + clinic block + rule). */
    private void addPrescriptionPadHeader(Document document, Font clinicTitle, Font contactLine)
            throws DocumentException {
        PdfPTable top = new PdfPTable(new float[]{0.85f, 4.15f});
        top.setWidthPercentage(100);
        top.getDefaultCell().setBorder(Rectangle.NO_BORDER);
        top.setSpacingAfter(6);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setVerticalAlignment(Element.ALIGN_TOP);
        logoCell.setPaddingRight(10);
        Image logo = tryLoadClinicLogo();
        if (logo != null) {
            logo.scaleToFit(82, 82);
            logoCell.addElement(logo);
        }
        top.addCell(logoCell);

        PdfPCell textCell = new PdfPCell();
        textCell.setBorder(Rectangle.NO_BORDER);
        textCell.setVerticalAlignment(Element.ALIGN_TOP);
        textCell.setPaddingLeft(4);
        Paragraph clinic = new Paragraph(CLINIC_NAME_LINE, clinicTitle);
        clinic.setSpacingAfter(6);
        textCell.addElement(clinic);
        textCell.addElement(new Paragraph(CLINIC_ADDRESS_LINE, contactLine));
        textCell.addElement(new Paragraph("Contact No: " + CLINIC_PHONE_LINE, contactLine));
        textCell.addElement(new Paragraph("Email: " + CLINIC_EMAIL_LINE, contactLine));
        top.addCell(textCell);
        document.add(top);

        LineSeparator divider = new LineSeparator(0.6f, 100f, Color.BLACK, Element.ALIGN_CENTER, 0);
        document.add(divider);
        document.add(Chunk.NEWLINE);
    }

    private void addPrescriptionPadPatientBlock(Document document, Prescription first, Font label, Font value)
            throws DocumentException {
        PdfPTable row1 = new PdfPTable(new float[]{1.2f, 2.2f, 0.5f, 1.1f});
        row1.setWidthPercentage(100);
        row1.getDefaultCell().setBorder(Rectangle.NO_BORDER);
        row1.getDefaultCell().setPadding(2);
        row1.addCell(new Phrase("Pet's Name:", label));
        PdfPCell petVal = new PdfPCell(new Phrase(nullToEmpty(first.getPet()), value));
        petVal.setBorder(Rectangle.BOTTOM);
        petVal.setBorderWidth(0.5f);
        petVal.setPaddingBottom(4);
        row1.addCell(petVal);
        row1.addCell(new Phrase("Date:", label));
        PdfPCell dateVal = new PdfPCell(new Phrase(
                first.getDate() != null ? first.getDate().toString() : "", value));
        dateVal.setBorder(Rectangle.BOTTOM);
        dateVal.setBorderWidth(0.5f);
        dateVal.setPaddingBottom(4);
        row1.addCell(dateVal);
        document.add(row1);

        PdfPTable row2 = new PdfPTable(new float[]{1.2f, 4.8f});
        row2.setWidthPercentage(100);
        row2.getDefaultCell().setBorder(Rectangle.NO_BORDER);
        row2.getDefaultCell().setPadding(2);
        row2.setSpacingAfter(8);
        row2.addCell(new Phrase("Owner's Name:", label));
        PdfPCell ownerVal = new PdfPCell(new Phrase(nullToEmpty(first.getOwner()), value));
        ownerVal.setBorder(Rectangle.BOTTOM);
        ownerVal.setBorderWidth(0.5f);
        ownerVal.setPaddingBottom(4);
        row2.addCell(ownerVal);
        document.add(row2);

        Pet petEntity = first.getPetEntity();
        if (petEntity != null && (petEntity.getLastVaccinationDate() != null
                || stringHasText(petEntity.getLastVaccinationPlace())
                || stringHasText(petEntity.getLastVaccinationVet()))) {
            PdfPTable row3 = new PdfPTable(new float[]{1.2f, 4.8f});
            row3.setWidthPercentage(100);
            row3.getDefaultCell().setBorder(Rectangle.NO_BORDER);
            row3.getDefaultCell().setPadding(2);
            row3.setSpacingAfter(8);
            row3.addCell(new Phrase("Last vaccinated:", label));
            String vaccInfo = formatLastVaccination(petEntity);
            PdfPCell vaccVal = new PdfPCell(new Phrase(vaccInfo, value));
            vaccVal.setBorder(Rectangle.BOTTOM);
            vaccVal.setBorderWidth(0.5f);
            vaccVal.setPaddingBottom(4);
            row3.addCell(vaccVal);
            document.add(row3);
        }
        document.add(Chunk.NEWLINE);
    }

    private static String formatLastVaccination(Pet pet) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (pet.getLastVaccinationDate() != null) {
            parts.add(pet.getLastVaccinationDate().toString());
        }
        if (pet.getLastVaccinationPlace() != null && !pet.getLastVaccinationPlace().isBlank()) {
            parts.add(pet.getLastVaccinationPlace().trim());
        }
        if (pet.getLastVaccinationVet() != null && !pet.getLastVaccinationVet().isBlank()) {
            parts.add("Vet: " + pet.getLastVaccinationVet().trim());
        }
        return parts.isEmpty() ? "" : String.join("  \u2022  ", parts);
    }

    private static boolean stringHasText(String s) {
        return s != null && !s.isBlank();
    }

    private void addPrescriptionPadRxBody(Document document,
                                          List<Prescription> prescriptions,
                                          Font rxSymbol,
                                          Font medFont) throws DocumentException {
        // Stacked like the physical pad:
        //   Rx
        //       Medicine …                    #
        //               Sig: …
        final float medIndentPt = 36f;
        final float sigIndentPt = 80f;

        PdfPTable block = new PdfPTable(1);
        block.setWidthPercentage(100);
        block.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        PdfPCell rxCell = new PdfPCell(new Phrase("Rx", rxSymbol));
        rxCell.setBorder(Rectangle.NO_BORDER);
        rxCell.setPaddingTop(6);
        rxCell.setPaddingBottom(10);
        block.addCell(rxCell);

        int n = 1;
        for (Prescription rx : prescriptions) {
            String drugLine = nullToEmpty(rx.getDrug());
            if (!nullToEmpty(rx.getDosage()).isBlank()) {
                drugLine = drugLine + " " + rx.getDosage().trim();
            }

            PdfPTable medRow = new PdfPTable(new float[]{5f, 0.55f});
            medRow.setWidthPercentage(100);
            medRow.getDefaultCell().setBorder(Rectangle.NO_BORDER);

            PdfPCell nameCell = new PdfPCell(new Phrase(prescriptionBullet(n) + "  " + drugLine, medFont));
            nameCell.setBorder(Rectangle.NO_BORDER);
            nameCell.setPaddingBottom(2);
            medRow.addCell(nameCell);

            String qtyText = (rx.getDispenseQty() != null && rx.getDispenseQty() > 0)
                    ? "# " + rx.getDispenseQty() : "#";
            PdfPCell qtyCell = new PdfPCell(new Phrase(qtyText, medFont));
            qtyCell.setBorder(Rectangle.NO_BORDER);
            qtyCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            qtyCell.setVerticalAlignment(Element.ALIGN_TOP);
            medRow.addCell(qtyCell);

            PdfPCell medCell = new PdfPCell();
            medCell.setBorder(Rectangle.NO_BORDER);
            medCell.setPaddingLeft(medIndentPt);
            medCell.setPaddingBottom(4);
            medCell.addElement(medRow);
            block.addCell(medCell);

            String sig = nullToEmpty(rx.getDirections());
            PdfPCell sigCell = new PdfPCell(new Phrase("Sig: " + sig, medFont));
            sigCell.setBorder(Rectangle.NO_BORDER);
            sigCell.setPaddingLeft(sigIndentPt);
            sigCell.setPaddingBottom(14);
            block.addCell(sigCell);

            n++;
        }

        block.setSpacingAfter(16);
        document.add(block);
    }

    private void addPrescriptionPadFooter(Document document,
                                          Prescription first,
                                          Font label,
                                          Font value,
                                          Font notesFont) throws DocumentException {
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);

        String followUp = formatFollowUp(first.getDaysSupply());
        PdfPTable foot = new PdfPTable(2);
        foot.setWidthPercentage(100);
        foot.setWidths(new float[]{1.2f, 1f});
        foot.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.addElement(new Paragraph("Follow up: " + followUp, value));
        String vetNotes = nullToEmpty(first.getVetNotes());
        if (!vetNotes.isBlank()) {
            Paragraph notes = new Paragraph(vetNotes, notesFont);
            notes.setSpacingBefore(8);
            left.addElement(notes);
        }
        foot.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph line = new Paragraph("______________________________", value);
        line.setAlignment(Element.ALIGN_CENTER);
        right.addElement(line);
        String prescriber = nullToEmpty(first.getPrescriber());
        if (!prescriber.isBlank()) {
            Paragraph vetName = new Paragraph(prescriber, value);
            vetName.setAlignment(Element.ALIGN_CENTER);
            vetName.setSpacingBefore(4);
            right.addElement(vetName);
        }
        Paragraph vetLabel = new Paragraph("Veterinarian", label);
        vetLabel.setAlignment(Element.ALIGN_CENTER);
        vetLabel.setSpacingBefore(2);
        right.addElement(vetLabel);
        String licenseNo = nullToEmpty(first.getPrescriberLicenseNo());
        if (!licenseNo.isBlank()) {
            Paragraph licenseLine = new Paragraph("Licensed No. " + licenseNo, value);
            licenseLine.setAlignment(Element.ALIGN_CENTER);
            licenseLine.setSpacingBefore(2);
            right.addElement(licenseLine);
        }
        foot.addCell(right);

        PdfPCell pageCell = new PdfPCell(new Paragraph("Page 1 of 1", value));
        pageCell.setColspan(2);
        pageCell.setBorder(Rectangle.NO_BORDER);
        pageCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        pageCell.setPaddingTop(10f);
        foot.addCell(pageCell);

        document.add(foot);
    }
    
    /**
     * Branded letterhead (logo, clinic block, divider) consistent with prescription PDFs.
     *
     * @param centeredBanner optional title below the divider; pass {@code null} to omit
     */
    private void addReportHeader(Document document, Font header, Font sub, String centeredBanner) throws DocumentException {
        PdfPTable top = new PdfPTable(2);
        top.setWidthPercentage(100);
        top.setWidths(new float[]{1, 4});
        top.getDefaultCell().setBorder(Rectangle.NO_BORDER);
        top.setSpacingAfter(6);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Image logo = tryLoadClinicLogo();
        if (logo != null) {
            logo.scaleToFit(54, 54);
            logoCell.addElement(logo);
        } else if (sub.getBaseFont() != null) {
            logoCell.addElement(new Phrase("\ud83d\udc3e", new Font(sub.getBaseFont(), 22, Font.NORMAL, CLINIC_BLUE)));
        }
        top.addCell(logoCell);

        PdfPCell textCell = new PdfPCell();
        textCell.setBorder(Rectangle.NO_BORDER);
        textCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Paragraph clinic = new Paragraph("Bayport Veterinary Clinic", header);
        clinic.setSpacingAfter(3);
        textCell.addElement(clinic);
        textCell.addElement(new Paragraph("322 Quirino Avenue, Brgy. Don Galo, Parañaque City  •  0968 633 2940", sub));
        textCell.addElement(new Paragraph("Generated: " + TS.format(LocalDateTime.now()), sub));
        top.addCell(textCell);
        document.add(top);

        LineSeparator divider = new LineSeparator(1.2f, 100f, CLINIC_BLUE, Element.ALIGN_CENTER, 0);
        document.add(divider);
        document.add(Chunk.NEWLINE);

        if (centeredBanner != null && !centeredBanner.isBlank()) {
            Paragraph ban = new Paragraph(centeredBanner, header);
            ban.setAlignment(Element.ALIGN_CENTER);
            ban.setSpacingAfter(10);
            document.add(ban);
        }
    }

    private static PdfPCell summaryTableHeaderCell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBackgroundColor(SOFT_BLUE);
        c.setPadding(7);
        c.setBorderColor(CLINIC_BLUE);
        return c;
    }

    /** Loads the clinic seal from the backend JAR only (never a random file on disk). */
    private Image tryLoadClinicLogo() {
        String[] classpathCandidates = {
                "/assets/logo.png",
                "/static/assets/logo.png",
                "/logo.png"
        };
        for (String candidate : classpathCandidates) {
            try (InputStream in = PdfService.class.getResourceAsStream(candidate)) {
                if (in != null) {
                    return Image.getInstance(in.readAllBytes());
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String formatFollowUp(Object daysSupply) {
        if (daysSupply == null) return "";
        String s = String.valueOf(daysSupply).trim();
        if (s.isEmpty()) return "";
        if (s.matches("\\d+")) return s + " days";
        return s;
    }

    /** Circled index (①②…) like the printed Rx pad; falls back to "1." beyond 20. */
    private static String prescriptionBullet(int index) {
        if (index >= 1 && index <= 20) {
            return String.valueOf((char) (0x2460 + index - 1));
        }
        return index + ".";
    }

    /**
     * Builds a printable summary PDF for the given report summary. Adds a footer
     * with "Prepared by: <name>" and "Page X of Y" on each page.
     */
    public byte[] buildSummaryPdf(ReportSummary summary, String preparedBy) {
        String prepared = (preparedBy == null || preparedBy.isBlank()) ? "Admin" : preparedBy;

        try {
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

            Font header = new Font(base, 17, Font.BOLD, CLINIC_BLUE);
            Font sub = new Font(base, 10, Font.NORMAL, Color.DARK_GRAY);
            Font label = new Font(base, 11, Font.BOLD, CLINIC_BLUE);
            Font value = new Font(base, 11, Font.NORMAL, Color.BLACK);
            Font section = new Font(base, 12, Font.BOLD, CLINIC_BLUE);

            addReportHeader(document, header, sub, "Financial & operations report");

            String period = summary.period != null ? summary.period : "day";
            String from = summary.from != null ? summary.from : "";
            String to = summary.to != null ? summary.to : "";
            Paragraph periodLabel = new Paragraph(String.format("Period: %s", period.toUpperCase()), label);
            periodLabel.setSpacingAfter(3);
            document.add(periodLabel);

            Paragraph dateRange = new Paragraph(String.format("From: %s  To: %s", from, to), value);
            dateRange.setSpacingAfter(12);
            document.add(dateRange);

            Paragraph finTitle = new Paragraph("Financial snapshot", section);
            finTitle.setSpacingAfter(6);
            document.add(finTitle);

            PdfPTable fin = new PdfPTable(2);
            fin.setWidthPercentage(100);
            fin.setWidths(new float[]{1.35f, 1f});
            fin.setSpacingAfter(10);
            BigDecimal posTotal = summary.posSales != null ? summary.posSales : BigDecimal.ZERO;
            addRow(fin, "Total POS sales", MoneyUtils.formatPeso(posTotal), label, value);
            addRow(fin, "Paid (POS)", MoneyUtils.formatPeso(posTotal), label, value);
            addRow(fin, "Pending (billing)", MoneyUtils.formatPeso(summary.pendingBilling), label, value);
            addRow(fin, "Voided", MoneyUtils.formatPeso(summary.voidedAmount), label, value);
            document.add(fin);

            Paragraph payTitle = new Paragraph("Sales by payment method", section);
            payTitle.setSpacingBefore(4);
            payTitle.setSpacingAfter(6);
            document.add(payTitle);

            BigDecimal payDenom = summary.posCash.add(summary.posCard).add(summary.posOtherPayment);
            PdfPTable pay = new PdfPTable(3);
            pay.setWidthPercentage(100);
            pay.setWidths(new float[]{1f, 1f, 1f});
            pay.setSpacingAfter(10);
            pay.addCell(summaryTableHeaderCell("Method", label));
            pay.addCell(summaryTableHeaderCell("Amount", label));
            pay.addCell(summaryTableHeaderCell("% of mix", label));
            addPaymentRow(pay, value, "Cash", summary.posCash, payDenom);
            addPaymentRow(pay, value, "Card", summary.posCard, payDenom);
            addPaymentRow(pay, value, "Other", summary.posOtherPayment, payDenom);
            document.add(pay);

            Paragraph dailyTitle = new Paragraph("POS sales by day", section);
            dailyTitle.setSpacingBefore(4);
            dailyTitle.setSpacingAfter(6);
            document.add(dailyTitle);

            if (summary.dailyPos == null || summary.dailyPos.isEmpty()) {
                document.add(new Paragraph("No POS transactions in this period.", value));
            } else {
                PdfPTable daily = new PdfPTable(3);
                daily.setWidthPercentage(100);
                daily.setWidths(new float[]{1f, 0.7f, 1f});
                daily.setHeaderRows(1);
                daily.setSpacingAfter(10);
                daily.addCell(summaryTableHeaderCell("Date", label));
                daily.addCell(summaryTableHeaderCell("Txns", label));
                daily.addCell(summaryTableHeaderCell("Total", label));
                for (ReportDailyPosRow d : summary.dailyPos) {
                    daily.addCell(paddedPhraseCell(nullToEmpty(d.date), value));
                    daily.addCell(paddedPhraseCell(String.valueOf(d.transactions), value));
                    daily.addCell(paddedPhraseCell(MoneyUtils.formatPeso(d.total), value));
                }
                document.add(daily);
            }

            Paragraph topTitle = new Paragraph("Top selling items", section);
            topTitle.setSpacingBefore(4);
            topTitle.setSpacingAfter(6);
            document.add(topTitle);

            if (summary.topItems == null || summary.topItems.isEmpty()) {
                document.add(new Paragraph("No line-level sales data in this period.", value));
            } else {
                PdfPTable top = new PdfPTable(4);
                top.setWidthPercentage(100);
                top.setWidths(new float[]{2f, 1f, 0.55f, 1f});
                top.setHeaderRows(1);
                top.setSpacingAfter(12);
                top.addCell(summaryTableHeaderCell("Item", label));
                top.addCell(summaryTableHeaderCell("Category", label));
                top.addCell(summaryTableHeaderCell("Qty", label));
                top.addCell(summaryTableHeaderCell("Total", label));
                int n = 0;
                for (ReportTopItemRow t : summary.topItems) {
                    if (++n > 25) {
                        break;
                    }
                    top.addCell(paddedPhraseCell(nullToEmpty(t.name), value));
                    top.addCell(paddedPhraseCell(nullToEmpty(t.category), value));
                    top.addCell(paddedPhraseCell(String.valueOf(t.qty), value));
                    top.addCell(paddedPhraseCell(MoneyUtils.formatPeso(t.total), value));
                }
                document.add(top);
            }

            Paragraph metricsTitle = new Paragraph("Clinical & operations metrics", section);
            metricsTitle.setSpacingBefore(6);
            metricsTitle.setSpacingAfter(8);
            document.add(metricsTitle);

            PdfPTable metrics = new PdfPTable(2);
            metrics.setWidthPercentage(100);
            metrics.setWidths(new float[]{1, 1});
            metrics.setSpacingAfter(10);
            addRow(metrics, "Appointments completed", String.valueOf(summary.appointmentsDone), label, value);
            addRow(metrics, "Prescriptions dispensed", String.valueOf(summary.prescriptionsDispensed), label, value);
            addRow(metrics, "New patients registered", String.valueOf(summary.petsAdded), label, value);
            addRow(metrics, "POS procedures (line revenue)", MoneyUtils.formatPeso(summary.posProcedureRevenue), label, value);
            addRow(metrics, "POS products (line revenue)", MoneyUtils.formatPeso(summary.posProductRevenue), label, value);
            document.add(metrics);

            document.add(Chunk.NEWLINE);

            addPosSaleLinesSection(document, label, value, "Procedure line items (POS)",
                    summary.posProcedureLines, summary.posProcedureRevenue);
            addPosSaleLinesSection(document, label, value, "Product line items (POS)",
                    summary.posProductLines, summary.posProductRevenue);

            if (summary.newPatients != null && !summary.newPatients.isEmpty()) {
                Paragraph newPatientsTitle = new Paragraph("New patients", label);
                newPatientsTitle.setSpacingBefore(10);
                newPatientsTitle.setSpacingAfter(8);
                document.add(newPatientsTitle);

                PdfPTable newPatientsTable = new PdfPTable(3);
                newPatientsTable.setWidthPercentage(100);
                newPatientsTable.setWidths(new float[]{1.5f, 2f, 2f});
                newPatientsTable.setSpacingBefore(5);
                newPatientsTable.setSpacingAfter(10);
                newPatientsTable.setHeaderRows(1);

                newPatientsTable.addCell(summaryTableHeaderCell("Date added", label));
                newPatientsTable.addCell(summaryTableHeaderCell("Pet name", label));
                newPatientsTable.addCell(summaryTableHeaderCell("Owner", label));

                for (ReportNewPatient np : summary.newPatients) {
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

    private void addPaymentRow(PdfPTable table, Font value, String method, BigDecimal amt, BigDecimal denom) {
        BigDecimal a = amt != null ? amt : BigDecimal.ZERO;
        String pctStr;
        if (denom == null || denom.signum() == 0) {
            pctStr = a.signum() == 0 ? "0%" : "100%";
        } else {
            BigDecimal pct = a.multiply(BigDecimal.valueOf(100)).divide(denom, 1, RoundingMode.HALF_UP);
            pctStr = pct.stripTrailingZeros().toPlainString() + "%";
        }
        table.addCell(paddedPhraseCell(method, value));
        table.addCell(paddedPhraseCell(MoneyUtils.formatPeso(a), value));
        table.addCell(paddedPhraseCell(pctStr, value));
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
            table.addCell(summaryTableHeaderCell(h, label));
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
            
            Font header = new Font(base, 17, Font.BOLD, CLINIC_BLUE);
            Font sub = new Font(base, 10, Font.NORMAL, Color.DARK_GRAY);
            Font label = new Font(base, 11, Font.BOLD, CLINIC_BLUE);
            Font value = new Font(base, 11, Font.NORMAL, Color.BLACK);
            Font title = new Font(base, 18, Font.BOLD, CLINIC_BLUE);
            
            // Clinic header
            addReportHeader(document, header, sub, null);
            
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
            if (pet.getLastVaccinationDate() != null) {
                addRow(infoTable, "Last vaccination:", pet.getLastVaccinationDate().toString(), label, value);
            }
            if (pet.getLastVaccinationPlace() != null && !pet.getLastVaccinationPlace().isBlank()) {
                addRow(infoTable, "Vaccination place:", pet.getLastVaccinationPlace().trim(), label, value);
            }
            if (pet.getLastVaccinationVet() != null && !pet.getLastVaccinationVet().isBlank()) {
                addRow(infoTable, "Vaccinating vet:", pet.getLastVaccinationVet().trim(), label, value);
            }
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
            
            Font header = new Font(base, 17, Font.BOLD, CLINIC_BLUE);
            Font sub = new Font(base, 10, Font.NORMAL, Color.DARK_GRAY);
            Font label = new Font(base, 11, Font.BOLD, CLINIC_BLUE);
            Font value = new Font(base, 10, Font.NORMAL, Color.BLACK);
            Font title = new Font(base, 18, Font.BOLD, CLINIC_BLUE);
            
            // Clinic header
            addReportHeader(document, header, sub, null);
            
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
                PdfPCell cell = summaryTableHeaderCell(h, label);
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
     * Comprehensive medical history PDF — clinic branding, structured sections, chronological tables.
     */
    public byte[] buildMedicalHistoryPdf(
            Pet pet,
            List<PetMedicalRecord> records,
            List<Prescription> prescriptions,
            List<VaccinationScheduleItem> vaccinationSchedule,
            List<Reminder> reminders) {
        if (pet == null) {
            throw new IllegalArgumentException("Pet cannot be null");
        }
        try {
            Document document = new Document(PageSize.A4, 54, 54, 54, 54);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new PetProfileFooterEvent());
            document.open();
            document.addTitle("Medical History - " + nullToEmpty(pet.getName()));

            BaseFont base;
            try {
                base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception e) {
                base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);
            }
            Font header = new Font(base, 16, Font.BOLD, CLINIC_BLUE);
            Font sub = new Font(base, 10, Font.NORMAL, Color.DARK_GRAY);
            Font section = new Font(base, 13, Font.BOLD, CLINIC_BLUE);
            Font label = new Font(base, 10, Font.BOLD, Color.DARK_GRAY);
            Font value = new Font(base, 10, Font.NORMAL, Color.BLACK);
            Font title = new Font(base, 18, Font.BOLD, CLINIC_BLUE);

            addReportHeader(document, header, sub, null);
            Paragraph docTitle = new Paragraph("COMPREHENSIVE MEDICAL HISTORY", title);
            docTitle.setAlignment(Element.ALIGN_CENTER);
            docTitle.setSpacingAfter(4);
            document.add(docTitle);
            Paragraph generated = new Paragraph("Generated: " + TS.format(LocalDateTime.now()), sub);
            generated.setAlignment(Element.ALIGN_CENTER);
            generated.setSpacingAfter(16);
            document.add(generated);

            addSectionTitle(document, "BASIC INFORMATION", section);
            PdfPTable basic = new PdfPTable(2);
            basic.setWidthPercentage(100);
            basic.setWidths(new float[]{1.1f, 2.4f});
            addRow(basic, "Pet name:", nullToEmpty(pet.getName()), label, value);
            addRow(basic, "Species / Breed:", nullToEmpty(pet.getSpecies()) + " / " + nullToEmpty(pet.getBreed()), label, value);
            addRow(basic, "Gender / Age:", nullToEmpty(pet.getGender()) + " / " + (pet.getAge() != null ? pet.getAge() + " yrs" : "N/A"), label, value);
            addRow(basic, "Date of birth:", pet.getDateOfBirth() != null ? pet.getDateOfBirth().toString() : "N/A", label, value);
            addRow(basic, "Weight:", nullToEmpty(pet.getWeight()), label, value);
            addRow(basic, "Microchip:", nullToEmpty(pet.getMicrochip()), label, value);
            addRow(basic, "Owner:", nullToEmpty(pet.getOwner()), label, value);
            addRow(basic, "Allergies:", nullToEmpty(pet.getAllergies()), label, value);
            addRow(basic, "Chronic conditions:", nullToEmpty(pet.getChronicConditions()), label, value);
            addRow(basic, "Known medications:", nullToEmpty(pet.getKnownMedications()), label, value);
            document.add(basic);

            if (vaccinationSchedule != null && !vaccinationSchedule.isEmpty()) {
                addSectionTitle(document, "VACCINATION SCHEDULE & UPCOMING DUE DATES", section);
                PdfPTable sched = new PdfPTable(5);
                sched.setWidthPercentage(100);
                sched.setWidths(new float[]{1.6f, 1.2f, 1.2f, 1.2f, 1.8f});
                addTableHeader(sched, new String[]{"Vaccine", "Last given", "Next due", "Status", "Source"}, label);
                for (VaccinationScheduleItem item : vaccinationSchedule) {
                    sched.addCell(cell(nullToEmpty(item.vaccineName()), value));
                    sched.addCell(cell(item.lastGiven() != null ? item.lastGiven().toString() : "—", value));
                    sched.addCell(cell(item.nextDue() != null ? item.nextDue().toString() : "—", value));
                    sched.addCell(cell(nullToEmpty(item.status()), value));
                    sched.addCell(cell(nullToEmpty(item.source()), value));
                }
                document.add(sched);
            }

            List<PetMedicalRecord> vaccRecords = filterRecords(records, MedicalRecordType.VACCINATION);
            if (!vaccRecords.isEmpty() || pet.getLastVaccinationDate() != null) {
                addSectionTitle(document, "VACCINATION HISTORY", section);
                if (pet.getLastVaccinationDate() != null) {
                    document.add(new Paragraph("Last in-clinic vaccination: " + pet.getLastVaccinationDate()
                            + (pet.getLastVaccinationPlace() != null ? " at " + pet.getLastVaccinationPlace() : "")
                            + (pet.getLastVaccinationVet() != null ? " by " + pet.getLastVaccinationVet() : ""), value));
                }
                if (!vaccRecords.isEmpty()) {
                    PdfPTable vt = new PdfPTable(6);
                    vt.setWidthPercentage(100);
                    vt.setWidths(new float[]{1.1f, 1.4f, 1.2f, 1f, 1.1f, 1.2f});
                    addTableHeader(vt, new String[]{"Date", "Vaccine", "Dose", "Clinic", "Vet", "Next due"}, label);
                    for (PetMedicalRecord r : vaccRecords) {
                        vt.addCell(cell(dateStr(r.getRecordDate()), value));
                        vt.addCell(cell(nullToEmpty(r.getVaccineType() != null ? r.getVaccineType() : r.getTitle()), value));
                        vt.addCell(cell(nullToEmpty(r.getDoseNumber()), value));
                        vt.addCell(cell(nullToEmpty(r.getSourceClinic()), value));
                        vt.addCell(cell(nullToEmpty(r.getVeterinarian()), value));
                        vt.addCell(cell(r.getNextDueDate() != null ? r.getNextDueDate().toString() : "—", value));
                    }
                    document.add(vt);
                }
            }

            addProcedureHistorySection(document, pet, section, label, value);

            addTypedRecordsSection(document, records, MedicalRecordType.CLINICAL_NOTE, "CLINICAL NOTES", section, label, value);
            addTypedRecordsSection(document, records, MedicalRecordType.DIAGNOSTIC, "DIAGNOSTIC & TEST RESULTS", section, label, value);
            addTypedRecordsSection(document, records, MedicalRecordType.MEDICATION, "MEDICATIONS & PARASITE PREVENTATIVES", section, label, value);
            addTypedRecordsSection(document, records, MedicalRecordType.SURGERY, "SURGICAL HISTORY", section, label, value);
            addTypedRecordsSection(document, records, MedicalRecordType.EXTERNAL_DOCUMENT, "EXTERNAL CLINIC RECORDS", section, label, value);

            if (prescriptions != null && !prescriptions.isEmpty()) {
                addSectionTitle(document, "PRESCRIPTIONS", section);
                PdfPTable rx = new PdfPTable(5);
                rx.setWidthPercentage(100);
                rx.setWidths(new float[]{1.1f, 1.4f, 1.2f, 1.6f, 1.2f});
                addTableHeader(rx, new String[]{"Date", "Drug", "Dosage", "Directions", "Prescriber"}, label);
                prescriptions.stream()
                        .sorted(Comparator.comparing(Prescription::getDate, Comparator.nullsLast(Comparator.reverseOrder())))
                        .forEach(p -> {
                            rx.addCell(cell(p.getDate() != null ? p.getDate().toString() : "", value));
                            rx.addCell(cell(nullToEmpty(p.getDrug()), value));
                            rx.addCell(cell(nullToEmpty(p.getDosage()), value));
                            rx.addCell(cell(nullToEmpty(p.getDirections()), value));
                            rx.addCell(cell(nullToEmpty(p.getPrescriber()), value));
                        });
                document.add(rx);
            }

            if (reminders != null && !reminders.isEmpty()) {
                addSectionTitle(document, "REMINDERS", section);
                PdfPTable rem = new PdfPTable(4);
                rem.setWidthPercentage(100);
                rem.setWidths(new float[]{1.2f, 2.4f, 1f, 1f});
                addTableHeader(rem, new String[]{"Due date", "Message", "Sent", "Auto"}, label);
                for (Reminder r : reminders) {
                    rem.addCell(cell(r.getDate() != null ? r.getDate().toString() : "", value));
                    rem.addCell(cell(truncate(nullToEmpty(r.getMessage()), 120), value));
                    rem.addCell(cell(r.isSent() ? "Yes" : "No", value));
                    rem.addCell(cell(r.isAutoGenerated() ? "Yes" : "No", value));
                }
                document.add(rem);
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate medical history PDF: " + e.getMessage(), e);
        }
    }

    private void addProcedureHistorySection(Document document, Pet pet, Font section, Font label, Font value) throws DocumentException {
        if (pet.getProcedures() == null || pet.getProcedures().isEmpty()) return;
        addSectionTitle(document, "IN-CLINIC VISIT HISTORY (CHRONOLOGICAL)", section);
        PdfPTable procTable = new PdfPTable(6);
        procTable.setWidthPercentage(100);
        procTable.setWidths(new float[]{1f, 1.4f, 1.1f, 1.6f, 1.4f, 0.9f});
        addTableHeader(procTable, new String[]{"Date", "Procedure", "Category", "Notes", "Medications", "Vet"}, label);
        pet.getProcedures().stream()
                .sorted(Comparator.comparing(Procedure::getPerformedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(proc -> {
                    procTable.addCell(cell(proc.getPerformedAt() != null ? proc.getPerformedAt().toString() : "", value));
                    procTable.addCell(cell(nullToEmpty(proc.getName()), value));
                    procTable.addCell(cell(nullToEmpty(proc.getCategory()), value));
                    procTable.addCell(cell(truncate(nullToEmpty(proc.getNotes()), 80), value));
                    procTable.addCell(cell(truncate(nullToEmpty(proc.getMedications()), 60), value));
                    procTable.addCell(cell(nullToEmpty(proc.getVet()), value));
                });
        document.add(procTable);
    }

    private void addTypedRecordsSection(
            Document document,
            List<PetMedicalRecord> records,
            MedicalRecordType type,
            String title,
            Font section,
            Font label,
            Font value) throws DocumentException {
        List<PetMedicalRecord> filtered = filterRecords(records, type);
        if (filtered.isEmpty()) return;
        addSectionTitle(document, title, section);
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1f, 1.4f, 1.2f, 2f, 1.2f});
        addTableHeader(table, new String[]{"Date", "Title", "Clinic / Vet", "Details", "Attachment"}, label);
        for (PetMedicalRecord r : filtered) {
            table.addCell(cell(dateStr(r.getRecordDate()), value));
            table.addCell(cell(nullToEmpty(r.getTitle()), value));
            String clinicVet = (nullToEmpty(r.getSourceClinic()) + " / " + nullToEmpty(r.getVeterinarian())).replace(" / $", "");
            table.addCell(cell(clinicVet, value));
            String details = switch (type) {
                case CLINICAL_NOTE -> joinParts(r.getDiagnosis(), r.getTreatmentPlan(), r.getDescription());
                case DIAGNOSTIC -> joinParts(r.getTestResults(), r.getDescription());
                case MEDICATION -> joinParts(r.getMedications(), r.getDescription());
                case SURGERY -> joinParts(r.getDiagnosis(), r.getTreatmentPlan(), r.getDescription());
                case EXTERNAL_DOCUMENT -> joinParts(r.getDescription(), r.getDiagnosis());
                default -> nullToEmpty(r.getDescription());
            };
            table.addCell(cell(truncate(details, 100), value));
            table.addCell(cell(r.getAttachmentName() != null ? r.getAttachmentName() : "—", value));
        }
        document.add(table);
    }

    private static List<PetMedicalRecord> filterRecords(List<PetMedicalRecord> records, MedicalRecordType type) {
        if (records == null) return List.of();
        return records.stream().filter(r -> r.getRecordType() == type).collect(Collectors.toList());
    }

    private static void addSectionTitle(Document document, String text, Font font) throws DocumentException {
        Paragraph p = new Paragraph(text, font);
        p.setSpacingBefore(14);
        p.setSpacingAfter(8);
        document.add(p);
        LineSeparator line = new LineSeparator(1f, 100f, CLINIC_BLUE, Element.ALIGN_CENTER, -2);
        document.add(new Chunk(line));
        document.add(Chunk.NEWLINE);
    }

    private static void addTableHeader(PdfPTable table, String[] headers, Font font) {
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, font));
            c.setBackgroundColor(new Color(235, 243, 255));
            c.setPadding(6);
            table.addCell(c);
        }
    }

    private static PdfPCell cell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "", font));
        c.setPadding(5);
        return c;
    }

    private static String dateStr(LocalDate d) {
        return d != null ? d.toString() : "";
    }

    private static String joinParts(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append(p.trim());
            }
        }
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
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
