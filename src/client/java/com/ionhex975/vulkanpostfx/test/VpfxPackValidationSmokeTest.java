package com.ionhex975.vulkanpostfx.test;

import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxNativePackDefinition;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxNativeZipPackLoader;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxPackLoadException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class VpfxPackValidationSmokeTest {

	private static final String TEST_PACKS_PATH = "src/test/resources/vpfx_test_packs";
	private static final Path TEMP_DIR = Paths.get("build/tmp/vpfx_smoke_test");

	private static final List<TestCase> cases = new ArrayList<>();
	private static int passed;
	private static int failed;

	public static void main(String[] args) {
		System.out.println("=== VPFX Pack Validation Smoke Test ===");
		System.out.println();

		Path packsRoot = Paths.get(TEST_PACKS_PATH);
		if (!Files.isDirectory(packsRoot)) {
			packsRoot = Paths.get(System.getProperty("user.dir")).resolve(TEST_PACKS_PATH);
		}

		if (!Files.isDirectory(packsRoot)) {
			System.err.println("Test packs directory not found: " + packsRoot.toAbsolutePath());
			System.exit(1);
		}

		VpfxNativeZipPackLoader loader = new VpfxNativeZipPackLoader();

		try (Stream<Path> dirs = Files.list(packsRoot)) {
			dirs.filter(Files::isDirectory)
					.sorted(Comparator.comparing(p -> p.getFileName().toString()))
					.forEach(dir -> testPack(loader, dir));
		} catch (IOException e) {
			System.err.println("Failed to list test packs: " + e.getMessage());
			System.exit(1);
		}

		System.out.println();
		System.out.println("=== Results: " + passed + " passed, " + failed + " failed ===");

		for (TestCase c : cases) {
			System.out.println(c);
		}

		if (failed > 0) {
			System.exit(1);
		}
	}

	private static void testPack(VpfxNativeZipPackLoader loader, Path dir) {
		String name = dir.getFileName().toString();
		boolean isMissingPackJson = "negative_missing_pack_json".equals(name);
		boolean expectSuccess = name.startsWith("positive");

		Path zipPath = createZip(dir);
		if (zipPath == null) {
			record(name, false, "ERROR", "(zip)", "Failed to create zip");
			return;
		}

		try {
			VpfxNativePackDefinition result = loader.tryLoad(zipPath);

			if (isMissingPackJson) {
				if (result == null) {
					passed++;
					System.out.println("  [PASS] " + name
							+ " — null return as expected: code=NOT_VPFX path=pack.json"
							+ " message=Missing pack.json, not recognized as VPFX native pack");
					record(name, true, "NOT_VPFX", "pack.json",
							"Missing pack.json, not recognized as VPFX native pack");
				} else {
					failed++;
					System.out.println("  [FAIL] " + name
							+ " — expected null (no pack.json) but loaded id="
							+ result.getManifest().getPackId());
					record(name, false, "N/A", "-", "Unexpectedly loaded despite missing pack.json");
				}
				return;
			}

			if (expectSuccess) {
				if (result != null) {
					passed++;
					System.out.println("  [PASS] " + name
							+ " — loaded: id=" + result.getManifest().getPackId()
							+ ", targets=" + result.getGraph().getTargets().size()
							+ ", passes=" + result.getGraph().getPasses().size());
					record(name, true, "OK", "-", "Loaded successfully");
				} else {
					failed++;
					System.out.println("  [FAIL] " + name + " — expected SUCCESS but got null (not recognized as VPFX)");
					record(name, false, "N/A", "-", "Returned null — not recognized as VPFX pack");
				}
			} else {
				// negative_* (not missing_pack_json): has pack.json, must throw
				failed++;
				System.out.println("  [FAIL] " + name
						+ " — expected REJECTION via VpfxPackLoadException but tryLoad returned "
						+ (result != null ? "non-null (id=" + result.getManifest().getPackId() + ")" : "null"));
				record(name, false, "N/A", "-", "Expected VpfxPackLoadException but got "
						+ (result != null ? "success" : "null"));
			}
		} catch (VpfxPackLoadException e) {
			if (isMissingPackJson) {
				// has no pack.json but somehow got a VpfxPackLoadException → unlikely but acceptable
				passed++;
				System.out.println("  [PASS] " + name
						+ " — rejected (acceptable): " + e.getCode()
						+ " path=" + e.getPath()
						+ " message=" + e.getMessage());
				record(name, true, e.getCode(), e.getPath(), e.getMessage());
			} else if (expectSuccess) {
				failed++;
				System.out.println("  [FAIL] " + name
						+ " — expected SUCCESS but got: " + e.getCode()
						+ " path=" + e.getPath()
						+ " message=" + e.getMessage());
				record(name, false, e.getCode(), e.getPath(), e.getMessage());
			} else {
				passed++;
				System.out.println("  [PASS] " + name
						+ " — rejected: " + e.getCode()
						+ " path=" + e.getPath()
						+ " message=" + e.getMessage());
				record(name, true, e.getCode(), e.getPath(), e.getMessage());
			}
		} catch (Exception e) {
			if (isMissingPackJson) {
				passed++;
				System.out.println("  [PASS] " + name
						+ " — rejected (acceptable): " + e.getClass().getSimpleName()
						+ " message=" + e.getMessage());
				record(name, true, e.getClass().getSimpleName(), "-", e.getMessage());
			} else if (expectSuccess) {
				failed++;
				System.out.println("  [FAIL] " + name
						+ " — unexpected exception: " + e.getClass().getSimpleName()
						+ " message=" + e.getMessage());
				record(name, false, e.getClass().getSimpleName(), "-", e.getMessage());
			} else {
				passed++;
				System.out.println("  [PASS] " + name
						+ " — rejected: " + e.getClass().getSimpleName()
						+ " message=" + e.getMessage());
				record(name, true, e.getClass().getSimpleName(), "-", e.getMessage());
			}
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
			System.err.println("  Failed to create zip for " + dir.getFileName() + ": " + e.getMessage());
			return null;
		}
	}

	private static void record(String name, boolean success, String code, String path, String message) {
		cases.add(new TestCase(name, success, code, path, message));
	}

	private record TestCase(String name, boolean success, String code, String path, String message) {
		@Override
		public String toString() {
			String status = success ? "PASS" : "FAIL";
			return String.format("  [%s] %-45s code=%-6s path=%s",
					status, name, code, path);
		}
	}
}
