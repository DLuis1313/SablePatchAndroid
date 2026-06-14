package dev.sableandroid.mixin;

import dev.sableandroid.SableAndroidPatch;
import dev.sableandroid.NativeLibraryExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

/**
 * Alvo: a classe do Sable 2.0.x que carrega a lib nativa do Rapier.
 *
 * No Sable 2.0.x, o carregamento foi movido para:
 *   gay.sable.sable.physics.rapier.RapierLoader  (nome provável baseado no pacote do mod)
 * ou
 *   gay.sable.sable.SableRapierNatives
 *
 * O método estático load() (ou loadNatives()) chama System.loadLibrary("sable_rapier")
 * ou System.load(path) com um path extraído do JAR.
 *
 * ATENÇÃO: Ajuste o @Mixin target para o nome real da classe após descompilar o Sable 2.0.x.
 * Use: jar tf sable-2.0.x.jar | grep -i rapier
 * ou: javap -c para encontrar a classe exata.
 *
 * Nomes mais prováveis para Sable 2.0.x:
 *   gay.sable.sable.physics.NativeLoader
 *   gay.sable.sable.rapier.RapierNatives
 */
@Mixin(targets = "gay.sable.sable.physics.NativeLoader", remap = false)
public class SableRapierLoaderMixin {

    /**
     * Intercepta o método que carrega o nativo antes que ele tente.
     * Se estiver no Android, extrai o .so do JAR do mod e carrega manualmente.
     * Se não estiver no Android, deixa o método original rodar normalmente.
     *
     * Ajuste o "method" para o nome exato do método de carregamento do Sable 2.0.x.
     * Nomes comuns: "load", "loadNatives", "init", "initialize", "loadLibraries"
     */
    @Inject(
        method = "load()V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void onLoadNatives(CallbackInfo ci) {
        if (!SableAndroidPatch.IS_ANDROID) {
            return; // deixa o método original rodar no desktop
        }

        SableAndroidPatch.LOGGER.info("[SableAndroidPatch] Interceptando carregamento do sable_rapier...");

        try {
            // Extrai libsable_rapier.so do JAR do patch e carrega
            File nativeLib = NativeLibraryExtractor.extractAndGetNative("libsable_rapier.so");
            System.load(nativeLib.getAbsolutePath());
            SableAndroidPatch.LOGGER.info("[SableAndroidPatch] libsable_rapier.so carregado com sucesso: {}",
                    nativeLib.getAbsolutePath());
            ci.cancel(); // cancela o método original para evitar o loadLibrary que quebraria
        } catch (Exception e) {
            SableAndroidPatch.LOGGER.error("[SableAndroidPatch] FALHA ao carregar sable_rapier nativo!", e);
            SableAndroidPatch.LOGGER.error("[SableAndroidPatch] Certifique-se que libsable_rapier.so está dentro do JAR em /natives/android/arm64-v8a/");
            // Não cancela: deixa o método original tentar (vai falhar, mas com mensagem melhor)
        }
    }
}
