package com.interviewlab.javacore.cache;

import com.interviewlab.labrunner.LabRunnerPrint;

import com.interviewlab.javacore.cache.CacheAsideProductService;
import com.interviewlab.javacore.cache.NoCacheProductService;

/** IntelliJ'de sağ tık -> Run/Debug. Postman/HTTP GEREKMEZ - Spring de GEREKMEZ (saf POJO). */
public final class CacheAsideLabRunner {

    public static void main(String[] args) {
        LabRunnerPrint.banner("CACHE-ASIDE — hit/miss/invalidate");

        NoCacheProductService bad = new NoCacheProductService();
        bad.seed(1L, "Widget");
        bad.read(1L); // <- BREAKPOINT 1: cache YOK, HER çağrı DB'ye gider
        bad.read(1L);
        bad.read(1L);
        LabRunnerPrint.fact("BAD databaseReadCount (3 çağrı sonrası)", bad.databaseReadCount());

        CacheAsideProductService good = new CacheAsideProductService();
        good.seed(1L, "Widget");
        good.read(1L); // <- BREAKPOINT 2: cache.get(id)==null -> MISS -> DB'ye git, cache'e yaz
        good.read(1L); // <- BREAKPOINT 3: cache.get(id) DOLU -> HIT -> DB'ye HİÇ gitme
        good.read(1L);
        LabRunnerPrint.fact("GOOD databaseReadCount (3 çağrı sonrası, sadece 1. miss)", good.databaseReadCount());

        good.write(1L, "NewWidget"); // <- BREAKPOINT 4: cache.remove(id) - GÜNCELLEME değil, İPTAL
        LabRunnerPrint.fact("GOOD isCachedImmediatelyAfterWrite", good.isCached(1L));
        good.read(1L);
        LabRunnerPrint.fact("GOOD databaseReadCount (write sonrası tekrar miss)", good.databaseReadCount());

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("Cache-Aside, DB yükünü SADECE cache miss'lerine indirger. write(), cache'i GÜNCELLEMEK");
        LabRunnerPrint.line("yerine SİLER (invalidate) - bir sonraki read() DB'den taze veriyi çeker.");
    }
}
