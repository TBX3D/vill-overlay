package dev.villoverlay;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Hypixel Bed Wars stats overlay for 1.8.9. Only does work while you're in a
 * Bed Wars game; stats refresh at most every {@code refreshSeconds} (30 by
 * default). Bind keys under Controls -> "Vill Overlay", or use {@code /vill}.
 */
@Mod(
        modid = BwOverlayMod.MODID,
        name = "Vill Overlay",
        version = BwOverlayMod.VERSION,
        clientSideOnly = true
)
public class BwOverlayMod {

    public static final String MODID = "villoverlay";
    public static final String VERSION = "1.1.0";

    private static KeyBinding keyToggle;
    private static KeyBinding keyRefresh;
    private static KeyBinding keyAi;

    private static ExecutorService pool;
    private static volatile StatsProvider provider;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        BwConfig.load(event.getSuggestedConfigurationFile());
        File cfgDir = event.getSuggestedConfigurationFile().getParentFile();
        PlayerTags.init(cfgDir);
        SessionHistory.get().init(cfgDir);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        pool = Executors.newFixedThreadPool(3, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "villoverlay-fetch");
                t.setDaemon(true);
                return t;
            }
        });
        rebuildProvider();

        keyToggle = new KeyBinding("Toggle overlay", Keyboard.KEY_NONE, "Vill Overlay");
        keyRefresh = new KeyBinding("Force refresh stats", Keyboard.KEY_NONE, "Vill Overlay");
        keyAi = new KeyBinding("AI commentary", Keyboard.KEY_NONE, "Vill Overlay");
        ClientRegistry.registerKeyBinding(keyToggle);
        ClientRegistry.registerKeyBinding(keyRefresh);
        ClientRegistry.registerKeyBinding(keyAi);

        MinecraftForge.EVENT_BUS.register(new BwEvents());
        MinecraftForge.EVENT_BUS.register(new OverlayHud());
        ClientCommandHandler.instance.registerCommand(new BwCommand());
    }

    public static void rebuildProvider() {
        provider = "proxy".equalsIgnoreCase(BwConfig.provider) ? new ProxyProvider() : new HypixelProvider();
    }

    public static StatsProvider provider() {
        return provider;
    }

    public static ExecutorService pool() {
        return pool;
    }

    public static void triggerAi() {
        Commentary.runAi(StatsService.get().sorted(), pool);
    }

    /** Polled each client tick from {@link BwEvents}. */
    public static void handleKeys() {
        if (keyToggle != null && keyToggle.isPressed()) {
            BwConfig.setBool("enabled", !BwConfig.enabled);
            Commentary.chat("§8[§bVill§8] §7overlay " + (BwConfig.enabled ? "§aon" : "§coff"));
        }
        if (keyRefresh != null && keyRefresh.isPressed()) {
            StatsService.get().forceRefresh();
            Commentary.chat("§8[§bVill§8] §7refreshing...");
        }
        if (keyAi != null && keyAi.isPressed()) {
            triggerAi();
        }
    }
}
