package com.example.shop;

import com.example.shop.Models.InventoryItem;
import com.example.shop.Models.StockUpdate;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private static final String SELECT = """
            SELECT i.product_id, p.sku, p.name, i.quantity_on_hand, i.reorder_level, i.updated_at
            FROM inventory i JOIN products p ON p.id = i.product_id
            """;

    private static final RowMapper<InventoryItem> MAPPER = (rs, n) -> new InventoryItem(
            rs.getLong("product_id"), rs.getString("sku"), rs.getString("name"),
            rs.getInt("quantity_on_hand"), rs.getInt("reorder_level"), rs.getString("updated_at"));

    private final JdbcTemplate jdbc;

    public InventoryController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<InventoryItem> list(@RequestParam(defaultValue = "false") boolean lowStock,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        size = Math.max(1, Math.min(size, 100));
        String sql = SELECT
                + (lowStock ? " WHERE i.quantity_on_hand <= i.reorder_level" : "")
                + " ORDER BY i.product_id LIMIT ? OFFSET ?";
        return jdbc.query(sql, MAPPER, size, (long) Math.max(0, page) * size);
    }

    /** Used by the agent: filtered stock search. Returns the TOTAL match count so truncation is visible. */
    public Map<String, Object> search(String category, Integer maxQuantity, boolean lowStock, int page, int size) {
        size = Math.max(1, Math.min(size, 100));
        page = Math.max(0, page);

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (category != null)    { where.append(" AND p.category = ?");            args.add(category); }
        if (maxQuantity != null) { where.append(" AND i.quantity_on_hand <= ?");   args.add(maxQuantity); }
        if (lowStock)            { where.append(" AND i.quantity_on_hand <= i.reorder_level"); }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory i JOIN products p ON p.id = i.product_id" + where,
                Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<InventoryItem> items = jdbc.query(
                SELECT + where + " ORDER BY i.quantity_on_hand, i.product_id LIMIT ? OFFSET ?",
                MAPPER, pageArgs.toArray());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("page", page);
        out.put("returned", items.size());
        out.put("items", items);
        return out;
    }

    /** Used by the agent: stock for a specific set of products (max 100). */
    public List<InventoryItem> byIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Long> limited = ids.size() > 100 ? ids.subList(0, 100) : ids;
        String marks = String.join(",", java.util.Collections.nCopies(limited.size(), "?"));
        return jdbc.query(SELECT + " WHERE i.product_id IN (" + marks + ") ORDER BY i.product_id",
                MAPPER, limited.toArray());
    }

    @GetMapping("/{productId}")
    public InventoryItem get(@PathVariable long productId) {
        List<InventoryItem> rows = jdbc.query(SELECT + " WHERE i.product_id = ?", MAPPER, productId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not found");
        return rows.get(0);
    }

    @PutMapping("/{productId}")
    public InventoryItem setStock(@PathVariable long productId, @Valid @RequestBody StockUpdate body) {
        int updated = jdbc.update(
                "UPDATE inventory SET quantity_on_hand = ?, updated_at = datetime('now') WHERE product_id = ?",
                body.quantityOnHand(), productId);
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not found");
        return get(productId);
    }
}
