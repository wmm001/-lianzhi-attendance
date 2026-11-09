import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.StringJoiner;

/** One-time local demo-data migration. The MySQL target must be empty. */
public final class MigrateH2ToMySql {
    private static final String[] TABLES = {
        "user", "course", "office", "attendance_session", "attendance_session_member",
        "student_course", "student_group", "student_group_member", "attendance",
        "leave_request", "attendance_audit", "face_reenroll_request", "employee_office",
        "work_attendance"
    };

    public static void main(String[] args) throws Exception {
        String password = System.getenv("DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("DB_PASSWORD is required");
        }
        String h2 = "jdbc:h2:file:./data/attendance;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;AUTO_SERVER=TRUE";
        String mysql = "jdbc:mysql://127.0.0.1:3306/lianzhi_attendance?useUnicode=true&characterEncoding=utf8" +
                "&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
        try (Connection source = DriverManager.getConnection(h2, "sa", "");
             Connection destination = DriverManager.getConnection(mysql, "lianzhi_app", password)) {
            destination.setAutoCommit(false);
            try {
                for (String table : TABLES) {
                    try (Statement query = destination.createStatement();
                         ResultSet result = query.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
                        result.next();
                        if (result.getLong(1) != 0) {
                            throw new IllegalStateException("MySQL table is not empty: " + table);
                        }
                    }
                }
                for (String table : TABLES) {
                    int count = copyTable(source, destination, table);
                    System.out.println(table + ": " + count + " rows");
                }
                destination.commit();
            } catch (Exception error) {
                destination.rollback();
                throw error;
            }
        }
    }

    private static int copyTable(Connection source, Connection destination, String table) throws Exception {
        try (Statement query = source.createStatement();
             ResultSet rows = query.executeQuery("SELECT * FROM " + table)) {
            ResultSetMetaData metadata = rows.getMetaData();
            int columns = metadata.getColumnCount();
            StringJoiner names = new StringJoiner(",");
            StringJoiner placeholders = new StringJoiner(",");
            for (int index = 1; index <= columns; index++) {
                names.add("`" + metadata.getColumnLabel(index) + "`");
                placeholders.add("?");
            }
            String sql = "INSERT INTO `" + table + "` (" + names + ") VALUES (" + placeholders + ")";
            int count = 0;
            try (PreparedStatement insert = destination.prepareStatement(sql)) {
                while (rows.next()) {
                    for (int index = 1; index <= columns; index++) {
                        Object value = rows.getObject(index);
                        if (value instanceof Clob) {
                            Clob clob = (Clob) value;
                            value = clob.getSubString(1, Math.toIntExact(clob.length()));
                        }
                        insert.setObject(index, value);
                    }
                    insert.executeUpdate();
                    count++;
                }
            }
            return count;
        }
    }
}
