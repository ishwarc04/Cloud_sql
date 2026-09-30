CREATE TABLE employees (
  id INTEGER PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  department VARCHAR(80) NOT NULL,
  salary DECIMAL(12, 2) NOT NULL
);

INSERT INTO employees VALUES
  (1, 'Maya', 'Engineering', 98000.00),
  (2, 'Noah', 'Finance', 87000.00),
  (3, 'Ava', 'Engineering', 112000.00),
  (4, 'Ethan', 'Product', 98000.00),
  (5, 'Sofia', 'Finance', 76000.00);
