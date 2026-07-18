package controller;

import app.AppContext;
import app.ContextAware;
import app.Navigator;
import domain.MenuCategory;
import domain.MenuItem;
import domain.enums.Availability;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import service.exception.RmsException;
import service.exception.ValidationException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Menu categories, items, prices, and availability (T041, FR-05…FR-07) over {@code MenuService} —
 * never the DAOs. Rules live in the service; this maps its typed exceptions to messages.
 */
public final class MenuController implements ContextAware {

    @FXML private TableView<MenuCategory> categoryTable;
    @FXML private TableColumn<MenuCategory, String> categoryNameColumn;
    @FXML private TableColumn<MenuCategory, String> categoryOrderColumn;
    @FXML private TextField categoryNameField;
    @FXML private TextField categoryOrderField;
    @FXML private Button saveCategoryButton;
    @FXML private Button newCategoryButton;
    @FXML private Button deleteCategoryButton;

    @FXML private TableView<MenuItem> itemTable;
    @FXML private TableColumn<MenuItem, String> itemNameColumn;
    @FXML private TableColumn<MenuItem, String> itemCategoryColumn;
    @FXML private TableColumn<MenuItem, String> itemPriceColumn;
    @FXML private TableColumn<MenuItem, String> itemAvailabilityColumn;
    @FXML private Label itemFilterLabel;
    @FXML private Button toggleAvailabilityButton;
    @FXML private Button deleteItemButton;

    @FXML private Label itemFormTitle;
    @FXML private TextField itemNameField;
    @FXML private ComboBox<MenuCategory> itemCategoryCombo;
    @FXML private TextField itemPriceField;
    @FXML private ComboBox<Availability> itemAvailabilityCombo;
    @FXML private TextArea itemDescriptionField;
    @FXML private Button saveItemButton;
    @FXML private Button newItemButton;

    @FXML private Label messageLabel;
    @FXML private Button refreshButton;

    private AppContext context;

    private final ObservableList<MenuCategory> categories = FXCollections.observableArrayList();
    private final ObservableList<MenuItem> items = FXCollections.observableArrayList();

    /** category_id → name, so the item table can show a category without a per-row lookup. */
    private final Map<Integer, String> categoryNames = new HashMap<>();

    private MenuCategory editingCategory;
    private MenuItem editingItem;

    @FXML
    private void initialize() {
        categoryNameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getName()));
        categoryOrderColumn.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().getDisplayOrder() == null ? "—" : String.valueOf(cell.getValue().getDisplayOrder())));
        categoryTable.setItems(categories);

        itemNameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getName()));
        itemCategoryColumn.setCellValueFactory(cell -> new SimpleStringProperty(
            categoryNames.getOrDefault(cell.getValue().getCategoryId(), "—")));
        itemPriceColumn.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().getPrice() == null ? "" : cell.getValue().getPrice().toPlainString()));
        itemAvailabilityColumn.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().getAvailability().dbValue()));
        itemTable.setItems(items);

        itemCategoryCombo.setItems(categories);
        itemCategoryCombo.setButtonCell(categoryCell());
        itemCategoryCombo.setCellFactory(list -> categoryCell());
        itemAvailabilityCombo.setItems(FXCollections.observableArrayList(Availability.values()));

        categoryTable.getSelectionModel().selectedItemProperty()
            .addListener((observable, old, selected) -> onCategorySelected(selected));
        itemTable.getSelectionModel().selectedItemProperty()
            .addListener((observable, old, selected) -> showItemInForm(selected));

        saveCategoryButton.setOnAction(event -> saveCategory());
        newCategoryButton.setOnAction(event -> startNewCategory());
        deleteCategoryButton.setOnAction(event -> deleteCategory());

        saveItemButton.setOnAction(event -> saveItem());
        newItemButton.setOnAction(event -> startNewItem());
        toggleAvailabilityButton.setOnAction(event -> toggleAvailability());
        deleteItemButton.setOnAction(event -> deleteItem());

        refreshButton.setOnAction(event -> reload());
    }

    private static ListCell<MenuCategory> categoryCell() {
        return new ListCell<MenuCategory>() {
            @Override
            protected void updateItem(MenuCategory category, boolean empty) {
                super.updateItem(category, empty);
                setText(empty || category == null ? null : category.getName());
            }
        };
    }

    @Override
    public void init(AppContext context, Navigator navigator) {
        this.context = context;
        startNewCategory();
        startNewItem();
        reload();
    }

    private void reload() {
        run(() -> {
            categories.setAll(context.menuService().listCategories());
            categoryNames.clear();
            for (MenuCategory category : categories) {
                categoryNames.put(category.getCategoryId(), category.getName());
            }
            reloadItems();
        });
    }

    private void reloadItems() {
        MenuCategory filter = categoryTable.getSelectionModel().getSelectedItem();
        if (filter == null) {
            // Menu order, not alphabetical soup: cluster items by category, then by name.
            java.util.Map<Integer, Integer> position = new HashMap<>();
            for (int i = 0; i < categories.size(); i++) {
                position.put(categories.get(i).getCategoryId(), i);
            }
            java.util.List<MenuItem> all = context.menuService().listItems();
            all.sort((a, b) -> {
                int byCategory = Integer.compare(
                    position.getOrDefault(a.getCategoryId(), Integer.MAX_VALUE),
                    position.getOrDefault(b.getCategoryId(), Integer.MAX_VALUE));
                return byCategory != 0 ? byCategory : a.getName().compareToIgnoreCase(b.getName());
            });
            items.setAll(all);
            itemFilterLabel.setText("All categories · grouped");
        } else {
            items.setAll(context.menuService().listItemsInCategory(filter.getCategoryId()));
            itemFilterLabel.setText("In " + filter.getName());
        }
    }

    // --- Categories -------------------------------------------------------------

    private void onCategorySelected(MenuCategory category) {
        if (category == null) return;
        editingCategory = category;
        categoryNameField.setText(category.getName());
        categoryOrderField.setText(
            category.getDisplayOrder() == null ? "" : String.valueOf(category.getDisplayOrder()));
        run(this::reloadItems);
    }

    private void startNewCategory() {
        editingCategory = null;
        categoryNameField.clear();
        categoryOrderField.clear();
        categoryTable.getSelectionModel().clearSelection();
        hideMessage();
    }

    private void saveCategory() {
        run(() -> {
            MenuCategory category = new MenuCategory();
            if (editingCategory != null) {
                category.setCategoryId(editingCategory.getCategoryId());
            }
            category.setName(categoryNameField.getText());
            category.setDisplayOrder(parseOptionalInt(categoryOrderField.getText(), "display order"));

            context.menuService().saveCategory(context.session(), category);
            startNewCategory();
            reload();
        });
    }

    private void deleteCategory() {
        MenuCategory selected = categoryTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Select a category first.");
            return;
        }
        if (!confirm("Delete " + selected.getName() + "?", "This cannot be undone.")) return;
        run(() -> {
            context.menuService().deleteCategory(context.session(), selected.getCategoryId());
            startNewCategory();
            reload();
        });
    }

    // --- Items ------------------------------------------------------------------

    private void startNewItem() {
        editingItem = null;
        itemFormTitle.setText("New item");
        itemNameField.clear();
        itemPriceField.clear();
        itemDescriptionField.clear();
        itemAvailabilityCombo.setValue(Availability.AVAILABLE);
        itemCategoryCombo.setValue(categoryTable.getSelectionModel().getSelectedItem());
        itemTable.getSelectionModel().clearSelection();
        hideMessage();
    }

    private void showItemInForm(MenuItem item) {
        if (item == null) return;
        editingItem = item;
        itemFormTitle.setText("Edit item");
        itemNameField.setText(item.getName());
        itemPriceField.setText(item.getPrice() == null ? "" : item.getPrice().toPlainString());
        itemDescriptionField.setText(item.getDescription() == null ? "" : item.getDescription());
        itemAvailabilityCombo.setValue(item.getAvailability());
        itemCategoryCombo.setValue(findCategory(item.getCategoryId()));
        hideMessage();
    }

    private MenuCategory findCategory(int categoryId) {
        for (MenuCategory category : categories) {
            if (category.getCategoryId() == categoryId) return category;
        }
        return null;
    }

    private void saveItem() {
        run(() -> {
            MenuCategory category = itemCategoryCombo.getValue();
            if (category == null) {
                throw new ValidationException("Select a category for this item.");
            }

            MenuItem item = new MenuItem();
            if (editingItem != null) {
                item.setItemId(editingItem.getItemId());
            }
            item.setName(itemNameField.getText());
            item.setCategoryId(category.getCategoryId());
            item.setPrice(parsePrice(itemPriceField.getText()));
            item.setAvailability(itemAvailabilityCombo.getValue());
            String description = itemDescriptionField.getText();
            item.setDescription(description == null || description.trim().isEmpty() ? null : description.trim());

            context.menuService().saveItem(context.session(), item);
            startNewItem();
            reload();
        });
    }

    private void toggleAvailability() {
        MenuItem selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Select an item first.");
            return;
        }
        run(() -> {
            Availability flipped = selected.getAvailability() == Availability.AVAILABLE
                ? Availability.UNAVAILABLE : Availability.AVAILABLE;
            context.menuService().setAvailability(context.session(), selected.getItemId(), flipped);
            reload();
        });
    }

    private void deleteItem() {
        MenuItem selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Select an item first.");
            return;
        }
        if (!confirm("Delete " + selected.getName() + "?",
                     "An item that appears on past orders is marked Unavailable instead, so order history stays intact.")) {
            return;
        }
        run(() -> {
            boolean removed = context.menuService().deleteItem(context.session(), selected.getItemId());
            if (!removed) {
                showMessage(selected.getName()
                    + " appears on past orders, so it was marked Unavailable rather than deleted.");
            }
            startNewItem();
            reload();
        });
    }

    // --- Parsing / plumbing -----------------------------------------------------

    /** Appendix A wording for a bad price, rather than letting NumberFormatException escape. */
    private static BigDecimal parsePrice(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new ValidationException("Enter a valid non-negative price.");
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Enter a valid non-negative price.");
        }
    }

    private static Integer parseOptionalInt(String text, String field) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Enter a whole number for the " + field + ".");
        }
    }

    private void run(Runnable action) {
        hideMessage();
        try {
            action.run();
        } catch (RmsException e) {
            showMessage(e.getMessage());
        }
    }

    private void showMessage(String message) {
        messageLabel.setText(message);
        messageLabel.setVisible(true);
        messageLabel.setManaged(true);
    }

    private void hideMessage() {
        messageLabel.setVisible(false);
        messageLabel.setManaged(false);
    }

    private static boolean confirm(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle("Golden Fork RMS");
        alert.setHeaderText(header);
        Optional<ButtonType> choice = alert.showAndWait();
        return choice.isPresent() && choice.get() == ButtonType.OK;
    }
}
