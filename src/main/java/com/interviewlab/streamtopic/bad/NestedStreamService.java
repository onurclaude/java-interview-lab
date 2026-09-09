package com.interviewlab.streamtopic.bad;

import java.util.List;
import java.util.stream.Collectors;

/**
 * NE YANLIŞ?
 * Tüm siparişlerdeki her öğenin düz bir listesini, her siparişi KENDİ öğe listesine
 * eşleyerek elde etmek; bu bir {@code List<List<String>>} üretir - ve onu düzleştirmek için
 * ayrı, ikinci bir adım (manuel bir döngü ya da başka bir stream pipeline) gerektirir.
 *
 * <p>NEDEN YANLIŞ? Bir doğruluk hatası değil, okunabilirlik/idiyom sorunu: {@link #allItemsNested}
 * çağıranı, sonucu kendisinin düzleştirmesi gerektiğini bilmek zorundadır ve "tüm
 * siparişlerdeki tüm öğelere" ihtiyaç duyan diğer her yer, ya bu düzleştirme adımını
 * tekrarlar ya da sonucunun hâlâ iş gerektirdiğini adından belli etmeyen bir metodu çağırır.
 *
 * <p>DESEN DÜZELTMESİ: {@code flatMap} - bkz.
 * {@link com.interviewlab.streamtopic.good.FlatMapService}, zaten düz olan sonucu
 * doğrudan döndürür, herhangi bir çağrı noktasında ayrı bir düzleştirme adımına gerek
 * kalmaz.
 */
public class NestedStreamService {

    public List<List<String>> allItemsNested(List<List<String>> ordersWithItems) {
        return ordersWithItems.stream().collect(Collectors.toList()); // hâlâ List<List<String>> - düz değil
    }
}
