package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args) {
        Connection conn = null;
        try {
            // JDBC Driver Registration
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Creating a connection to the database
            conn = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/test",
                    "user",
                    "1234");

            System.out.println("Connection established successfully!");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                    System.out.println("Connection closed successfully!");
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}