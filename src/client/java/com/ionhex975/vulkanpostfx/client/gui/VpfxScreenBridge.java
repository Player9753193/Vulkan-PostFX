package com.ionhex975.vulkanpostfx.client.gui;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 26.2 snapshot GUI bridge.
 *
 * 现在 26.2 快照映射比较不稳定：
 * - 有些环境叫 Minecraft#setScreen
 * - 有些环境叫 setScreenAndShow / setScreenAndRender
 * - 有些环境只暴露 intermediary / obfuscated 名
 *
 * 所以这里不用编译期直接调用 setScreen/screen 字段，
 * 避免因为映射名变动导致 compileClientJava 直接失败。
 */
public final class VpfxScreenBridge {
    private static final String[] SCREEN_SETTER_NAMES = {
            "setScreen",
            "setScreenAndShow",
            "setScreenAndRender",
            "forceSetScreen",
            "method_1507",
            "method_29970",
            "m_91152_",
            "m_91346_",
            "a",
            "c"
    };

    private VpfxScreenBridge() {
    }

    public static void open(Screen screen) {
        setScreen(screen);
    }

    public static void close() {
        setScreen(null);
    }

    public static void setScreen(Screen screen) {
        Minecraft client = Minecraft.getInstance();
        setScreen(client, screen);
    }

    public static void setScreen(Minecraft client, Screen screen) {
        if (client == null) {
            return;
        }

        client.execute(() -> setScreenNow(client, screen));
    }

    public static Screen currentScreen() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return null;
        }

        return currentScreen(client);
    }

    public static boolean isCurrentScreen(Screen screen) {
        return screen != null && currentScreen() == screen;
    }

    private static void setScreenNow(Minecraft client, Screen screen) {
        Method setter = findScreenSetter();

        if (setter != null) {
            try {
                setter.invoke(client, screen);
                return;
            } catch (ReflectiveOperationException e) {
                VulkanPostFX.LOGGER.warn(
                        "[{}] Failed to invoke reflected Minecraft screen setter '{}'; falling back to direct field assignment",
                        VulkanPostFX.MOD_ID,
                        setter.getName(),
                        e
                );
            }
        }

        assignScreenField(client, screen);
    }

    private static Method findScreenSetter() {
        Class<Minecraft> minecraftClass = Minecraft.class;

        for (String candidateName : SCREEN_SETTER_NAMES) {
            Method method = findScreenSetterByName(minecraftClass, candidateName);
            if (method != null) {
                return method;
            }
        }

        return findAnySingleScreenSetter(minecraftClass);
    }

    private static Method findScreenSetterByName(Class<?> owner, String name) {
        for (Method method : owner.getDeclaredMethods()) {
            if (isScreenSetter(method) && method.getName().equals(name)) {
                method.setAccessible(true);
                return method;
            }
        }

        for (Method method : owner.getMethods()) {
            if (isScreenSetter(method) && method.getName().equals(name)) {
                method.setAccessible(true);
                return method;
            }
        }

        return null;
    }

    private static Method findAnySingleScreenSetter(Class<?> owner) {
        Method fallback = null;

        for (Method method : owner.getDeclaredMethods()) {
            if (!isScreenSetter(method)) {
                continue;
            }

            // 避免误选 disconnect/clearClientLevel 一类方法。
            // 只在完全找不到已知名字时兜底，而且优先选择名字最短的第一个候选。
            if (fallback == null || method.getName().length() < fallback.getName().length()) {
                fallback = method;
            }
        }

        if (fallback != null) {
            fallback.setAccessible(true);
            VulkanPostFX.LOGGER.warn(
                    "[{}] Using fallback reflected Minecraft screen setter '{}'; mapping name is not recognized",
                    VulkanPostFX.MOD_ID,
                    fallback.getName()
            );
        }

        return fallback;
    }

    private static boolean isScreenSetter(Method method) {
        return method.getReturnType() == Void.TYPE
                && method.getParameterCount() == 1
                && method.getParameterTypes()[0] == Screen.class;
    }

    private static Screen currentScreen(Minecraft client) {
        for (Field field : Minecraft.class.getDeclaredFields()) {
            if (field.getType() != Screen.class) {
                continue;
            }

            try {
                field.setAccessible(true);
                return (Screen) field.get(client);
            } catch (ReflectiveOperationException e) {
                VulkanPostFX.LOGGER.warn(
                        "[{}] Failed to read reflected Minecraft current screen field '{}'",
                        VulkanPostFX.MOD_ID,
                        field.getName(),
                        e
                );
                return null;
            }
        }

        return null;
    }

    private static void assignScreenField(Minecraft client, Screen screen) {
        for (Field field : Minecraft.class.getDeclaredFields()) {
            if (field.getType() != Screen.class) {
                continue;
            }

            try {
                field.setAccessible(true);
                field.set(client, screen);
                VulkanPostFX.LOGGER.warn(
                        "[{}] Directly assigned Minecraft screen field '{}' because no callable screen setter was found",
                        VulkanPostFX.MOD_ID,
                        field.getName()
                );
                return;
            } catch (ReflectiveOperationException e) {
                VulkanPostFX.LOGGER.error(
                        "[{}] Failed to directly assign Minecraft screen field '{}'",
                        VulkanPostFX.MOD_ID,
                        field.getName(),
                        e
                );
                return;
            }
        }

        VulkanPostFX.LOGGER.error(
                "[{}] Could not open VPFX screen: no Minecraft Screen setter or Screen field was found in current mappings",
                VulkanPostFX.MOD_ID
        );
    }
}