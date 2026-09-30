package com.cloudsql.lab.problem;

public class QueryValidationException extends RuntimeException {
    public QueryValidationException(String message) {
        super(message);
    }
}
