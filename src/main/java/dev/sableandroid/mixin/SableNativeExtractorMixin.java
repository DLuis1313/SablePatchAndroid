package dev.sableandroid.mixin;

import dev.sableandroid.SableAndroidPatch;
import dev.sableandroid.NativeLibraryExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;

/**
 * Alvo: a classe do Sable 2.0.x que extrai o nativo do JAR.
 *
 * No Sable 2.0.x, antes de chamar System.load(), o Sable extrai o binário
 * do JAR para um diretório temporário. Essa classe intercepta esse extrator
 * para substituir o arquivo extraído pela versão Android.
 *
 * Alvo provável: gay.sable.sable.physics.NativeExtractor
 * ou: gay.sable.sable.util.NativeUtil
 *
 * Método alvo: extract(String libName) : Path
 * ou: extractLib() : File
 *
 * ATENÇÃO: Ajuste o target e o método conforme a classe real do Sable 2.0.x.
 */
@Mixin(targets = "gay.sable.sable.physics.NativeExtractor", remap = false)
public class SableNativeExtractorMixin {

    /**
     * Intercepta a extração do nativo para substituir pelo .so ARM64 do Android.
     * Se não está no Android, deixa o método original rodar.
     */
    @Inject(
        method = "extract(Ljava/lang/String;)Ljava/nio/file/Path;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void onExtract(String libName, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Path> cir) {
        if (!SableAndroidPatch.IS_ANDROID) {
            return;
        }

        if (!libName.contains("rapier") && !libName.contains("sable")) {
            return; // só intercepta libs relevantes do Sable
        }

        SableAndroidPatch.LOGGER.info("[SableAndroidPatch] Interceptando extração de: {}", libName);

        try {
            String soName = libName.endsWith(".so") ? libName : "lib" + libName + ".so";
            File extracted = NativeLibraryExtractor.extractAndGetNative(soName);
            cir.setReturnValue(extracted.toPath());
        } catch (Exception e) {
            SableAndroidPatch.LOGGER.error("[SableAndroidPatch] Falha ao extrair nativo Android para: {}", libName, e);
        }
    }
}
