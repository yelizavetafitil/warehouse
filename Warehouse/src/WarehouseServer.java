import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;

public class WarehouseServer {
    private Connection connection;
    private ServerSocket serverSocket;
    private boolean running;

    public WarehouseServer(String url, String user, String password) throws SQLException {
        connection = DriverManager.getConnection(url, user, password);
        System.out.println("Соединение с базой данных установлено.");
    }

    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("Сервер запущен на порту " + port);

        while (running) {
            Socket clientSocket = serverSocket.accept();
            new ClientHandler(clientSocket, this).start();
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
            if (connection != null) connection.close();
            System.out.println("Сервер остановлен.");
        } catch (IOException | SQLException e) {
            System.out.println("Ошибка при остановке сервера: " + e.getMessage());
        }
    }

    // Методы для работы с пользователями
    public String authenticate(String email, String password) throws SQLException {
        String query = "SELECT access_level_id FROM employees WHERE email = ? AND password = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, email);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("access_level_id") : null;
        }
    }

    public List<String> loadUsers() throws SQLException {
        List<String> users = new ArrayList<>();
        String query = "SELECT employee_id, email, position, access_level_id FROM employees";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                users.add(rs.getInt("employee_id") + "," +
                        rs.getString("email") + "," +
                        rs.getString("position") + "," +
                        rs.getInt("access_level_id"));
            }
        }
        return users;
    }

    public String addUser(String email, String password, String position, int accessLevel) throws SQLException {
        String query = "INSERT INTO employees (email, password, position, access_level_id) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, email);
            stmt.setString(2, password);
            stmt.setString(3, position);
            stmt.setInt(4, accessLevel);
            stmt.executeUpdate();
            return "Пользователь добавлен успешно.";
        }
    }

    public String editUser(int employeeId, String email, String position, int accessLevel) {
        String query = "UPDATE employees SET email = ?, position = ?, access_level_id = ? WHERE employee_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, email);
            statement.setString(2, position);
            statement.setInt(3, accessLevel);
            statement.setInt(4, employeeId);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Ошибка изменения пользователя: " + e.getMessage());
        }
        return "Пользователь отредактирован успешно.";
    }

    public String deleteUser(int employeeId) {
        String query = "DELETE FROM employees WHERE employee_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, employeeId);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Ошибка удаления пользователя: " + e.getMessage());
        }
        return "Пользователь удален успешно.";
    }

    // Методы для работы с категориями
    public List<String> loadCategories() throws SQLException {
        List<String> categories = new ArrayList<>();
        String query = "SELECT category_id, category_name FROM categories";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                categories.add(rs.getInt("category_id") + "," + rs.getString("category_name"));
            }
        }
        return categories;
    }

    public String addCategory(String name) throws SQLException {
        String query = "INSERT INTO categories (category_name) VALUES (?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, name);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                return "Категория добавлена успешно.";
            } else {
                return "Не удалось добавить категорию.";
            }
        }
    }

    public String editCategory(int id, String newName) throws SQLException {
        String query = "UPDATE categories SET category_name = ? WHERE category_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, newName);
            stmt.setInt(2, id);
            stmt.executeUpdate();
            return "Категория изменена успешно.";
        }
    }

    public String deleteCategory(int id) throws SQLException {
        String query = "DELETE FROM categories WHERE category_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
            return "Категория удалена успешно.";
        }
    }

    public List<String> getCategories() throws SQLException {
        List<String> categories = new ArrayList<>();
        String query = "SELECT category_name FROM categories"; // Измените на правильное название поля

        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                categories.add(rs.getString("category_name")); // Измените на правильное название поля
            }
        } catch (SQLException e) {
            // Обработка ошибок
            throw new SQLException("Ошибка при получении категорий: " + e.getMessage());
        }

        return categories;
    }

    // Методы для работы с товарами
    public List<String> loadProducts() throws SQLException {
        List<String> products = new ArrayList<>();
        String query = "SELECT p.product_id, p.name, c.category_name AS category, p.price, p.quantity, p.unit, p.volume, w.warehouse_name " +
                "FROM products p JOIN categories c ON p.category_id = c.category_id JOIN " +
                "warehouses w ON p.warehouse_id = w.warehouse_id;";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                products.add(rs.getInt("product_id") + "," +
                        rs.getString("name") + "," +
                        rs.getString("category") + "," +
                        rs.getInt("quantity") + "," +
                        rs.getString("unit") + "," +
                        rs.getDouble("price")+ "," +
                        rs.getDouble("volume")+ "," +
                        rs.getString("warehouse_name"));
            }
        }
        return products;
    }

    public String addProduct(String name, String category, String warehouse, int quantity, String unit, double price, double volume) throws SQLException {
        // Проверка свободного объема на складе
        String checkQuery = "SELECT (w.volume - COALESCE(SUM(p.volume), 0)) AS free_volume " +
                "FROM warehouses w " +
                "LEFT JOIN products p ON w.warehouse_id = p.warehouse_id " +
                "WHERE w.warehouse_name = ? " +
                "GROUP BY w.warehouse_id";

        try (PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
            checkStmt.setString(1, warehouse);
            ResultSet rs = checkStmt.executeQuery();

            double freeVolume = 0;
            if (rs.next()) {
                freeVolume = rs.getDouble("free_volume");
            }

            // Проверяем, достаточно ли свободного объема
            if (freeVolume < volume) {
                return "ERROR, Недостаточно свободного объема на складе";
            }
        }

        // Вставка нового товара
        String query = "INSERT INTO products (name, category_id, warehouse_id, quantity, unit, price, volume) " +
                "VALUES (?, (SELECT category_id FROM categories WHERE category_name = ?), " +
                "(SELECT warehouse_id FROM Warehouses WHERE warehouse_name = ?), ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, name);
            stmt.setString(2, category);
            stmt.setString(3, warehouse);
            stmt.setInt(4, quantity);
            stmt.setString(5, unit);
            stmt.setDouble(6, price);
            stmt.setDouble(7, volume);
            stmt.executeUpdate();
            return "SUCCESS, Товар успешно добавлен";
        }
    }

    public String editProduct(int id, String name, String category, String warehouse, int quantity, String unit, double price, double volume) throws SQLException {
        // Проверка свободного объема на складе
        String checkQuery = "SELECT (w.volume - COALESCE(SUM(p.volume), 0)) AS free_volume " +
                "FROM warehouses w " +
                "LEFT JOIN products p ON w.warehouse_id = p.warehouse_id " +
                "WHERE w.warehouse_name = ? " +
                "GROUP BY w.warehouse_id";

        try (PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
            checkStmt.setString(1, warehouse);
            ResultSet rs = checkStmt.executeQuery();

            double freeVolume = 0;
            if (rs.next()) {
                freeVolume = rs.getDouble("free_volume");
            }

            // Проверяем, достаточно ли свободного объема
            if (freeVolume < volume) {
                return "ERROR, Недостаточно свободного объема на складе";
            }
        }

        String query = "UPDATE products SET name = ?, " +
                "category_id = (SELECT category_id FROM categories WHERE category_name = ?), " +
                "warehouse_id = (SELECT warehouse_id FROM Warehouses WHERE warehouse_name = ?), " +
                "quantity = ?, unit = ?, price = ?, volume = ? WHERE product_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, name);
            stmt.setString(2, category);
            stmt.setString(3, warehouse);
            stmt.setInt(4, quantity);
            stmt.setString(5, unit);
            stmt.setDouble(6, price);
            stmt.setDouble(7, volume);
            stmt.setInt(8, id);

            int updated = stmt.executeUpdate();
            return updated > 0 ? "SUCCESS, Товар успешно обновлен" : "ERROR, Товар не найден";
        }
    }

    public String deleteProduct(int id) {
        String query = "DELETE FROM products WHERE product_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Ошибка удаления товара: " + e.getMessage());
        }
        return "Товар удален успешно.";
    }

    public List<String> loadWarehouse() throws SQLException {
        List<String> suppliers = new ArrayList<>();
        String query = "SELECT warehouse_id, warehouse_name, volume FROM warehouses";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                suppliers.add(rs.getInt("warehouse_id") + "," +
                        rs.getString("warehouse_name") + "," +
                        rs.getDouble("volume"));
            }
        }
        return suppliers;
    }

    public String addWarehouse(String name, Double volume) throws SQLException {
        String query = "INSERT INTO warehouses (warehouse_name, volume) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, name);
            stmt.setDouble(2, volume);
            stmt.executeUpdate();
            return "Склад добавлен успешно.";
        }
    }

    public String editWarehouse(int warehouseId, String name, Double volume) {
        String query = "UPDATE warehouses SET warehouse_name = ?, volume = ? WHERE warehouse_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, name);
            statement.setDouble(2, volume);
            statement.setInt(3, warehouseId);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Ошибка изменения : " + e.getMessage());
        }
        return "Склад отредактирован успешно.";
    }

    public String deleteWarehouse(int warehouseId) {
        String query = "DELETE FROM warehouses WHERE warehouse_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, warehouseId);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Ошибка удаления: " + e.getMessage());
        }
        return "Склад удален успешно.";
    }

    public List<String> loadReception() throws SQLException {
        List<String> suppliers = new ArrayList<>();
        String query = "SELECT trp.transaction_id, trp.name AS name, trp.quantity, trp.unit, trp.price, trp.volume, c.category_name, w.warehouse_name FROM transactions_reception_products trp JOIN categories c ON trp.category_id = c.category_id JOIN warehouses w ON trp.warehouse_id = w.warehouse_id;";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                suppliers.add(rs.getInt("transaction_id") + "," +
                        rs.getString("name") + "," +
                        rs.getInt("quantity")+ "," +
                        rs.getString("unit")+ "," +
                        rs.getDouble("price")+ "," +
                        rs.getDouble("volume")+ "," +
                        rs.getString("category_name")+ "," +
                        rs.getString("warehouse_name"));
            }
        }
        return suppliers;
    }

    public String addReception(int transactionId, String name, int quantity, String unit, double price, double volume, String category, String warehouse) throws SQLException {

        String checkQuery = "SELECT (w.volume - COALESCE(SUM(p.volume), 0)) AS free_volume " +
                "FROM warehouses w " +
                "LEFT JOIN products p ON w.warehouse_id = p.warehouse_id " +
                "WHERE w.warehouse_name = ? " +
                "GROUP BY w.warehouse_id";

        try (PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
            checkStmt.setString(1, warehouse);
            ResultSet rs = checkStmt.executeQuery();

            double freeVolume = 0;
            if (rs.next()) {
                freeVolume = rs.getDouble("free_volume");
            }

            // Проверяем, достаточно ли свободного объема
            if (freeVolume < volume) {
                return "ERROR, Недостаточно свободного объема на складе";
            }
        }

        String query = "INSERT INTO transactions_reception_products (transaction_id, name, quantity, unit, price, volume, category_id, warehouse_id) VALUES (?, ?, ?, ?, ?, ?, (SELECT category_id FROM categories WHERE category_name = ?), (SELECT warehouse_id FROM warehouses WHERE warehouse_name = ?))";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, transactionId);
            stmt.setString(2, name);
            stmt.setInt(3, quantity);
            stmt.setString(4, unit);
            stmt.setDouble(5, price);
            stmt.setDouble(6, volume);
            stmt.setString(7, category);  // Добавляем category_id
            stmt.setString(8, warehouse);  // Добавляем warehouse_id
            stmt.executeUpdate();
            return "Товар добавлен в транзакцию успешно.";
        }
    }

    public String editReception(int warehouseId, String name, Double volume) {
        String query = "UPDATE warehouses SET warehouse_name = ?, volume = ? WHERE warehouse_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, name);
            statement.setDouble(2, volume);
            statement.setInt(3, warehouseId);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Ошибка изменения : " + e.getMessage());
        }
        return "Склад отредактирован успешно.";
    }

    public String deleteReception(int warehouseId) {
        String query = "DELETE FROM transactions_reception_products WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, warehouseId);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Ошибка удаления: " + e.getMessage());
        }
        return "Товар удален успешно.";
    }

    public List<String> loadSuppliers() throws SQLException {
        List<String> suppliers = new ArrayList<>();
        String query = "SELECT supplier_id, name, contact_info FROM suppliers";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                suppliers.add(rs.getInt("supplier_id") + "," +
                        rs.getString("name") + "," +
                        rs.getString("contact_info"));
            }
        }
        return suppliers;
    }

    public String addSupplier(String name, String contact_info) throws SQLException {
        String query = "INSERT INTO suppliers (name, contact_info) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, name);
            stmt.setString(2, contact_info);
            stmt.executeUpdate();
            return "Поставщик добавлен успешно.";
        }
    }

    public String editSupplier(int supplierId, String name, String contact_info) {
        String query = "UPDATE suppliers SET name = ?, contact_info = ? WHERE supplier_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, name);
            statement.setString(2, contact_info);
            statement.setInt(3, supplierId);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Ошибка изменения : " + e.getMessage());
        }
        return "Поставщик отредактирован успешно.";
    }

    public String deleteSupplier(int supplierId) {
        String query = "DELETE FROM suppliers WHERE supplier_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, supplierId);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Ошибка удаления поставщика: " + e.getMessage());
        }
        return "Поставщик удален успешно.";
    }

    public List<String> loadOrders() throws SQLException {
        List<String> products = new ArrayList<>();
        String query = "SELECT o.order_id, s.name, o.order_date, o.status " +
                "FROM orders o JOIN suppliers s ON o.supplier_id = s.supplier_id";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                products.add(rs.getInt("order_id") + "," +
                        rs.getString("name") + "," +
                        rs.getString("order_date") + "," +
                        rs.getString("status"));
            }
        }
        return products;
    }

    public String addOrder(String name, String order_date, String status) throws SQLException {
        String query = "INSERT INTO orders (supplier_id, order_date, status) " +
                "VALUES ((SELECT supplier_id FROM suppliers WHERE name = ?), ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, name);
            stmt.setString(2, order_date);
            stmt.setString(3, status);
            stmt.executeUpdate();
            return "SUCCESS,Заказ успешно добавлен";
        }
    }

    public String ediOrder(int id, String name, String order_date, String status) throws SQLException {
        String query = "UPDATE orders SET " +
                "supplier_id = (SELECT supplier_id FROM suppliers WHERE name = ?), " +
                "order_date = ?, status = ? WHERE order_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, name);
            stmt.setString(2, order_date);
            stmt.setString(3, status);
            stmt.setInt(4, id);

            int updated = stmt.executeUpdate();
            return updated > 0 ? "SUCCESS,Заказ успешно обновлен" : "ERROR,Заказ не найден";
        }
    }

    public String deleteOrder(int id) {
        String query = "DELETE FROM orders WHERE order_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Ошибка удаления заказа: " + e.getMessage());
        }
        return "Заказ удален успешно.";
    }


    public List<String> loadOrderItems() throws SQLException {
        List<String> products = new ArrayList<>();

        // Запрос для получения данных из таблиц
        String query = "SELECT oi.order_item_id, oi.order_id, p.name, w.warehouse_name, " +
                "oi.quantity AS order_quantity, p.quantity AS available_quantity, " +
                "o.order_date, o.status " +
                "FROM orders o " +
                "JOIN order_items oi ON o.order_id = oi.order_id " +
                "JOIN products p ON oi.product_id = p.product_id " +
                "JOIN warehouses w ON p.warehouse_id = w.warehouse_id"; // Добавлено JOIN для получения имени склада

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                int orderQuantity = rs.getInt("order_quantity");
                int availableQuantity = rs.getInt("available_quantity");
                String status = (orderQuantity > availableQuantity) ? "НЕДОСТАТОК" : "";

                products.add(rs.getInt("order_item_id") + "," +
                        rs.getInt("order_id") + "," +
                        rs.getString("name") + " (" + rs.getString("warehouse_name") + ")," +
                        orderQuantity + (status.isEmpty() ? "" : " (" + status + ")") + "," + // Добавляем статус нехватки
                        rs.getString("order_date") + "," +
                        rs.getString("status"));
            }
        }
        return products;
    }

    public String addOrderItem(int orderId, int productId, int quantity) throws SQLException {
        // Проверяем, достаточно ли товара для выполнения заказа
        String stockQuery = "SELECT quantity FROM products WHERE product_id = ?";

        try (PreparedStatement stockStmt = connection.prepareStatement(stockQuery)) {
            stockStmt.setInt(1, productId);

            ResultSet rs = stockStmt.executeQuery();
            if (rs.next()) {
                int availableStock = rs.getInt("quantity");
                if (availableStock < quantity) {
                    return "ERROR, Недостаточно товара на складе. Доступно: " + availableStock + ", запрашиваемое количество: " + quantity;
                }
            } else {
                return "ERROR, Товар не найден";
            }
        }

        // Проверяем, существует ли уже запись с таким order_id и product_id
        String checkQuery = "SELECT COUNT(*) FROM order_items WHERE order_id = ? AND product_id = ?";

        try (PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
            checkStmt.setInt(1, orderId);
            checkStmt.setInt(2, productId);

            ResultSet rs = checkStmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                return "ERROR, Позиция на данный заказ уже существует.";
            }
        }

        // Если записи не существует, добавляем новую позицию заказа
        String insertQuery = "INSERT INTO order_items (order_id, product_id, quantity) VALUES (?, ?, ?)";

        try (PreparedStatement insertStmt = connection.prepareStatement(insertQuery)) {
            insertStmt.setInt(1, orderId);
            insertStmt.setInt(2, productId);
            insertStmt.setInt(3, quantity);
            insertStmt.executeUpdate();
            return "SUCCESS, Позиция заказа успешно добавлена";
        } catch (SQLException e) {
            return "ERROR, Не удалось добавить позицию заказа: " + e.getMessage();
        }
    }

    public String editOrderItem(int orderId, int productId, int quantity) throws SQLException {
        String query = "UPDATE order_items SET quantity = ? " +
                "WHERE order_item_id = ? AND product_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, quantity);
            stmt.setInt(2, orderId);
            stmt.setInt(3, productId);

            int updated = stmt.executeUpdate();
            return updated > 0 ? "SUCCESS, Позиция заказа успешно обновлена" : "ERROR, Позиция заказа не найдена";
        }
    }

    public String deleteOrderItem(int id) {
        String query = "DELETE FROM order_items WHERE order_item_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Ошибка удаления : " + e.getMessage());
        }
        return "Позиция удалена успешно.";
    }


    public List<String> loadTransactions() throws SQLException {
        List<String> products = new ArrayList<>();
        String query = "SELECT t.transaction_id, t.transaction_date, t.transaction_type, p.name, t.quantity, p.unit " +
                "FROM transactions t JOIN products p ON t.product_id = p.product_id";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                products.add(rs.getInt("transaction_id") + "," +
                        rs.getString("transaction_date") + "," +
                        rs.getString("transaction_type") + "," +
                        rs.getString("name")+ "," +
                        rs.getInt("quantity")+ "," +
                        rs.getString("unit"));
            }
        }
        return products;
    }

    public String addTransaction(String name, String order_date, String status) throws SQLException {
        String query = "INSERT INTO orders (supplier_id, order_date, status) " +
                "VALUES ((SELECT supplier_id FROM suppliers WHERE name = ?), ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, name);
            stmt.setString(2, order_date);
            stmt.setString(3, status);
            stmt.executeUpdate();
            return "SUCCESS,Заказ успешно добавлен";
        }
    }

    public String editTransaction(int id, String name, String order_date, String status) throws SQLException {
        String query = "UPDATE orders SET " +
                "supplier_id = (SELECT supplier_id FROM suppliers WHERE name = ?), " +
                "order_date = ?, status = ? WHERE order_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, name);
            stmt.setString(2, order_date);
            stmt.setString(3, status);
            stmt.setInt(4, id);

            int updated = stmt.executeUpdate();
            return updated > 0 ? "SUCCESS,Заказ успешно обновлен" : "ERROR,Заказ не найден";
        }
    }

    public String deleteTransaction(int id) {
        String query = "DELETE FROM orders WHERE order_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Ошибка удаления заказа: " + e.getMessage());
        }
        return "Заказ удален успешно.";
    }

    public List<String> getSuppliers() {
        List<String> suppliers = new ArrayList<>();
        String query = "SELECT supplier_id, name FROM suppliers";

        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String supplierName = rs.getString("name");
                suppliers.add(supplierName);
            }
        } catch (SQLException e) {
            e.printStackTrace(); // Обработка ошибок
        }

        return suppliers;
    }

    public List<String> getProducts() throws SQLException {
        List<String> productList = new ArrayList<>();
        String query = "SELECT p.product_id, p.name, w.warehouse_name " +
                "FROM products p " +
                "JOIN Warehouses w ON p.warehouse_id = w.warehouse_id"; // SQL-запрос для выборки продуктов с учетом склада

        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("product_id");
                String name = rs.getString("name");
                String warehouse = rs.getString("warehouse_name"); // Получаем имя склада
                productList.add(id + "," + name + "," + warehouse); // Добавляем в список в формате "id,name,склад"
            }
        }
        return productList;
    }

    public List<String> getWarehouses() throws SQLException {
        List<String> list = new ArrayList<>();
        String query = "SELECT warehouse_name FROM warehouses";

        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("warehouse_name");
                list.add(name);
            }
        }
        return list;
    }

    public String getProductUnit(int productId) throws SQLException {
        String unit = null;
        String query = "SELECT unit FROM products WHERE product_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, productId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                unit = rs.getString("unit");
            }
        }
        return unit;
    }


    public List<Integer> getOrderId() {
        List<Integer> orderIds = new ArrayList<>();
        String query = "SELECT order_id FROM orders";

        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                int orderId = rs.getInt("order_id");
                orderIds.add(orderId);
            }
        } catch (SQLException e) {
            e.printStackTrace(); // Обработка ошибок
        }

        return orderIds;
    }

    public int getReceptionTransactionId() throws SQLException {
        String sql = "INSERT INTO transactions (transaction_date, transaction_type) "
                + "VALUES (NOW(), 'Прием товаров')";

        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Не удалось создать транзакцию");
            }

            String sql1 = "SELECT transaction_id FROM transactions "
                    + "WHERE transaction_type = 'Прием товаров' "
                    + "ORDER BY transaction_date DESC LIMIT 1";

            try (PreparedStatement stmt1 = connection.prepareStatement(sql1);
                 ResultSet rs = stmt1.executeQuery()) {

                return rs.next() ? rs.getInt("transaction_id") : null;
            }
        }
    }


    private static class ClientHandler extends Thread {
        private final Socket clientSocket;
        private final WarehouseServer server;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket socket, WarehouseServer server) {
            this.clientSocket = socket;
            this.server = server;

            try {
                // Инициализация потоков в конструкторе
                this.out = new PrintWriter(socket.getOutputStream(), true);
                this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Установка таймаута для операций чтения
                socket.setSoTimeout(30000); // 30 секунд
            } catch (IOException e) {
                System.err.println("Ошибка инициализации клиентского соединения: " + e.getMessage());
                closeResources();
            }
        }


        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                String email = in.readLine();
                String password = in.readLine();
                String accessLevel = server.authenticate(email, password);
                if (accessLevel != null) {
                    out.println("Авторизация успешна! Уровень доступа: " + accessLevel);

                    String command;
                    while ((command = in.readLine()) != null) {
                        String[] parts = command.split(",");
                        switch (parts[0]) {
                            case "LOAD_USERS":
                                sendList(server.loadUsers());
                                break;
                            case "ADD_USER":
                                handleAddUser(parts);
                                break;
                            case "EDIT_USER":
                                handleEditUser(parts);
                                break;
                            case "DELETE_USER":
                                handleDeleteUser(parts);
                                break;
                            case "LOAD_WAREHOUSE":
                                sendList(server.loadWarehouse());
                                break;
                            case "ADD_WAREHOUSE":
                                handleAddWarehouse(parts);
                                break;
                            case "EDIT_WAREHOUSE":
                                handleEditWarehouse(parts);
                                break;
                            case "DELETE_WAREHOUSE":
                                handleDeleteWarehouse(parts);
                                break;
                            case "LOAD_SUPPLIERS":
                                sendList(server.loadSuppliers());
                                break;
                            case "ADD_SUPPLIERS":
                                handleAddSupplier(parts);
                                break;
                            case "EDIT_SUPPLIERS":
                                handleEditSupplier(parts);
                                break;
                            case "DELETE_SUPPLIERS":
                                handleDeleteSupplier(parts);
                                break;
                            case "LOAD_CATEGORIES":
                                sendList(server.loadCategories());
                                break;
                            case "ADD_CATEGORY":
                                handleAddCategory(parts);
                                break;
                            case "EDIT_CATEGORY":
                                handleEditCategory(parts);
                                break;
                            case "DELETE_CATEGORY":
                                handleDeleteCategory(parts);
                                break;
                            case "LOAD_ORDER":
                                sendList(server.loadOrders());
                                break;
                            case "ADD_ORDER":
                                handleAddOrder(parts);
                                break;
                            case "EDIT_ORDER":
                                handleEditOrder(parts);
                                break;
                            case "DELETE_ORDER":
                                handleDeleteOrder(parts);
                                break;
                            case "LOAD_PRODUCTS":
                                sendList(server.loadProducts());
                                break;
                            case "ADD_PRODUCT":
                                handleAddProduct(parts);
                                break;
                            case "EDIT_PRODUCT":
                                handleEditProduct(parts);
                                break;
                            case "DELETE_PRODUCT":
                                handleDeleteProduct(parts);
                                break;
                            case "LOAD_RECEPTION":
                                sendList(server.loadReception());
                                break;
                            case "ADD_RECEPTION":
                                handleAddReception(parts);
                                break;
                            case "EDIT_RECEPTION":
                                handleEditReception(parts);
                                break;
                            case "DELETE_RECEPTION":
                                handleDeleteReception(parts);
                                break;
                            case "LOAD_ORDER_ITEM":
                                sendList(server.loadOrderItems());
                                break;
                            case "ADD_ORDER_ITEM":
                                handleAddOrderItem(parts);
                                break;
                            case "EDIT_ORDER_ITEM":
                                handleEditOrderItem(parts);
                                break;
                            case "DELETE_ORDER_ITEM":
                                handleDeleteOrderItem(parts);
                                break;
                            case "LOAD_TRANSACTIONS":
                                sendList(server.loadTransactions());
                                break;
                            case "ADD_TRANSACTIONS":
                                handleAddTransaction(parts);
                                break;
                            case "EDIT_TRANSACTIONS":
                                handleEditTransaction(parts);
                                break;
                            case "DELETE_TRANSACTIONS":
                                handleDeleteTransaction(parts);
                                break;
                            case "GET_CATEGORIES":
                                handleGetCategories();
                                break;
                            case "GET_SUPPLIERS":
                                handleGetSuppliers();
                                break;
                            case "GET_ORDER_ID":
                                handleGetOrderId();
                                break;
                            case "GET_PRODUCTS":
                                handleGetProducts();
                                break;
                            case "GET_WAREHOUSES":
                                handleGetWarehouses();
                                break;
                            case "GET_PRODUCT_UNIT":
                                handleGetProductUnit(Integer.parseInt(parts[1]));
                                break;
                            case "CREATE_RECEPTION_TRANSACTION":
                                handleGetRecTransId();
                                break;
                            default:
                                sendError("Неизвестная команда: " + parts[0]);
                        }
                    }
                } else {
                    out.println("Неверный email или пароль.");
                }
            } catch (IOException e) {
                System.out.println("Ошибка обработки клиента: " + e.getMessage());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.out.println("Ошибка закрытия сокета: " + e.getMessage());
                }
            }
        }

        // Реализация обработчиков команд
        private void handleAddUser(String[] parts) throws SQLException {
            if (parts.length < 5) throw new ArrayIndexOutOfBoundsException();
            out.println(server.addUser(parts[1], parts[2], parts[3], Integer.parseInt(parts[4])));
        }

        private void handleEditUser(String[] parts) throws SQLException {
            if (parts.length < 5) throw new ArrayIndexOutOfBoundsException();
            out.println(server.editUser(Integer.parseInt(parts[1]), parts[2], parts[3], Integer.parseInt(parts[4])));
        }

        private void handleDeleteUser(String[] parts) throws SQLException {
            if (parts.length < 2) throw new ArrayIndexOutOfBoundsException();
            out.println(server.deleteUser(Integer.parseInt(parts[1])));
        }

        private void handleAddSupplier(String[] parts) throws SQLException {
            if (parts.length < 3) throw new ArrayIndexOutOfBoundsException();
            out.println(server.addSupplier(parts[1], parts[2]));
        }

        private void handleEditSupplier(String[] parts) throws SQLException {
            if (parts.length < 4) throw new ArrayIndexOutOfBoundsException();
            out.println(server.editSupplier(Integer.parseInt(parts[1]), parts[2], parts[3]));
        }

        private void handleDeleteSupplier(String[] parts) throws SQLException {
            if (parts.length < 2) throw new ArrayIndexOutOfBoundsException();
            out.println(server.deleteSupplier(Integer.parseInt(parts[1])));
        }

        private void handleAddWarehouse(String[] parts) throws SQLException {
            if (parts.length < 3) throw new ArrayIndexOutOfBoundsException();
            out.println(server.addWarehouse(parts[1], Double.parseDouble(parts[2])));
        }

        private void handleEditWarehouse(String[] parts) throws SQLException {
            if (parts.length < 4) throw new ArrayIndexOutOfBoundsException();
            out.println(server.editWarehouse(Integer.parseInt(parts[1]), parts[2],  Double.parseDouble(parts[3])));
        }

        private void handleDeleteWarehouse(String[] parts) throws SQLException {
            if (parts.length < 2) throw new ArrayIndexOutOfBoundsException();
            out.println(server.deleteWarehouse(Integer.parseInt(parts[1])));
        }


        // Реализация обработчиков команд
        private void handleGetCategories() throws SQLException {
            List<String> categories = server.getCategories(); // Предполагается, что у вас есть метод getCategories на сервере
            for (String category : categories) {
                out.println(category); // Отправляем каждую категорию клиенту
            }
            out.println("END"); // Помечаем конец списка категорий
        }

        // Реализация обработчиков команд
        private void handleGetSuppliers() throws SQLException {
            List<String> suppliers = server.getSuppliers(); // Предполагается, что у вас есть метод getCategories на сервере
            for (String supplier : suppliers) {
                out.println(supplier); // Отправляем каждую категорию клиенту
            }
            out.println("END"); // Помечаем конец списка категорий
        }

        private void handleGetOrderId() throws SQLException {
            List<Integer> orderId = server.getOrderId(); // Предполагается, что у вас есть метод getCategories на сервере
            for (int order : orderId) {
                out.println(order); // Отправляем каждую категорию клиенту
            }
            out.println("END"); // Помечаем конец списка категорий
        }

        private void handleGetRecTransId() {
            try {
                // Получаем ID транзакции с сервера
                Integer transactionId = server.getReceptionTransactionId();  // Теперь возвращает только один ID

                if (!(transactionId == null || transactionId <= 0)) {
                    out.println(transactionId);
                }

            } catch (SQLException e) {
                out.println("ERROR,Ошибка при получении транзакции: " + e.getMessage());
            }
        }

        private void handleGetProducts() throws SQLException {
            List<String> products = server.getProducts(); // Предполагается, что метод возвращает список строк в формате "id,name"

            for (String product : products) {
                out.println(product); // Отправляем каждую строку клиенту
            }
            out.println("END"); // Помечаем конец списка
        }

        private void handleGetProductUnit(int productId) throws SQLException {
            String unit = server.getProductUnit(productId); // Получаем единицу измерения для конкретного продукта
            if (unit != null) {
                out.println(unit); // Отправляем единицу измерения клиенту
            } else {
                out.println("ERROR, Продукт не найден");
            }
        }

        private void handleGetWarehouses() throws SQLException {
            List<String> warehouses = server.getWarehouses(); // Предполагается, что метод возвращает список строк в формате "id,name"

            for (String warehouse : warehouses) {
                out.println(warehouse); // Отправляем каждую строку клиенту
            }
            out.println("END"); // Помечаем конец списка
        }

        // Аналогичные методы для категорий и продуктов
        private void handleAddCategory(String[] parts) throws SQLException {
            if (parts.length < 2) throw new ArrayIndexOutOfBoundsException();
            out.println(server.addCategory(parts[1]));
        }

        private void handleEditCategory(String[] parts) throws SQLException {
            if (parts.length < 3) throw new ArrayIndexOutOfBoundsException();
            out.println(server.editCategory(Integer.parseInt(parts[1]), parts[2]));
        }

        private void handleDeleteCategory(String[] parts) throws SQLException {
            if (parts.length < 2) throw new ArrayIndexOutOfBoundsException();
            out.println(server.deleteCategory(Integer.parseInt(parts[1])));
        }

        private void handleAddOrder(String[] parts) throws SQLException {
            if (parts.length < 4) throw new ArrayIndexOutOfBoundsException();
            out.println(server.addOrder(parts[1], parts[2], parts[3]));
        }

        private void handleEditOrder(String[] parts) throws SQLException {
            if (parts.length < 5) throw new ArrayIndexOutOfBoundsException();
            out.println(server.ediOrder(Integer.parseInt(parts[1]), parts[2], parts[3], parts[4]));
        }

        private void handleDeleteOrder(String[] parts) throws SQLException {
            if (parts.length < 2) throw new ArrayIndexOutOfBoundsException();
            out.println(server.deleteOrder(Integer.parseInt(parts[1])));
        }

        private void handleAddOrderItem(String[] parts) throws SQLException {
            if (parts.length < 4) throw new ArrayIndexOutOfBoundsException();
            out.println(server.addOrderItem(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
        }

        private void handleEditOrderItem(String[] parts) throws SQLException {
            if (parts.length < 4) throw new ArrayIndexOutOfBoundsException();
            out.println(server.editOrderItem(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
        }

        private void handleDeleteOrderItem(String[] parts) throws SQLException {
            if (parts.length < 2) throw new ArrayIndexOutOfBoundsException();
            out.println(server.deleteOrderItem(Integer.parseInt(parts[1])));
        }

        private void handleAddTransaction(String[] parts) throws SQLException {
            if (parts.length < 4) throw new ArrayIndexOutOfBoundsException();
            out.println(server.addOrder(parts[1], parts[2], parts[3]));
        }

        private void handleEditTransaction(String[] parts) throws SQLException {
            if (parts.length < 5) throw new ArrayIndexOutOfBoundsException();
            out.println(server.ediOrder(Integer.parseInt(parts[1]), parts[2], parts[3], parts[4]));
        }

        private void handleDeleteTransaction(String[] parts) throws SQLException {
            if (parts.length < 2) throw new ArrayIndexOutOfBoundsException();
            out.println(server.deleteOrder(Integer.parseInt(parts[1])));
        }

        private void handleAddProduct(String[] parts) throws SQLException, NumberFormatException {
            if (parts.length < 6) throw new ArrayIndexOutOfBoundsException();

            // parts[1] - название, parts[2] - категория, parts[3] - склад, parts[4] - количество, parts[5] - цена, parts[6] - объем
            double price = Double.parseDouble(parts[5]);
            int quantity = Integer.parseInt(parts[4]);
            double volume = Double.parseDouble(parts[6]);

            out.println(server.addProduct(parts[1], parts[2], parts[3], quantity,parts.length > 7 ? parts[7] : "шт", price, volume));
        }

        private void handleEditProduct(String[] parts) throws SQLException, NumberFormatException {
            if (parts.length < 7) throw new ArrayIndexOutOfBoundsException();

            // parts[1] - ID, parts[2] - название, parts[3] - категория, parts[4] - склад, parts[5] - количество, parts[6] - цена, parts[7] - объем
            int id = Integer.parseInt(parts[1]);
            double price = Double.parseDouble(parts[5]);
            int quantity = Integer.parseInt(parts[6]);
            double volume = Double.parseDouble(parts[7]);
            String unit = parts.length > 8 ? parts[8] : "шт";

            out.println(server.editProduct(id, parts[2], parts[3], parts[4], quantity, unit, price, volume));
        }

        private void handleDeleteProduct(String[] parts) throws SQLException {
            if (parts.length < 2) throw new ArrayIndexOutOfBoundsException();
            out.println(server.deleteProduct(Integer.parseInt(parts[1])));
        }

        private void handleAddReception(String[] parts) throws SQLException, NumberFormatException {
            if (parts.length < 8) throw new ArrayIndexOutOfBoundsException();

            // parts[1] - название, parts[2] - категория, parts[3] - склад, parts[4] - количество, parts[5] - цена, parts[6] - объем
            String name = parts[2];
            int transactionId=Integer.parseInt(parts[1]);
            String category = parts[7];  // Предполагаем, что передается ID категории
            String warehouse = parts[8];  // Предполагаем, что передается ID склада
            int quantity = Integer.parseInt(parts[3]);
            double price = Double.parseDouble(parts[4]);
            double volume = Double.parseDouble(parts[5]);
            String unit = parts.length > 6 ? parts[6] : "шт"; // Если есть, используем указанный юнит, иначе по умолчанию "шт"

            // Добавление товара в транзакцию
            out.println(server.addReception(transactionId, name, quantity, unit, price, volume, category, warehouse));
        }

        private void handleEditReception(String[] parts) throws SQLException, NumberFormatException {
            if (parts.length < 8) throw new ArrayIndexOutOfBoundsException();

            // parts[1] - ID, parts[2] - название, parts[3] - категория, parts[4] - склад, parts[5] - количество, parts[6] - цена, parts[7] - объем
            int id = Integer.parseInt(parts[1]);
            String name = parts[2];
            String category = parts[3];
            String warehouse = parts[4];
            int quantity = Integer.parseInt(parts[5]);
            double price = Double.parseDouble(parts[6]);
            double volume = Double.parseDouble(parts[7]);
            String unit = parts.length > 8 ? parts[8] : "шт"; // Если есть, используем указанный юнит, иначе по умолчанию "шт"

            // Редактирование товара в транзакции
         //   out.println(server.editReception(id, name, category, warehouse, quantity, unit, price, volume));
        }

        private void handleDeleteReception(String[] parts) throws SQLException {
            if (parts.length < 2) throw new ArrayIndexOutOfBoundsException();

            out.println(server.deleteReception(Integer.parseInt(parts[1])));
        }


        private void sendList(List<String> items) {
            for (String item : items) {
                out.println(item);
            }
            out.println("END");
        }

        private void sendError(String message) {
            out.println("ERROR," + message);
        }

        private void closeResources() {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.err.println("Ошибка при закрытии ресурсов: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        try {
            WarehouseServer server = new WarehouseServer(
                    "jdbc:mysql://localhost:3306/warehouse",
                    "pma",
                    "your_password"
            );
            server.start(12345);
        } catch (SQLException | IOException e) {
            System.err.println("Ошибка запуска сервера: " + e.getMessage());
        }
    }
}