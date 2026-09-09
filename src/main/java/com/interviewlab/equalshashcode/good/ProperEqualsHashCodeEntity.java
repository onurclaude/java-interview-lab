package com.interviewlab.equalshashcode.good;

import java.util.Objects;

/** Doğru karşılık: equals() ve hashCode(), ikisi de aynı immutable alandan türetilir. */
public class ProperEqualsHashCodeEntity {

    private final Long id;

    public ProperEqualsHashCodeEntity(Long id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProperEqualsHashCodeEntity other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
