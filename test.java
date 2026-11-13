import java.sql.*;

public class test-connection {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5433/insurance_integration";
        String user = "rider";
        String password = "postgres";
        
        try {
            Class.forName("org.postgresql.Driver");
            System.out.println("Attempting to connect to: " + url);
            System.out.println("User: " + user);
            
            Connection conn = DriverManager.getConnection(url, user, password);
            System.out.println("Connected successfully!");
            
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT 1");
            if (rs.next()) {
                System.out.println("Query result: " + rs.getInt(1));
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
