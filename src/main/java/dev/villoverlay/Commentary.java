package dev.villoverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Two flavours of "who's dangerous":
 *  - heuristic(): instant, offline, one line for the HUD.
 *  - runAi(): shells out to the local `claude` CLI in print mode. That runs on
 *    your Claude subscription login (not the metered API), so it spends ZERO
 *    usage credits - same approach as the claudecode mod's cli backend.
 */
public final class Commentary {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private Commentary() {
    }

    public static String heuristic(List<BwStats> sorted) {
        if (sorted.isEmpty()) {
            return "";
        }
        BwStats top = null;
        BwStats easy = null;
        int sweats = 0;
        int nicks = 0;
        int counted = 0;
        double sumFkdr = 0;
        for (BwStats s : sorted) {
            if (s.loading || s.error != null) {
                continue;
            }
            if (s.nicked) {
                nicks++;
                continue;
            }
            counted++;
            sumFkdr += s.fkdr;
            if (top == null || s.index > top.index) {
                top = s;
            }
            if (easy == null || s.index < easy.index) {
                easy = s;
            }
            if (s.threat >= 3) {
                sweats++;
            }
        }
        if (counted == 0 && nicks == 0) {
            return "§7scanning lobby...";
        }
        StringBuilder sb = new StringBuilder();
        if (top != null) {
            sb.append("§c⚠ ").append(top.name).append(" §7").append(top.star)
                    .append("✫ §c").append(fmt(top.fkdr)).append("fkdr");
        }
        if (easy != null && easy != top) {
            sb.append(" §8| §aeasiest ").append(easy.name).append(" §7").append(fmt(easy.fkdr));
        }
        if (counted > 0) {
            sb.append(" §8| §7avg ").append(fmt(sumFkdr / counted)).append("fkdr, ")
                    .append(sweats).append(" sweat").append(sweats == 1 ? "" : "s");
        }
        if (nicks > 0) {
            sb.append(" §8| §d").append(nicks).append(" nick").append(nicks == 1 ? "" : "s");
        }
        return sb.toString();
    }

    static String fmt(double d) {
        return String.format("%.1f", d);
    }

    /** Build the lobby text and run `claude -p` off-thread; prints replies to chat. */
    public static void runAi(final List<BwStats> sorted, ExecutorService pool) {
        if (pool == null) {
            return;
        }
        if (sorted.isEmpty()) {
            chat("§b[Vill AI] §7no players to comment on yet");
            return;
        }
        chat("§b[Vill AI] §7thinking... §8(claude -p, your subscription)");
        pool.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    String out = runClaude(buildPrompt(sorted)).trim();
                    if (out.isEmpty()) {
                        out = "(no output)";
                    }
                    for (String line : out.split("\n")) {
                        chat("§b[Vill AI] §f" + line);
                    }
                } catch (Exception e) {
                    chat("§b[Vill AI] §cfailed: " + e.getMessage());
                }
            }
        });
    }

    private static String buildPrompt(List<BwStats> sorted) {
        StringBuilder sb = new StringBuilder();
        sb.append(BwConfig.aiPrompt).append("\n\nPlayers:\n");
        for (BwStats s : sorted) {
            if (s.loading) {
                continue;
            }
            sb.append("- ").append(s.name);
            if (s.nicked) {
                sb.append(" (NICKED - no stats)\n");
            } else if (s.error != null) {
                sb.append(" (lookup failed)\n");
            } else {
                sb.append(": ").append(s.star).append(" star, ")
                        .append(fmt(s.fkdr)).append(" FKDR, ")
                        .append(fmt(s.wlr)).append(" WLR, ")
                        .append(s.winstreakKnown ? s.winstreak + " ws" : "ws hidden")
                        .append("\n");
            }
        }
        return sb.toString();
    }

    private static String runClaude(String prompt) throws Exception {
        List<String> cmd = new ArrayList<String>();
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (windows) {
            cmd.add("cmd");
            cmd.add("/c");
        }
        cmd.add(BwConfig.claudePath);
        cmd.add("-p");
        cmd.add("--output-format");
        cmd.add("text");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(Minecraft.getMinecraft().mcDataDir);
        pb.redirectErrorStream(true);

        final Process proc;
        try {
            proc = pb.start();
        } catch (Exception e) {
            throw new Exception("couldn't run \"" + BwConfig.claudePath + "\" - set the full path with claudePath in the config", e);
        }

        OutputStream stdin = proc.getOutputStream();
        stdin.write(prompt.getBytes(UTF8));
        stdin.flush();
        stdin.close();

        StringBuilder full = new StringBuilder();
        Reader r = new InputStreamReader(proc.getInputStream(), UTF8);
        char[] buf = new char[512];
        int n;
        while ((n = r.read(buf)) > 0) {
            full.append(buf, 0, n);
        }
        int exit = proc.waitFor();
        if (exit != 0) {
            String err = full.toString().trim();
            if (err.length() > 200) {
                err = err.substring(0, 200) + "...";
            }
            throw new Exception("claude exit " + exit + (err.isEmpty() ? "" : ": " + err));
        }
        return full.toString();
    }

    /** Thread-safe chat print (hops to the main thread). */
    static void chat(final String msg) {
        final Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                if (mc.thePlayer != null) {
                    mc.thePlayer.addChatMessage(new ChatComponentText(msg));
                }
            }
        });
    }
}
