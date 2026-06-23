package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx;

public final class VpfxResourceRef {

	public static final String SCENE_COLOR = "minecraft:scene_color";
	public static final String SCENE_DEPTH = "minecraft:scene_depth";
	public static final String SHADOW_DEPTH = "minecraft:shadow_depth";
	public static final String MAIN = "minecraft:main";

	public enum Kind {
		BUILTIN_SCENE_COLOR,
		BUILTIN_SCENE_DEPTH,
		BUILTIN_SHADOW_DEPTH,
		BUILTIN_MAIN,
		PACK_TARGET,
		HISTORY
	}

	private final Kind kind;
	private final String reference;

	private VpfxResourceRef(Kind kind, String reference) {
		this.kind = kind;
		this.reference = reference;
	}

	public static VpfxResourceRef sceneColor() {
		return new VpfxResourceRef(Kind.BUILTIN_SCENE_COLOR, SCENE_COLOR);
	}

	public static VpfxResourceRef sceneDepth() {
		return new VpfxResourceRef(Kind.BUILTIN_SCENE_DEPTH, SCENE_DEPTH);
	}

	public static VpfxResourceRef shadowDepth() {
		return new VpfxResourceRef(Kind.BUILTIN_SHADOW_DEPTH, SHADOW_DEPTH);
	}

	public static VpfxResourceRef main() {
		return new VpfxResourceRef(Kind.BUILTIN_MAIN, MAIN);
	}

	public static VpfxResourceRef packTarget(String reference) {
		return new VpfxResourceRef(Kind.PACK_TARGET, reference);
	}

	public static VpfxResourceRef history(String reference) {
		return new VpfxResourceRef(Kind.HISTORY, reference);
	}

	public static VpfxResourceRef fromString(String target) {
		return switch (target) {
			case SCENE_COLOR -> sceneColor();
			case SCENE_DEPTH -> sceneDepth();
			case SHADOW_DEPTH -> shadowDepth();
			case MAIN -> main();
			default -> packTarget(target);
		};
	}

	public Kind kind() {
		return kind;
	}

	public String reference() {
		return reference;
	}

	public boolean isBuiltin() {
		return kind == Kind.BUILTIN_SCENE_COLOR
				|| kind == Kind.BUILTIN_SCENE_DEPTH
				|| kind == Kind.BUILTIN_SHADOW_DEPTH
				|| kind == Kind.BUILTIN_MAIN;
	}

	@Override
	public String toString() {
		return "VpfxResourceRef{" + "kind=" + kind + ", reference='" + reference + '\'' + '}';
	}
}
