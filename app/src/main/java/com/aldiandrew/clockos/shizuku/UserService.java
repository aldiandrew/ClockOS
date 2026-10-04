package com.aldiandrew.clockos.shizuku;

import android.graphics.Color;
import android.os.Process;
import android.util.TypedValue;

import androidx.annotation.Keep;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

@Keep
public class UserService extends IUserService.Stub {
    private static final String OVERLAY_COMMAND_ENABLE =
            "clockos:overlay:enable";
    private static final String OVERLAY_COMMAND_DISABLE =
            "clockos:overlay:disable";

    private static final String TARGET_PACKAGE =
            "com.android.systemui";
    private static final String OVERLAY_NAME =
            "ClockOSSystemUiClockMask";
    private static final String CLOCK_COLOR_RESOURCE =
            "com.android.systemui:color/status_bar_clock_color";

    @Keep
    public UserService() {}

    @Override
    public String exec(String command) {
        if (OVERLAY_COMMAND_ENABLE.equals(command)) {
            return enableClockOverlay();
        }

        if (OVERLAY_COMMAND_DISABLE.equals(command)) {
            return disableClockOverlay();
        }

        Process process = null;
        try {
            process = Runtime.getRuntime().exec(
                new String[]{"sh", "-c", command}
            );

            String stdout = read(process.getInputStream());
            String stderr = read(process.getErrorStream());
            int code = process.waitFor();

            StringBuilder result = new StringBuilder();
            result.append("exit=").append(code);

            if (!stdout.isEmpty()) {
                result.append("\n").append(stdout);
            }

            if (!stderr.isEmpty()) {
                result.append("\nstderr=").append(stderr);
            }

            return result.toString();
        } catch (Throwable t) {
            return "error=" + t;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private String enableClockOverlay() {
        try {
            Object overlayManager = getOverlayManager();
            Object overlay = createTransparentClockOverlay();

            Class<?> transactionClass =
                Class.forName(
                    "android.content.om.OverlayManagerTransaction"
                );

            Class<?> builderClass =
                Class.forName(
                    "android.content.om.OverlayManagerTransaction$Builder"
                );

            Object builder =
                builderClass
                    .getConstructor()
                    .newInstance();

            Class<?> fabricatedClass =
                Class.forName(
                    "android.content.om.FabricatedOverlay"
                );

            builderClass
                .getMethod(
                    "registerFabricatedOverlay",
                    fabricatedClass
                )
                .invoke(
                    builder,
                    overlay
                );

            Object identifier =
                fabricatedClass
                    .getMethod("getIdentifier")
                    .invoke(overlay);

            Class<?> identifierClass =
                Class.forName(
                    "android.content.om.OverlayIdentifier"
                );

            builderClass
                .getMethod(
                    "setEnabled",
                    identifierClass,
                    boolean.class,
                    int.class
                )
                .invoke(
                    builder,
                    identifier,
                    true,
                    0
                );

            Object transaction =
                builderClass
                    .getMethod("build")
                    .invoke(builder);

            Class<?> managerClass =
                Class.forName(
                    "android.content.om.IOverlayManager"
                );

            Method commit =
                managerClass.getMethod(
                    "commit",
                    transactionClass
                );

            commit.invoke(
                overlayManager,
                transaction
            );

            return "exit=0\noverlay=enabled";
        } catch (Throwable t) {
            Throwable cause = rootCause(t);
            return "exit=1\noverlay_error=" + cause;
        }
    }

    private String disableClockOverlay() {
        Throwable firstError = null;

        String[] owners = {
            "com.android.shell",
            "com.aldiandrew.clockos"
        };

        for (String owner : owners) {
            try {
                unregisterOverlay(owner);
                return "exit=0\noverlay=disabled";
            } catch (Throwable t) {
                if (firstError == null) {
                    firstError = rootCause(t);
                }
            }
        }

        return "exit=1\noverlay_disable_error=" +
            (firstError == null
                ? "unknown"
                : firstError);
    }

    private void unregisterOverlay(
        String ownerPackage
    ) throws Exception {
        Object overlayManager =
            getOverlayManager();

        Class<?> transactionClass =
            Class.forName(
                "android.content.om.OverlayManagerTransaction"
            );

        Class<?> builderClass =
            Class.forName(
                "android.content.om.OverlayManagerTransaction$Builder"
            );

        Class<?> identifierClass =
            Class.forName(
                "android.content.om.OverlayIdentifier"
            );

        Constructor<?> identifierConstructor =
            identifierClass.getConstructor(
                String.class,
                String.class
            );

        Object identifier =
            identifierConstructor.newInstance(
                ownerPackage,
                OVERLAY_NAME
            );

        Object builder =
            builderClass
                .getConstructor()
                .newInstance();

        builderClass
            .getMethod(
                "unregisterFabricatedOverlay",
                identifierClass
            )
            .invoke(
                builder,
                identifier
            );

        Object transaction =
            builderClass
                .getMethod("build")
                .invoke(builder);

        Class<?> managerClass =
            Class.forName(
                "android.content.om.IOverlayManager"
            );

        managerClass
            .getMethod(
                "commit",
                transactionClass
            )
            .invoke(
                overlayManager,
                transaction
            );
    }

    private Object createTransparentClockOverlay()
        throws Exception {
        Class<?> builderClass =
            Class.forName(
                "android.content.om.FabricatedOverlay$Builder"
            );

        Constructor<?> constructor =
            builderClass.getConstructor(
                String.class,
                String.class,
                String.class
            );

        String owner =
            "com.android.shell";

        try {
            int uid = Process.myUid();
            if (uid != 2000) {
                owner =
                    "com.aldiandrew.clockos";
            }
        } catch (Throwable ignored) {
        }

        Object builder =
            constructor.newInstance(
                owner,
                OVERLAY_NAME,
                TARGET_PACKAGE
            );

        builderClass
            .getMethod(
                "setResourceValue",
                String.class,
                int.class,
                int.class
            )
            .invoke(
                builder,
                CLOCK_COLOR_RESOURCE,
                TypedValue.TYPE_INT_COLOR_ARGB8,
                Color.TRANSPARENT
            );

        return builderClass
            .getMethod("build")
            .invoke(builder);
    }

    private Object getOverlayManager()
        throws Exception {
        Class<?> serviceManager =
            Class.forName(
                "android.os.ServiceManager"
            );

        android.os.IBinder binder =
            (android.os.IBinder)
                serviceManager
                    .getMethod(
                        "getService",
                        String.class
                    )
                    .invoke(
                        null,
                        "overlay"
                    );

        if (binder == null) {
            throw new IllegalStateException(
                "overlay service unavailable"
            );
        }

        Class<?> stub =
            Class.forName(
                "android.content.om.IOverlayManager$Stub"
            );

        return stub
            .getMethod(
                "asInterface",
                android.os.IBinder.class
            )
            .invoke(
                null,
                binder
            );
    }

    private static Throwable rootCause(
        Throwable t
    ) {
        Throwable current = t;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String read(
        java.io.InputStream input
    ) throws Exception {
        BufferedReader reader =
            new BufferedReader(
                new InputStreamReader(input)
            );

        StringBuilder out =
            new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {
            if (out.length() > 0) {
                out.append('\n');
            }

            out.append(line);
        }

        return out.toString();
    }

    public void destroy() {
        System.exit(0);
    }
}
