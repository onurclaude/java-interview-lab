package com.interviewlab.patterns.templatemethod;

import java.util.ArrayList;
import java.util.List;

/**
 * Problem: birkaç ödeme türü aynı genel işleme iskeletini (doğrula -&gt; yetkilendir ->
 * yürüt -&gt; denetle) paylaşır, ancak YETKİLENDİRME adımı türe göre değişir (bir kredi
 * kartı 3-D Secure tarzı bir kontrole ihtiyaç duyar; bir cüzdan yalnızca bir bakiye
 * kontrolüne ihtiyaç duyar).
 *
 * <p>Kötü alternatif (bir kullanıp atılan sınıf yaratmamak için burada kod olarak
 * uygulanmadı): tüm doğrula/yürüt/denetle sırasını her ödeme türünün processor'üne
 * kopyala-yapıştır yapmak, sadece yetkilendirme adımı gerçekten farklı olur. NEDEN KÖTÜ:
 * paylaşılan adımlar zamanla birbirinden sapar (örn. biri kredi kartının denetleme
 * adımındaki bir hatayı düzeltir ama cüzdanın birebir kopyasını unutur) ve genel algoritma
 * şekli bir kez ifade edilmek yerine tekrarlanır.
 *
 * <p>PATTERN: Template Method - iskelet ({@link #process}) temel sınıfta bir kez, final
 * olarak tanımlanır; sadece meşru olarak değişen adım ({@link #authorize}) abstract'tır.
 * Alt sınıflar adımların SIRASINI değiştiremez, yalnızca kendilerine ait olanı doldurabilir
 * - bu da hem pattern'in gücüdür (tutarlılık) hem de ana riskidir (aşağıya bakın).
 *
 * <p>NE ZAMAN KULLANILMAMALI: değişen adımlar giderek artıyorsa (bugün sadece
 * yetkilendirme, yarın doğrulama da türe göre değişmeli, sonra yürütme de), Template
 * Method size zorluk çıkarmaya başlar - bu noktada Strategy (tüm algoritmayı alt sınıflama
 * yerine bir collaborator olarak enjekte etmek) genellikle daha uygun bir seçimdir, çünkü
 * inheritance yerine composition'ı tercih eder ve derin, katı bir sınıf hiyerarşisinden
 * kaçınır.
 */
public abstract class PaymentProcessingTemplate {

    /** Final: alt sınıflar bir ADIMI özelleştirir, asla algoritmanın şeklini değil. */
    public final List<String> process(double amount) {
        List<String> steps = new ArrayList<>();
        steps.add(validate(amount));
        steps.add(authorize(amount));
        steps.add(execute(amount));
        steps.add(audit(amount));
        return steps;
    }

    protected String validate(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return "validated:" + amount;
    }

    /** Ödeme türüne göre gerçekten değişen tek adım - override edilmesi zorunlu olan tek şey. */
    protected abstract String authorize(double amount);

    protected String execute(double amount) {
        return "executed:" + amount;
    }

    protected String audit(double amount) {
        return "audited:" + amount;
    }
}
