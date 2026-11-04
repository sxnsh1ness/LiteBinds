package me.sxnsh1ness.litebinds.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseManager {

    private final HikariDataSource dataSource;
    private final boolean isSQLite;
    private final ExecutorService dbExecutor;

    public DatabaseManager(Plugin plugin, FileConfiguration config) {
        String type = config.getString("database.type", "sqlite").toLowerCase();
        this.isSQLite = type.equals("sqlite");

        // Выделенный поток для асинхронных операций с БД
        this.dbExecutor = Executors.newFixedThreadPool(4);

        HikariConfig hikariConfig = new HikariConfig();

        if (isSQLite) {
            File dbFile = new File(plugin.getDataFolder(), "binds.db");
            if (!dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            hikariConfig.setMaximumPoolSize(1); // Для SQLite достаточно 1 соединения во избежание SQLiteBusyException
        } else {
            String host = config.getString("mysql.host", "127.0.0.1");
            int port = config.getInt("mysql.port", 3306);
            String database = config.getString("mysql.database", "lite_binds");
            String user = config.getString("mysql.user", "root");
            String password = config.getString("mysql.password", "");

            hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
            hikariConfig.setUsername(user);
            hikariConfig.setPassword(password);
            hikariConfig.setMaximumPoolSize(10);
            hikariConfig.setMinimumIdle(2);

            // Оптимизация MySQL
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
            hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
        }

        this.dataSource = new HikariDataSource(hikariConfig);
        initializeDatabase();
    }

    private void initializeDatabase() {
        String createTableSQL;

        if (isSQLite) {
            createTableSQL = """
                CREATE TABLE IF NOT EXISTS player_actions (
                    player VARCHAR(16) PRIMARY KEY,
                    action_drop VARCHAR(32) NOT NULL DEFAULT 'NONE',
                    action_swap VARCHAR(32) NOT NULL DEFAULT 'NONE',
                    action_interact VARCHAR(32) NOT NULL DEFAULT 'NONE'
                );
                """;
        } else {
            createTableSQL = """
                CREATE TABLE IF NOT EXISTS player_actions (
                    player VARCHAR(16) PRIMARY KEY,
                    action_drop ENUM('NONE', 'SNOWBALL', 'JAKE', 'ALTERNATIVE_TRAP', 'STAN', 'TRAP', 'EXPLOSIVE', 'BACKPACK') NOT NULL DEFAULT 'NONE',
                    action_swap ENUM('NONE', 'SNOWBALL', 'JAKE', 'ALTERNATIVE_TRAP', 'STAN', 'TRAP', 'EXPLOSIVE', 'BACKPACK') NOT NULL DEFAULT 'NONE',
                    action_interact ENUM('NONE', 'SNOWBALL', 'JAKE', 'ALTERNATIVE_TRAP', 'STAN', 'TRAP', 'EXPLOSIVE', 'BACKPACK') NOT NULL DEFAULT 'NONE'
                );
                """;
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(createTableSQL);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public CompletableFuture<Void> savePlayerActions(PlayerActions playerActions) {
        return CompletableFuture.runAsync(() -> {
            String sql;

            if (isSQLite) {
                sql = """
                    INSERT INTO player_actions (player, action_drop, action_swap, action_interact)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(player) DO UPDATE SET
                    action_drop = excluded.action_drop,
                    action_swap = excluded.action_swap,
                    action_interact = excluded.action_interact;
                    """;
            } else {
                sql = """
                    INSERT INTO player_actions (player, action_drop, action_swap, action_interact)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                    action_drop = VALUES(action_drop),
                    action_swap = VALUES(action_swap),
                    action_interact = VALUES(action_interact);
                    """;
            }

            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, playerActions.getPlayer());
                statement.setString(2, playerActions.getActionDrop().name());
                statement.setString(3, playerActions.getActionSwap().name());
                statement.setString(4, playerActions.getActionInteract().name());

                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save player actions", e);
            }
        }, dbExecutor);
    }

    public CompletableFuture<Optional<PlayerActions>> getPlayerActions(String player) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM player_actions WHERE player = ?";

            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, player);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        PlayerActions actions = new PlayerActions();
                        actions.setPlayer(resultSet.getString("player"));
                        actions.setActionDrop(ActionType.valueOf(resultSet.getString("action_drop")));
                        actions.setActionSwap(ActionType.valueOf(resultSet.getString("action_swap")));
                        actions.setActionInteract(ActionType.valueOf(resultSet.getString("action_interact")));

                        return Optional.of(actions);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get player actions", e);
            }

            return Optional.empty();
        }, dbExecutor);
    }

    public CompletableFuture<Void> updateActionDrop(String player, ActionType actionDrop) {
        return updateSingleAction(player, "action_drop", actionDrop);
    }

    public CompletableFuture<Void> updateActionSwap(String player, ActionType actionSwap) {
        return updateSingleAction(player, "action_swap", actionSwap);
    }

    public CompletableFuture<Void> updateActionInteract(String player, ActionType actionInteract) {
        return updateSingleAction(player, "action_interact", actionInteract);
    }

    // Универсальный безопасный метод обновления одного столбца
    private CompletableFuture<Void> updateSingleAction(String player, String column, ActionType actionType) {
        return CompletableFuture.runAsync(() -> {
            String sql;
            if (isSQLite) {
                sql = "INSERT INTO player_actions (player, " + column + ") VALUES (?, ?) " +
                        "ON CONFLICT(player) DO UPDATE SET " + column + " = excluded." + column + ";";
            } else {
                sql = "INSERT INTO player_actions (player, " + column + ") VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE " + column + " = VALUES(" + column + ");";
            }

            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, player);
                statement.setString(2, actionType.name());
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to update " + column, e);
            }
        }, dbExecutor);
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            dbExecutor.shutdown();
        }
    }

}