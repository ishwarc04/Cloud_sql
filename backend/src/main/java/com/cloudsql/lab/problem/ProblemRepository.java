package com.cloudsql.lab.problem;

import com.cloudsql.lab.problem.model.ColumnPreview;
import com.cloudsql.lab.problem.model.ProblemDefinition;
import com.cloudsql.lab.problem.model.TablePreview;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ProblemRepository {
    private final List<ProblemDefinition> problems = List.of(
            new ProblemDefinition(1, "second-highest-salary", "Second Highest Salary", "Medium", "Aggregation",
                    "Return the second highest distinct salary from the employees table. Name the result second_highest_salary. If no second salary exists, return null.",
                    "SELECT *\nFROM employees;",
                    "SELECT MAX(salary) AS second_highest_salary FROM employees WHERE salary < (SELECT MAX(salary) FROM employees)",
                    "problems/problem-1.sql",
                    List.of(new TablePreview("employees",
                            List.of(new ColumnPreview("id", "INTEGER"), new ColumnPreview("name", "VARCHAR"), new ColumnPreview("department", "VARCHAR"), new ColumnPreview("salary", "DECIMAL")),
                            List.of(List.of(1, "Maya", "Engineering", 98000), List.of(2, "Noah", "Finance", 87000), List.of(3, "Ava", "Engineering", 112000))))),
            new ProblemDefinition(2, "customers-without-orders", "Customers Without Orders", "Easy", "Joins",
                    "List the id and name of every customer who has never placed an order. Sort the result by customer id.",
                    "SELECT *\nFROM customers;",
                    "SELECT c.id, c.name FROM customers c LEFT JOIN orders o ON o.customer_id = c.id WHERE o.id IS NULL ORDER BY c.id",
                    "problems/problem-2.sql",
                    List.of(
                            new TablePreview("customers", List.of(new ColumnPreview("id", "INTEGER"), new ColumnPreview("name", "VARCHAR"), new ColumnPreview("city", "VARCHAR")), List.of(List.of(1, "Northstar Labs", "Pune"), List.of(2, "Paper Kite", "Bengaluru"), List.of(3, "Juniper Works", "Mumbai"))),
                            new TablePreview("orders", List.of(new ColumnPreview("id", "INTEGER"), new ColumnPreview("customer_id", "INTEGER"), new ColumnPreview("total", "DECIMAL")), List.of(List.of(101, 1, 4200), List.of(102, 3, 2750))))),
            new ProblemDefinition(3, "department-salary-report", "Department Salary Report", "Medium", "Grouping",
                    "For each department, return the department name, employee count, and average salary rounded to two decimal places. Order by average salary descending.",
                    "SELECT *\nFROM employees;",
                    "SELECT department, COUNT(*) AS employee_count, ROUND(AVG(salary), 2) AS average_salary FROM employees GROUP BY department ORDER BY average_salary DESC",
                    "problems/problem-3.sql",
                    List.of(new TablePreview("employees",
                            List.of(new ColumnPreview("id", "INTEGER"), new ColumnPreview("name", "VARCHAR"), new ColumnPreview("department", "VARCHAR"), new ColumnPreview("salary", "DECIMAL")),
                            List.of(List.of(1, "Ira", "Platform", 105000), List.of(2, "Leo", "Data", 96000), List.of(3, "Zara", "Platform", 99000)))))
    );

    public List<ProblemDefinition> findAll() {
        return problems;
    }

    public Optional<ProblemDefinition> findById(long id) {
        return problems.stream().filter(problem -> problem.id() == id).findFirst();
    }
}
