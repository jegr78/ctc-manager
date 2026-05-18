package org.ctc.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContextLoadCountListenerTest {

    @Test
    void whenInitializeCalledTwice_thenCountIncrementsByTwo() {
        // given
        var listener = new ContextLoadCountListener();
        int before = ContextLoadCountListener.getCount();

        // when
        listener.initialize(null);
        listener.initialize(null);

        // then
        assertThat(ContextLoadCountListener.getCount()).isEqualTo(before + 2);
    }
}
