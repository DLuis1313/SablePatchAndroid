# Sable Android Patch 2

Patch para rodar **Sable 2.0.x** + **Create Aeronautics 1.3.x** no **ZalithLauncher 2** com o renderer **MobileGlues**, no NeoForge 1.21.1.

## O problema

O Sable usa o motor de física **Rapier** compilado em Rust como biblioteca nativa (`.so`/`.dll`). No Android, o ZalithLauncher usa uma JVM modificada que **não possui** o `libsable_rapier.so` e nem a infraestrutura para extraí-lo do JAR no caminho esperado, causando:

```
java.lang.UnsatisfiedLinkError: no sable_rapier in java.library.path
```

Além disso, os shaders do Sable 2.0.x usam GLSL 4.3+ com extensões ARB que o **MobileGlues** (OpenGL ES 3.x) não suporta.

O patch anterior (`sa1672ndo/sable-android-patch`) foi feito para Sable ≤1.1.x e **não funciona** com o Sable 2.0.x, pois a classe de carregamento do nativo foi refatorada.

## Como funciona este patch

1. **Detecção de Android** — verifica `java.vendor`, `java.vm.name` e `user.home` para detectar se está no ZalithLauncher 2.
2. **Mixin no NativeLoader** — intercepta o método que carrega o `sable_rapier` antes que ele chame `System.loadLibrary()` (que falharia).
3. **Extração do .so** — o `libsable_rapier.so` (ARM64) está embutido no JAR do patch em `natives/android/arm64-v8a/`. Ele é extraído para um diretório temporário e carregado com `System.load()`.
4. **Patch de shaders** — substitui construções GLSL incompatíveis com GLES (double precision, extensões ARB, versão GLSL) antes da compilação dos shaders.

## Como compilar (sem PC, via GitHub)

1. **Fork este repositório** no GitHub (botão "Fork" no canto superior direito).
2. Vá em **Actions** → ative os workflows se solicitado.
3. Clique em **"Build Sable Android Patch 2"** → **"Run workflow"**.
4. Aguarde a build terminar (~10-15 minutos).
5. Baixe o `.jar` na seção **Artifacts** da run.

O GitHub Actions vai:
- Compilar o `libsable_rapier.so` para ARM64 Android usando Rust + Android NDK
- Empacotar o `.so` dentro do JAR do mod
- Gerar o arquivo final pronto para usar

## ⚠️ Ajuste necessário antes de compilar

O nome exato das classes do Sable 2.0.x que carregam o nativo **pode ser diferente** do que está nos Mixins. Você precisa verificar:

### Como descobrir o nome correto das classes

1. Baixe o JAR do Sable 2.0.x
2. Execute no terminal (ou peça ao GitHub Actions para fazer isso):
   ```bash
   # Lista todas as classes que contém "rapier" ou "native" no nome
   jar tf sable-2.0.x.jar | grep -iE "rapier|native|loader"
   
   # Descompila a classe para ver o método de carregamento
   javap -c sable-2.0.x.jar!/gay/sable/sable/physics/NativeLoader.class
   ```

3. Procure por chamadas a `System.loadLibrary` ou `System.load`

### Ajuste os Mixins

Edite os seguintes arquivos com os nomes reais das classes:

**`SableRapierLoaderMixin.java`**:
```java
// Mude o target para a classe real:
@Mixin(targets = "gay.sable.sable.physics.NativeLoader", remap = false)
// E o método:
@Inject(method = "load()V", ...)
```

**`SableNativeExtractorMixin.java`**:
```java
@Mixin(targets = "gay.sable.sable.physics.NativeExtractor", remap = false)
@Inject(method = "extract(Ljava/lang/String;)Ljava/nio/file/Path;", ...)
```

**`SableGluesRendererMixin.java`**:
```java
@Mixin(targets = "gay.sable.sable.render.SableRenderer", remap = false)
```

Se o Mixin não encontrar a classe/método, o mod vai dar erro na inicialização. Nesse caso, verifique os logs do NeoForge para ver qual classe está sendo alvo.

## Estrutura do projeto

```
sable-android-patch-2x/
├── .github/workflows/build.yml     ← GitHub Actions (compile sem PC!)
├── src/main/
│   ├── java/dev/sableandroid/
│   │   ├── SableAndroidPatch.java          ← Classe principal do mod
│   │   ├── NativeLibraryExtractor.java     ← Extrai o .so do JAR
│   │   ├── mixin/
│   │   │   ├── SableRapierLoaderMixin.java ← ⚠️ AJUSTE O TARGET AQUI
│   │   │   ├── SableNativeExtractorMixin.java
│   │   │   └── SableGluesRendererMixin.java
│   │   └── compat/
│   │       └── GluesCompatHelper.java      ← Patches de shader GLES
│   └── resources/
│       ├── META-INF/
│       │   ├── neoforge.mods.toml
│       │   └── mixin.sableandroidpatch2.json
│       └── natives/android/arm64-v8a/
│           └── libsable_rapier.so          ← Gerado pelo build.yml
└── scripts/
    └── build_rapier_android.sh             ← Build manual (requer PC)
```

## Compatibilidade

| Mod | Versão | Status |
|-----|--------|--------|
| Sable | 2.0.x | ✅ Alvo deste patch |
| Create Aeronautics | 1.3.x | ✅ Compatível (usa mesmo nativo) |
| NeoForge | 21.1.x | ✅ |
| ZalithLauncher | 2.x | ✅ |
| MobileGlues | qualquer | ✅ via patches de shader |

## Créditos

Baseado no trabalho original de `sa1672ndo/sable-android-patch` (para Sable ≤1.1.x).
