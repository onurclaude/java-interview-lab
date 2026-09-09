package com.interviewlab.jpa.repository;

import com.interviewlab.jpa.entity.Order;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Fetch join: gerçek bir SQL JOIN kullanan, {@code orderItems}'ı yalnızca bu çağrı için
     * eager olarak dolduran tek bir sorgu - bunu, mapping'in kendisini EAGER yapmakla
     * (ki bu, bir {@code Order}'ın her yüklemesinde, her zaman, her yerde uygulanırdı)
     * kıyasla.
     */
    @Query("select distinct o from Order o left join fetch o.orderItems")
    List<Order> findAllWithItemsFetchJoin();

    /**
     * {@code @EntityGraph}: elle yazılmış bir {@code join fetch}'e deklaratif bir alternatif -
     * aynı tek sorgu sonucu, JPQL sözdizimi yerine "hangi ilişkilerin eager olarak dahil
     * edileceği" şeklinde ifade edilir. Temel sorgu elle yazılmak yerine zaten Spring Data
     * tarafından üretiliyorsa (buradaki örtük {@code select o from Order o} gibi) tercih
     * edilir.
     */
    @EntityGraph(attributePaths = "orderItems")
    @Query("select o from Order o")
    List<Order> findAllWithEntityGraph();

    @Query("select new com.interviewlab.jpa.repository.OrderSummary(o.id, o.customerName, count(oi)) "
            + "from Order o left join o.orderItems oi group by o.id, o.customerName")
    List<OrderSummary> findAllSummaries();
}
