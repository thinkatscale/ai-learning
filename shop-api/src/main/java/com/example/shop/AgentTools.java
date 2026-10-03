package com.example.shop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The agent's "hands": READ-ONLY tools that wrap the existing API logic.
 * Tool failures are returned to the model as {"error": "..."} so it can correct itself.
 */
@Component
public class AgentTools {

    private static final int MAX_RESULT_CHARS = 8000;

    private static final String TOOLS_JSON = """
    [
     {"type":"function","function":{"name":"get_stats",
       "description":"Row counts: total sales, total products, number of low-stock products.",
       "parameters":{"type":"object","properties":{}}}},

     {"type":"function","function":{"name":"list_products",
       "description":"Browse the product catalog (id, sku, name, category, unitPrice), ONE PAGE at a time (default 20). Not for counting or scanning all products, and not for stock questions: use search_inventory for those.",
       "parameters":{"type":"object","properties":{
         "category":{"type":"string","enum":["Electronics","Home","Grocery","Clothing","Toys","Sports"]},
         "page":{"type":"integer","description":"0-based page"},
         "size":{"type":"integer","description":"1-100, default 20"}}}}},

     {"type":"function","function":{"name":"get_inventory_for_products",
       "description":"Current stock (quantityOnHand, reorderLevel) for specific product ids. Use this to check stock of products found by other tools.",
       "parameters":{"type":"object","properties":{
         "product_ids":{"type":"array","items":{"type":"integer"},"description":"Up to 100 product ids"}},
         "required":["product_ids"]}}},

     {"type":"function","function":{"name":"list_low_stock",
       "description":"Products whose quantityOnHand is at or below their reorderLevel, ordered by product id.",
       "parameters":{"type":"object","properties":{
         "page":{"type":"integer","description":"0-based page"},
         "size":{"type":"integer","description":"1-100, default 50"}}}}},

     {"type":"function","function":{"name":"search_inventory",
       "description":"Search stock levels, joined with product info, with filters. Returns total (all matches), returned (this page) and items, lowest stock first. Use for out-of-stock / low-stock questions, optionally within a category. If total is larger than returned, page through or tell the user the result is partial.",
       "parameters":{"type":"object","properties":{
         "category":{"type":"string","enum":["Electronics","Home","Grocery","Clothing","Toys","Sports"]},
         "max_quantity":{"type":"integer","description":"Only items with quantityOnHand <= this. Use 0 for out of stock."},
         "low_stock_only":{"type":"boolean","description":"Only items at or below their reorderLevel"},
         "page":{"type":"integer","description":"0-based page"},
         "size":{"type":"integer","description":"1-100, default 50"}}}}},

     {"type":"function","function":{"name":"top_products",
       "description":"Best-selling products ranked by revenue, with unitsSold, revenue AND current stock (quantityOnHand, reorderLevel, lowStock = 1 if at or below reorder level). No need to call another tool to check stock of these products. Optional date range.",
       "parameters":{"type":"object","properties":{
         "limit":{"type":"integer","description":"1-100, default 10"},
         "from":{"type":"string","description":"YYYY-MM-DD inclusive"},
         "to":{"type":"string","description":"YYYY-MM-DD inclusive"}}}}},

     {"type":"function","function":{"name":"sales_by_region",
       "description":"Orders, units and revenue per region (NORTH, SOUTH, EAST, WEST, CENTRAL). Optional date range.",
       "parameters":{"type":"object","properties":{
         "from":{"type":"string","description":"YYYY-MM-DD inclusive"},
         "to":{"type":"string","description":"YYYY-MM-DD inclusive"}}}}},

     {"type":"function","function":{"name":"monthly_revenue",
       "description":"Orders and revenue for each month of a given year.",
       "parameters":{"type":"object","properties":{
         "year":{"type":"integer","description":"e.g. 2024"}},"required":["year"]}}}
    ]
    """;

    private final ObjectMapper mapper;
    private final ProductController products;
    private final InventoryController inventory;
    private final ReportController reports;
    private final ArrayNode specs;

    public AgentTools(ObjectMapper mapper, ProductController products,
                      InventoryController inventory, ReportController reports) throws JsonProcessingException {
        this.mapper = mapper;
        this.products = products;
        this.inventory = inventory;
        this.reports = reports;
        this.specs = (ArrayNode) mapper.readTree(TOOLS_JSON);
    }

    public ArrayNode specs() {
        return specs;
    }

    public String execute(String name, String argsJson) {
        try {
            JsonNode a = (argsJson == null || argsJson.isBlank()) ? mapper.createObjectNode() : mapper.readTree(argsJson);
            Object result = switch (name) {
                case "get_stats" -> reports.stats();
                case "list_products" -> products.list(text(a, "category"), intOr(a, "page", 0), intOr(a, "size", 20));
                case "get_inventory_for_products" -> inventory.byIds(longList(a, "product_ids"));
                case "search_inventory" -> inventory.search(text(a, "category"),
                        a.hasNonNull("max_quantity") ? Integer.valueOf(a.get("max_quantity").asInt()) : null,
                        a.path("low_stock_only").asBoolean(false), intOr(a, "page", 0), intOr(a, "size", 50));
                case "list_low_stock" -> inventory.list(true, intOr(a, "page", 0), intOr(a, "size", 50));
                case "top_products" -> reports.topProducts(intOr(a, "limit", 10), date(a, "from"), date(a, "to"));
                case "sales_by_region" -> reports.byRegion(date(a, "from"), date(a, "to"));
                case "monthly_revenue" -> reports.monthly(intOr(a, "year", 2024));
                default -> throw new IllegalArgumentException("Unknown tool: " + name);
            };
            String json = mapper.writeValueAsString(result);
            return json.length() > MAX_RESULT_CHARS ? json.substring(0, MAX_RESULT_CHARS) + "...[truncated]" : json;
        } catch (Exception e) {
            return mapper.createObjectNode().put("error", String.valueOf(e.getMessage())).toString();
        }
    }

    private static String text(JsonNode a, String f) {
        JsonNode n = a.get(f);
        return (n == null || n.isNull() || n.asText().isBlank()) ? null : n.asText();
    }

    private static int intOr(JsonNode a, String f, int def) {
        JsonNode n = a.get(f);
        return (n == null || n.isNull()) ? def : n.asInt(def);
    }

    private static LocalDate date(JsonNode a, String f) {
        String t = text(a, f);
        return t == null ? null : LocalDate.parse(t);
    }

    private static List<Long> longList(JsonNode a, String f) {
        List<Long> out = new ArrayList<>();
        JsonNode n = a.get(f);
        if (n != null && n.isArray()) n.forEach(x -> out.add(x.asLong()));
        return out;
    }
}
