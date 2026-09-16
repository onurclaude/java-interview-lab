package com.interviewlab.javacore.resources.good;

import com.interviewlab.javacore.resources.TrackedResource;

/**
 * {@link com.interviewlab.javacore.resources.bad.ManualCloseWithoutFinallyService}'in doğru
 * karşılığı: try-with-resources, derleyici tarafından üretilen bir {@code finally} bloğuna
 * çevrilir - {@code close()}, {@code doWork()} normal dönse de exception fırlatsa da HER
 * ZAMAN çağrılır. {@code finalize()} (deprecated, JEP 421 ile kaldırılmaya aday) buna
 * GÜVENİLİR bir alternatif DEĞİLDİR - GC'nin ne zaman (hatta çalışıp çalışmayacağı bile)
 * garanti edilmez.
 */
public class TryWithResourcesService {

    public void run(boolean shouldFail) {
        try (TrackedResource resource = new TrackedResource()) {
            resource.doWork(shouldFail);
        } // derleyici burada resource.close()'u finally-benzeri bir blokta çağırır
    }
}
