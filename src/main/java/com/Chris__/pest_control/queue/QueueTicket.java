package com.Chris__.pest_control.queue;

import com.Chris__.pest_control.Tier;

public record QueueTicket(String playerUuid, Tier tier, long queuedAtMillis) {
}
