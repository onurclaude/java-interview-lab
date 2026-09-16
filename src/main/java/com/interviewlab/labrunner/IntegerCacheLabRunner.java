package com.interviewlab.labrunner;

/** IntelliJ'de sağ tık -> Run/Debug. */
public final class IntegerCacheLabRunner {

    public static void main(String[] args) {
        LabRunnerPrint.banner("INTEGER CACHE — JLS 5.1.7 autoboxing garantisi");

        Integer a = 127;
        Integer b = 127; // BREAKPOINT 1: burada dur, a ve b'nin AYNI cache nesnesine işaret ettiğini gör
        Integer c = 128;
        Integer d = 128; // BREAKPOINT 2: burada dur, c ve d'nin FARKLI nesneler olduğunu gör

        LabRunnerPrint.fact("a == b (127)", a == b);
        LabRunnerPrint.fact("c == d (128)", c == d);
        LabRunnerPrint.fact("c.equals(d)", c.equals(d));

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("JLS 5.1.7, Integer.valueOf(int)'in -128..127 aralığını CACHE'lemesini GARANTİ eder -");
        LabRunnerPrint.line("bu aralıktaki autoboxing HER ZAMAN aynı nesneyi döndürür. 128 bu aralığın DIŞINDA,");
        LabRunnerPrint.line("bu yüzden her autoboxing YENİ bir Integer nesnesi oluşturur.");

        LabRunnerPrint.section("BREAKPOINT");
        LabRunnerPrint.line("IntegerCacheLabRunner.java:9 ve :11 - Variables panelinde a/b/c/d'nin");
        LabRunnerPrint.line("object id'lerini karşılaştır (aynı id = aynı nesne).");

        LabRunnerPrint.section("TRY");
        LabRunnerPrint.line("-128 ve -129'u dene (alt sınır) - JLS aralığı [-128, 127]'dir, -128 cache'te,");
        LabRunnerPrint.line("-129 değil. -Djava.lang.Integer.IntegerCache.high=1000 JVM argümanıyla üst");
        LabRunnerPrint.line("sınırı GENİŞLETEBİLİRSİN (JDK implementasyon detayı, JLS garantisi değil).");
    }
}
