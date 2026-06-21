package dev.villoverlay;

/** A lobby player we want stats for: a name and, when the tab list gave us one, a uuid. */
public final class PlayerRef {
    public final String name;
    public final String uuid; // undashed, may be null

    public PlayerRef(String name, String uuid) {
        this.name = name;
        this.uuid = uuid;
    }
}
