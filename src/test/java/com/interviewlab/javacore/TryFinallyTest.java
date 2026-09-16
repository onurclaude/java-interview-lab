package com.interviewlab.javacore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.interviewlab.javacore.resources.TrackedResource;
import com.interviewlab.javacore.resources.bad.ManualCloseWithoutFinallyService;
import com.interviewlab.javacore.resources.good.TryWithResourcesService;
import org.junit.jupiter.api.Test;

/** final/finally/try-with-resources yazısı için docs/NOTES_CORRECTIONS.md dosyasına bakın. */
class TryFinallyTest {

    @Test
    void shouldLeakResourceWhenCloseIsNotInFinally() {
        TrackedResource.reset();
        ManualCloseWithoutFinallyService service = new ManualCloseWithoutFinallyService();

        assertThatThrownBy(() -> service.run(true)).isInstanceOf(IllegalStateException.class);

        assertThat(TrackedResource.openCount()).isEqualTo(1);
        assertThat(TrackedResource.closedCount())
                .as("exception, close() satırını atladı - kaynak sızdı")
                .isZero();
    }

    @Test
    void shouldAlwaysCloseResourceWithTryWithResourcesEvenOnFailure() {
        TrackedResource.reset();
        TryWithResourcesService service = new TryWithResourcesService();

        assertThatThrownBy(() -> service.run(true)).isInstanceOf(IllegalStateException.class);

        assertThat(TrackedResource.openCount()).isEqualTo(1);
        assertThat(TrackedResource.closedCount())
                .as("try-with-resources, exception fırlasa BİLE close()'u garanti eder")
                .isEqualTo(1);
    }

    @Test
    void shouldAlsoCloseResourceOnTheHappyPath() {
        TrackedResource.reset();
        new TryWithResourcesService().run(false);

        assertThat(TrackedResource.closedCount()).isEqualTo(1);
    }
}
