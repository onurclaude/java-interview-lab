package com.interviewlab.streamtopic;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewlab.streamtopic.bad.NestedStreamService;
import com.interviewlab.streamtopic.bad.ParallelStreamSharedMutableStateService;
import com.interviewlab.streamtopic.good.FlatMapService;
import com.interviewlab.streamtopic.good.ParallelStreamCollectService;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Stream API yazısı için docs mülakat materyaline bakın. */
class StreamApiTest {

    private static final List<Integer> LARGE_INPUT = IntStream.rangeClosed(1, 50_000).boxed().collect(Collectors.toList());

    @Test
    void shouldLoseElementsWithParallelStreamAndSharedMutableArrayList() {
        ParallelStreamSharedMutableStateService service = new ParallelStreamSharedMutableStateService();

        boolean corruptionObserved = false;
        for (int attempt = 0; attempt < 10 && !corruptionObserved; attempt++) {
            try {
                List<Integer> result = service.squareAll(LARGE_INPUT);
                if (result.size() != LARGE_INPUT.size()) {
                    corruptionObserved = true;
                }
            } catch (RuntimeException e) {
                // senkronize edilmemiş ArrayList'ten gelen ArrayIndexOutOfBoundsException veya benzeri de sayılır
                corruptionObserved = true;
            }
        }

        assertThat(corruptionObserved)
                .as("paralel bir stream'in forEach'inden paylaşılan düz bir ArrayList'i değiştirmek, yeterince "
                        + "büyük bir girdi üzerinde birkaç denemede eninde sonunda eleman kaybına ya da exception'a yol açmalı")
                .isTrue();
    }

    @Test
    void shouldNeverLoseElementsWithParallelStreamCollect() {
        ParallelStreamCollectService service = new ParallelStreamCollectService();

        for (int attempt = 0; attempt < 10; attempt++) {
            List<Integer> result = service.squareAll(LARGE_INPUT);
            assertThat(result).hasSize(LARGE_INPUT.size());
        }
    }

    @Test
    void shouldFlattenNestedListsWithFlatMapButNotWithPlainCollect() {
        List<List<String>> ordersWithItems = List.of(List.of("a", "b"), List.of("c"));

        NestedStreamService nestedStreamService = new NestedStreamService();
        assertThat(nestedStreamService.allItemsNested(ordersWithItems)).hasSize(2); // hâlâ 2 liste, düzleştirilmedi

        FlatMapService flatMapService = new FlatMapService();
        assertThat(flatMapService.allItemsFlat(ordersWithItems)).containsExactly("a", "b", "c");
    }
}
