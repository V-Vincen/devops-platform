package com.vincent.devops.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchDeleteRequestTest {

    @Test
    void shouldKeepDistinctIdsForBatchDeletion() {
        List<Long> ids = List.of(1L, 2L);

        assertEquals(ids, new BatchDeleteRequest(ids).uniqueIds());
    }

    @Test
    void shouldRejectDuplicateIdsBeforeServiceExecution() {
        BatchDeleteRequest request = new BatchDeleteRequest(List.of(1L, 1L));

        assertThrows(IllegalArgumentException.class, request::uniqueIds);
    }
}
