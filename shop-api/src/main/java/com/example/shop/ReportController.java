package com.example.shop;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregations over the multi-million-row sales table. */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final JdbcTemplate jdbc;

    public ReportController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sales", jdbc.queryForObject("SELECT COUNT(*) FROM sales", Long.class));
        m.put("products", jdbc.queryForObject("SELECT COUNT(*) FROM products", Long.class));
        m.put("lowStockProducts", jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory WHERE quantity_on_hand <= reorder_level", Long.class));
        return m;
    }

    @GetMapping("/sales-by-region")
    public List<Map<String, Object>> byRegion(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return jdbc.queryForList("""
                SELECT region, COUNT(*) AS orders, SUM(quantity) AS units, ROUND(SUM(total), 2) AS revenue
                FROM sales WHERE sale_date BETWEEN ? AND ?
                GROUP BY region ORDER BY revenue DESC
                """, lo(from), hi(to));
    }

    @GetMapping("/top-products")
    public List<Map<String, Object>> topProducts(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        limit = Math.max(1, Math.min(limit, 100));
        return jdbc.queryForList("""
                SELECT p.id AS productId, p.sku, p.name,
                       SUM(s.quantity) AS unitsSold, ROUND(SUM(s.total), 2) AS revenue,
                       i.quantity_on_hand AS quantityOnHand, i.reorder_level AS reorderLevel,
                       (i.quantity_on_hand <= i.reorder_level) AS lowStock
                FROM sales s
                JOIN products p ON p.id = s.product_id
                JOIN inventory i ON i.product_id = p.id
                WHERE s.sale_date BETWEEN ? AND ?
                GROUP BY p.id ORDER BY revenue DESC LIMIT ?
                """, lo(from), hi(to), limit);
    }

    @GetMapping("/monthly-revenue")
    public List<Map<String, Object>> monthly(@RequestParam(defaultValue = "2024") int year) {
        return jdbc.queryForList("""
                SELECT substr(sale_date, 1, 7) AS month, COUNT(*) AS orders, ROUND(SUM(total), 2) AS revenue
                FROM sales WHERE sale_date >= ? AND sale_date <= ?
                GROUP BY month ORDER BY month
                """, year + "-01-01", year + "-12-31");
    }

    private static String lo(LocalDate d) { return d == null ? "0000-01-01" : d.toString(); }
    private static String hi(LocalDate d) { return d == null ? "9999-12-31" : d.toString(); }
}
