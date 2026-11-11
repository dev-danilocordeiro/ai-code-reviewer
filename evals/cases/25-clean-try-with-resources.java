package com.example.export;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ActiveCustomerCounter {

    private static final String SQL = "SELECT COUNT(*) FROM customers WHERE active = ?";

    private final DataSource dataSource;

    public ActiveCustomerCounter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public long countActive() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SQL)) {
            statement.setBoolean(1, true);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }
}
