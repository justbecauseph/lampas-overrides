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

        for (Faction f : values()) {
            if (f.name().equals(trimmed)) {
                return f;
            }
        }
        return NONE;
    }
}
