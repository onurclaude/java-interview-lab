package com.interviewlab.labrunner;

import com.interviewlab.immutability.bad.MutableEventLog;
import com.interviewlab.immutability.good.DefensiveCopyEventLog;
import java.util.List;

/** IntelliJ'de sağ tık -> Run/Debug. */
public final class ImmutabilityLabRunner {

    public static void main(String[] args) {
        LabRunnerPrint.banner("IMMUTABILITY — getter dahili state'i sızdırıyor mu?");

        MutableEventLog badLog = new MutableEventLog();
        badLog.record("order-created");
        List<String> leaked = badLog.getEvents(); // BREAKPOINT 1: leaked ve badLog'un iç alanının AYNI liste olduğunu gör
        leaked.clear(); // çağıranın "kendi kopyası" sandığı şey aslında ORİJİNAL

        DefensiveCopyEventLog goodLog = new DefensiveCopyEventLog();
        goodLog.record("order-created");
        List<String> copy = goodLog.getEvents(); // BREAKPOINT 2: copy'nin FARKLI bir liste olduğunu gör

        boolean copyClearThrew;
        try {
            copy.clear(); // List.copyOf(...) DEĞİŞTİRİLEMEZ bir liste döner - bu ATILACAK
            copyClearThrew = false;
        } catch (UnsupportedOperationException e) {
            copyClearThrew = true; // BEKLENEN: List.copyOf() sadece ayrı değil, aynı zamanda immutable
        }

        LabRunnerPrint.fact("badLog.getEvents() after external clear()", badLog.getEvents());
        LabRunnerPrint.fact("goodLog copy.clear() threw UnsupportedOperationException", copyClearThrew);
        LabRunnerPrint.fact("goodLog.getEvents() (etkilenmedi)", goodLog.getEvents());

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("MutableEventLog.getEvents(), dahili ArrayList referansını DOĞRUDAN döndürüyor -");
        LabRunnerPrint.line("çağıranın listesi üzerindeki HERHANGİ bir değişiklik nesnenin KENDİ durumunu");
        LabRunnerPrint.line("değiştiriyor. DefensiveCopyEventLog, List.copyOf(...) kullanıyor - bu SADECE ayrı");
        LabRunnerPrint.line("bir kopya DEĞİL, aynı zamanda DEĞİŞTİRİLEMEZ (immutable) bir liste döndürür - bu");
        LabRunnerPrint.line("yüzden copy.clear() bile UnsupportedOperationException fırlatıyor (daha GÜÇLÜ bir garanti).");

        LabRunnerPrint.section("BREAKPOINT");
        LabRunnerPrint.line("ImmutabilityLabRunner.java:12 ve :17 - Variables panelinde leaked/copy'nin");
        LabRunnerPrint.line("object id'sini badLog/goodLog'un dahili alanıyla karşılaştır.");

        LabRunnerPrint.section("TRY");
        LabRunnerPrint.line("DefensiveCopyEventLog.getEvents()'in List.copyOf() kullandığını doğrula - copy'ye");
        LabRunnerPrint.line("bir eleman EKLEMEYİ dene (copy.add(...)) - UnsupportedOperationException alırsın,");
        LabRunnerPrint.line("çünkü List.copyOf() DEĞİŞTİRİLEMEZ bir liste döndürür.");
    }
}
