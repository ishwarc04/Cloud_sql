CREATE TABLE employees (
  id INTEGER PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  department VARCHAR(80) NOT NULL,
  salary DECIMAL(12, 2) NOT NULL
);

INSERT INTO employees VALUES
  (1, 'Ira', 'Platform', 105000.00),
  (2, 'Leo', 'Data', 96000.00),
  (3, 'Zara', 'Platform', 99000.00),
  (4, 'Ravi', 'Data', 102000.00),
  (5, 'Mina', 'Design', 88000.00),
  (6, 'Omar', 'Design', 92000.00);
