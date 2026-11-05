package com.insurance.integration.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Paginated response wrapper for list APIs.
 * All list endpoints must return this structure.
 *
 * @param <T> Type of records in the page.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PagedResponse<T> {

    private final boolean success;
    private final String message;
    private final List<T> records;
    private final int currentPage;
    private final int pageSize;
    private final long totalRecords;
    private final int totalPages;
    private final boolean lastPage;
    private final String sortField;
    private final String sortDirection;
    private final Instant timestamp;

    public static <T> PagedResponse<T> of(
            List<T> records,
            int currentPage,
            int pageSize,
            long totalRecords,
            String sortField,
            String sortDirection) {

        int totalPages = (int) Math.ceil((double) totalRecords / pageSize);
        return PagedResponse.<T>builder()
                .success(true)
                .records(records)
                .currentPage(currentPage)
                .pageSize(pageSize)
                .totalRecords(totalRecords)
                .totalPages(totalPages)
                .lastPage(currentPage >= totalPages - 1)
                .sortField(sortField)
                .sortDirection(sortDirection)
                .timestamp(Instant.now())
                .build();
    }
}
