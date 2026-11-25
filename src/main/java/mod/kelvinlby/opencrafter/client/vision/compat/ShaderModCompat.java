package mod.kelvinlby.opencrafter.client.vision.compat;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Detects shader mods without a compile-time dependency. Iris is queried through its public
 * {@code IrisApi} by reflection, so the mod works with any Iris version or none at all.
 */
public final class ShaderModCompat {
	private static final MethodHandle IRIS_SHADER_PACK_IN_USE = findIrisShaderPackInUse();

	private ShaderModCompat() {
	}

	public static boolean isIrisLoaded() {
		return FabricLoader.getInstance().isModLoaded("iris");
	}

	public static boolean isShaderPackInUse() {
		if (IRIS_SHADER_PACK_IN_USE == null) {
			return false;
		}
		try {
			return (boolean) IRIS_SHADER_PACK_IN_USE.invoke();
		} catch (Throwable e) {
			return false;
		}
	}

	private static MethodHandle findIrisShaderPackInUse() {
		if (!isIrisLoaded()) {
			return null;
		}
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			Object instance = lookup.findStatic(api, "getInstance", MethodType.methodType(api)).invoke();
			return lookup.findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class)).bindTo(instance);
		} catch (Throwable e) {
			return null;
		}
	}
}
