package com.interviewlab.labrunner;

import com.interviewlab.javacore.hashmapinternals.CollidingKey;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/** IntelliJ'de sağ tık -> Run/Debug. add-opens gerekmez (main() olarak çalışırken JVM argümanı verebilirsin, ama burada sadece boyut/collision okunuyor). */
public final class HashMapInternalsLabRunner {

    public static void main(String[] args) throws Exception {
        LabRunnerPrint.banner("HASHMAP INTERNALS — collision, load factor, treeification");

        Map<CollidingKey, String> map = new HashMap<>(64);
        for (int i = 0; i < 9; i++) {
            map.put(new CollidingKey("k" + i), "v" + i); // BREAKPOINT 1: her put()'ta bucket index'ini Evaluate Expression ile hesapla
        }

        Field tableField = HashMap.class.getDeclaredField("table");
        try {
            tableField.setAccessible(true);
        } catch (RuntimeException e) {
            LabRunnerPrint.line("HATA: " + e.getMessage());
            LabRunnerPrint.line("DÜZELTME: IntelliJ Run/Debug Configuration'ında VM options'a şunu ekle:");
            LabRunnerPrint.line("  --add-opens java.base/java.util=ALL-UNNAMED");
            LabRunnerPrint.line("(java.base modülü java.util'i deep reflection'a OPEN etmez - bkz. docs/NOTES_CORRECTIONS.md #4)");
            return;
        }
        Object[] table = (Object[]) tableField.get(map);
        int bucketIndex = (table.length - 1) & spreadHash(new CollidingKey("k0").hashCode());
        Object bucketHead = table[bucketIndex]; // BREAKPOINT 2: bucketHead'in sınıfını incele (Node mu TreeNode mu?)

        LabRunnerPrint.fact("map.size()", map.size());
        LabRunnerPrint.fact("table.length (capacity)", table.length);
        LabRunnerPrint.fact("bucketIndex for k0..k8", bucketIndex);
        LabRunnerPrint.fact("bucket head class", bucketHead.getClass().getSimpleName());

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("CollidingKey.hashCode() KASITLI olarak sabit (42) - 9 key de AYNI bucket'a düşüyor.");
        LabRunnerPrint.line("TREEIFY_THRESHOLD=8 + capacity>=64 (MIN_TREEIFY_CAPACITY) koşulu sağlanınca,");
        LabRunnerPrint.line("bucket bir linked list yerine kırmızı-siyah ağaca (TreeNode) dönüşür.");

        LabRunnerPrint.section("BREAKPOINT");
        LabRunnerPrint.line("HashMapInternalsLabRunner.java:14 (put loop) ve :20 (bucketHead) - Variables");
        LabRunnerPrint.line("panelinde table[bucketIndex]'i genişlet, 'next' zincirini ya da TreeNode alanlarını izle.");

        LabRunnerPrint.section("TRY");
        LabRunnerPrint.line("9'u 7'ye düşür (TREEIFY_THRESHOLD altında kal) - bucket head'in Node kalıp");
        LabRunnerPrint.line("TreeNode'a DÖNÜŞMEDİĞİNİ gözlemle. new HashMap<>(8) dene (capacity<64) -");
        LabRunnerPrint.line("treeify yerine RESIZE tetiklendiğini gör (kaynak: HashMap.treeifyBin()).");
    }

    private static int spreadHash(int h) {
        return h ^ (h >>> 16);
    }
}
