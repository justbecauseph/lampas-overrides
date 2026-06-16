package town.lampas.overrides;

public enum Faction {
    NAVY,
    TOURISM,
    LMI,
    MERCHANTS,
    RELIGION,
    FISHERIES,
    NOBILITY,
    NONE;

    // Cached to avoid the defensive array clone that values() performs on every call.
    private static final Faction[] VALUES = values();

    public static Faction fromString(String name) {
        if (name == null) return NONE;
        String trimmed = name.trim().toUpperCase();

        for (Faction f : VALUES) {
            if (f.name().equals(trimmed)) {
                return f;
            }
        }
        return NONE;
    }
}
