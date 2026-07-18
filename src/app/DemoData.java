package app;

import dao.ConnectionFactory;
import domain.DiningTable;
import domain.MenuCategory;
import domain.MenuItem;
import domain.PaymentMethod;
import domain.PurchaseOrder;
import domain.PurchaseOrderItem;
import domain.Reservation;
import domain.Staff;
import domain.StockItem;
import domain.Supplier;
import domain.Order;
import domain.enums.Availability;
import domain.enums.OrderType;
import domain.enums.Status;
import domain.enums.TableStatus;
import service.security.Session;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds the live database with presentable demo data: a 12-table floor, a real menu, stocked
 * inventory with ledger history, suppliers, staff, tonight's reservations, and today's orders
 * (some paid, some still open). Wipes existing operational rows first (users, roles, payment
 * methods, and system config are kept), so it is safe to re-run.
 *
 * <p>Run with: {@code java -cp "target/classes;<deps>" app.DemoData}
 */
public final class DemoData {

    private DemoData() {}

    public static void main(String[] args) throws Exception {
        AppContext context = AppContext.bootstrap();
        Session admin = context.authService().login("admin", "admin123");
        context.setSession(admin);

        wipe();
        System.out.println("[seed] old operational rows cleared");

        seedTables(context, admin);
        seedSuppliersAndStaff(context, admin);
        seedMenu(context, admin);
        seedInventory(context, admin);
        seedPurchasing(context, admin);
        seedReservations(context, admin);
        seedOrders(context, admin);

        System.out.println("[seed] done");
    }

    /** Clears operational data in FK order; keeps user_account, role, payment_method, system_config. */
    private static void wipe() throws Exception {
        ConnectionFactory connections = new ConnectionFactory();
        try (Connection c = connections.getConnection(); Statement s = c.createStatement()) {
            for (String table : new String[] {
                "payment", "order_item", "orders", "reservation",
                "stock_movement", "purchase_order_item", "purchase_order",
                "stock_item", "menu_item", "menu_category",
                "dining_table", "supplier", "staff"
            }) {
                s.executeUpdate("DELETE FROM " + table);
            }
        }
    }

    // ---------- floor ----------

    private static final Map<String, DiningTable> TABLES = new HashMap<>();

    private static void seedTables(AppContext ctx, Session admin) {
        int[] capacities = {2, 4, 4, 2, 4, 2, 6, 4, 2, 4, 6, 4};
        for (int i = 0; i < capacities.length; i++) {
            DiningTable t = new DiningTable();
            t.setLabel("T" + (i + 1));
            t.setCapacity(capacities[i]);
            TABLES.put(t.getLabel(), ctx.tableService().defineTable(admin, t));
        }
        System.out.println("[seed] 12 tables");
    }

    // ---------- suppliers + staff ----------

    private static Supplier freshFarms;
    private static Supplier deltaMeats;

    private static void seedSuppliersAndStaff(AppContext ctx, Session admin) {
        freshFarms = supplier(ctx, admin, "Fresh Farms Co", "Hany Mostafa", "01002214471", "orders@freshfarms.eg", "Obour wholesale market, Cairo");
        supplier(ctx, admin, "Nile Fisheries", "Walid Samir", "01118904432", "sales@nilefisheries.eg", "Ataba fish market, Cairo");
        deltaMeats = supplier(ctx, admin, "Delta Meats", "Omar Farouk", "01225558830", "supply@deltameats.eg", "Shubra, Cairo");
        supplier(ctx, admin, "Cairo Grocers Ltd", "Nadia Kamel", "01099887712", "hello@cairogrocers.eg", "Downtown, Cairo");

        staff(ctx, admin, "Ahmed Ali", "Head Cashier", "01012345601", "a.ali@goldenfork.eg");
        staff(ctx, admin, "Mona Hassan", "Restaurant Manager", "01012345602", "m.hassan@goldenfork.eg");
        staff(ctx, admin, "Karim Nabil", "Head Chef", "01012345603", "k.nabil@goldenfork.eg");
        staff(ctx, admin, "Sara Fawzy", "Floor Supervisor", "01012345604", "s.fawzy@goldenfork.eg");
        staff(ctx, admin, "Youssef Adel", "Waiter", "01012345605", "y.adel@goldenfork.eg");
        staff(ctx, admin, "Laila Sherif", "Waiter", "01012345606", "l.sherif@goldenfork.eg");
        System.out.println("[seed] 4 suppliers, 6 staff");
    }

    private static Supplier supplier(AppContext ctx, Session admin, String name, String contact,
                                     String phone, String email, String address) {
        Supplier s = new Supplier();
        s.setName(name);
        s.setContactPerson(contact);
        s.setPhone(phone);
        s.setEmail(email);
        s.setAddress(address);
        s.setStatus(Status.ACTIVE);
        return ctx.supplierService().save(admin, s);
    }

    private static void staff(AppContext ctx, Session admin, String name, String position,
                              String phone, String email) {
        Staff s = new Staff();
        s.setFullName(name);
        s.setPosition(position);
        s.setPhone(phone);
        s.setEmail(email);
        s.setStatus(Status.ACTIVE);
        ctx.staffService().save(admin, s);
    }

    // ---------- menu ----------

    private static final Map<String, MenuItem> MENU = new HashMap<>();

    private static void seedMenu(AppContext ctx, Session admin) {
        Map<String, String[][]> menu = new LinkedHashMap<>();
        menu.put("Starters", new String[][] {
            {"Mezze Platter", "120", "Hummus, baba ghanoush, tahina, and warm baladi bread"},
            {"Filo Cheese Rolls", "75", "Crisp filo, white cheese, mint"},
            {"Lentil Soup", "65", "With toasted bread and lemon"},
        });
        menu.put("Grills & Mains", new String[][] {
            {"Lamb Tagine", "240", "Slow-cooked with prunes and almonds"},
            {"Mixed Grill", "320", "Kofta, shish tawook, and lamb chops"},
            {"Molokhia with Chicken", "160", "Served with vermicelli rice"},
            {"Koshari", "95", "The classic, with extra crispy onions"},
            {"Stuffed Pigeon", "210", "Freekeh stuffing, served whole"},
        });
        menu.put("Seafood", new String[][] {
            {"Grilled Sea Bass", "280", "Whole fish, chermoula, charred lemon"},
            {"Shrimp Tagen", "290", "Baked in tomato and garlic"},
        });
        menu.put("Desserts", new String[][] {
            {"Om Ali", "85", "Baked with cream and roasted nuts"},
            {"Basbousa", "60", "Semolina cake, clotted cream"},
            {"Rice Pudding", "55", "Cinnamon and pistachio"},
        });
        menu.put("Beverages", new String[][] {
            {"Fresh Orange Juice", "45", null},
            {"Hibiscus Iced Tea", "40", null},
            {"Mint Lemonade", "42", null},
            {"Turkish Coffee", "35", null},
        });

        int order = 1;
        for (Map.Entry<String, String[][]> entry : menu.entrySet()) {
            MenuCategory category = new MenuCategory();
            category.setName(entry.getKey());
            category.setDisplayOrder(order++);
            category = ctx.menuService().saveCategory(admin, category);
            for (String[] row : entry.getValue()) {
                MenuItem item = new MenuItem();
                item.setCategoryId(category.getCategoryId());
                item.setName(row[0]);
                item.setPrice(new BigDecimal(row[1]));
                item.setAvailability(Availability.AVAILABLE);
                item.setDescription(row[2]);
                MENU.put(row[0], ctx.menuService().saveItem(admin, item));
            }
        }
        System.out.println("[seed] menu: " + menu.size() + " categories, " + MENU.size() + " items");
    }

    // ---------- inventory ----------

    private static final Map<String, StockItem> STOCK = new HashMap<>();

    private static void seedInventory(AppContext ctx, Session admin) {
        // name, unit, reorder, opening, adjustment (consumption already recorded today)
        Object[][] items = {
            {"Tomatoes", "kg", "5", "10", "-7"},        // 3.0 on hand -> LOW
            {"Olive Oil", "L", "2", "3", "-2"},         // 1.0 on hand -> LOW
            {"Sea Bass (whole)", "kg", "6", "14", "-2"},
            {"Lamb (leg)", "kg", "5", "0", null},       // stocked via the received PO below
            {"Chicken Breast", "kg", "8", "0", null},   // stocked via the received PO below
            {"Basmati Rice", "kg", "10", "25", "-1"},
            {"Lemons", "kg", "4", "6", "-1.5"},
            {"Mint", "bunch", "10", "8", "-2"},         // 6.0 on hand -> LOW
            {"Filo Pastry", "pack", "4", "9", null},
            {"Baladi Bread", "dozen", "6", "12", "-3"},
        };
        for (Object[] row : items) {
            StockItem item = new StockItem();
            item.setName((String) row[0]);
            item.setUnitOfMeasure((String) row[1]);
            item.setReorderLevel(new BigDecimal((String) row[2]));
            item.setQuantityOnHand(new BigDecimal((String) row[3]));
            item.setStatus(Status.ACTIVE);
            item = ctx.inventoryService().save(admin, item);
            STOCK.put(item.getName(), item);
            if (row[4] != null) {
                ctx.inventoryService().adjustStock(admin, item.getStockItemId(), new BigDecimal((String) row[4]));
            }
        }
        System.out.println("[seed] " + items.length + " stock items with ledger history");
    }

    // ---------- purchasing ----------

    private static void seedPurchasing(AppContext ctx, Session admin) {
        // A delivery received earlier today: puts Receipt rows in the ledger and stocks the meats.
        PurchaseOrder received = new PurchaseOrder();
        received.setSupplierId(deltaMeats.getSupplierId());
        received.setExpectedDate(LocalDate.now());
        received = ctx.purchasingService().createPO(admin, received, java.util.Arrays.asList(
            poLine(STOCK.get("Lamb (leg)"), "12", "260"),
            poLine(STOCK.get("Chicken Breast"), "20", "95")));
        Map<Integer, BigDecimal> receive = new HashMap<>();
        for (PurchaseOrderItem line : ctx.purchasingService().listLines(received.getPoId())) {
            receive.put(line.getPoItemId(),
                line.getStockItemId() == STOCK.get("Lamb (leg)").getStockItemId()
                    ? new BigDecimal("8.5") : new BigDecimal("15"));
        }
        ctx.purchasingService().receiveDelivery(admin, received.getPoId(), receive);

        // A pending order awaiting delivery: appears in the Receive list.
        PurchaseOrder pending = new PurchaseOrder();
        pending.setSupplierId(freshFarms.getSupplierId());
        pending.setExpectedDate(LocalDate.now().plusDays(2));
        ctx.purchasingService().createPO(admin, pending, java.util.Arrays.asList(
            poLine(STOCK.get("Tomatoes"), "20", "18"),
            poLine(STOCK.get("Lemons"), "10", "12"),
            poLine(STOCK.get("Olive Oil"), "6", "210")));
        System.out.println("[seed] 1 received + 1 pending purchase order");
    }

    private static PurchaseOrderItem poLine(StockItem item, String qty, String cost) {
        PurchaseOrderItem line = new PurchaseOrderItem();
        line.setStockItemId(item.getStockItemId());
        line.setOrderedQty(new BigDecimal(qty));
        line.setUnitCost(new BigDecimal(cost));
        return line;
    }

    // ---------- reservations ----------

    /** Seated in {@link #seedOrders} — the order must open while T5 is still Reserved (BR-14). */
    private static Reservation adelBooking;

    private static void seedReservations(AppContext ctx, Session admin) {
        // Evening slots must be in the future (BR): tonight if it's still early, else tomorrow.
        LocalDate day = LocalDateTime.now().getHour() < 16 ? LocalDate.now() : LocalDate.now().plusDays(1);

        booking(ctx, admin, "K. Mahmoud", "T3", day, "19:00", 4, "01002214471", null);            // Booked, holds T3
        overCapacityBooking(ctx, admin, "N. Wael", "T8", day, "20:00", 6, "01228879930");         // Booked, holds T8
        booking(ctx, admin, "L. Sami", "T6", day, "19:30", 2, "01225558890", "l.sami@mail.com");  // Booked, holds T6
        adelBooking = booking(ctx, admin, "H. Adel", "T5", day, "18:30", 3, "01014432210", null);
        Reservation sobhy = booking(ctx, admin, "R. Sobhy", "T1", day, "21:00", 2, "01119902277", null);
        Reservation nabil = booking(ctx, admin, "F. Nabil", "T9", day, "18:00", 2, "01007765521", null);
        Reservation ghali = booking(ctx, admin, "S. Ghali", "T7", day, "21:30", 4, "01063348890", null);

        ctx.reservationService().seat(admin, sobhy.getReservationId());
        ctx.reservationService().complete(admin, sobhy.getReservationId());  // Completed; T1 needs cleaning
        ctx.tableService().changeStatus(admin, TABLES.get("T1").getTableId(), TableStatus.FREE);
        ctx.reservationService().markNoShow(admin, nabil.getReservationId()); // No-Show; T9 freed
        ctx.reservationService().cancel(admin, ghali.getReservationId());     // Cancelled; T7 freed
        System.out.println("[seed] 7 reservations across the lifecycle");
    }

    private static Reservation booking(AppContext ctx, Session admin, String name, String table,
                                       LocalDate day, String time, int party, String phone, String email) {
        return booking(ctx, admin, name, table, day, time, party, phone, email, false);
    }

    /** A knowingly over-capacity booking — the "confirm anyway" path a host sometimes takes. */
    private static void overCapacityBooking(AppContext ctx, Session admin, String name, String table,
                                            LocalDate day, String time, int party, String phone) {
        booking(ctx, admin, name, table, day, time, party, phone, null, true);
    }

    private static Reservation booking(AppContext ctx, Session admin, String name, String table,
                                       LocalDate day, String time, int party, String phone, String email,
                                       boolean overrideCapacity) {
        Reservation r = new Reservation();
        r.setTableId(TABLES.get(table).getTableId());
        r.setCustomerName(name);
        r.setContactPhone(phone);
        r.setContactEmail(email);
        r.setReservationDatetime(LocalDateTime.of(day, LocalTime.parse(time)));
        r.setPartySize(party);
        return ctx.reservationService().create(admin, r, overrideCapacity);
    }

    // ---------- orders ----------

    private static void seedOrders(AppContext ctx, Session admin) {
        List<PaymentMethod> methods = ctx.systemConfigService().listPaymentMethods(admin);
        int cash = methods.get(0).getMethodId();
        int card = methods.size() > 1 ? methods.get(1).getMethodId() : cash;

        // Paid earlier today — these feed the dashboard's sales figure and the reports.
        paidOrder(ctx, admin, "T4", cash, new BigDecimal("500"),
            line("Koshari", 2), line("Fresh Orange Juice", 2));
        paidOrder(ctx, admin, "T9", card, null,
            line("Grilled Sea Bass", 1), line("Mezze Platter", 1), line("Hibiscus Iced Tea", 2));
        paidOrder(ctx, admin, "T10", cash, new BigDecimal("1200"),
            line("Mixed Grill", 2), line("Om Ali", 2), line("Mint Lemonade", 2));
        paidTakeaway(ctx, admin, card,
            line("Molokhia with Chicken", 1), line("Turkish Coffee", 1));

        // Tables T4 and T9 have been cleaned since; T10 is still being turned over.
        ctx.tableService().changeStatus(admin, TABLES.get("T4").getTableId(), TableStatus.FREE);
        ctx.tableService().changeStatus(admin, TABLES.get("T9").getTableId(), TableStatus.FREE);

        // Still open — the live floor. T5: the order opens against the Reserved table (allowed by
        // BR-14, moves it Occupied), then H. Adel's party is marked Seated.
        openOrder(ctx, admin, "T2", line("Lamb Tagine", 1), line("Mixed Grill", 1), line("Fresh Orange Juice", 2));
        openOrder(ctx, admin, "T5", line("Grilled Sea Bass", 1), line("Filo Cheese Rolls", 1), line("Hibiscus Iced Tea", 2));
        ctx.reservationService().seat(admin, adelBooking.getReservationId());
        openOrder(ctx, admin, "T7", line("Mixed Grill", 2), line("Koshari", 1), line("Mint Lemonade", 3));
        openOrder(ctx, admin, "T11", line("Stuffed Pigeon", 2), line("Lentil Soup", 2), line("Turkish Coffee", 4));
        Order takeaway = ctx.orderService().openOrder(admin, OrderType.TAKEAWAY, null);
        addLines(ctx, admin, takeaway, line("Om Ali", 2), line("Basbousa", 1));
        System.out.println("[seed] 4 paid orders today, 5 open orders");
    }

    private static Object[] line(String item, int qty) {
        return new Object[] {item, qty};
    }

    private static void paidOrder(AppContext ctx, Session admin, String table, int methodId,
                                  BigDecimal tendered, Object[]... lines) {
        Order order = ctx.orderService().openOrder(admin, OrderType.DINE_IN, TABLES.get(table).getTableId());
        addLines(ctx, admin, order, lines);
        ctx.orderService().finalise(admin, order.getOrderId(), methodId, tendered);
    }

    private static void paidTakeaway(AppContext ctx, Session admin, int methodId, Object[]... lines) {
        Order order = ctx.orderService().openOrder(admin, OrderType.TAKEAWAY, null);
        addLines(ctx, admin, order, lines);
        ctx.orderService().finalise(admin, order.getOrderId(), methodId, null);
    }

    private static void openOrder(AppContext ctx, Session admin, String table, Object[]... lines) {
        Order order = ctx.orderService().openOrder(admin, OrderType.DINE_IN, TABLES.get(table).getTableId());
        addLines(ctx, admin, order, lines);
    }

    private static void addLines(AppContext ctx, Session admin, Order order, Object[]... lines) {
        for (Object[] l : lines) {
            ctx.orderService().addLine(admin, order.getOrderId(),
                MENU.get((String) l[0]).getItemId(), (Integer) l[1]);
        }
    }
}
