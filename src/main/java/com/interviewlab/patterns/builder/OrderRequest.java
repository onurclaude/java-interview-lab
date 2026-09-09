package com.interviewlab.patterns.builder;

import java.util.List;

/**
 * Problem: birçok alanı olan, bunların bir kısmı opsiyonel (kupon kodu, hediye paketi,
 * teslimat notu) immutable bir {@code OrderRequest} nesnesini, ne aşamalı bir constructor
 * ({@code OrderRequest(customerId, items)}, {@code OrderRequest(customerId, items, couponCode)},
 * {@code OrderRequest(customerId, items, couponCode, giftWrap)}, ...) ne de yarım
 * yapılandırılmış halde bırakılabilen mutable bir setter bean'i kullanmadan oluşturmak.
 *
 * <p>BURADA RECORD NEDEN YETERSİZ: bir Java {@code record}, TÜM alanların zorunlu olduğu ve
 * constructor çağrı noktasının okunabilir kaldığı durumlarda çok uygundur - ancak birden
 * fazla opsiyonel alan varken, record'un canonical constructor'ı her çağıranı ihtiyacı
 * olmayan alanlar için pozisyonel olarak {@code null} (ya da boş string/false) geçmeye
 * zorlar; bu da tam olarak Builder'ın çözdüğü okunamaz-çağrı-noktası problemidir. Bu sınıfın
 * yalnızca 2-3 her zaman zorunlu alanı olsaydı, record daha basit ve daha iyi bir seçim
 * olurdu (bkz. docs/design-patterns.md).
 *
 * <p>PATTERN: Builder - inşa edilmekte olan nesne yalnızca {@code build()} çağrılarak elde
 * edilebilir; bu noktada bir kez doğrulanır ve tamamen oluşmuş, immutable halde teslim
 * edilir; ara builder durumu mutable'dır ama {@code OrderRequest}'in kendisi hiçbir zaman
 * değildir.
 */
public final class OrderRequest {

    private final String customerId;
    private final List<String> itemIds;
    private final String couponCode; // opsiyonel
    private final boolean giftWrap; // opsiyonel, varsayılan false
    private final String deliveryNote; // opsiyonel

    private OrderRequest(Builder builder) {
        this.customerId = builder.customerId;
        this.itemIds = List.copyOf(builder.itemIds);
        this.couponCode = builder.couponCode;
        this.giftWrap = builder.giftWrap;
        this.deliveryNote = builder.deliveryNote;
    }

    public static Builder builder(String customerId) {
        return new Builder(customerId);
    }

    public String customerId() {
        return customerId;
    }

    public List<String> itemIds() {
        return itemIds;
    }

    public String couponCode() {
        return couponCode;
    }

    public boolean giftWrap() {
        return giftWrap;
    }

    public String deliveryNote() {
        return deliveryNote;
    }

    public static final class Builder {
        private final String customerId;
        private List<String> itemIds = List.of();
        private String couponCode;
        private boolean giftWrap;
        private String deliveryNote;

        private Builder(String customerId) {
            this.customerId = customerId;
        }

        public Builder items(List<String> itemIds) {
            this.itemIds = itemIds;
            return this;
        }

        public Builder couponCode(String couponCode) {
            this.couponCode = couponCode;
            return this;
        }

        public Builder giftWrap(boolean giftWrap) {
            this.giftWrap = giftWrap;
            return this;
        }

        public Builder deliveryNote(String deliveryNote) {
            this.deliveryNote = deliveryNote;
            return this;
        }

        public OrderRequest build() {
            if (customerId == null || customerId.isBlank()) {
                throw new IllegalStateException("customerId is required");
            }
            if (itemIds.isEmpty()) {
                throw new IllegalStateException("an order needs at least one item");
            }
            return new OrderRequest(this);
        }
    }
}
