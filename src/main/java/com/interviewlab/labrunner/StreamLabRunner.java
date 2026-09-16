package com.interviewlab.labrunner;

import java.util.List;
import java.util.stream.Stream;

/** IntelliJ'de sağ tık -> Run/Debug. */
public final class StreamLabRunner {

    public static void main(String[] args) {
        LabRunnerPrint.banner("STREAM API — lazy evaluation, intermediate vs terminal");

        List<String> names = List.of("Ada", "Grace", "Linus", "Barbara", "Alan");

        Stream<String> lazyStream = names.stream()
                .filter(n -> {
                    LabRunnerPrint.line("filter checking: " + n); // BREAKPOINT 1: bu HİÇ çalışmaz, terminal operasyon yoksa
                    return n.length() > 4;
                })
                .map(String::toUpperCase);
        LabRunnerPrint.line("(intermediate operasyonlar tanımlandı ama HENÜZ ÇALIŞMADI - hiçbir 'filter checking' satırı görmedin)");

        List<String> result = lazyStream.toList(); // BREAKPOINT 2: TERMINAL operasyon - şimdi tüm zincir çalışır
        LabRunnerPrint.fact("result", result);

        long count = names.stream().filter(n -> n.length() > 4).count();
        LabRunnerPrint.fact("count (4+ karakter)", count);

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("filter()/map() gibi INTERMEDIATE operasyonlar LAZY'dir - bir TERMINAL operasyon");
        LabRunnerPrint.line("(toList/count/forEach/reduce) çağrılana kadar HİÇBİR ŞEY ÇALIŞMAZ. Bu, gereksiz");
        LabRunnerPrint.line("işlemi önler (ör. bir sonraki adımda zaten filtrelenecek elemanları map'lememek).");

        LabRunnerPrint.section("BREAKPOINT");
        LabRunnerPrint.line("StreamLabRunner.java:14 (filter lambda) - buraya breakpoint koy, önce .toList()");
        LabRunnerPrint.line("SATIRINA kadar hiç tetiklenmediğini, SONRA (terminal op ile) tetiklendiğini gör.");

        LabRunnerPrint.section("TRY");
        LabRunnerPrint.line("peek(System.out::println) ekle - debug için kullanışlı ama business side-effect için");
        LabRunnerPrint.line("KÖTÜ bir fikirdir (JDK dokümantasyonu peek'i sadece debugging için önerir, çünkü");
        LabRunnerPrint.line("bazı implementasyonlar bunu HİÇ ÇAĞIRMAYABİLİR - ör. short-circuit terminal op'larla).");
    }
}
