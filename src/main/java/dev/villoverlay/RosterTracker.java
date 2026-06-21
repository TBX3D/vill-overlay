package dev.villoverlay;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Builds the lobby roster passively from the tab list - no commands sent. For
 * real (non-nicked) players the tab entry already carries the real account
 * uuid, so we usually skip the Mojang lookup entirely. Must run on the main
 * thread.
 */
public final class RosterTracker {

    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");

    private RosterTracker() {
    }

    public static List<PlayerRef> scanTabList(Minecraft mc) {
        List<PlayerRef> out = new ArrayList<PlayerRef>();
        NetHandlerPlayClient nh = mc.getNetHandler();
        if (nh == null) {
            return out;
        }
        Collection<NetworkPlayerInfo> infos = nh.getPlayerInfoMap();
        if (infos == null) {
            return out;
        }
        for (NetworkPlayerInfo info : infos) {
            GameProfile gp = info.getGameProfile();
            if (gp == null) {
                continue;
            }
            String name = gp.getName();
            if (name == null || !VALID_NAME.matcher(name).matches()) {
                continue;
            }
            String uuid = gp.getId() == null ? null : gp.getId().toString().replace("-", "");
            out.add(new PlayerRef(name, uuid));
        }
        return out;
    }
}
