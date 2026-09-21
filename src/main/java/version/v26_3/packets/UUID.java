package version.v26_3.packets;

public class UUID {
    long lower;
    long upper;

    public UUID(String uuid) {
        String stripped = uuid.replace("-", "");
        this.lower = Long.parseUnsignedLong(stripped.substring(0, 16), 16);
        this.upper = Long.parseUnsignedLong(stripped.substring(16, 32), 16);
    }

    public UUID(long lower, long upper) {
        this.lower = lower;
        this.upper = upper;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }

        UUID uuid = (UUID) o;

        if (lower != uuid.lower) { return false; }
        return upper == uuid.upper;
    }

    @Override
    public int hashCode() {
        int result = (int) (lower ^ (lower >>> 32));
        result = 31 * result + (int) (upper ^ (upper >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return String.format("%016X", lower).toLowerCase() + String.format("%016X", upper).toLowerCase();
    }

    /**
     * Returns the UUID in standard dashed format (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx),
     * as required by the Mojang sessionserver API.
     */
    public String toDashedString() {
        String hex = toString();
        return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
            + "-" + hex.substring(16, 20) + "-" + hex.substring(20, 32);
    }

    public long getUpper() {
        return upper;
    }

    public long getLower() {
        return lower;
    }

    public int[] asIntArray() {
        return new int[]{(int) (lower >> 32), (int) lower, (int) (upper >> 32), (int) upper};
    }
}
