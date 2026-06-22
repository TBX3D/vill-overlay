package dev.villoverlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Owns the roster + fetched stats and decides when to (re)fetch. The decision
 * runs on the main thread (from the client tick); the actual HTTP runs on the
 * fetch pool. Results are published into concurrent maps the HUD reads.
 *
 * Refresh is throttled: a pass happens at most once per {@code refreshSeconds},
 * and within a pass only players whose data is older than that are re-fetched.
 * This is what honours "every 30s, not 24/7".
 */
public final class StatsService {

    private static final StatsService INSTANCE = new StatsService();

    public static StatsService get() {
        return INSTANCE;
    }

    private final ConcurrentHashMap<String, BwStats> results = new ConcurrentHashMap<String, BwStats>();
    private final ConcurrentHashMap<String, Long> lastFetch = new ConcurrentHashMap<String, Long>();
    private final Set<String> inFlight = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private volatile List<PlayerRef> roster = new ArrayList<PlayerRef>();
    private volatile long lastPass = 0L;
    private volatile String commentaryLine = "";

    private StatsService() {
    }

    public void updateRoster(List<PlayerRef> r) {
        this.roster = r;
    }

    public int rosterSize() {
        return roster.size();
    }

    /** Players currently held in the stat cache (current roster plus retained absentees). */
    public int cachedCount() {
        return results.size();
    }

    /**
     * Whether to retain a cached stat line. Players in the current roster are
     * always kept; one who has left is kept until their last fetch is older than
     * the cache window, so re-encountering them shows instantly and skips a
     * refetch. A zero window restores drop-on-leave. This bounds the maps to the
     * current roster plus whoever was fetched within the window, not the whole
     * session.
     */
    static boolean keepCached(boolean inRoster, Long lastFetch, long now, long cacheMs) {
        if (inRoster) {
            return true;
        }
        return lastFetch != null && now - lastFetch <= cacheMs;
    }

    /** Force the next tick to re-fetch everyone. */
    public void forceRefresh() {
        lastPass = 0L;
        lastFetch.clear();
    }

    public void maybeRefresh(ExecutorService pool, StatsProvider provider) {
        if (pool == null || provider == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long refreshMs = Math.max(10, BwConfig.refreshSeconds) * 1000L;
        if (now - lastPass < refreshMs) {
            return;
        }
        lastPass = now;

        final List<PlayerRef> snapshot = roster;

        // Keep recently-seen players cached across games instead of dropping
        // everyone who left the lobby; entries fall out once they have been gone
        // longer than the cache window.
        Set<String> present = new java.util.HashSet<String>();
        for (PlayerRef p : snapshot) {
            present.add(p.name.toLowerCase());
        }
        long cacheMs = Math.max(0, BwConfig.cacheMinutes) * 60000L;
        for (String key : new ArrayList<String>(results.keySet())) {
            if (!keepCached(present.contains(key), lastFetch.get(key), now, cacheMs)) {
                results.remove(key);
                lastFetch.remove(key);
            }
        }

        for (final PlayerRef p : snapshot) {
            final String key = p.name.toLowerCase();
            Long last = lastFetch.get(key);
            if (last != null && now - last < refreshMs) {
                continue;
            }
            if (!inFlight.add(key)) {
                continue;
            }
            if (!results.containsKey(key)) {
                results.put(key, BwStats.loading(p.name, p.uuid));
            }
            final StatsProvider prov = provider;
            pool.submit(new Runnable() {
                @Override
                public void run() {
                    fetchOne(p, key, prov);
                }
            });
        }
    }

    /** Tag a finished stat line if its uuid is on the shared blacklist (no-op when off). */
    private static BwStats applyFlag(BwStats s) {
        if (s == null || s.uuid == null || s.nicked || s.loading || s.error != null) {
            return s;
        }
        Blacklist.Result r = Blacklist.resolve(s.uuid);
        return r != null ? s.withFlag(r.label) : s;
    }

    private void fetchOne(PlayerRef p, String key, StatsProvider provider) {
        try {
            String uuid = (p.uuid != null && !p.uuid.isEmpty()) ? p.uuid : MojangResolver.uuidFor(p.name);
            BwStats s;
            if (uuid != null) {
                s = HypixelParse.parse(provider.fetchPlayer(uuid), p.name, uuid);
            } else {
                // No Mojang account for this name => a Hypixel nick. Try to denick
                // it to a real account; on success fetch and flag those real stats.
                Denicker.Result d = Denicker.resolve(p.name);
                if (d != null && d.uuid != null) {
                    BwStats real = HypixelParse.parse(provider.fetchPlayer(d.uuid), p.name, d.uuid);
                    s = real.asDenicked(d.ign != null ? d.ign : p.name);
                } else {
                    s = BwStats.nick(p.name);
                }
            }
            results.put(key, applyFlag(s));
        } catch (StatsException e) {
            results.put(key, BwStats.error(p.name, p.uuid, e.getMessage()));
        } finally {
            // Count the attempt either way; /vill refresh (or the next cycle) retries.
            lastFetch.put(key, System.currentTimeMillis());
            inFlight.remove(key);
            commentaryLine = Commentary.heuristic(sorted());
        }
    }

    /** Current roster's stats, scariest first. Missing entries show as "loading". */
    public List<BwStats> sorted() {
        List<PlayerRef> snap = roster;
        List<BwStats> list = new ArrayList<BwStats>();
        for (PlayerRef p : snap) {
            BwStats s = results.get(p.name.toLowerCase());
            list.add(s != null ? s : BwStats.loading(p.name, p.uuid));
        }
        final String field = BwConfig.sortBy;
        Collections.sort(list, new Comparator<BwStats>() {
            @Override
            public int compare(BwStats a, BwStats b) {
                return compareFor(a, b, field);
            }
        });
        return list;
    }

    /** Sort keys offered to {@code /vill sort}; also the source of truth for validation. */
    public static final Set<String> SORT_FIELDS = Collections.unmodifiableSet(new java.util.LinkedHashSet<String>(
            java.util.Arrays.asList("index", "star", "fkdr", "wlr", "bblr", "finals", "wins", "ws")));

    /**
     * Roster order for the HUD: blacklisted players always come first, then by the
     * chosen field, highest first. An unknown field falls back to the threat index.
     */
    static int compareFor(BwStats a, BwStats b, String field) {
        if (a.flagged != b.flagged) {
            return a.flagged ? -1 : 1; // blacklisted players first, always
        }
        return Double.compare(sortValue(b, field), sortValue(a, field));
    }

    static double sortValue(BwStats s, String field) {
        if ("star".equals(field)) {
            return s.star;
        }
        if ("fkdr".equals(field)) {
            return s.fkdr;
        }
        if ("wlr".equals(field)) {
            return s.wlr;
        }
        if ("bblr".equals(field)) {
            return s.bblr;
        }
        if ("finals".equals(field)) {
            return s.finalKills;
        }
        if ("wins".equals(field)) {
            return s.wins;
        }
        if ("ws".equals(field)) {
            return s.winstreakKnown ? s.winstreak : -1;
        }
        return s.index;
    }

    public String commentary() {
        return commentaryLine;
    }
}
