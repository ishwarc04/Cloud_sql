CREATE TABLE customers (
  id INTEGER PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  city VARCHAR(80) NOT NULL
);

CREATE TABLE orders (
  id INTEGER PRIMARY KEY,
  customer_id INTEGER NOT NULL REFERENCES customers(id),
  total DECIMAL(12, 2) NOT NULL
);

INSERT INTO customers VALUES
  (1, 'Northstar Labs', 'Pune'),
  (2, 'Paper Kite', 'Bengaluru'),
  (3, 'Juniper Works', 'Mumbai'),
  (4, 'Grey Pine', 'Delhi');

INSERT INTO orders VALUES
  (101, 1, 4200.00),
  (102, 3, 2750.00),
  (103, 1, 1350.00);
