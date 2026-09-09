package com.interviewlab.immutability.good;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link com.interviewlab.immutability.bad.MutableEventLog}'un doğru karşılığı:
 * {@link #getEvents()}, gerçek bir kopya dışarı verir ({@code List.copyOf}, kendisi
 * değiştirilemez VE ayrı bir dizi tarafından desteklenir), bu yüzden çağıranın döndürülen
 * liste üzerinde yaptığı hiçbir şey bu nesnenin dahili durumunu etkileyemez ve bunun tersi
 * de geçerlidir.
 */
public class DefensiveCopyEventLog {

    private final List<String> events = new ArrayList<>();

    public void record(String event) {
        events.add(event);
    }

    public List<String> getEvents() {
        return List.copyOf(events); // gerçek, bağımsız bir kopya - sadece değiştirilemez bir görünüm değil
    }
}
