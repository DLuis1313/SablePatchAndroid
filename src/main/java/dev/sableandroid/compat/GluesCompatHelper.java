package dev.sableandroid.compat;

import dev.sableandroid.SableAndroidPatch;

/**
 * Aplica patches de compatibilidade entre o renderer do Sable 2.0.x
 * e o MobileGlues (renderer OpenGL ES 3.x do ZalithLauncher 2).
 *
 * Problemas principais e soluções:
 *
 * 1. gl_FragDepth sem extensão declarada:
 *    GLSL desktop assume que gl_FragDepth está disponível.
 *    No GLES, precisa de: #extension GL_EXT_frag_depth : enable
 *
 * 2. ARB_shader_storage_buffer_object (SSBO):
 *    Não disponível no GLES 3.0, parcialmente no GLES 3.1.
 *    Sable usa SSBO para passar dados de física para o shader.
 *    → Precisamos desabilitar ou usar fallback com UBOs.
 *
 * 3. Double precision (dvec, dmat) nos shaders:
 *    Não disponível no GLES. Precisa fazer downcast para float.
 *
 * 4. gl_ClipDistance:
 *    Requer extensão no GLES: #extension GL_EXT_clip_cull_distance
 */
public class GluesCompatHelper {

    private static boolean patchesApplied = false;

    /**
     * Configura o sistema de renderer para funcionar com MobileGlues.
     * Chamado antes de initShaders() no renderer do Sable.
     */
    public static void applyRendererPatches() {
        if (patchesApplied) return;

        SableAndroidPatch.LOGGER.info("[GluesCompat] Aplicando patches de renderer para MobileGlues...");

        // Desabilita features que o MobileGlues não suporta
        setSystemProperty("sable.disableSSBO", "true");
        setSystemProperty("sable.disableComputeShaders", "false"); // GLES 3.1 suporta
        setSystemProperty("sable.forceMobileShaders", "true");
        setSystemProperty("sable.maxWorkgroupSize", "128"); // limite seguro no Android

        // MobileGlues no ZalithLauncher 2 usa ANGLE internamente
        // algumas extensões precisam de fallback
        tryDisableExtension("GL_ARB_shader_storage_buffer_object");
        tryDisableExtension("GL_ARB_gpu_shader_fp64");

        patchesApplied = true;
        SableAndroidPatch.LOGGER.info("[GluesCompat] Patches de renderer aplicados.");
    }

    /**
     * Aplica patches nos sources dos shaders carregados pelo Sable.
     * Substitui construções GLSL incompatíveis com GLES.
     *
     * Nota: O Sable 2.0.x provavelmente carrega shaders como recursos.
     * Este método usa reflection para acessar o cache de shaders
     * e substituir os sources antes da compilação.
     */
    public static void patchShaderSources() {
        SableAndroidPatch.LOGGER.info("[GluesCompat] Aplicando patches nos shader sources...");

        try {
            // Tenta acessar o registro de shaders do Sable via reflection
            // O nome da classe pode variar – ajuste conforme o Sable 2.0.x real
            Class<?> shaderRegistry = Class.forName("gay.sable.sable.render.ShaderRegistry");
            java.lang.reflect.Field sourcesField = shaderRegistry.getDeclaredField("SHADER_SOURCES");
            sourcesField.setAccessible(true);

            @SuppressWarnings("unchecked")
            java.util.Map<String, String> sources =
                (java.util.Map<String, String>) sourcesField.get(null);

            if (sources != null) {
                sources.replaceAll((name, src) -> patchGlslSource(name, src));
                SableAndroidPatch.LOGGER.info("[GluesCompat] {} shaders patcheados.", sources.size());
            }
        } catch (ClassNotFoundException e) {
            SableAndroidPatch.LOGGER.warn("[GluesCompat] ShaderRegistry não encontrado – pulando patch de shaders. " +
                "Ajuste o nome da classe em GluesCompatHelper.java");
        } catch (Exception e) {
            SableAndroidPatch.LOGGER.error("[GluesCompat] Erro ao patchar shaders:", e);
        }
    }

    /**
     * Transforma um source GLSL de desktop para GLES-compatível.
     */
    static String patchGlslSource(String name, String source) {
        if (source == null) return null;

        String patched = source;

        // 1. Atualiza versão GLSL para GLES-compatível
        // #version 430 core → #version 310 es (mínimo para compute no GLES)
        patched = patched.replaceAll("#version 4[0-9]{2}( core)?", "#version 310 es");
        patched = patched.replaceAll("#version 3[3-9]{2}( core)?", "#version 310 es");

        // 2. Remove extensões ARB que não existem no GLES
        patched = patched.replaceAll(
            "#extension GL_ARB_[a-z_]+ : (require|enable)", "// [SableAndroidPatch] ARB ext removida");

        // 3. Adiciona extensões GLES necessárias (se não presentes)
        if (patched.contains("gl_FragDepth") && !patched.contains("GL_EXT_frag_depth")) {
            patched = patched.replaceFirst(
                "(#version[^\n]*\n)",
                "$1#extension GL_EXT_frag_depth : enable\n");
        }

        if (patched.contains("gl_ClipDistance") && !patched.contains("GL_EXT_clip_cull_distance")) {
            patched = patched.replaceFirst(
                "(#version[^\n]*\n)",
                "$1#extension GL_EXT_clip_cull_distance : enable\n");
        }

        // 4. double precision → float (GLES não tem double em shaders)
        patched = patched.replaceAll("\\bdouble\\b", "float");
        patched = patched.replaceAll("\\bdvec([234])\\b", "vec$1");
        patched = patched.replaceAll("\\bdmat([234])\\b", "mat$1");

        // 5. layout(binding=N) sem qualifier storage → compatibilidade GLES
        // GLES não permite layout binding em uniform blocks sem std140
        patched = patched.replaceAll(
            "layout\\(binding = (\\d+)\\) buffer",
            "layout(binding = $1, std430) buffer");

        if (!source.equals(patched)) {
            SableAndroidPatch.LOGGER.debug("[GluesCompat] Shader '{}' foi modificado para GLES.", name);
        }

        return patched;
    }

    private static void setSystemProperty(String key, String value) {
        try {
            System.setProperty(key, value);
        } catch (SecurityException ignored) {
            // Alguns ambientes não permitem setProperty; não é crítico
        }
    }

    private static void tryDisableExtension(String ext) {
        // Sable pode verificar System.getProperty("sable.disable.<ext>")
        // Normaliza o nome da extensão para uma chave de propriedade
        String key = "sable.disable." + ext.replace("GL_", "").toLowerCase();
        setSystemProperty(key, "true");
    }
}
