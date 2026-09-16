package com.interviewlab.labrunner;

/** IntelliJ'de sağ tık -> Run/Debug. Breakpoint için main() içindeki satır numaralarına bak. */
public final class StringPoolLabRunner {

    public static void main(String[] args) {
        LabRunnerPrint.banner("STRING POOL — YANLIŞ VARSAYIM: \"iki string == ise eşittir\"");

        String a = "java";
        String b = "java";
        String c = new String("java"); // BREAKPOINT 1: burada dur, 'c'nin heap adresini incele
        String d = c.intern(); // BREAKPOINT 2: burada dur, d'nin a ile AYNI adrese döndüğünü gör

        LabRunnerPrint.fact("a == b", a == b);
        LabRunnerPrint.fact("a.equals(b)", a.equals(b));
        LabRunnerPrint.fact("a == c", a == c);
        LabRunnerPrint.fact("a.equals(c)", a.equals(c));
        LabRunnerPrint.fact("a == d (intern)", a == d);

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("a ve b, AYNI string literal'dır - derleme zamanında JVM'in string pool'una");
        LabRunnerPrint.line("(constant pool) interned edilirler, bu yüzden a == b true (AYNI nesne).");
        LabRunnerPrint.line("c, new String(\"java\") ile AÇIKÇA yeni bir heap nesnesi olarak oluşturuldu -");
        LabRunnerPrint.line("değeri aynı olsa bile FARKLI bir nesne, bu yüzden a == c false.");
        LabRunnerPrint.line("c.intern(), pool'daki MEVCUT instance'a referansı döndürür - d == a true.");

        LabRunnerPrint.section("BREAKPOINT");
        LabRunnerPrint.line("StringPoolLabRunner.java:12 (String c = ...) ve :13 (intern) - Variables");
        LabRunnerPrint.line("panelinde a/b/c/d'nin object id'lerini (referans adreslerini) karşılaştır.");

        LabRunnerPrint.section("TRY");
        LabRunnerPrint.line("String e = \"ja\" + \"va\"; ekle ve a == e'yi tahmin et (İPUCU: derleme-zamanı");
        LabRunnerPrint.line("constant expression'lar da interned edilir - true çıkar). Sonra");
        LabRunnerPrint.line("String prefix = \"ja\"; String f = prefix + \"va\"; dene - bu RUNTIME'da");
        LabRunnerPrint.line("birleştirilir (StringBuilder), interned DEĞİLDİR - a == f false çıkar.");
    }
}
