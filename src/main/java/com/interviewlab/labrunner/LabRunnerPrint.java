package com.interviewlab.labrunner;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Category B lab runner'ları (main() ile IntelliJ'den Run/Debug edilir) için ortak konsol
 * formatı.
 *
 * <p><b>Neden System.out'u explicit UTF-8'e zorluyoruz?</b> Gerçek, doğrulanmış bir bulgu:
 * bu makinede JVM'in varsayılan {@code stdout.encoding} sistem property'si Windows'un
 * Türkçe konsol code page'i olan {@code Cp1254}'tür (UTF-8 DEĞİL) - {@code file.encoding}
 * UTF-8 olsa BİLE. Bunu düzeltmeden çalıştırmak, Türkçe karakterlerin (ı, ş, ğ, ç, ü, ö)
 * konsolda bozuk (mojibake) görünmesine yol açtı - bunu bizzat çalıştırıp gördük, varsaymadık.
 */
final class LabRunnerPrint {

    static {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
    }

    private LabRunnerPrint() {
    }

    static void banner(String title) {
        String line = "=".repeat(40);
        System.out.println(line);
        System.out.println("LAB: " + title);
        System.out.println(line);
        System.out.println();
    }

    static void fact(String label, Object value) {
        System.out.printf("%-14s: %s%n", label, value);
    }

    static void section(String header) {
        System.out.println();
        System.out.println(header + ":");
    }

    static void line(String text) {
        System.out.println(text);
    }
}
