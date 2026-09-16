package com.interviewlab.labrunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** IntelliJ'de sağ tık -> Run/Debug. */
public final class ReflectionLabRunner {

    private static final class Sample {
        private final String name;

        private Sample(String name) {
            this.name = name;
        }

        private String greet() {
            return "hello, " + name;
        }
    }

    public static void main(String[] args) throws Exception {
        LabRunnerPrint.banner("REFLECTION — framework'lerin \"sihri\" nasıl çalışır");

        Class<Sample> clazz = Sample.class;
        LabRunnerPrint.fact("declared fields", clazz.getDeclaredFields().length);
        LabRunnerPrint.fact("declared methods", clazz.getDeclaredMethods().length);

        Field nameField = clazz.getDeclaredField("name"); // BREAKPOINT 1: private alan handle'ı
        nameField.setAccessible(true); // erişim kontrolünü BYPASS EDER - framework'lerin yaptığı tam olarak bu
        Method greet = clazz.getDeclaredMethod("greet");
        greet.setAccessible(true);

        Sample sample = new Sample("Ada");
        LabRunnerPrint.fact("greet() via reflection", greet.invoke(sample)); // BREAKPOINT 2: private metodu DIŞARIDAN çağır

        nameField.set(sample, "Grace"); // private final alanı bile DEĞİŞTİRİR
        LabRunnerPrint.fact("greet() after field overwrite", greet.invoke(sample));

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("Spring'in @Autowired'ı, Jackson'ın JSON<->nesne dönüşümü, JUnit'in @Test metod");
        LabRunnerPrint.line("keşfi - hepsi TEMELDE bunu yapıyor: Class<?> ile metadata sorgula, setAccessible(true)");
        LabRunnerPrint.line("ile erişim kontrolünü bypass et, Field/Method.invoke() ile OKU/YAZ/ÇAĞIR.");

        LabRunnerPrint.section("BREAKPOINT");
        LabRunnerPrint.line("ReflectionLabRunner.java:27 ve :31 - Variables panelinde nameField/greet nesnelerinin");
        LabRunnerPrint.line("KENDİSİNİ incele (bunlar Field/Method NESNELERİDİR, string değil).");

        LabRunnerPrint.section("TRY");
        LabRunnerPrint.line("nameField.setAccessible(true) satırını YORUM SATIRI yap - IllegalAccessException");
        LabRunnerPrint.line("alırsın (private alana normal erişim engeli). java.util.HashMap gibi bir JDK");
        LabRunnerPrint.line("internal sınıfına aynısını dene - InaccessibleObjectException alırsın (bkz.");
        LabRunnerPrint.line("HashMapInternalsLabRunner - java.base modülü java.util'i OPEN etmez).");
    }
}
