CREATE TABLE IF NOT EXISTS products (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  sku        TEXT NOT NULL UNIQUE,
  name       TEXT NOT NULL,
  category   TEXT NOT NULL,
  unit_price REAL NOT NULL
);

CREATE TABLE IF NOT EXISTS inventory (
  product_id       INTEGER PRIMARY KEY REFERENCES products(id),
  quantity_on_hand INTEGER NOT NULL CHECK (quantity_on_hand >= 0),
  reorder_level    INTEGER NOT NULL,
  updated_at       TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS sales (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  product_id  INTEGER NOT NULL REFERENCES products(id),
  customer_id INTEGER NOT NULL,
  region      TEXT NOT NULL,
  quantity    INTEGER NOT NULL,
  unit_price  REAL NOT NULL,
  total       REAL NOT NULL,
  sale_date   TEXT NOT NULL            -- ISO date, e.g. 2024-05-17
);
