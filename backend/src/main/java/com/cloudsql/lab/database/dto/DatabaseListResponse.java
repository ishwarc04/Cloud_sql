package com.cloudsql.lab.database.dto;

import java.util.List;

public record DatabaseListResponse(List<DatabaseResponse> databases, DatabaseQuotaResponse quota) {
}
