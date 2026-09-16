package com.interviewlab.labrunner;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** IntelliJ'de sağ tık -> Run/Debug. Güvenli - OOM ÜRETMEZ, sadece reachability'i gözlemler. */
public final class GcReachabilityLabRunner {

    private static final List<Object> LEAKY_STATIC_CACHE = new ArrayList<>();

    public static void main(String[] args) throws InterruptedException {
        LabRunnerPrint.banner("GC REACHABILITY — bir static collection nasıl (yanlışlıkla) sızdırır");

        Object localOnly = new Object();
        WeakReference<Object> localRef = new WeakReference<>(localOnly);

        Object leaked = new Object();
        WeakReference<Object> leakedRef = new WeakReference<>(leaked);
        LEAKY_STATIC_CACHE.add(leaked); // BREAKPOINT 1: burada dur - artık 'leaked' static bir GC root'tan erişilebilir

        localOnly = null; // artık HİÇBİR yerel değişken buna işaret etmiyor
        leaked = null; // yerel referans gitti, AMA static liste HÂLÂ tutuyor

        System.gc(); // sadece bir ÖNERİ - garanti değil, ama pratikte genelde çalışır
        Thread.sleep(200);

        LabRunnerPrint.fact("localOnly (yerel ref) GC edildi mi", localRef.get() == null);
        LabRunnerPrint.fact("leaked (static listede) GC edildi mi", leakedRef.get() == null); // BREAKPOINT 2

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("localOnly'ye artık HİÇBİR referans yok (ne stack'te ne heap'te) - GC roots'tan");
        LabRunnerPrint.line("ulaşılamaz, toplanabilir. leaked ise LEAKY_STATIC_CACHE (bir GC root - static alan)");
        LabRunnerPrint.line("içinde HÂLÂ tutuluyor - yerel değişken null olsa bile UNREACHABLE DEĞİL.");

        LabRunnerPrint.section("BREAKPOINT");
        LabRunnerPrint.line("GcReachabilityLabRunner.java:19 - LEAKY_STATIC_CACHE'e ekleme anını incele.");

        LabRunnerPrint.section("TRY");
        LabRunnerPrint.line("LEAKY_STATIC_CACHE.clear() ekle (leaked = null'dan sonra) ve leakedRef.get()'in");
        LabRunnerPrint.line("artık null döndüğünü gözlemle - static referansı temizlemek reachability'i geri kazandırır.");
    }
}
