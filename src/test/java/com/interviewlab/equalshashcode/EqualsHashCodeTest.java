package com.interviewlab.equalshashcode;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewlab.equalshashcode.bad.MutableFieldHashCodeEntity;
import com.interviewlab.equalshashcode.bad.NoHashCodeOverrideEntity;
import com.interviewlab.equalshashcode.good.ProperEqualsHashCodeEntity;
import com.interviewlab.equalshashcode.good.StableHashCodeJpaStyleEntity;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** equals/hashCode yazısı için docs mülakat materyaline bakın. */
class EqualsHashCodeTest {

    @Test
    void shouldAllowDuplicatesInHashSetWithoutHashCodeOverride() {
        Set<NoHashCodeOverrideEntity> set = new HashSet<>();
        set.add(new NoHashCodeOverrideEntity(1L));
        set.add(new NoHashCodeOverrideEntity(1L)); // farklı bir instance, ama birinciye .equals() ile eşit

        assertThat(set)
                .as("her iki eleman da birbirine .equals() ile eşit, ama bozuk hashCode ikisinin de set'e girmesine izin veriyor")
                .hasSize(2);
    }

    @Test
    void shouldTreatEqualObjectsAsOneWithProperHashCodeOverride() {
        Set<ProperEqualsHashCodeEntity> set = new HashSet<>();
        set.add(new ProperEqualsHashCodeEntity(1L));
        set.add(new ProperEqualsHashCodeEntity(1L));

        assertThat(set).hasSize(1);
        assertThat(set.contains(new ProperEqualsHashCodeEntity(1L))).isTrue();
    }

    @Test
    void shouldLoseElementInHashSetWhenHashCodeFieldMutatesAfterInsertion() {
        Set<MutableFieldHashCodeEntity> set = new HashSet<>();
        MutableFieldHashCodeEntity entity = new MutableFieldHashCodeEntity();
        set.add(entity); // id == null iken eklendi, buna göre hash'lendi

        entity.assignId(42L); // Hibernate'in persist() sonrası id atamasını simüle eder

        assertThat(set.contains(entity))
                .as("AYNI instance hâlâ fiziksel olarak set içinde, ama hash code'u değişti, "
                        + "bu yüzden bir lookup artık yanlış bucket'a bakıyor ve onu bulamıyor")
                .isFalse();
    }

    @Test
    void shouldStillFindElementInHashSetAfterIdIsAssignedWithStableHashCode() {
        Set<StableHashCodeJpaStyleEntity> set = new HashSet<>();
        StableHashCodeJpaStyleEntity entity = new StableHashCodeJpaStyleEntity();
        set.add(entity);

        entity.assignId(42L);

        assertThat(set.contains(entity))
                .as("sabit bir hashCode, bucket'ın asla değişmediği anlamına gelir, bu yüzden entity asla kaybolmaz")
                .isTrue();
    }
}
