package com.brightloop.modules.invoice;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Generates a valid, self-contained PDF 1.4 document for an Invoice using standard Type1 Helvetica. */
public final class InvoicePdfGenerator {
  private InvoicePdfGenerator() {}

  public static byte[] generate(Invoice inv) {
    List<String> lines = new ArrayList<>();
    lines.add("BRIGHTLOOP SUBSCRIPTION INVOICE");
    lines.add("======================================================================");
    lines.add("Invoice Number:    " + safe(inv.invoiceNumber));
    lines.add("Status:            " + safe(inv.state.name()));
    lines.add("Issue Date:        " + safe(inv.issueDate));
    lines.add("Due Date:          " + safe(inv.dueDate));
    lines.add("Subscription:      " + safe(inv.subscriptionNumber) + " (" + safe(inv.planName) + ")");
    lines.add(
        "Billing Period:    "
            + safe(inv.billingPeriodStart)
            + " to "
            + safe(inv.billingPeriodEnd)
            + " ("
            + safe(inv.billingPeriod)
            + ")");
    lines.add("Payment Terms:     " + safe(inv.paymentTerms));
    lines.add("----------------------------------------------------------------------");
    lines.add("BILL TO:");
    lines.add("Customer:          " + safe(inv.customerName));
    lines.add("Email:             " + safe(inv.customerEmail));
    if (inv.customerCompany != null && !inv.customerCompany.isBlank())
      lines.add("Company:           " + safe(inv.customerCompany));
    if (inv.billingAddress != null && !inv.billingAddress.isBlank())
      lines.add("Billing Address:   " + safe(inv.billingAddress));
    lines.add("----------------------------------------------------------------------");
    lines.add(
        String.format(
            "%-26s %-6s %10s %10s %8s %10s",
            "ITEM", "QTY", "UNIT", "DISCOUNT", "TAX %", "AMOUNT"));
    lines.add("----------------------------------------------------------------------");
    for (var l : inv.lines) {
      String item =
          l.productName + (l.variantName != null && !l.variantName.isBlank() ? " (" + l.variantName + ")" : "");
      if (item.length() > 26) item = item.substring(0, 23) + "...";
      lines.add(
          String.format(
              "%-26s %-6d %10s %10s %7s%% %10s",
              safe(item),
              l.quantity,
              l.unitPrice.toPlainString(),
              l.discountAmount.toPlainString(),
              l.taxRate.toPlainString(),
              l.amount.toPlainString()));
    }
    lines.add("----------------------------------------------------------------------");
    lines.add(String.format("%54s %15s", "Subtotal:", inv.subtotal.toPlainString()));
    lines.add(
        String.format(
            "%54s -%14s",
            "Discount" + (inv.discountName != null ? " (" + inv.discountName + "):" : ":"),
            inv.discountAmount.toPlainString()));
    lines.add(String.format("%54s %15s", "Tax Amount:", inv.taxAmount.toPlainString()));
    lines.add(String.format("%54s %15s", "Invoice Total:", inv.total.toPlainString()));
    var paid = inv.amountPaid != null ? inv.amountPaid : java.math.BigDecimal.ZERO.setScale(2);
    var balance =
        (inv.state == InvoiceState.CANCELLED || inv.state == InvoiceState.PAID)
            ? java.math.BigDecimal.ZERO.setScale(2)
            : inv.total.subtract(paid).max(java.math.BigDecimal.ZERO.setScale(2));
    lines.add(String.format("%54s %15s", "Amount Paid:", paid.toPlainString()));
    lines.add("======================================================================");
    lines.add(String.format("%54s %15s", "TOTAL DUE:", balance.toPlainString()));
    lines.add("======================================================================");

    StringBuilder stream = new StringBuilder();
    stream.append("BT\n/F1 10 Tf\n14 TL\n45 750 Td\n");
    for (String line : lines) {
      stream.append("(").append(escapePdf(line)).append(") Tj\nT*\n");
    }
    stream.append("ET\n");
    byte[] streamBytes = stream.toString().getBytes(StandardCharsets.US_ASCII);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    List<Integer> offsets = new ArrayList<>();
    writeAscii(out, "%PDF-1.4\n");

    offsets.add(out.size());
    writeAscii(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

    offsets.add(out.size());
    writeAscii(out, "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");

    offsets.add(out.size());
    writeAscii(
        out,
        "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1"
            + " 4 0 R >> >> /Contents 5 0 R >>\nendobj\n");

    offsets.add(out.size());
    writeAscii(
        out,
        "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>\nendobj\n");

    offsets.add(out.size());
    writeAscii(out, "5 0 obj\n<< /Length " + streamBytes.length + " >>\nstream\n");
    out.writeBytes(streamBytes);
    writeAscii(out, "\nendstream\nendobj\n");

    int xrefOffset = out.size();
    writeAscii(out, "xref\n0 6\n0000000000 65535 f \n");
    for (int off : offsets) {
      writeAscii(out, String.format("%010d 00000 n \n", off));
    }
    writeAscii(
        out,
        "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xrefOffset + "\n%%EOF\n");
    return out.toByteArray();
  }

  private static void writeAscii(ByteArrayOutputStream out, String s) {
    out.writeBytes(s.getBytes(StandardCharsets.US_ASCII));
  }

  private static String safe(Object o) {
    if (o == null) return "";
    return o.toString().replaceAll("[^\\x20-\\x7E]", "?");
  }

  private static String escapePdf(String s) {
    return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
  }
}
