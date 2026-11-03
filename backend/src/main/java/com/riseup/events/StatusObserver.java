package com.riseup.events;

public interface StatusObserver {
    void onStatusChanged(StatusEvent event);

    default boolean caresAbout(String subjectType) {
        return true;
    }
}
