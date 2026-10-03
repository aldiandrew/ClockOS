package com.aldiandrew.clockos.shizuku;

import androidx.annotation.Keep;
import java.io.BufferedReader;
import java.io.InputStreamReader;

@Keep
public class UserService extends IUserService.Stub {
    @Keep
    public UserService() {}

    @Override
    public String exec(String command) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            String stdout = read(process.getInputStream());
            String stderr = read(process.getErrorStream());
            int code = process.waitFor();
            StringBuilder result = new StringBuilder();
            result.append("exit=").append(code);
            if (!stdout.isEmpty()) result.append("\n").append(stdout);
            if (!stderr.isEmpty()) result.append("\nstderr=").append(stderr);
            return result.toString();
        } catch (Throwable t) {
            return "error=" + t;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static String read(java.io.InputStream input) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (out.length() > 0) out.append('\n');
            out.append(line);
        }
        return out.toString();
    }

    public void destroy() { System.exit(0); }
}
