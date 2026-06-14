package dev.sableandroid;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Extrai libsable_rapier.so (compilada para ARM64 Android) do JAR deste mod
 * para um diretório temporário, e retorna o File para que seja carregado
 * com System.load().
 *
 * O .so compilado para ARM64 Android precisa ser obtido compilando o crate
 * sable_rapier (Rust) com target aarch64-linux-android.
 * Veja: scripts/build_rapier_android.sh
 *
 * O .so deve ser colocado em:
 *   src/main/resources/natives/android/arm64-v8a/libsable_rapier.so
 */
public class NativeLibraryExtractor {

    // Pasta dentro do JAR onde ficam os .so para Android
    private static final String NATIVE_RESOURCE_PATH = "/natives/android/arm64-v8a/";

    // Cache: evita extrair múltiplas vezes
    private static File cachedNative = null;

    /**
     * Extrai o nativo do JAR e retorna o File onde foi extraído.
     * Se já foi extraído antes (e o hash bate), reutiliza.
     */
    public static synchronized File extractAndGetNative(String soFileName) throws IOException {
        if (cachedNative != null && cachedNative.exists()) {
            return cachedNative;
        }

        String resourcePath = NATIVE_RESOURCE_PATH + soFileName;
        InputStream soStream = NativeLibraryExtractor.class.getResourceAsStream(resourcePath);

        if (soStream == null) {
            throw new FileNotFoundException(
                "Nativo Android não encontrado no JAR: " + resourcePath +
                "\nCompile o libsable_rapier.so para aarch64-linux-android e coloque em " +
                "src/main/resources" + NATIVE_RESOURCE_PATH
            );
        }

        // Extrai para diretório temporário
        Path extractDir = getTempDir();
        Path destination = extractDir.resolve(soFileName);

        try (soStream) {
            byte[] data = soStream.readAllBytes();

            // Verifica se já existe e tem o mesmo conteúdo (por hash MD5)
            if (Files.exists(destination)) {
                byte[] existing = Files.readAllBytes(destination);
                if (md5(existing).equals(md5(data))) {
                    SableAndroidPatch.LOGGER.info(
                        "[SableAndroidPatch] Reutilizando nativo já extraído: {}", destination);
                    cachedNative = destination.toFile();
                    return cachedNative;
                }
            }

            Files.write(destination, data, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
        }

        // Garante permissão de execução (necessário no Android)
        destination.toFile().setExecutable(true, false);
        destination.toFile().setReadable(true, false);

        SableAndroidPatch.LOGGER.info(
            "[SableAndroidPatch] Nativo extraído para: {}", destination.toAbsolutePath());

        cachedNative = destination.toFile();
        return cachedNative;
    }

    /**
     * Retorna (e cria se necessário) o diretório temporário para os nativos.
     * Usa o diretório de nativos do ZalithLauncher 2 se disponível,
     * senão usa um subdir em /data/local/tmp ou java.io.tmpdir.
     */
    private static Path getTempDir() throws IOException {
        File nativesDir = SableAndroidPatch.getNativeDir();
        Path dir = nativesDir.toPath().resolve("sable_patch");
        Files.createDirectories(dir);
        return dir;
    }

    private static String md5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception e) {
            return "";
        }
    }
}
