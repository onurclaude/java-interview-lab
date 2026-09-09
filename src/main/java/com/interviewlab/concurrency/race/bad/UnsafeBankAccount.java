package com.interviewlab.concurrency.race.bad;

/**
 * NE YANLIŞ?
 * {@link #withdraw(int)}, klasik bir check-then-act (kontrol-et-sonra-uygula) yapar:
 * {@code balance}'ı oku, karar ver, sonra {@code balance}'a yaz - hiçbir senkronizasyon
 * olmadan.
 *
 * <p>NEDEN YANLIŞ?
 * "{@code if (balance >= amount) balance -= amount;}" aslında bir grup olarak atomik olmayan
 * üç ayrı işlemdir (bakiyeyi oku, karşılaştır, bakiyeye yaz). İki thread, herhangi biri geri
 * yazmadan önce aynı {@code balance}'ı okuyabilir, ikisi de yeterli bakiye görebilir ve ikisi
 * de para çekme işlemine devam edebilir - ikinci yazma, birincinin etkisinin üzerine inşa
 * etmek yerine onun üzerine yazar. Bu, tamamen uygulama seviyesindeki eşzamanlılıktan
 * kaynaklanan klasik bir "kayıp güncelleme" (lost update) örneğidir ve locking laboratuvarlarında
 * ele alınan veritabanı kayıp güncelleme probleminden farklıdır.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Eşzamanlı isteklerde (ör. bir kullanıcının "para çek" butonuna çift tıklaması, ya da iki
 * servis örneğinin aynı hesabı işlemesi), hesap olması gerekenden daha yüksek bir bakiyeyle
 * (kaybolan bir borç kaydı) ya da kontrolün kendisi eşzamanlı bir borç kaydını geride
 * bırakırsa negatif bile kalabilir - bu domainin asla izin vermemesi gereken tam olarak bu
 * hatadır.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code RaceConditionTest.shouldLoseUpdatesWithUnsynchronizedWithdraw()}, seri şekilde
 * çalıştırılsaydı hesabı tam olarak boşaltacak sayıda eşzamanlı para çekme işlemi tetikler ve
 * son bakiyenin sıfır olmadığını gösterir - başarılı olması gereken para çekme işlemlerine
 * rağmen para "hayatta kalmıştır".
 *
 * <p>NASIL DÜZELTİLİR?
 * Tüm oku-kontrol-et-yaz dizisini atomik hale getir - {@code synchronized} çözümü için
 * {@link com.interviewlab.concurrency.race.good.SynchronizedBankAccount}'a bak (mekanizmanın
 * kendisi - synchronized ile açık kilitler karşılaştırması - {@code concurrency.synchronization}
 * içinde daha ayrıntılı ele alınır).
 */
public class UnsafeBankAccount {

    private int balance;

    public UnsafeBankAccount(int initialBalance) {
        this.balance = initialBalance;
    }

    public boolean withdraw(int amount) {
        if (balance >= amount) {
            // Race penceresini kasıtlı olarak genişlet: Thread.yield() sadece bir ipucudur ve
            // yüzlerce runnable thread varken JVM/OS onu güvenilir şekilde uygulamayabilir - bu
            // yüzden kısa bir sleep kullanıyoruz, gerçek bir context switch'i zorluyor, böylece
            // yüzlerce eşzamanlı çağıranla kayıp güncelleme şansla "genellikle" değil,
            // güvenilir şekilde reproduce edilir.
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            balance -= amount;
            return true;
        }
        return false;
    }

    public int getBalance() {
        return balance;
    }
}
