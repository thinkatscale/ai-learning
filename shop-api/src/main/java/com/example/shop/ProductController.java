package com.example.shop;

import com.example.shop.Models.Product;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final RowMapper<Product> MAPPER = (rs, i) -> new Product(
            rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
            rs.getString("category"), rs.getDouble("unit_price"));

    private final JdbcTemplate jdbc;

    public ProductController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Product> list(@RequestParam(required = false) String category,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size) {
        size = Math.max(1, Math.min(size, 100));
        List<Object> args = new ArrayList<>();
        String sql = "SELECT id, sku, name, category, unit_price FROM products";
        if (category != null) {
            sql += " WHERE category = ?";
            args.add(category);
        }
        sql += " ORDER BY id LIMIT ? OFFSET ?";
        args.add(size);
        args.add((long) Math.max(0, page) * size);
        return jdbc.query(sql, MAPPER, args.toArray());
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable long id) {
        List<Product> rows = jdbc.query(
                "SELECT id, sku, name, category, unit_price FROM products WHERE id = ?", MAPPER, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        return rows.get(0);
    }
}
