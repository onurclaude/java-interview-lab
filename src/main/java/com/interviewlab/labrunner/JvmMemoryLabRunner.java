package com.interviewlab.labrunner;

/** IntelliJ'de sağ tık -> Run/Debug. */
public final class JvmMemoryLabRunner {

    static class HeapObject {
        int value;
        HeapObject(int value) {
            this.value = value;
        }
    }

    public static void main(String[] args) {
        LabRunnerPrint.banner("JVM MEMORY: HEAP vs STACK — referans mı, nesnenin kendisi mi?");

        HeapObject shared = new HeapObject(1); // 'shared' HEAP'teki bir nesneye işaret eden bir STACK referansı
        mutateThroughReference(shared); // BREAKPOINT 1: burada dur, 'shared' ve parametrenin AYNI heap adresine işaret ettiğini gör
        LabRunnerPrint.fact("shared.value after mutation", shared.value);

        int primitive = 1;
        mutatePrimitive(primitive); // BREAKPOINT 2: primitive'in DEĞİŞMEDİĞİNİ gör - kopyalanarak geçti
        LabRunnerPrint.fact("primitive after 'mutation'", primitive);

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("Java HER ZAMAN pass-by-value'dur - ama bir nesne referansı geçildiğinde, geçilen");
        LabRunnerPrint.line("DEĞER referansın KENDİSİDİR (heap adresi) - metod içinde nesnenin ALANLARINI");
        LabRunnerPrint.line("değiştirmek, çağıranın gördüğü AYNI heap nesnesini değiştirir. Primitive'ler tamamen");
        LabRunnerPrint.line("kopyalanır - metod içindeki değişiklik çağıranın stack frame'ini hiç etkilemez.");

        LabRunnerPrint.section("BREAKPOINT");
        LabRunnerPrint.line("JvmMemoryLabRunner.java:15 ve :19 - Variables panelinde 'shared' (main'in stack");
        LabRunnerPrint.line("frame'inde) ile mutateThroughReference'ın parametresinin (KENDİ stack frame'inde)");
        LabRunnerPrint.line("object id'lerini karşılaştır - AYNI olmalı (aynı heap nesnesi).");

        LabRunnerPrint.section("TRY");
        LabRunnerPrint.line("mutateThroughReference içinde 'obj = new HeapObject(999);' dene (referansın");
        LabRunnerPrint.line("KENDİSİNİ değiştir, alanını değil) - shared.value'nun DEĞİŞMEDİĞİNİ gör, çünkü bu");
        LabRunnerPrint.line("sadece metodun KENDİ stack frame'indeki yerel referansı başka bir nesneye yönlendirir.");
    }

    private static void mutateThroughReference(HeapObject obj) {
        obj.value = 42; // heap'teki PAYLAŞILAN nesneyi değiştiriyor
    }

    private static void mutatePrimitive(int value) {
        value = 999; // sadece bu metodun KENDİ stack frame'indeki kopyayı değiştiriyor
    }
}
