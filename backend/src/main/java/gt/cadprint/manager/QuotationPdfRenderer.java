package gt.cadprint.manager;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import gt.cadprint.manager.QuotationDtos.CommercialView;
import org.springframework.stereotype.Component;

@Component
class QuotationPdfRenderer {
    static final String VERSION = "cadprint-v1";
    private static final float SIDE_MARGIN = 45;
    private static final float BODY_WIDTH = PageSize.A4.getWidth() - 2 * SIDE_MARGIN;
    private static final Color ACCENT = new Color(32, 78, 92);
    private static final Color INK = new Color(34, 44, 54);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");

    byte[] render(CommercialView view, String version) throws IOException {
        if (!VERSION.equals(version)) {
            throw new IOException("Versión de plantilla no disponible");
        }
        BaseFont font;
        try (var resource = PdfWriter.class.getResourceAsStream("/font-fallback/LiberationSans-Regular.ttf")) {
            if (resource == null) {
                throw new IOException("Fuente PDF no disponible");
            }
            font = BaseFont.createFont("LiberationSans-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                    true, resource.readAllBytes(), null);
        }

        var bytes = new ByteArrayOutputStream();
        var pdf = new Document(PageSize.A4, SIDE_MARGIN, SIDE_MARGIN, 45, 55);
        PdfWriter writer = PdfWriter.getInstance(pdf, bytes);
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter current, Document page) {
                ColumnText.showTextAligned(current.getDirectContent(), Element.ALIGN_LEFT,
                        new Phrase(view.quotationNumber() + " · Revisión " + view.revisionNumber()
                                + " · Página " + current.getPageNumber(), new Font(font, 9, Font.NORMAL, ACCENT)),
                        page.left(), 29, 0);
            }
        });
        pdf.addTitle("CADPRINT · " + view.quotationNumber() + " · R" + view.revisionNumber());
        pdf.addCreator("CADPRINT Manager / " + version);
        pdf.open();
        try {
            add(pdf, "CADPRINT", new Font(font, 24, Font.BOLD, ACCENT));
            add(pdf, "Cotización " + view.quotationNumber() + "  |  Revisión " + view.revisionNumber(),
                    new Font(font, 15, Font.BOLD, INK));
            Font body = new Font(font, 10, Font.NORMAL, INK);
            add(pdf, "Emisión: " + DATE.format(view.issuedAt().atZone(ZONE)) + "   •   Vencimiento: "
                    + DATE.format(view.validUntil()), body);
            add(pdf, "Destinatario", new Font(font, 12, Font.BOLD, ACCENT));
            var recipient = view.recipient();
            for (String value : new String[]{recipient.name(), recipient.contact(), recipient.email(),
                    recipient.phone(), recipient.address()}) {
                add(pdf, value, body);
            }
            pdf.add(items(view, body, font));
            pdf.add(summary(view, body, font));
            if (view.commercialTerms() != null) {
                add(pdf, "Condiciones comerciales", new Font(font, 12, Font.BOLD, ACCENT));
                add(pdf, view.commercialTerms(), body);
            }
            add(pdf, "Emisor", new Font(font, 12, Font.BOLD, ACCENT));
            var issuer = view.issuer();
            for (String value : new String[]{issuer.name(), issuer.email(), issuer.phone(), issuer.address()}) {
                add(pdf, value, body);
            }
        } finally {
            pdf.close();
        }
        return bytes.toByteArray();
    }

    private PdfPTable items(CommercialView view, Font body, BaseFont font) {
        PdfPTable table = new PdfPTable(new float[]{38, 10, 18, 16, 18});
        table.setWidthPercentage(100);
        table.setSpacingBefore(15);
        table.setHeaderRows(1);
        table.setSplitRows(true);
        table.setSplitLate(true);
        for (String heading : new String[]{"Artículo", "Cant.", "Precio unit.", "Descuento", "Total línea"}) {
            PdfPCell cell = cell(heading, new Font(font, 9, Font.BOLD, Color.WHITE), Element.ALIGN_LEFT);
            cell.setBackgroundColor(ACCENT);
            table.addCell(cell);
        }
        for (var line : view.lines()) {
            String item = line.name() + (line.sku() == null ? "" : "\nSKU: " + line.sku())
                    + (line.description() == null ? "" : "\n" + line.description());
            table.addCell(cell(item, body, Element.ALIGN_LEFT));
            table.addCell(cell(Integer.toString(line.quantity()), body, Element.ALIGN_RIGHT));
            table.addCell(amountCell(line.unitPrice(), body, BODY_WIDTH * 0.18f));
            table.addCell(amountCell(line.discountAmount(), body, BODY_WIDTH * 0.16f));
            table.addCell(amountCell(line.netAmount(), body, BODY_WIDTH * 0.18f));
        }
        return table;
    }

    private PdfPTable summary(CommercialView view, Font body, BaseFont font) {
        var totals = view.totals();
        var table = new PdfPTable(new float[]{60, 40});
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setWidthPercentage(70);
        table.setSpacingBefore(12);
        table.setKeepTogether(true);
        total(table, "Bruto de artículos", totals.grossAmount(), body);
        total(table, "Descuentos de línea", totals.lineDiscountTotal().negate(), body);
        total(table, "Subtotal", totals.subtotal(), body);
        total(table, "Descuento general", totals.generalDiscountAmount().negate(), body);
        total(table, "Cargo de envío", totals.shippingAmount(), body);
        total(table, "Total general (GTQ)", totals.totalAmount(), new Font(font, 12, Font.BOLD, ACCENT));
        return table;
    }

    private void total(PdfPTable table, String label, BigDecimal value, Font font) {
        table.addCell(cell(label, font, Element.ALIGN_LEFT));
        table.addCell(amountCell(value, font, BODY_WIDTH * 0.70f * 0.40f));
    }

    private PdfPCell amountCell(BigDecimal value, Font font, float columnWidth) {
        String text = money(value);
        float size = font.getSize();
        float textWidth = font.getBaseFont().getWidthPoint(text, size);
        float available = columnWidth - 14;
        Font fitted = textWidth <= available ? font
                : new Font(font.getBaseFont(), size * available / textWidth, font.getStyle(), font.getColor());
        return cell(text, fitted, Element.ALIGN_RIGHT);
    }

    private PdfPCell cell(String text, Font font, int alignment) {
        var cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(6);
        cell.setBorderColor(new Color(224, 229, 233));
        return cell;
    }

    private void add(Document document, String value, Font font) {
        if (value != null && !value.isBlank()) {
            var paragraph = new Paragraph(value, font);
            paragraph.setSpacingAfter(4);
            document.add(paragraph);
        }
    }

    private String money(BigDecimal value) {
        var format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "Q " + format.format(value);
    }
}
