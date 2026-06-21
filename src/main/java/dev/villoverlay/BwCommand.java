package dev.villoverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import java.util.Arrays;
import java.util.List;

/** Client-side {@code /vill} command (registered through ClientCommandHandler). */
public final class BwCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "vill";
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("bw", "villoverlay");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/vill <key|proxykey|provider|refresh|ai|toggle|hud|status|tag|untag|note|tags|denick|alert|seen>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            msg(sender, getCommandUsage(sender));
            return;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("key")) {
            if (args.length < 2) {
                msg(sender, "§cusage: /vill key <hypixel-api-key>");
                return;
            }
            BwConfig.setString("hypixelKey", args[1]);
            msg(sender, "§aHypixel key saved.");
        } else if (sub.equals("proxykey")) {
            if (args.length < 2) {
                msg(sender, "§cusage: /vill proxykey <key>");
                return;
            }
            BwConfig.setString("proxyKey", args[1]);
            msg(sender, "§aProxy key saved.");
        } else if (sub.equals("provider")) {
            if (args.length < 2) {
                msg(sender, "§7provider = §f" + BwConfig.provider);
                return;
            }
            String p = args[1].toLowerCase();
            if (!p.equals("hypixel") && !p.equals("proxy")) {
                msg(sender, "§cprovider must be 'hypixel' or 'proxy'");
                return;
            }
            BwConfig.setString("provider", p);
            BwOverlayMod.rebuildProvider();
            StatsService.get().forceRefresh();
            msg(sender, "§aprovider = §f" + p);
        } else if (sub.equals("refresh")) {
            StatsService.get().forceRefresh();
            msg(sender, "§arefreshing now.");
        } else if (sub.equals("ai")) {
            BwOverlayMod.triggerAi();
        } else if (sub.equals("toggle")) {
            BwConfig.setBool("enabled", !BwConfig.enabled);
            msg(sender, "§7overlay " + (BwConfig.enabled ? "§aon" : "§coff"));
        } else if (sub.equals("hud")) {
            if (args.length >= 3) {
                try {
                    BwConfig.setInt("hudX", Integer.parseInt(args[1]));
                    BwConfig.setInt("hudY", Integer.parseInt(args[2]));
                    msg(sender, "§ahud moved to " + args[1] + "," + args[2]);
                } catch (NumberFormatException ex) {
                    msg(sender, "§cusage: /vill hud <x> <y>");
                }
            } else {
                msg(sender, "§7hud at §f" + BwConfig.hudX + "," + BwConfig.hudY + " §8(usage: /vill hud <x> <y>)");
            }
        } else if (sub.equals("status")) {
            msg(sender, "§7provider §f" + BwConfig.provider
                    + " §7| hypixel key " + (BwConfig.hypixelKey.isEmpty() ? "§cmissing" : "§aset")
                    + " §7| proxy key " + (BwConfig.proxyKey.isEmpty() ? "§8-" : "§aset"));
            msg(sender, "§7refresh §f" + BwConfig.refreshSeconds + "s §7| roster §f" + StatsService.get().rosterSize()
                    + " §7| active §f" + GameDetector.active(Minecraft.getMinecraft()));
        } else if (sub.equals("tag")) {
            if (args.length < 3) {
                msg(sender, "§cusage: /vill tag <player> <label> §8(sniper/cheater/blacklist/friend/...)");
                return;
            }
            PlayerTags.set(args[1], args[2], null);
            String label = args[2].toLowerCase();
            msg(sender, "§atagged §f" + args[1] + " " + PlayerTags.colorFor(label) + label
                    + (PlayerTags.isDanger(label) ? " §7(will alert)" : ""));
        } else if (sub.equals("untag")) {
            if (args.length < 2) {
                msg(sender, "§cusage: /vill untag <player>");
                return;
            }
            msg(sender, PlayerTags.remove(args[1]) ? "§auntagged §f" + args[1] : "§7no tag on §f" + args[1]);
        } else if (sub.equals("note")) {
            if (args.length < 3) {
                msg(sender, "§cusage: /vill note <player> <text...>");
                return;
            }
            PlayerTags.note(args[1], join(args, 2));
            msg(sender, "§anote saved for §f" + args[1]);
        } else if (sub.equals("tags")) {
            if (PlayerTags.size() == 0) {
                msg(sender, "§7no tags yet §8(/vill tag <player> <label>)");
                return;
            }
            msg(sender, "§7tagged players §8(" + PlayerTags.size() + "):");
            for (java.util.Map.Entry<String, PlayerTags.Tag> e : PlayerTags.all().entrySet()) {
                PlayerTags.Tag t = e.getValue();
                msg(sender, " " + t.color + e.getKey() + " §8- " + t.color + t.label
                        + (t.note != null ? " §7" + t.note : ""));
            }
        } else if (sub.equals("denick")) {
            if (args.length < 2) {
                msg(sender, "§7denick " + (BwConfig.denickEnabled ? "§aon" : "§coff")
                        + " §7| url §f" + BwConfig.denickUrl);
                msg(sender, "§8usage: /vill denick <on|off|url <template>|key <key>>");
                return;
            }
            String d = args[1].toLowerCase();
            if (d.equals("on") || d.equals("off")) {
                BwConfig.setBool("denickEnabled", d.equals("on"));
                StatsService.get().forceRefresh();
                msg(sender, "§7denick " + (BwConfig.denickEnabled ? "§aon" : "§coff"));
            } else if (d.equals("url") && args.length >= 3) {
                BwConfig.setString("denickUrl", join(args, 2));
                msg(sender, "§adenick url set.");
            } else if (d.equals("key") && args.length >= 3) {
                BwConfig.setString("denickKey", args[2]);
                msg(sender, "§adenick key saved.");
            } else {
                msg(sender, "§8usage: /vill denick <on|off|url <template>|key <key>>");
            }
        } else if (sub.equals("alert")) {
            if (args.length < 2) {
                msg(sender, "§7alert " + (BwConfig.alertEnabled ? "§aon" : "§coff")
                        + " §7| sound " + (BwConfig.alertSound ? "§aon" : "§coff")
                        + " §7| star §f" + BwConfig.alertStar + " §7| fkdr §f" + BwConfig.alertFkdr
                        + " §7| ws §f" + BwConfig.alertWinstreak);
                msg(sender, "§8usage: /vill alert <on|off|sound on|off|star <n>|fkdr <n>|ws <n>|nick on|off>");
                return;
            }
            handleAlert(sender, args);
        } else if (sub.equals("seen")) {
            if (args.length < 2) {
                msg(sender, "§cusage: /vill seen <player>");
                return;
            }
            SessionHistory.Seen info = SessionHistory.get().info(args[1]);
            if (info == null) {
                msg(sender, "§7never seen §f" + args[1]);
            } else {
                msg(sender, "§7seen §f" + args[1] + " §7x§f" + info.count
                        + " §8(first " + ago(info.first) + ", last " + ago(info.last) + ")");
            }
        } else {
            msg(sender, getCommandUsage(sender));
        }
    }

    private static void handleAlert(ICommandSender sender, String[] args) {
        String a = args[1].toLowerCase();
        if (a.equals("on") || a.equals("off")) {
            BwConfig.setBool("alertEnabled", a.equals("on"));
            msg(sender, "§7alerts " + (BwConfig.alertEnabled ? "§aon" : "§coff"));
        } else if (a.equals("sound") && args.length >= 3) {
            BwConfig.setBool("alertSound", args[2].equalsIgnoreCase("on"));
            msg(sender, "§7alert sound " + (BwConfig.alertSound ? "§aon" : "§coff"));
        } else if (a.equals("nick") && args.length >= 3) {
            BwConfig.setBool("alertOnNick", args[2].equalsIgnoreCase("on"));
            msg(sender, "§7alert on nick " + (BwConfig.alertOnNick ? "§aon" : "§coff"));
        } else if ((a.equals("star") || a.equals("fkdr") || a.equals("ws")) && args.length >= 3) {
            try {
                int n = Integer.parseInt(args[2]);
                String field = a.equals("star") ? "alertStar" : a.equals("fkdr") ? "alertFkdr" : "alertWinstreak";
                BwConfig.setInt(field, n);
                msg(sender, "§a" + a + " threshold = §f" + n + " §8(0 = off)");
            } catch (NumberFormatException ex) {
                msg(sender, "§cnot a number: " + args[2]);
            }
        } else {
            msg(sender, "§8usage: /vill alert <on|off|sound on|off|star <n>|fkdr <n>|ws <n>|nick on|off>");
        }
    }

    private static String join(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    /** Rough "Nm/Nh/Nd ago" for a past epoch-millis, or "?" if unset. */
    private static String ago(long when) {
        if (when <= 0) {
            return "?";
        }
        long secs = (System.currentTimeMillis() - when) / 1000L;
        if (secs < 60) {
            return secs + "s ago";
        }
        if (secs < 3600) {
            return (secs / 60) + "m ago";
        }
        if (secs < 86400) {
            return (secs / 3600) + "h ago";
        }
        return (secs / 86400) + "d ago";
    }

    private static void msg(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText("§8[§bVill§8] §r" + text));
    }
}
