import javax.sql.DataSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import gt.cadprint.manager.CadprintApplication;

class Sprint2SchemaProbe {
    public static void main(String[] args) throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            try (var context = new SpringApplicationBuilder(CadprintApplication.class).run(
                    "--server.port=0", "--spring.datasource.url=jdbc:h2:file:" + args[0],
                    "--logging.level.root=WARN");
                 var connection = context.getBean(DataSource.class).getConnection();
                 var statement = connection.createStatement();
                 var result = statement.executeQuery(
                         "SELECT (SELECT COUNT(*) FROM categories), (SELECT COUNT(*) FROM materials), "
                         + "(SELECT COUNT(*) FROM material_changes), (SELECT COUNT(*) FROM \"flyway_schema_history\" "
                         + "WHERE \"success\" AND \"version\" IN ('1','2'))")) {
                if (!result.next() || result.getInt(1) != 5 || result.getInt(2) != 0
                        || result.getInt(3) != 0 || result.getInt(4) != 2) {
                    throw new IllegalStateException("Unexpected schema");
                }
                System.out.println("File startup " + (attempt + 1) + ": schema v2, five categories, no materials or audit entries");
            }
        }
    }
}
