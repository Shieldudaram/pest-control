package com.Chris__.pest_control.queue;

import com.Chris__.pest_control.Tier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QueueServiceTest {

    @Test
    void picksTierByOldestTicket() {
        QueueService queue = new QueueService();
        queue.enqueue("a", Tier.INTERMEDIATE, 2000L);
        queue.enqueue("b", Tier.NOVICE, 1000L);
        queue.enqueue("c", Tier.VETERAN, 3000L);

        assertEquals(Tier.NOVICE, queue.pickTierByOldestTicket());
    }

    @Test
    void preventsDuplicateQueueEntriesAndSupportsLeave() {
        QueueService queue = new QueueService();

        assertTrue(queue.enqueue("player-1", Tier.NOVICE, 100L));
        assertFalse(queue.enqueue("player-1", Tier.VETERAN, 110L));

        assertEquals(Tier.NOVICE, queue.getTierForPlayer("player-1"));
        assertTrue(queue.remove("player-1"));
        assertNull(queue.getTierForPlayer("player-1"));
    }
}
