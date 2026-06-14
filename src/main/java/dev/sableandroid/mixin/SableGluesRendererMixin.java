package dev.sableandroid.mixin;

import dev.sableandroid.SableAndroidPatch;
import dev.sableandroid.compat.GluesCompatHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Patch de compatibilidade do renderer do Sable com o MobileGlues.
 *
 * O Sable usa shaders GLSL que chamam funções/extensões não disponíveis no
 * MobileGlues (que é OpenGL ES 3.x sobre Vulkan via ANGLE/GLES).
 *
 * Problemas conhecidos com MobileGlues + Sable 2.0.x:
 *  - Uso de gl_FragDepth sem declaração de extensão → patch remove/adapta
 *  - Uso de ARB_shader_storage_buffer → desativado no mobile
 *  - Compute shaders com workgroup sizes incompatíveis
 *
 * Alvo: gay.sable.sable.render.SableRenderer  (ou similar)
 * Método: initShaders() ou setupRenderer() ou init()
 *
 * ATENÇÃO: Ajuste o target para a classe de renderer real do Sable 2.0.x.
 */
@Mixin(targets = "gay.sable.sable.render.SableRenderer", remap = false)
public class SableGluesRendererMixin {

    /**
     * Antes de inicializar o renderer, notifica o GluesCompatHelper
     * para aplicar patches nos shaders e reduzir features que o MobileGlues
     * não suporta.
     */
    @Inject(
        method = "init()V",
        at = @At("HEAD"),
        remap = false
    )
    private void onInitRenderer(CallbackInfo ci) {
        if (!SableAndroidPatch.IS_ANDROID) {
            return;
        }
        SableAndroidPatch.LOGGER.info("[SableAndroidPatch] Aplicando compatibilidade MobileGlues no SableRenderer...");
        GluesCompatHelper.applyRendererPatches();
    }

    /**
     * Intercepta o carregamento de shaders para substituir versões incompatíveis.
     * O Sable 2.0.x provavelmente tem um método loadShader(String name) ou similar.
     */
    @Inject(
        method = "loadShaders()V",
        at = @At("HEAD"),
        remap = false
    )
    private void onLoadShaders(CallbackInfo ci) {
        if (!SableAndroidPatch.IS_ANDROID) {
            return;
        }
        GluesCompatHelper.patchShaderSources();
    }
}
