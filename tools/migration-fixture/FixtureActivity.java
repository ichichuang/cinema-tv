package com.ichichuang.cinema.migrationfixture;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;

/** Disposable test package: synthetic preferences only, no network or production data. */
public final class FixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(),
                    Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES
                            : PackageManager.GET_SIGNATURES);
            android.content.SharedPreferences prefs = getSharedPreferences("synthetic", MODE_PRIVATE);
            seedInitialData(prefs, info.versionCode);
            Signature[] current = Build.VERSION.SDK_INT >= 28
                    ? info.signingInfo.getApkContentsSigners() : info.signatures;
            Signature[] history = Build.VERSION.SDK_INT >= 28
                    ? info.signingInfo.getSigningCertificateHistory() : info.signatures;
            String nonce = getIntent().getStringExtra("observationNonce");
            if (nonce == null || !nonce.matches("[0-9a-f]{32}")) nonce = "";
            String json = "{\"nonce\":\"" + nonce + "\",\"uid\":" + Process.myUid() + ",\"version\":" + info.versionCode
                    + ",\"marker\":\"" + prefs.getString("marker", "") + "\",\"progress\":"
                    + prefs.getLong("progress", -1) + ",\"current\":" + hashes(current)
                    + ",\"history\":" + hashes(history) + "}";
            try (FileOutputStream stream = new FileOutputStream(new File(getFilesDir(), "observation.json"))) {
                stream.write(json.getBytes(StandardCharsets.US_ASCII));
                stream.getFD().sync();
            }
        } catch (Exception error) {
            throw new IllegalStateException("Fixture observation failed", error);
        }
        finish();
    }

    static void seedInitialData(android.content.SharedPreferences prefs, int versionCode) {
        if (versionCode == 1001 && !prefs.contains("marker")) {
            if (!prefs.edit().putString("marker", "migration-synthetic-data")
                    .putLong("progress", 12345L).commit()) throw new IllegalStateException("write failed");
        }
    }

    private static String hashes(Signature[] signatures) throws Exception {
        ArrayList<String> values = new ArrayList<>();
        if (signatures != null) for (Signature signature : signatures) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            values.add("\"" + hex + "\"");
        }
        Collections.sort(values);
        return "[" + String.join(",", values) + "]";
    }
}
