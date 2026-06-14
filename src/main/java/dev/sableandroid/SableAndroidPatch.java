package dev.sableandroid;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod("sableandroidpatch2")
public class SableAndroidPatch {

    public static final String MOD_ID = "sableandroidpatch2";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    /**
     * Detecta se está rodando no Android pelo vendor da JVM ou pela presença
     * de APIs Android. O ZalithLauncher injeta a propriedade "java.vendor"
     * com valor contendo "Android" ou "Pojav".
     */
    public static final boolean IS_ANDROID = detectAndroid();

    private static boolean detectAndroid() {
        String vendor = System.getProperty("java.vendor", "").toLowerCase();
        String vmName = System.getProperty("java.vm.name", "").toLowerCase();
        String osName = System.getProperty("os.name", "").toLowerCase();

        boolean byVendor = vendor.contains("android") || vendor.contains("pojav")
                        || vendor.contains("zalith");
        boolean byVm = vmName.contains("dalvik") || vmName.contains("art");
        // ZalithLauncher 2 em Linux-emulado pode reportar "linux" mas com nativeDir
        boolean byNativeDir = System.getProperty("user.home", "").startsWith("/data/");

        return byVendor || byVm || byNativeDir;
    }

    /**
     * Retorna o diretório de libs nativas do Android a partir das propriedades
     * injetadas pelo ZalithLauncher 2.
     * O ZalithLauncher 2 define "natives.dir" ou usa o padrão do pojav.
     */
    public static File getNativeDir() {
        // ZalithLauncher 2 injeta essa propriedade com o caminho para as .so
        String nativesDir = System.getProperty("natives.dir",
                            System.getProperty("pojav.nativesDir", null));
        if (nativesDir != null) {
            return new File(nativesDir);
        }
        // Fallback: pasta padrão das libs da app no Android
        String home = System.getProperty("user.home", "/data/data/net.kdt.pojavlaunch");
        return new File(home, "libs");
    }

    public SableAndroidPatch(IEventBus modBus, ModContainer container) {
        if (IS_ANDROID) {
            LOGGER.info("[SableAndroidPatch] Android detectado! Ativando patches para Sable 2.0.x...");
        } else {
            LOGGER.info("[SableAndroidPatch] Não está rodando no Android. Patches desativados.");
        }
        modBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        if (IS_ANDROID) {
            LOGGER.info("[SableAndroidPatch] Setup concluído. Rapier nativo: {}",
                    getNativeDir().getAbsolutePath());
        }
    }
}
