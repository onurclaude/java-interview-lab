package com.interviewlab.javacore.resources.bad;

import com.interviewlab.javacore.resources.TrackedResource;

/**
 * NE YANLIŞ?
 * {@link #run(boolean)}, {@code close()}'u sıradan, ardışık bir ifade olarak çağırır -
 * {@code try/finally} ya da try-with-resources OLMADAN.
 *
 * <p>NEDEN YANLIŞ?
 * {@code doWork()} bir exception fırlatırsa, çalışma doğrudan {@code close()} satırını
 * ATLAR - kaynak sonsuza dek açık kalır. Bu, projedeki
 * {@link com.interviewlab.concurrency.synchronization.bad.LockWithoutFinallyService} ile
 * AYNI kök nedendir (kilit yerine kaynak), ve modern Java'nın {@code finally} yerine
 * try-with-resources'ı NEDEN tercih ettiğinin de temel motivasyonudur.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Bir dosya handle'ı, DB connection'ı ya da socket, başarısız her istekte sızar - bir süre
 * sonra "too many open files" ya da bağlantı havuzu tükenmesi hatalarıyla süreç çöker.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code TryFinallyTest.shouldLeakResourceWhenCloseIsNotInFinally()}, {@code doWork(true)}
 * ile başarısız bir çağrı yapar ve {@code TrackedResource.closedCount()}'un hâlâ 0
 * olduğunu gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * try-with-resources kullan (bkz.
 * {@link com.interviewlab.javacore.resources.good.TryWithResourcesService}) - derleyici,
 * {@code close()}'un normal dönüşte VEYA exception'da HER ZAMAN çağrılmasını garanti eden
 * bir {@code finally} bloğu üretir.
 */
public class ManualCloseWithoutFinallyService {

    public void run(boolean shouldFail) {
        TrackedResource resource = new TrackedResource();
        resource.doWork(shouldFail); // exception fırlatırsa, aşağıdaki close() hiç çalışmaz
        resource.close();
    }
}
