package de.staff.sus.manager;

import java.util.ArrayList;
import java.util.List;

/**
 * Haelt alle gesammelten Verdachtsmomente eines einzelnen Spielers.
 */
public class SuspiciousEntry {

    public static class Flag {
        private final String reason;
        private final long timestamp;

        public Flag(String reason, long timestamp) {
            this.reason = reason;
            this.timestamp = timestamp;
        }

        public String getReason() {
            return reason;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    private final List<Flag> flags = new ArrayList<>();
    private int score = 0;

    public void addFlag(String reason) {
        flags.add(new Flag(reason, System.currentTimeMillis()));
        score++;
    }

    public List<Flag> getFlags() {
        return flags;
    }

    public int getScore() {
        return score;
    }

    public String getLastReason() {
        if (flags.isEmpty()) return "Unbekannt";
        return flags.get(flags.size() - 1).getReason();
    }
}
