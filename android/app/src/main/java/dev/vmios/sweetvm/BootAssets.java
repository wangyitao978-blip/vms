package dev.vmios.sweetvm;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Extracts the CI-bundled Host Raster payload into app-private VM storage. */
public final class BootAssets {
    public static final String PAYLOAD_NAME = "sweet-qemu-gpu-payload.bin";
    private static final String VERSION_NAME = "payload.ver";
    private static final String HASH_ASSET = "boot/payload.sha256";
    private static final String PAYLOAD_ASSET = "boot/" + PAYLOAD_NAME;

    private BootAssets() {}

    public static File payloadFile(Context context) {
        return new File(new File(context.getFilesDir(), "vm"), PAYLOAD_NAME);
    }

    public static boolean ensure(Context context, long appVersion) throws IOException {
        File dir = new File(context.getFilesDir(), "vm");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir);
        }
        String expected = readAssetText(context, HASH_ASSET).trim().toLowerCase(java.util.Locale.US);
        if (!expected.matches("[0-9a-f]{64}")) {
            throw new IOException("内置 payload SHA-256 清单无效");
        }

        File payload = new File(dir, PAYLOAD_NAME);
        File version = new File(dir, VERSION_NAME);
        String marker = appVersion + ":" + expected;
        if (payload.isFile() && payload.length() > 0 && version.isFile()
                && marker.equals(readFileText(version).trim())
                && expected.equals(sha256(payload))) {
            return false;
        }

        File temp = new File(dir, PAYLOAD_NAME + ".tmp");
        temp.delete();
        MessageDigest digest = sha256Digest();
        try (InputStream in = context.getAssets().open(PAYLOAD_ASSET);
             FileOutputStream out = new FileOutputStream(temp)) {
            byte[] buffer = new byte[1 << 20];
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
                digest.update(buffer, 0, count);
            }
            out.getFD().sync();
        } catch (IOException e) {
            temp.delete();
            throw e;
        }
        String actual = hex(digest.digest());
        if (!expected.equals(actual)) {
            temp.delete();
            throw new IOException("内置 payload 校验失败，SHA-256 不匹配");
        }
        if (payload.exists() && !payload.delete()) {
            temp.delete();
            throw new IOException("cannot replace old payload");
        }
        if (!temp.renameTo(payload)) {
            temp.delete();
            throw new IOException("cannot finalize bundled payload");
        }
        try (FileOutputStream out = new FileOutputStream(version)) {
            out.write(marker.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        return true;
    }

    private static String readAssetText(Context context, String name) throws IOException {
        try (InputStream in = context.getAssets().open(name)) {
            byte[] bytes = new byte[256];
            int n = in.read(bytes);
            return n <= 0 ? "" : new String(bytes, 0, n, StandardCharsets.US_ASCII);
        }
    }

    private static String readFileText(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) Math.min(file.length(), 256)];
            int n = in.read(bytes);
            return n <= 0 ? "" : new String(bytes, 0, n, StandardCharsets.UTF_8);
        }
    }

    private static String sha256(File file) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[1 << 20];
            int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        return hex(digest.digest());
    }

    private static MessageDigest sha256Digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
        return out.toString();
    }
}
