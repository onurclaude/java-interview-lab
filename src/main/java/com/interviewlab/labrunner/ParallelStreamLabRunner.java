package com.interviewlab.labrunner;

import com.interviewlab.streamtopic.bad.ParallelStreamSharedMutableStateService;
import com.interviewlab.streamtopic.good.ParallelStreamCollectService;
import java.util.List;
import java.util.stream.IntStream;

/** IntelliJ'de sağ tık -> Run/Debug. Sonuç çalıştırmadan çalıştırmaya DEĞİŞEBİLİR (bu KASITLI - gerçek race). */
public final class ParallelStreamLabRunner {

    public static void main(String[] args) {
        LabRunnerPrint.banner("PARALLEL STREAM — paylaşılan mutable state ile toplama YAPMA");

        List<Integer> input = IntStream.rangeClosed(1, 10_000).boxed().toList();

        List<Integer> badResult = new ParallelStreamSharedMutableStateService().squareAll(input); // BREAKPOINT 1: senkronize edilmemiş ArrayList'e paralel add()
        List<Integer> goodResult = new ParallelStreamCollectService().squareAll(input); // BREAKPOINT 2: Collectors.toList() ile doğru toplama

        LabRunnerPrint.fact("input.size()", input.size());
        LabRunnerPrint.fact("badResult.size() (KAYIP OLABİLİR)", badResult.size());
        LabRunnerPrint.fact("goodResult.size() (HER ZAMAN tam)", goodResult.size());

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("parallelStream(), common ForkJoinPool'daki birden fazla thread'de ÇALIŞIR - paylaşılan");
        LabRunnerPrint.line("senkronize edilmemiş bir ArrayList'e forEach ile add() eklemek gerçek bir race");
        LabRunnerPrint.line("condition'dır (ArrayList thread-safe değildir). Collectors.toList() ile toplamak,");
        LabRunnerPrint.line("her thread'in kendi ara sonucunu üretip SONRA birleştirmesini sağlar - paylaşım YOK.");

        LabRunnerPrint.section("BREAKPOINT");
        LabRunnerPrint.line("ParallelStreamLabRunner.java:14 - Threads panelinde 'ForkJoinPool.commonPool-worker-N'");
        LabRunnerPrint.line("thread'lerinin AYNI ArrayList'e eşzamanlı add() yaptığını gözlemle.");

        LabRunnerPrint.section("TRY");
        LabRunnerPrint.line("badResult.size()'ı BİRKAÇ KEZ çalıştır (Run'a tekrar bas) - 10000'den KÜÇÜK, DEĞİŞKEN");
        LabRunnerPrint.line("bir sayı göreceksin (bazen tam 10000 bile çıkabilir - bu 'çalışıyor gibi görünen'");
        LabRunnerPrint.line("ama GÜVENİLİR OLMAYAN kodun asıl tehlikesidir).");
    }
}
