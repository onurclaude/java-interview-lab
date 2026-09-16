package com.interviewlab.concurrency.race.bad;

/**
 * En saf, en klasik race condition örneği: senkronizasyon olmadan {@code count++}. Bu tek
 * satır, aslında üç ayrı, atomik olmayan adımdır (oku, artır, yaz) - iki thread, birbirinin
 * yazmasını görmeden aynı değeri okuyabilir, ikisi de artırıp geri yazabilir ve bir artış
 * kaybolur (lost update). Bkz. {@code /api/labs/concurrency/counter/bad}.
 */
public class UnsynchronizedIntCounter {

    private int count;

    public void increment() {
        count++;
    }

    public int get() {
        return count;
    }
}
