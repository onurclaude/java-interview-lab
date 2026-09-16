package com.interviewlab.labrunner;

import com.interviewlab.equalshashcode.bad.NoHashCodeOverrideEntity;
import com.interviewlab.equalshashcode.good.ProperEqualsHashCodeEntity;
import java.util.HashSet;
import java.util.Set;

/** IntelliJ'de sağ tık -> Run/Debug. */
public final class EqualsHashCodeLabRunner {

    public static void main(String[] args) {
        LabRunnerPrint.banner("EQUALS/HASHCODE CONTRACT — HashSet neden \"eşit\" kabul etmiyor");

        Set<NoHashCodeOverrideEntity> badSet = new HashSet<>();
        badSet.add(new NoHashCodeOverrideEntity(1L));
        badSet.add(new NoHashCodeOverrideEntity(1L)); // BREAKPOINT 1: aynı id, ama hashCode() override edilmemiş

        Set<ProperEqualsHashCodeEntity> goodSet = new HashSet<>();
        goodSet.add(new ProperEqualsHashCodeEntity(1L));
        goodSet.add(new ProperEqualsHashCodeEntity(1L)); // BREAKPOINT 2: aynı id, hashCode() override edilmiş

        LabRunnerPrint.fact("badSet.size() (equals/hashCode YOK)", badSet.size());
        LabRunnerPrint.fact("goodSet.size() (equals/hashCode VAR)", goodSet.size());

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("HashSet, önce hashCode()'a göre bucket seçer, SONRA o bucket içinde equals() ile");
        LabRunnerPrint.line("karşılaştırır. hashCode() override edilmemişse (Object'in varsayılanı - kimlik/adres");
        LabRunnerPrint.line("tabanlı), İKİ 'eşit' nesne FARKLI bucket'lara düşer - equals() hiç ÇAĞRILMAZ bile,");
        LabRunnerPrint.line("ikisi de ayrı ayrı eklenir (size=2). Contract: a.equals(b) ise a.hashCode()==b.hashCode() OLMALI.");

        LabRunnerPrint.section("BREAKPOINT");
        LabRunnerPrint.line("EqualsHashCodeLabRunner.java:14 ve :18 - Evaluate Expression ile her iki entity'nin");
        LabRunnerPrint.line("hashCode() değerini karşılaştır (badSet'te FARKLI, goodSet'te AYNI olmalı).");

        LabRunnerPrint.section("TRY");
        LabRunnerPrint.line("badSet.contains(new NoHashCodeOverrideEntity(1L)) çağır - false döner (id aynı olsa");
        LabRunnerPrint.line("bile) - equals/hashCode olmadan 'aynı varlık' kavramı HashSet için anlamsızdır.");
    }
}
