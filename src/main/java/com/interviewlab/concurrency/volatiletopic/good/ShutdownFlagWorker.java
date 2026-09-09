package com.interviewlab.concurrency.volatiletopic.good;

/**
 * {@code volatile}'ın gerçekte kullanım amacı olan senaryo: bir thread tarafından yazılan,
 * diğerleri tarafından okunan ve üzerindeki işlemin bir oku-değiştir-yaz değil, sade bir
 * atama olduğu tek bir alan. {@link #requestStop()} tek bir yazma işlemidir;
 * {@link #run()}'un döngü koşulu tek bir okuma işlemidir. Burada bir race'in saklanabileceği
 * bileşik (compound) bir işlem yoktur, bu yüzden tek başına görünürlük (ki {@code volatile}
 * bunu garanti eder) yeterlidir: bu olmasaydı, okuyucu bir thread, alanın bayat (stale)
 * başlangıç değerini bir CPU register'ında önbelleğe alınmış şekilde sonsuza kadar meşru
 * olarak gözlemleyebilir ve durdurma isteğini asla göremezdi.
 */
public class ShutdownFlagWorker implements Runnable {

    private volatile boolean stopRequested = false;
    private volatile int iterationsCompleted = 0;

    @Override
    public void run() {
        while (!stopRequested) {
            iterationsCompleted++;
        }
    }

    public void requestStop() {
        stopRequested = true;
    }

    public int getIterationsCompleted() {
        return iterationsCompleted;
    }
}
