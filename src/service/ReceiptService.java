package service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfWriter;
import dao.MenuItemDAO;
import dao.OrderDAO;
import dao.OrderItemDAO;
import dao.PaymentDAO;
import dao.PaymentMethodDAO;
import dao.UserDAO;
import domain.MenuItem;
import domain.Order;
import domain.OrderItem;
import domain.Payment;
import domain.PaymentMethod;
import domain.User;
import domain.enums.DiscountType;
import domain.enums.OrderStatus;
import domain.enums.OrderType;
import service.exception.PersistenceException;
import service.exception.ValidationException;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a finalised order to a PDF receipt (FR-16, BR-20).
 *
 * <p><b>Reads back the stored figures; never recomputes.</b> Every number on the receipt — subtotal,
 * discount, tax rate, tax, total — is the value {@code OrderService} committed at finalisation, so a
 * reprint is byte-for-byte identical regardless of any later menu-price or tax-rate change (BR-20,
 * BR-09). Only Paid/Closed orders have a receipt.
 *
 * <p>No {@code javafx.*} (Principle I): the PDF is produced with OpenPDF and returned as bytes, so
 * the same service backs a desktop print and a future web download.
 */
public final class ReceiptService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final OrderDAO orderDAO;
    private final OrderItemDAO orderItemDAO;
    private final PaymentDAO paymentDAO;
    private final PaymentMethodDAO paymentMethodDAO;
    private final MenuItemDAO menuItemDAO;
    private final UserDAO userDAO;

    public ReceiptService(OrderDAO orderDAO, OrderItemDAO orderItemDAO, PaymentDAO paymentDAO,
                          PaymentMethodDAO paymentMethodDAO, MenuItemDAO menuItemDAO, UserDAO userDAO) {
        this.orderDAO = orderDAO;
        this.orderItemDAO = orderItemDAO;
        this.paymentDAO = paymentDAO;
        this.paymentMethodDAO = paymentMethodDAO;
        this.menuItemDAO = menuItemDAO;
        this.userDAO = userDAO;
    }

    /**
     * Produces the receipt PDF for a finalised order.
     *
     * @return the PDF as a byte array — identical on every call for the same order (BR-20)
     * @throws ValidationException if the order does not exist or is not finalised
     */
    public byte[] generate(int orderId) {
        Order order = orderDAO.findById(orderId);
        if (order == null) {
            throw new ValidationException("That order no longer exists.");
        }
        if (order.getStatus() != OrderStatus.PAID_CLOSED) {
            throw new ValidationException("A receipt is available only after the order is paid.");
        }

        List<OrderItem> lines = orderItemDAO.findByOrder(orderId);
        Payment payment = paymentDAO.findByOrder(orderId);
        PaymentMethod method = payment == null ? null : paymentMethodDAO.findById(payment.getMethodId());
        User cashier = userDAO.findById(order.getCreatedBy());
        Map<Integer, String> itemNames = itemNames(lines);

        // A narrow thermal-style receipt page.
        Rectangle page = new Rectangle(PageSize.A6.getWidth(), PageSize.A6.getHeight());
        Document document = new Document(page, 24, 24, 24, 24);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();
            write(document, order, lines, itemNames, payment, method, cashier);
            document.close();
        } catch (RuntimeException e) {
            throw new PersistenceException("The receipt could not be generated.", e);
        }
        return out.toByteArray();
    }

    private void write(Document doc, Order order, List<OrderItem> lines, Map<Integer, String> itemNames,
                       Payment payment, PaymentMethod method, User cashier) {
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 9);

        add(doc, centred("Golden Fork", title));
        add(doc, centred("Restaurant Management System", body));
        add(doc, centred("- - - - - - - - - - - - - - - - - -", body));

        add(doc, line("Receipt: " + order.getOrderNumber(), body));
        LocalDateTime when = order.getClosedAt() == null ? order.getCreatedAt() : order.getClosedAt();
        add(doc, line("Date: " + (when == null ? "" : STAMP.format(when)), body));
        String seat = order.getOrderType() == OrderType.TAKEAWAY
            ? "Takeaway"
            : "Table: " + (order.getTableId() == null ? "-" : order.getTableId());
        add(doc, line(seat, body));
        add(doc, line("Cashier: " + (cashier == null ? "-" : cashier.getFullName()), body));
        add(doc, line("- - - - - - - - - - - - - - - - - -", body));

        add(doc, line("Items", heading));
        for (OrderItem item : lines) {
            String name = itemNames.getOrDefault(item.getItemId(), "Item #" + item.getItemId());
            add(doc, line(item.getQuantity() + " x " + name + "  @ " + money(item.getUnitPrice())
                + "   " + money(item.getLineTotal()), body));
        }
        add(doc, line("- - - - - - - - - - - - - - - - - -", body));

        add(doc, line(pad("Subtotal", money(order.getSubtotal())), body));
        if (order.getDiscountType() != null && order.getDiscountType() != DiscountType.NONE) {
            add(doc, line(pad("Discount (" + discountLabel(order) + ")", "-" + money(order.getDiscountAmount())), body));
        }
        add(doc, line(pad("Tax (" + taxPercent(order.getTaxRate()) + "%)", money(order.getTaxAmount())), body));
        add(doc, line(pad("TOTAL", money(order.getTotal())), heading));
        add(doc, line("- - - - - - - - - - - - - - - - - -", body));

        add(doc, line("Paid: " + (method == null ? "-" : method.getMethodName()), body));
        if (payment != null && payment.getAmountTendered() != null) {
            add(doc, line(pad("Tendered", money(payment.getAmountTendered())), body));
            add(doc, line(pad("Change", money(payment.getChangeGiven())), body));
        }
        add(doc, centred("- - - - - - - - - - - - - - - - - -", body));
        add(doc, centred("Thank you!", body));
    }

    private Map<Integer, String> itemNames(List<OrderItem> lines) {
        Map<Integer, String> names = new HashMap<>();
        for (OrderItem line : lines) {
            if (!names.containsKey(line.getItemId())) {
                MenuItem item = menuItemDAO.findById(line.getItemId());
                names.put(line.getItemId(), item == null ? null : item.getName());
            }
        }
        return names;
    }

    private static String discountLabel(Order order) {
        if (order.getDiscountType() == DiscountType.PERCENTAGE) {
            return order.getDiscountValue().stripTrailingZeros().toPlainString() + "%";
        }
        return "fixed";
    }

    private static String taxPercent(BigDecimal rate) {
        if (rate == null) return "0";
        return rate.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString();
    }

    private static String money(BigDecimal v) {
        return v == null ? "0.00" : v.toPlainString();
    }

    /** Left label, right value on a ~34-char receipt line. */
    private static String pad(String label, String value) {
        int width = 30;
        int spaces = Math.max(1, width - label.length() - value.length());
        StringBuilder sb = new StringBuilder(label);
        for (int i = 0; i < spaces; i++) sb.append(' ');
        return sb.append(value).toString();
    }

    private static Paragraph line(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setLeading(12f);
        return p;
    }

    private static Paragraph centred(String text, Font font) {
        Paragraph p = line(text, font);
        p.setAlignment(Element.ALIGN_CENTER);
        return p;
    }

    private static void add(Document doc, Paragraph p) {
        try {
            doc.add(p);
        } catch (Exception e) {
            throw new PersistenceException("The receipt could not be written.", e);
        }
    }
}
