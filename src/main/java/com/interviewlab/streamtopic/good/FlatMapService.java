package com.interviewlab.streamtopic.good;

import java.util.List;
import java.util.stream.Collectors;

/** {@code flatMap}, bir koleksiyon stream'ini, tek bir pipeline içinde, elemanlarının tek bir düz stream'ine dönüştürür. */
public class FlatMapService {

    public List<String> allItemsFlat(List<List<String>> ordersWithItems) {
        return ordersWithItems.stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }
}
