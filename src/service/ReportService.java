package service;

import dao.LoginEventDAO;
import dao.MenuCategoryDAO;
import dao.MenuItemDAO;
import dao.OrderDAO;
import dao.OrderItemDAO;
import dao.StockItemDAO;
import dao.UserDAO;
import domain.LoginEvent;
import domain.MenuCategory;
import domain.MenuItem;
import domain.Order;
import domain.OrderItem;
import domain.StockItem;
import domain.User;
import domain.enums.LoginEventType;
import domain.enums.OrderType;
import domain.enums.Status;
import domain.report.ReportDocument;
import service.exception.ValidationException;
import service.security.Permission;
import service.security.RbacGuard;
import service.security.Session;
import util.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Management reports (FR-27, FR-28, FR-29; BR-25, BR-30).
 *
 * <p>Guarded by {@code VIEW_REPORTS} (Manager/Administrator) — a Cashier is refused even if the UI
 * were bypassed (BR-03). Every figure aggregates <b>finalised</b> orders only and derives from the
 * order's <b>stored</b> figures, never today's menu prices, so a report reconciles to the bills it
 * summarises (BR-30, SC-007).
 *
 * <p>Each method returns a neutral {@link ReportDocument} that the view renders and the exporter
 * reproduces unchanged (FR-30). No {@code javafx.*} (Principle I).
 */
public final class ReportService {

    private final OrderDAO orderDAO;
    private final OrderItemDAO orderItemDAO;
    private final MenuItemDAO menuItemDAO;
    private final MenuCategoryDAO menuCategoryDAO;
    private final StockItemDAO stockItemDAO;
    private final LoginEventDAO loginEventDAO;
    private final UserDAO userDAO;

    public ReportService(OrderDAO orderDAO, OrderItemDAO orderItemDAO, MenuItemDAO menuItemDAO,
                         MenuCategoryDAO menuCategoryDAO, StockItemDAO stockItemDAO,
                         LoginEventDAO loginEventDAO, UserDAO userDAO) {
        this.orderDAO = orderDAO;
        this.orderItemDAO = orderItemDAO;
        this.menuItemDAO = menuItemDAO;
        this.menuCategoryDAO = menuCategoryDAO;
        this.stockItemDAO = stockItemDAO;
        this.loginEventDAO = loginEventDAO;
        this.userDAO = userDAO;
    }

    // --- FR-27: sales -----------------------------------------------------------

    /**
     * Sales over an inclusive day range (FR-27, BR-30): totals, count, average order value, tax,
     * discounts, and per-item / per-category quantity and revenue — all from finalised orders.
     *
     * @param typeFilter restrict to one order type, or {@code null} for both
     * @throws ValidationException if start is after end
     */
    public ReportDocument salesReport(Session session, LocalDate start, LocalDate end, OrderType typeFilter) {
        RbacGuard.require(session, Permission.VIEW_REPORTS);
        requireOrderedRange(start, end);

        List<Order> orders = new ArrayList<>();
        for (Order order : orderDAO.findFinalisedByRange(start.atStartOfDay(), end.plusDays(1).atStartOfDay())) {
            if (typeFilter == null || order.getOrderType() == typeFilter) {
                orders.add(order);
            }
        }

        ReportDocument doc = new ReportDocument("Sales Report");
        doc.addParameter("From", start.toString());
        doc.addParameter("To", end.toString());
        doc.addParameter("Order type", typeFilter == null ? "All" : typeFilter.dbValue());

        BigDecimal totalSales = Money.ZERO;
        BigDecimal taxCollected = Money.ZERO;
        BigDecimal discountsGiven = Money.ZERO;
        Map<Integer, long[]> qtyByItem = new LinkedHashMap<>();   // itemId -> [qty]
        Map<Integer, BigDecimal> revByItem = new LinkedHashMap<>();
        Map<Integer, long[]> qtyByCategory = new LinkedHashMap<>();
        Map<Integer, BigDecimal> revByCategory = new LinkedHashMap<>();

        for (Order order : orders) {
            totalSales = Money.add(totalSales, nz(order.getTotal()));
            taxCollected = Money.add(taxCollected, nz(order.getTaxAmount()));
            discountsGiven = Money.add(discountsGiven, nz(order.getDiscountAmount()));

            for (OrderItem line : orderItemDAO.findByOrder(order.getOrderId())) {
                accumulate(qtyByItem, revByItem, line.getItemId(), line.getQuantity(), nz(line.getLineTotal()));
                Integer categoryId = categoryOf(line.getItemId());
                if (categoryId != null) {
                    accumulate(qtyByCategory, revByCategory, categoryId, line.getQuantity(), nz(line.getLineTotal()));
                }
            }
        }

        if (orders.isEmpty()) {
            doc.addSummary("No finalised orders in this range.");
            return doc;
        }

        BigDecimal aov = Money.round(totalSales.divide(BigDecimal.valueOf(orders.size()), Money.SCALE, Money.ROUNDING));
        doc.addSummary("Total sales: " + totalSales.toPlainString());
        doc.addSummary("Orders: " + orders.size());
        doc.addSummary("Average order value: " + aov.toPlainString());
        doc.addSummary("Tax collected: " + taxCollected.toPlainString());
        doc.addSummary("Discounts given: " + discountsGiven.toPlainString());

        ReportDocument.Section items = doc.addSection("By item", Arrays.asList("Item", "Qty", "Revenue"));
        for (Map.Entry<Integer, BigDecimal> entry : revByItem.entrySet()) {
            items.addRow(Arrays.asList(
                itemName(entry.getKey()),
                String.valueOf(qtyByItem.get(entry.getKey())[0]),
                entry.getValue().toPlainString()));
        }

        ReportDocument.Section cats = doc.addSection("By category", Arrays.asList("Category", "Qty", "Revenue"));
        for (Map.Entry<Integer, BigDecimal> entry : revByCategory.entrySet()) {
            cats.addRow(Arrays.asList(
                categoryName(entry.getKey()),
                String.valueOf(qtyByCategory.get(entry.getKey())[0]),
                entry.getValue().toPlainString()));
        }

        return doc;
    }

    // --- FR-28: inventory -------------------------------------------------------

    /** Active stock with a low-stock indicator (FR-28, BR-25). Quantities are live at generation. */
    public ReportDocument inventoryReport(Session session, boolean belowReorderOnly) {
        RbacGuard.require(session, Permission.VIEW_REPORTS);

        ReportDocument doc = new ReportDocument("Inventory Report");
        doc.addParameter("Scope", belowReorderOnly ? "Low stock only" : "All active items");

        List<StockItem> items = belowReorderOnly ? stockItemDAO.findLowStock() : stockItemDAO.findActive();
        ReportDocument.Section section = doc.addSection("Stock",
            Arrays.asList("Item", "Unit", "On hand", "Reorder", "Low?"));

        int lowCount = 0;
        for (StockItem item : items) {
            boolean low = item.getStatus() == Status.ACTIVE
                && item.getQuantityOnHand().compareTo(item.getReorderLevel()) <= 0;
            if (low) lowCount++;
            section.addRow(Arrays.asList(
                item.getName(),
                item.getUnitOfMeasure(),
                plain(item.getQuantityOnHand()),
                plain(item.getReorderLevel()),
                low ? "LOW" : ""));
        }

        doc.addSummary("Items listed: " + items.size());
        doc.addSummary("At or below reorder level: " + lowCount);
        return doc;
    }

    // --- FR-29: staff activity --------------------------------------------------

    /**
     * Per-user activity over an inclusive day range (FR-29): logins, orders processed, sales handled,
     * and discounts applied — the order figures from finalised orders only.
     *
     * @throws ValidationException if start is after end
     */
    public ReportDocument staffActivityReport(Session session, LocalDate start, LocalDate end) {
        RbacGuard.require(session, Permission.VIEW_REPORTS);
        requireOrderedRange(start, end);

        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay();

        // Orders processed per user, from finalised orders in range.
        Map<Integer, int[]> ordersByUser = new LinkedHashMap<>();     // userId -> [count]
        Map<Integer, BigDecimal> salesByUser = new LinkedHashMap<>();
        Map<Integer, BigDecimal> discountsByUser = new LinkedHashMap<>();
        for (Order order : orderDAO.findFinalisedByRange(from, to)) {
            int uid = order.getCreatedBy();
            ordersByUser.computeIfAbsent(uid, k -> new int[1])[0]++;
            salesByUser.merge(uid, nz(order.getTotal()), Money::add);
            discountsByUser.merge(uid, nz(order.getDiscountAmount()), Money::add);
        }

        ReportDocument doc = new ReportDocument("Staff Activity Report");
        doc.addParameter("From", start.toString());
        doc.addParameter("To", end.toString());

        ReportDocument.Section section = doc.addSection("By user",
            Arrays.asList("User", "Logins", "Orders", "Sales handled", "Discounts applied"));

        for (User user : userDAO.findAll()) {
            int logins = 0;
            for (LoginEvent event : loginEventDAO.findByUserAndRange(user.getUserId(), from, to)) {
                if (event.getEventType() == LoginEventType.LOGIN) logins++;
            }
            int orders = ordersByUser.containsKey(user.getUserId()) ? ordersByUser.get(user.getUserId())[0] : 0;
            BigDecimal sales = salesByUser.getOrDefault(user.getUserId(), Money.ZERO);
            BigDecimal discounts = discountsByUser.getOrDefault(user.getUserId(), Money.ZERO);

            // Skip users with no activity in the range to keep the report focused.
            if (logins == 0 && orders == 0) continue;

            section.addRow(Arrays.asList(
                user.getUsername(),
                String.valueOf(logins),
                String.valueOf(orders),
                sales.toPlainString(),
                discounts.toPlainString()));
        }

        if (section.getRows().isEmpty()) {
            doc.addSummary("No staff activity in this range.");
        }
        return doc;
    }

    // --- helpers ----------------------------------------------------------------

    private final Map<Integer, MenuItem> itemCache = new LinkedHashMap<>();
    private final Map<Integer, String> categoryNameCache = new LinkedHashMap<>();

    private static void requireOrderedRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new ValidationException("Choose a start and end date.");
        }
        if (start.isAfter(end)) {
            throw new ValidationException("The start date must be on or before the end date.");
        }
    }

    private static void accumulate(Map<Integer, long[]> qty, Map<Integer, BigDecimal> revenue,
                                   int key, int q, BigDecimal r) {
        qty.computeIfAbsent(key, k -> new long[1])[0] += q;
        revenue.merge(key, r, Money::add);
    }

    private MenuItem item(int itemId) {
        return itemCache.computeIfAbsent(itemId, menuItemDAO::findById);
    }

    private String itemName(int itemId) {
        MenuItem item = item(itemId);
        return item == null ? "Item #" + itemId : item.getName();
    }

    private Integer categoryOf(int itemId) {
        MenuItem item = item(itemId);
        return item == null ? null : item.getCategoryId();
    }

    private String categoryName(int categoryId) {
        return categoryNameCache.computeIfAbsent(categoryId, id -> {
            MenuCategory category = menuCategoryDAO.findById(id);
            return category == null ? "Category #" + id : category.getName();
        });
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? Money.ZERO : v;
    }

    private static String plain(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }
}
