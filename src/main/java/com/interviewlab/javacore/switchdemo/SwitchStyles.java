package com.interviewlab.javacore.switchdemo;

/**
 * Aynı mantığın dört farklı switch söz dizimiyle yazılışı - hepsi AYNI sonucu üretir, ama
 * derleyicinin garanti ettiği şeyler farklıdır. Bkz. docs/NOTES_CORRECTIONS.md ve
 * docs/java-core-switch.md.
 */
public final class SwitchStyles {

    private SwitchStyles() {
    }

    /**
     * Geleneksel (traditional) switch statement: {@code break} unutulursa fall-through olur
     * (bir sonraki case'e "düşer") - klasik bir hata kaynağı. {@code default} yoksa, hiçbir
     * case eşleşmezse switch sessizce hiçbir şey yapmadan biter (bir enum'a yeni bir değer
     * eklenip burası güncellenmezse, derleyici SESSİZCE geçer - runtime'da fark edilir).
     */
    public static String traditional(PaymentStatus status) {
        String result;
        switch (status) {
            case PENDING:
                result = "waiting";
                break;
            case COMPLETED:
                result = "done";
                break;
            case FAILED:
                result = "error";
                break;
            case REFUNDED:
                result = "reversed";
                break;
            default:
                result = "unknown";
        }
        return result;
    }

    /**
     * Arrow syntax (Java 14+, JEP 361): her case tek bir ifade/blok, fall-through YOKTUR
     * (her dal kendiliğinden "break" eder - yazmaya gerek yok, unutma riski de yok).
     */
    public static String withArrowSyntax(PaymentStatus status) {
        return switch (status) {
            case PENDING -> "waiting";
            case COMPLETED -> "done";
            case FAILED -> "error";
            case REFUNDED -> "reversed";
        };
    }

    /**
     * Switch EXPRESSION + exhaustiveness: bir {@code enum} üzerinde switch, TÜM enum
     * değerlerini kapsıyorsa {@code default} GEREKMEZ - derleyici, enum'un tüm değerlerinin
     * ele alındığını STATİK OLARAK doğrular. {@link PaymentStatus}'a yeni bir değer eklenip
     * bu switch güncellenmezse, bu artık RUNTIME'DA sessizce geçilen bir durum DEĞİL, bir
     * DERLEME HATASIDIR - traditional switch'in en büyük zaafının doğrudan çözümü.
     */
    public static int exhaustiveWithoutDefault(PaymentStatus status) {
        return switch (status) {
            case PENDING -> 0;
            case COMPLETED -> 1;
            case FAILED -> 2;
            case REFUNDED -> 3;
        };
    }

    /** Bir case birden fazla değer paylaşabilir - virgülle ayrılmış çoklu label. */
    public static boolean isTerminalState(PaymentStatus status) {
        return switch (status) {
            case COMPLETED, FAILED, REFUNDED -> true;
            case PENDING -> false;
        };
    }
}
