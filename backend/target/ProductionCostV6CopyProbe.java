
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HexFormat;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import gt.cadprint.manager.CadprintApplication;

class CadprintV3Probe {
    private static final List<String> TABLES = List.of("materials", "supplier", "movements", "material_changes", "categories",
            "purchases", "purchase_lines", "purchase_receipts", "purchase_events",
            "product_categories", "catalog_skus", "products", "product_variants", "product_images",
            "production_cost_profiles", "production_machines", "production_calculations", "production_fdm_details",
            "production_sublimation_details", "production_cost_lines");

    public static void main(String[] args) throws Exception {
        String url = "jdbc:h2:file:" + args[1] + ";IFEXISTS=TRUE";
        if (args[0].equals("snapshot")) {
            try (Connection connection = DriverManager.getConnection(url + ";ACCESS_MODE_DATA=r", "sa", "")) {
                inspect(connection);
            }
            return;
        }
        try (var context = new SpringApplicationBuilder(CadprintApplication.class).run(
                "--server.port=0", "--spring.datasource.url=" + url, "--logging.level.root=WARN");
             var connection = context.getBean(DataSource.class).getConnection()) {
            String mode = context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto");
            if (!"validate".equals(mode)) throw new IllegalStateException("Hibernate mode: " + mode);
            System.out.println("PROBE Startup=OK ddl-auto=" + mode + " profiles="
                    + java.util.Arrays.toString(context.getEnvironment().getActiveProfiles()));
            inspect(connection);
            verifyForeignKey(connection);
        }
    }

    private static void inspect(Connection connection) throws Exception {
        System.out.println("PROBE JDBC=" + connection.getMetaData().getURL());
        try (var statement = connection.createStatement();
             var rs = statement.executeQuery("SELECT \"version\", \"success\" FROM \"flyway_schema_history\" "
                     + "WHERE \"version\" IS NOT NULL ORDER BY \"installed_rank\"")) {
            while (rs.next()) System.out.println("PROBE FLYWAY " + rs.getString(1) + " " + rs.getBoolean(2));
        }
        for (String table : TABLES) {
            try (var metadata = connection.getMetaData().getTables(null, "PUBLIC",
                    table.toUpperCase(java.util.Locale.ROOT), new String[]{"BASE TABLE"})) {
                if (!metadata.next()) continue;
            }
            String columns = table.equals("movements")
                    ? "id,material_id,type,quantity,before_stock,after_stock,reason,username,created_at" : "*";
            try (var statement = connection.createStatement();
                 var rs = statement.executeQuery("SELECT " + columns + " FROM " + table + " ORDER BY " + (table.endsWith("_details") ? "calculation_id" : "id"))) {
                var digest = MessageDigest.getInstance("SHA-256");
                int count = 0;
                while (rs.next()) {
                    count++;
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                        String value = rs.getString(i);
                        String field = value == null ? "-1:" : value.length() + ":" + value;
                        digest.update(field.getBytes(StandardCharsets.UTF_8));
                    }
                }
                System.out.println("PROBE DATA " + table + " " + count + " " + HexFormat.of().formatHex(digest.digest()));
            }
        }
    }

    private static void verifyForeignKey(Connection connection) throws Exception {
        try (var statement = connection.createStatement();
             var rs = statement.executeQuery("""
                     SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_NAME = 'MOVEMENTS'
                     AND ((CONSTRAINT_NAME = 'UQ_MOVEMENTS_PURCHASE_LINE' AND CONSTRAINT_TYPE = 'UNIQUE')
                     OR (CONSTRAINT_NAME = 'FK_MOVEMENTS_PURCHASE_LINE_MATERIAL' AND CONSTRAINT_TYPE = 'FOREIGN KEY'))
                     """)) {
            if (!rs.next() || rs.getInt(1) != 2) throw new IllegalStateException("Missing V3 constraints");
        }
        try (var statement = connection.createStatement();
             var rs = statement.executeQuery("SELECT COUNT(purchase_line_id) FROM movements")) {
            rs.next();
            System.out.println("PROBE Column and constraints=OK; linked movements=" + rs.getInt(1));
        }
    }
}

