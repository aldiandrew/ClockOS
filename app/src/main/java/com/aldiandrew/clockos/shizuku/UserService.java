package com.aldiandrew.clockos.shizuku;

import android.os.IBinder;

import androidx.annotation.Keep;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

@Keep
public class UserService extends IUserService.Stub {
    @Keep
    public UserService() {}

    @Override
    public String exec(String command) {
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

    @Override
    public String setStatusBarIcon(
            String slot,
            String packageName,
            int iconId,
            int iconLevel,
            String contentDescription
    ) {
        try {
            Object statusBar = getStatusBarService();

            Method method = statusBar.getClass().getMethod(
                "setIcon",
                String.class,
                String.class,
                int.class,
                int.class,
                String.class
            );

            method.invoke(
                statusBar,
                slot,
                packageName,
                iconId,
                iconLevel,
                contentDescription
            );

            return "ok";
        } catch (Throwable t) {
            return "error=" + rootCause(t);
        }
    }

    @Override
    public String removeStatusBarIcon(String slot) {
        try {
            Object statusBar = getStatusBarService();

            Method method = statusBar.getClass().getMethod(
                "removeIcon",
                String.class
            );

            method.invoke(statusBar, slot);

            return "ok";
        } catch (Throwable t) {
            return "error=" + rootCause(t);
        }
    }

    private static Object getStatusBarService() throws Exception {
        Class<?> serviceManager =
            Class.forName("android.os.ServiceManager");

        Method getService =
            serviceManager.getMethod("getService", String.class);

        IBinder binder =
            (IBinder) getService.invoke(null, "statusbar");

        if (binder == null) {
            throw new IllegalStateException(
                "statusbar service unavailable"
            );
        }

        Class<?> stub =
            Class.forName(
                "com.android.internal.statusbar.IStatusBarService$Stub"
            );

        Method asInterface =
            stub.getMethod("asInterface", IBinder.class);

        Object service =
            asInterface.invoke(null, binder);

        if (service == null) {
            throw new IllegalStateException(
                "IStatusBarService unavailable"
            );
        }

        return service;
    }

    private static String rootCause(Throwable throwable) {
        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current.toString();
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
