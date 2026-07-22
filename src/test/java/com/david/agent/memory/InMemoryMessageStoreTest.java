package com.david.agent.memory;

import com.david.agent.agent.message.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryMessageStoreTest {

    @Test
    void storesIndependentConversationHistories() {
        InMemoryMessageStore store = new InMemoryMessageStore();
        store.append("one", Message.user("first"));
        store.append("two", Message.user("second"));

        assertEquals("first", store.history("one").get(0).content());
        assertEquals("second", store.history("two").get(0).content());

        store.clear("one");
        assertTrue(store.history("one").isEmpty());
    }
}
