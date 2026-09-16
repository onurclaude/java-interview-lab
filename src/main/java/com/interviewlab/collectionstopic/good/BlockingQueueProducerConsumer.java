package com.interviewlab.collectionstopic.good;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * {@link BlockingQueue}'nun temel sözleşmesi: {@code put()}/{@code take()} GEREKTİĞİNDE
 * BLOKLAR (kuyruk doluysa put, boşsa take bekler) - ham bir {@link java.util.Queue}'nun
 * {@code add()}/{@code poll()}'undan (asla bloklamaz, kapasiteyi aşarsa exception fırlatır
 * ya da boşsa null döner) temel farkı budur. `docs/collections-internals.md` dosyasına bakın.
 */
public final class BlockingQueueProducerConsumer {

    private final BlockingQueue<Integer> queue;

    public BlockingQueueProducerConsumer(BlockingQueue<Integer> queue) {
        this.queue = queue;
    }

    /** Kuyruk doluysa BLOKLAR - bu, üreticinin (producer) doğal olarak yavaşlatılmasıdır (backpressure). */
    public void produce(int value) throws InterruptedException {
        queue.put(value);
    }

    /** Kuyruk boşsa BLOKLAR - yeni bir eleman gelene kadar bekler. */
    public int consume() throws InterruptedException {
        return queue.take();
    }

    /** Bloklamayan varyant: kapasiteyi/boşluğu aşarsa hemen false/null döner, hiç beklemez. */
    public boolean tryProduceNonBlocking(int value) {
        return queue.offer(value);
    }

    public Integer tryConsumeWithTimeout(long timeoutMillis) throws InterruptedException {
        return queue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public int size() {
        return queue.size();
    }
}
