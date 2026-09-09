package com.interviewlab.patterns.decorator;

/**
 * Problem: siparişe bağlı olarak öngörülemeyen bir ayarlama KOMBİNASYONUNA (yüzdesel bir
 * indirim, sabit bir işlem ücreti, belki ikisi birden, belki hiçbiri, belki de iki indirim)
 * ihtiyaç duyan bir temel fiyat.
 *
 * <p>Kötü alternatif: giderek büyüyen bir boolean/opsiyonel bayrak listesine sahip bir
 * {@code calculatePrice(base, hasDiscount, discountPct, hasFee, feeAmount, ...)} metodu, ya
 * da kombinasyon başına bir alt sınıf ({@code DiscountedFeeItem}, {@code DiscountedItem},
 * {@code FeeItem}, ...) - gereken alt sınıf sayısı, her bağımsız ayarlamanın KARTEZYEN
 * ÇARPIMI olur ve kombinatoryal olarak patlar.
 *
 * <p>PATTERN: Decorator - her ayarlama başka bir {@link PricedItem}'ı sarmalar ve kendi
 * davranışını üzerine ekler, böylece herhangi bir kombinasyon sadece iç içe geçmiş bir
 * construction'dır, kombinasyon başına yeni bir sınıfa gerek yoktur.
 *
 * <p>NE ZAMAN KULLANILMAMALI: ayarlamaların, çağıranın doğru sırayla uygulaması gereken,
 * belirli ve iş açısından anlamlı bir SIRAYLA uygulanması gerekiyorsa (örn. "ücret
 * indirimden önce" ile "indirim ücretten önce" farklı toplamlar verir) ve bu sıranın yanlışlıkla
 * bozulması kolaysa, her çağrı noktasının decorator'ları doğru şekilde iç içe geçirmesine
 * güvenmek yerine, sırayı bir kez kodlayan açık bir fiyatlandırma pipeline'ı/stratejisi
 * tercih edin.
 */
public interface PricedItem {
    double price();
}
