package town.lampas.overrides;

public enum Faction {
    NAVY("NAVY"),
    TOURISM("TOURISM"),
    LMI("LMI"),
    MERCHANTS("MERCHANTS"),
    RELIGION("RELIGION"),
    FISHERIES("FISHERIES"),
    NOBILITY("NOBILITY"),
    NONE("");

    // Cached to avoid the defensive array clone that values() performs on every call.
    private static final Faction[] VALUES = values();

    private final String apiValue;

    Faction(String apiValue) {
        this.apiValue = apiValue;
    }

    public String getApiValue() {
        return apiValue;
    }

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
