package com.interviewlab.jpa.repository;

/**
 * DTO projection: {@link OrderRepository#findAllSummaries()}'in arkasındaki JPQL sorgusu
 * doğrudan yalnızca bu üç değeri (bir id, bir isim, bir sayı) seçer - persistence context'e
 * hiçbir {@code Order} ya da {@code OrderItem} entity'si, hiçbir koleksiyon yüklenmez.
 * Çağıranın yalnızca bir özete ihtiyacı olduğunda (buradaki gibi), bu hem en hızlı hem de en
 * az bellek kullanan seçenektir: tek sorgu, N+1 imkansızdır çünkü onu tetikleyecek lazy bir
 * koleksiyon bile yoktur.
 */
public record OrderSummary(Long id, String customerName, long itemCount) {
}
