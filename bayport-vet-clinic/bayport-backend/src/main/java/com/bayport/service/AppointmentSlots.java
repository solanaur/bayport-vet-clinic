package com.bayport.service;

import java.util.Collection;

/**
 * A veterinarian is double-booked when any other appointment already occupies the same slot.
 */
public final class AppointmentSlots {

    private AppointmentSlots() {}

    public static boolean takenBySomeoneElse(Long selfId, Collection<Long> overlappingIds) {
        if (overlappingIds == null) {
            return false;
        }
        for (Long id : overlappingIds) {
            if (id == null) {
                continue;
            }
            if (selfId == null || !selfId.equals(id)) {
                return true;
            }
        }
        return false;
    }
}
