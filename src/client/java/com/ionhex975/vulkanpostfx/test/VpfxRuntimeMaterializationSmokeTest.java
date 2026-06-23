package com.ionhex975.vulkanpostfx.test;

import com.ionhex975.vulkanpostfx.client.pack.ShaderPackContainer;
import com.ionhex975.vulkanpostfx.client.pack.ShaderPackManifest;
import com.ionhex975.vulkanpostfx.client.pack.ShaderPackResourceIndex;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxGraphDefinition;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxNativePackDefinition;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxNativeZipPackLoader;
import com.ionhex975.vulkanpostfx.client.postfx.PostFxExternalTargetIds;
import com.ionhex975.vulkanpostfx.client.runtime.vpfx.VpfxPostChainBackend;
import com.ionhex975.vulkanpostfx.client.runtime.vpfx.VpfxRuntimeBackend;
import com.ionhex975.vulkanpostfx.client.runtime.zip.RuntimeZipPackMaterializationResult;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class VpfxRuntimeMaterializationSmokeTest {

	private static final String TEST_PACKS_PATH = "src/test/resources/vpfx_test_packs";
	private static final Path TEMP_DIR = Paths.get("build/tmp/vpfx_materialization_smoke");

	private static boolean allPassed = true;
	private static VpfxRuntimeBackend backend;

	public static void main(String[] args) {
		System.out.println("=== VPFX Runtime Materialization Smoke Test ===");
		System.out.println();

		Path packsRoot = Paths.get(TEST_PACKS_PATH);
		if (!Files.isDirectory(packsRoot)) {
			packsRoot = Paths.get(System.getProperty("user.dir")).resolve(TEST_PACKS_PATH);
		}

		if (!Files.isDirectory(packsRoot)) {
			System.err.println("Test packs directory not found: " + packsRoot.toAbsolutePath());
			System.exit(1);
		}

		backend = new VpfxPostChainBackend();
		System.out.println("VPFX runtime backend:");
		System.out.println("  id: " + backend.id());
		System.out.println("  display: " + backend.displayName());
		System.out.println("  capabilities: " + backend.capabilities());
		System.out.println();

		boolean backendValid = backend.id().equals("minecraft_postchain")
				&& !backend.capabilities().nativeRuntime()
				&& !backend.capabilities().supportsCompute();

		if (backendValid) {
			System.out.println("  [PASS] PostChain backend identity and capabilities validated (minecraft_postchain, native=false, compute=false)");
		} else {
			System.out.println("  [FAIL] PostChain backend identity or capabilities mismatch");
			allPassed = false;
		}

		if (PostFxExternalTargetIds.SCENE_COLOR != null
				&& PostFxExternalTargetIds.allowedTargets().contains(PostFxExternalTargetIds.SCENE_COLOR)) {
			System.out.println("  [PASS] minecraft:scene_color is registered as an allowed PostChain external target");
		} else {
			System.out.println("  [FAIL] minecraft:scene_color is NOT in PostFxExternalTargetIds");
			allPassed = false;
		}

		System.out.println();

		VpfxNativeZipPackLoader loader = new VpfxNativeZipPackLoader();

		for (String packName : List.of("positive_minimal", "positive_showcase")) {
			Path packDir = packsRoot.resolve(packName);
			if (!Files.isDirectory(packDir)) {
				System.out.println("  SKIP: " + packName + " not found");
				continue;
			}
			testPack(loader, packDir, packName);
		}

		System.out.println();
		if (allPassed) {
			System.out.println("=== RESULT: ALL PASSED ===");
		} else {
			System.out.println("=== RESULT: SOME CHECKS FAILED ===");
			System.exit(1);
		}
	}

	private static void testPack(VpfxNativeZipPackLoader loader, Path packDir, String packName) {
		System.out.println("--- Testing: " + packName + " ---");

		Path zipPath = createZip(packDir);
		if (zipPath == null) {
			System.out.println("  [FAIL] Failed to create zip");
			allPassed = false;
			System.out.println();
			return;
		}

		System.out.println("  Source pack zipped: " + zipPath);

		VpfxNativePackDefinition vpfxDef;
		try {
			vpfxDef = loader.tryLoad(zipPath);
		} catch (Exception e) {
			System.out.println("  [FAIL] tryLoad threw: " + e.getMessage());
			allPassed = false;
			System.out.println();
			return;
		}

		if (vpfxDef == null) {
			System.out.println("  [FAIL] tryLoad returned null");
			allPassed = false;
			System.out.println();
			return;
		}

		System.out.println("  OK: VPFX native pack loaded: id=" + vpfxDef.getManifest().getPackId()
				+ ", passes=" + vpfxDef.getGraph().getPasses().size());

		boolean packUsesSceneColor = packUsesSceneColor(vpfxDef.getGraph());
		if (packUsesSceneColor) {
			System.out.println("  OK: Graph input target includes minecraft:scene_color");
		}

		ShaderPackContainer container = buildContainer(vpfxDef, zipPath);
		System.out.println("  OK: ShaderPackContainer built: id=" + container.manifest().id()
				+ ", resources=" + container.resourceIndex().size());

		Path gameDir;
		try {
			gameDir = Files.createTempDirectory(TEMP_DIR, "game_");
		} catch (IOException e) {
			System.out.println("  [FAIL] failed to create temp game dir: " + e.getMessage());
			allPassed = false;
			System.out.println();
			return;
		}

		try {
			RuntimeZipPackMaterializationResult result = backend.materialize(container, gameDir);

			Path runtimeRoot = result.runtimeRoot();
			String runtimeNs = result.runtimeNamespace();
			Path assetsDir = runtimeRoot.resolve("assets").resolve(runtimeNs);

			System.out.println("  Materialized to: " + runtimeRoot);
			System.out.println("  runtimeNamespace: " + runtimeNs);
			System.out.println("  Generated resources:");

			List<Path> allFiles;
			try (Stream<Path> walk = Files.walk(runtimeRoot)) {
				allFiles = walk.filter(Files::isRegularFile)
						.sorted(Comparator.comparing(p -> runtimeRoot.relativize(p).toString()))
						.collect(Collectors.toList());
			}

			for (Path file : allFiles) {
				System.out.println("    " + runtimeRoot.relativize(file));
			}

			allPassed &= checkExists(assetsDir, "post_effect/main.json",
					"Post effect JSON (runtime namespace rewritten)");

			allPassed &= checkExists(assetsDir, "shaders/composite/final.vsh",
					"Vertex shader (non-post/ path: composite/final)");
			allPassed &= checkContains(assetsDir, "shaders/composite/final.vsh",
					"#version", "version directive in preprocessed vertex shader");

			allPassed &= checkExists(assetsDir, "shaders/composite/final.fsh",
					"Fragment shader (non-post/ path: composite/final)");
			allPassed &= checkContains(assetsDir, "shaders/composite/final.fsh",
					"#version", "version directive in preprocessed fragment shader");

			Path postEffectJson = assetsDir.resolve("post_effect/main.json");
			if (Files.exists(postEffectJson)) {
				String jsonContent = Files.readString(postEffectJson);
				if (jsonContent.contains(runtimeNs)) {
					System.out.println("  [PASS] post_effect/main.json uses runtime namespace");
				} else {
					System.out.println("  [FAIL] post_effect/main.json does not use runtime namespace");
					allPassed = false;
				}
				if (jsonContent.contains("vertex_shader") && jsonContent.contains("fragment_shader")) {
					System.out.println("  [PASS] post_effect/main.json contains vertex_shader and fragment_shader refs");
				} else {
					System.out.println("  [FAIL] post_effect/main.json missing shader references");
					allPassed = false;
				}
			}

			Path vpfxDir = assetsDir.resolve("vpfx");
			Path texturesJson = vpfxDir.resolve("textures.json");
			if (Files.exists(texturesJson)) {
				System.out.println("  [PASS] vpfx/textures.json generated (texture manifest)");
			}

			if (packUsesSceneColor && Files.exists(postEffectJson)) {
				String jsonContent = Files.readString(postEffectJson);
				if (jsonContent.contains("minecraft:scene_color")) {
					System.out.println("  [PASS] materialized JSON preserves minecraft:scene_color target");
				} else {
					System.out.println("  [FAIL] materialized JSON missing minecraft:scene_color target");
					allPassed = false;
				}
			}

			if (Files.exists(postEffectJson)) {
				String jsonContent = Files.readString(postEffectJson);
				int samplerIdx = jsonContent.indexOf("\"sampler_name\"");
				if (samplerIdx >= 0) {
					int valStart = jsonContent.indexOf('"', jsonContent.indexOf(':', samplerIdx) + 1) + 1;
					int valEnd = jsonContent.indexOf('"', valStart);
					String samplerName = jsonContent.substring(valStart, valEnd);

					Path fshPath = assetsDir.resolve("shaders/composite/final.fsh");
					if (Files.exists(fshPath)) {
						String fshContent = Files.readString(fshPath);
						String expectedUniform = "uniform sampler2D " + samplerName + "Sampler";
						if (fshContent.contains(expectedUniform)) {
							System.out.println("  [PASS] sampler_name '" + samplerName + "' matches shader uniform '"
									+ samplerName + "Sampler' (PostChain convention)");
						} else {
							System.out.println("  [FAIL] sampler_name '" + samplerName
									+ "' does not match any uniform in final.fsh (expected '" + expectedUniform + "')");
							allPassed = false;
						}
					}
				}
			}

		} catch (Exception e) {
			System.out.println("  [FAIL] materialization threw: " + e.getMessage());
			allPassed = false;
		}

		System.out.println();
	}

	private static boolean packUsesSceneColor(VpfxGraphDefinition graph) {
		for (var pass : graph.getPasses()) {
			for (var input : pass.getInputs()) {
				if ("minecraft:scene_color".equals(input.getTarget())) {
					return true;
				}
			}
		}
		return false;
	}

	private static ShaderPackContainer buildContainer(VpfxNativePackDefinition vpfxDef, Path zipPath) {
		var vpfxManifest = vpfxDef.getManifest();

		ShaderPackManifest packManifest = new ShaderPackManifest(
				vpfxManifest.getPackId(),
				vpfxManifest.getName(),
				vpfxManifest.getFormatVersion(),
				"",
				vpfxManifest.getEntryPostEffect()
		);

		Set<String> resourcePaths = new LinkedHashSet<>();
		try (ZipFile zf = new ZipFile(zipPath.toFile())) {
			var entries = zf.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (!entry.isDirectory()) {
					resourcePaths.add(entry.getName().replace('\\', '/'));
				}
			}
		} catch (IOException ignored) {
		}

		ShaderPackResourceIndex resourceIndex = new ShaderPackResourceIndex(resourcePaths);

		return new ShaderPackContainer(
				packManifest,
				"zip",
				zipPath,
				resourceIndex,
				vpfxDef
		);
	}

	private static boolean checkExists(Path base, String relative, String description) {
		Path path = base.resolve(relative);
		if (Files.exists(path)) {
			System.out.println("  [PASS] " + description + " → " + relative);
			return true;
		}
		System.out.println("  [FAIL] " + description + " → MISSING: " + relative);
		return false;
	}

	private static boolean checkContains(Path base, String relative, String expectedContent, String description) {
		Path path = base.resolve(relative);
		if (!Files.exists(path)) {
			return false;
		}
		try {
			String content = Files.readString(path);
			if (content.contains(expectedContent)) {
				System.out.println("  [PASS] " + description + " → found '" + expectedContent + "' in " + relative);
				return true;
			}
			System.out.println("  [FAIL] " + description + " → '" + expectedContent + "' NOT found in " + relative);
			return false;
		} catch (IOException e) {
			System.out.println("  [FAIL] " + description + " → read error: " + e.getMessage());
			return false;
		}
	}

	private static Path createZip(Path dir) {
		try {
			Files.createDirectories(TEMP_DIR);
			Path zipPath = TEMP_DIR.resolve(dir.getFileName() + ".zip");
			Files.deleteIfExists(zipPath);

			try (OutputStream fos = Files.newOutputStream(zipPath);
				 ZipOutputStream zos = new ZipOutputStream(fos)) {
				try (Stream<Path> walk = Files.walk(dir)) {
					walk.filter(Files::isRegularFile).forEach(file -> {
						try {
							String entryName = dir.relativize(file).toString().replace('\\', '/');
							zos.putNextEntry(new ZipEntry(entryName));
							Files.copy(file, zos);
							zos.closeEntry();
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					});
				} catch (RuntimeException e) {
					if (e.getCause() instanceof IOException io) throw io;
					throw e;
				}
			}

			return zipPath;
		} catch (IOException e) {
			System.err.println("  Failed to create zip: " + e.getMessage());
			return null;
		}
	}
}
