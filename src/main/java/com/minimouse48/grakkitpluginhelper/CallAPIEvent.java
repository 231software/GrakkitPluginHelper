package com.minimouse48.grakkitpluginhelper;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class CallAPIEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final String message;

    public CallAPIEvent(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
