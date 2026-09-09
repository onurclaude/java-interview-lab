package com.interviewlab.concurrency.threadlocal.bad;

/**
 * NE YANLIŞ?
 * {@link #setCorrelationId(String)}, bir {@link ThreadLocal} değeri ayarlar ve hiçbir yerde
 * bu değer üzerinde {@code remove()} çağrılmaz.
 *
 * <p>NEDEN YANLIŞ?
 * Bir {@link ThreadLocal} değeri, onu ayarlayan "mantıksal görev" (ör. bir HTTP isteği) kadar
 * değil, {@link Thread} nesnesi yaşadığı sürece yaşar. Havuzlanmış (pooled) worker thread'ler
 * (bir {@link java.util.concurrent.ExecutorService}'ten ya da bir servlet container'ının
 * istek işleme havuzundan) birçok görev arasında yeniden kullanılır. Bir görev bir
 * {@code ThreadLocal} ayarlar ve temizlemezse, aynı havuzlanmış thread üzerinde çalışan
 * bir sonraki, tamamen alakasız görev bu bayat (stale) değeri devralır.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * İstek B'nin logları istek A'nın correlation ID'sini gösterir (bu da olay araştırmasını
 * (incident investigation) aktif olarak yanıltıcı hale getirir), ya da daha kötüsü: bir
 * güvenlik principal'ı ya da tenant ID'si taşıyan bir {@code ThreadLocal}, yeniden
 * kullanılan bir thread üzerinde bir kullanıcının isteğinden başka bir kullanıcının isteğine
 * sızar - kozmetik değil, gerçek bir veri izolasyonu hatası.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code ThreadLocalTest.shouldLeakThreadLocalStateWhenRemoveIsNotCalled()}, tek thread'li
 * bir executor kullanır (aynı OS thread'inin her iki görevi de işlemesini garanti eder),
 * görev 1'de temizlemeden bir ID ayarlar ve hiçbir şey ayarlamamış olan görev 2'nin hâlâ
 * görev 1'in değerini okuduğunu gösterir.
 *
 * <p>NASIL DÜZELTİLİR?
 * {@code set()}'i her zaman {@code try { ... } finally { threadLocal.remove(); } } ile
 * eşleştir - bkz.
 * {@link com.interviewlab.concurrency.threadlocal.good.CleanCorrelationIdService}.
 */
public class LeakyCorrelationIdService {

    private static final ThreadLocal<String> CORRELATION_ID = new ThreadLocal<>();

    public void setCorrelationId(String id) {
        CORRELATION_ID.set(id);
        // remove() yok - bu "istek" bittikten sonra da değer kalıcı olur.
    }

    public String getCorrelationId() {
        return CORRELATION_ID.get();
    }
}
