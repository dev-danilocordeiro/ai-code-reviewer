package com.example.export;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CustomerCounter {

    private final DataSource dataSource;

    public CustomerCounter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public long countActive() throws SQLException {
        Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM customers WHERE active = true");
        ResultSet rs = statement.executeQuery();
        rs.next();
        return rs.getLong(1);
    }
}
