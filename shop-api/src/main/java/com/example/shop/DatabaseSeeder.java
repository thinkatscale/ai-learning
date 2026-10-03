package com.example.shop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Generates demo data inside SQLite itself (recursive CTE), which is far faster than
 * inserting millions of rows from Java. Runs only when the tables are empty, so the
 * data survives container restarts when /data is a volume.
 */
@Component
public class DatabaseSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSeeder.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    @Value("${app.seed.products:1000}")
    private int productCount;

    @Value("${app.seed.sales:2000000}")
    private int salesCount;

    public DatabaseSeeder(JdbcTemplate jdbc, PlatformTransactionManager tm) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(tm);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!hasRows("products")) seedProducts();
        if (!hasRows("sales") && salesCount > 0) seedSales();
        createIndexes();
    }

    private boolean hasRows(String table) {
        Integer v = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM " + table + ")", Integer.class);
        return v != null && v == 1;
    }

    private void seedProducts() {
        log.info("Seeding {} products + inventory...", productCount);
        tx.executeWithoutResult(status -> {
            jdbc.update("""
                WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < ?)
                INSERT INTO products (sku, name, category, unit_price)
                SELECT printf('SKU-%05d', n),
                       'Product ' || n,
                       CASE n % 6 WHEN 0 THEN 'Electronics' WHEN 1 THEN 'Home' WHEN 2 THEN 'Grocery'
                                  WHEN 3 THEN 'Clothing'    WHEN 4 THEN 'Toys' ELSE 'Sports' END,
                       round(5 + (abs(random()) % 49500) / 100.0, 2)
                FROM seq
                """, productCount);

            jdbc.update("""
                INSERT INTO inventory (product_id, quantity_on_hand, reorder_level, updated_at)
                SELECT id, abs(random()) % 500, 20 + abs(random()) % 60, datetime('now')
                FROM products
                """);
        });
    }

    private void seedSales() {
        Integer products = jdbc.queryForObject("SELECT COUNT(*) FROM products", Integer.class);
        log.info("Seeding {} sales rows (this takes a little while)...", salesCount);
        long t0 = System.currentTimeMillis();

        // MATERIALIZED makes sure each random value is generated once per row
        jdbc.update("""
            WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < ?),
            gen AS MATERIALIZED (
              SELECT 1 + abs(random()) % ?     AS pid,
                     1 + abs(random()) % 50000 AS cust,
                     abs(random()) % 5         AS reg,
                     1 + abs(random()) % 10    AS qty,
                     abs(random()) % 1095      AS day_offset
              FROM seq
            )
            INSERT INTO sales (product_id, customer_id, region, quantity, unit_price, total, sale_date)
            SELECT g.pid,
                   g.cust,
                   CASE g.reg WHEN 0 THEN 'NORTH' WHEN 1 THEN 'SOUTH' WHEN 2 THEN 'EAST'
                              WHEN 3 THEN 'WEST'  ELSE 'CENTRAL' END,
                   g.qty,
                   p.unit_price,
                   round(g.qty * p.unit_price, 2),
                   date('2023-01-01', '+' || g.day_offset || ' days')
            FROM gen g JOIN products p ON p.id = g.pid
            """, salesCount, products);

        log.info("Seeded sales in {} ms", System.currentTimeMillis() - t0);
    }

    // Created after the bulk load: much faster than maintaining them during insert.
    private void createIndexes() {
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_sales_product  ON sales(product_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_sales_customer ON sales(customer_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_sales_date     ON sales(sale_date)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_sales_region_date ON sales(region, sale_date)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_products_category ON products(category)");
        jdbc.execute("ANALYZE");
    }
}
