package com.example.shop;

import com.example.shop.Models.NewSale;
import com.example.shop.Models.Sale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class SaleService {

    private static final String COLUMNS =
            "SELECT id, product_id, customer_id, region, quantity, unit_price, total, sale_date FROM sales";

    private static final RowMapper<Sale> MAPPER = (rs, i) -> new Sale(
            rs.getLong("id"), rs.getLong("product_id"), rs.getLong("customer_id"),
            rs.getString("region"), rs.getInt("quantity"), rs.getDouble("unit_price"),
            rs.getDouble("total"), rs.getString("sale_date"));

    private final JdbcTemplate jdbc;

    public SaleService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Keyset pagination: pass the last id you received as afterId. Fast even at row 1,999,990. */
    public List<Sale> list(Long productId, Long customerId, String region,
                           LocalDate from, LocalDate to, Long afterId, int size) {
        size = Math.max(1, Math.min(size, 200));
        StringBuilder sql = new StringBuilder(COLUMNS).append(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (productId != null)  { sql.append(" AND product_id = ?");  args.add(productId); }
        if (customerId != null) { sql.append(" AND customer_id = ?"); args.add(customerId); }
        if (region != null)     { sql.append(" AND region = ?");      args.add(region.toUpperCase()); }
        if (from != null)       { sql.append(" AND sale_date >= ?");  args.add(from.toString()); }
        if (to != null)         { sql.append(" AND sale_date <= ?");  args.add(to.toString()); }
        if (afterId != null)    { sql.append(" AND id > ?");          args.add(afterId); }
        sql.append(" ORDER BY id LIMIT ?");
        args.add(size);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    public Sale get(long id) {
        List<Sale> rows = jdbc.query(COLUMNS + " WHERE id = ?", MAPPER, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sale not found");
        return rows.get(0);
    }

    /**
     * Records a sale and decrements stock atomically. The UPDATE comes first so the write lock
     * is taken immediately (avoids SQLite "database is locked" on read-then-write upgrades).
     */
    @Transactional
    public Sale create(NewSale req) {
        int updated = jdbc.update("""
                UPDATE inventory
                SET quantity_on_hand = quantity_on_hand - ?, updated_at = datetime('now')
                WHERE product_id = ? AND quantity_on_hand >= ?
                """, req.quantity(), req.productId(), req.quantity());

        if (updated == 0) {
            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM inventory WHERE product_id = ?", Integer.class, req.productId());
            if (exists == null || exists == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock");
        }

        Double price = jdbc.queryForObject(
                "SELECT unit_price FROM products WHERE id = ?", Double.class, req.productId());
        double total = Math.round(price * req.quantity() * 100.0) / 100.0;

        jdbc.update("""
                INSERT INTO sales (product_id, customer_id, region, quantity, unit_price, total, sale_date)
                VALUES (?, ?, ?, ?, ?, ?, date('now'))
                """, req.productId(), req.customerId(), req.region().toUpperCase(),
                req.quantity(), price, total);

        Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        return get(id);
    }
}
