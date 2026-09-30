package com.cloudsql.lab.problem;

public class ProblemNotFoundException extends RuntimeException {
    public ProblemNotFoundException(long id) {
        super("Problem " + id + " was not found.");
    }
}
