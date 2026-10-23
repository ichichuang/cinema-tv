package com.ichichuang.cinema.authruntime;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Process;
import com.ichichuang.cinema.data.update.UpdatePackageIdentity;
import com.ichichuang.cinema.data.update.auth.AuthenticatedDownload;
import com.ichichuang.cinema.data.update.auth.AuthenticatedUpdateVerifier;
import com.ichichuang.cinema.data.update.auth.FileUpdateReplayStore;
import com.ichichuang.cinema.data.update.auth.UpdateAuthenticationException;
import com.ichichuang.cinema.data.update.auth.UpdatePlatform;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import kotlin.Unit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Test-only adapter. Compiled with the real candidate class files, outside application source sets. */
public final class AuthenticatedRuntimeActivity extends Activity {
    private static final String FIXTURE = "com.ichichuang.cinema.migrationfixture";
    private static final String BASE = "https://localhost:9443";
    private final List<String> checks = new ArrayList<>();
    private final JSONObject details = new JSONObject();
    private File root;
    private PublicKey publicKey;
    private PrivateKey privateKey;
    private SSLContext tls;
    private int buffered;
    private int transferCalls;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String phase = getIntent().getStringExtra("phase");
        new Thread(() -> {
            JSONObject report = new JSONObject();
            try {
                root = getNoBackupFilesDir();
                configureTls();
                if ("prepare".equals(phase)) prepare();
                else if ("restart".equals(phase)) restart();
                else throw new AssertionError("Unknown phase");
                report.put("status", "PASS");
            } catch (Throwable error) {
                try {
                    report.put("status", "FAIL");
                    report.put("failure", error.getClass().getName() + ": " + error.getMessage());
                    java.io.StringWriter stack = new java.io.StringWriter();
                    error.printStackTrace(new java.io.PrintWriter(stack));
                    report.put("stack", stack.toString());
                } catch (Exception ignored) { /* Retain any report fields already available. */ }
            }
            try {
                report.put("phase", phase).put("pid", Process.myPid()).put("checks", new JSONArray(checks));
                report.put("details", details).put("production_protection", false);
                Files.write(new File(getFilesDir(), "auth-" + phase + ".json").toPath(),
                        report.toString(2).getBytes(StandardCharsets.UTF_8));
            } catch (Exception error) { throw new IllegalStateException(error); }
            runOnUiThread(this::finish);
        }, "isolated-auth-runtime").start();
    }

    private void configureTls() throws Exception {
        KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
        trust.load(null);
        try (InputStream input = getAssets().open("server.crt")) {
            trust.setCertificateEntry("disposable-loopback-server",
                    CertificateFactory.getInstance("X.509").generateCertificate(input));
        }
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(trust);
        tls = SSLContext.getInstance("TLS");
        tls.init(null, factory.getTrustManagers(), null);
    }

    private void prepare() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        KeyPair pair = generator.generateKeyPair();
        publicKey = pair.getPublic(); privateKey = pair.getPrivate();
        Files.write(new File(root, "test-public").toPath(), publicKey.getEncoded());
        Files.write(new File(root, "test-private").toPath(), privateKey.getEncoded());
        File replay = directory("pending");
        FileUpdateReplayStore store = new FileUpdateReplayStore(replay);
        store.initialize();
        byte[] apk = get("/fixture.apk?inspect", 1024 * 1024);
        File archive = new File(getCacheDir(), "inspect.apk");
        Files.write(archive.toPath(), apk);
        PackageInfo info = archive(archive);
        Set<String> current = hashes(info.signingInfo.getApkContentsSigners());
        Set<String> history = hashes(info.signingInfo.getSigningCertificateHistory());
        require(current.size() == 1 && history.size() == 2 && history.containsAll(current), "PM current/history");
        details.put("current_signers", new JSONArray(current)).put("signing_history", new JSONArray(history));
        details.put("jca_provider", Signature.getInstance("SHA256withRSA").getProvider().getName());
        details.put("installed_fixture_version", installed().getVersionCode());
        byte[] wire = wire(apk, current, 10);
        Files.write(new File(root, "pending-envelope").toPath(), wire);
        put(wire);
        AuthenticatedUpdateVerifier verifier = verifier(store);
        AuthenticatedDownload permit = verifier.authorize(get("/envelope", 16384));
        require(store.read().getSequence() == 10, "persist before permit");
        checks.add("android-jca-valid-signature-and-persisted-permit");

        int before = transferCalls;
        rejected(() -> {
            AuthenticatedDownload invalid = verifier.authorize(get("/tampered", 16384));
            download(verifier, invalid, new File(getCacheDir(), "forged.apk"));
        }, true);
        require(transferCalls == before && store.read().getSequence() == 10, "failure reached download");
        rejected(() -> AuthenticatedUpdateVerifier.Companion.production(), true);
        checks.add("authentication-failure-stops-before-transfer-production-entry-disabled");

        buffered = 0;
        rejected(() -> get("/oversized", 16384), false);
        require(buffered == 0, "declared oversize buffered");
        buffered = 0;
        rejected(() -> get("/oversized-stream", 16384), false);
        require(buffered <= 16384, "stream oversize buffered");
        details.put("maximum_oversize_buffered_bytes", buffered);
        checks.add("envelope-limit-before-buffering-declared-and-streamed");

        File target = new File(getCacheDir(), "authenticated.apk");
        rejected(() -> download(verifier, permit, target), false);
        require(!target.exists() && !new File(getCacheDir(), "authenticated.part").exists(), "interrupted cleanup");
        require(store.read().getSequence() == 10 && !store.read().getCompleted(), "pending retry lost");
        checks.add("controlled-network-interruption-cleans-partial-and-retains-pending");
        Files.write(new File(root, "prepare-pid").toPath(), String.valueOf(Process.myPid()).getBytes(StandardCharsets.US_ASCII));
    }

    private void restart() throws Exception {
        int priorPid = Integer.parseInt(new String(Files.readAllBytes(new File(root, "prepare-pid").toPath()), StandardCharsets.US_ASCII));
        require(priorPid != Process.myPid(), "process did not restart");
        details.put("prepare_pid", priorPid).put("restart_pid", Process.myPid());
        KeyFactory keys = KeyFactory.getInstance("RSA");
        publicKey = keys.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(new File(root, "test-public").toPath())));
        privateKey = keys.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(new File(root, "test-private").toPath())));
        FileUpdateReplayStore store = new FileUpdateReplayStore(directory("pending"));
        require(store.read().getSequence() == 10 && !store.read().getCompleted(), "replay floor after restart");
        AuthenticatedUpdateVerifier verifier = verifier(store);
        byte[] wire = Files.readAllBytes(new File(root, "pending-envelope").toPath());
        AuthenticatedDownload permit = verifier.authorize(wire);
        File accepted = download(verifier, permit, new File(getCacheDir(), "authenticated.apk"));
        require(accepted.isFile() && !new File(getCacheDir(), "authenticated.part").exists(), "retry failed");
        checks.add("actual-process-restart-retains-floor-and-identical-pending-retry");
        checks.add("interrupted-download-retry-real-apk-current-signer-extraction");

        byte[] apk = Files.readAllBytes(accepted.toPath());
        PackageInfo info = archive(accepted);
        Set<String> current = hashes(info.signingInfo.getApkContentsSigners());
        Set<String> history = hashes(info.signingInfo.getSigningCertificateHistory());
        rejected(() -> verifier.authorize(wire(apk, current, 9)), true);
        rejected(() -> verifier.authorize(wire(apk, history, 10)), true);
        require(store.read().getSequence() == 10, "replay changed floor");
        checks.add("signed-lower-sequence-and-changed-pending-payload-rejected");

        FileUpdateReplayStore historyStore = new FileUpdateReplayStore(directory("history-negative"));
        historyStore.initialize();
        AuthenticatedUpdateVerifier historyVerifier = verifier(historyStore);
        AuthenticatedDownload historyPermit = historyVerifier.authorize(wire(apk, history, 1));
        File wrong = new File(getCacheDir(), "history.apk");
        rejected(() -> download(historyVerifier, historyPermit, wrong), true);
        require(!wrong.exists() && !new File(getCacheDir(), "history.part").exists(), "history mismatch cleanup");
        checks.add("history-certificates-cannot-stand-in-for-current-signer-set");

        File storage = directory("injected-storage-failure");
        require(new File(storage, "update-replay.next").mkdir(), "failure injection");
        rejected(() -> new FileUpdateReplayStore(storage).initialize(), true);
        require(!new File(storage, "update-replay").exists(), "failed storage produced state");
        details.put("storage_failure", "injected path collision; no device storage fault or disk exhaustion");
        checks.add("injected-storage-write-failure-fails-closed");
    }

    private AuthenticatedUpdateVerifier verifier(FileUpdateReplayStore store) throws Exception {
        UpdatePackageIdentity actual = installed();
        return new AuthenticatedUpdateVerifier(Collections.singletonMap("runtime-test", publicKey), store,
                new UpdatePlatform(actual.getPackageName(), actual.getVersionCode(), Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS[0]),
                () -> System.currentTimeMillis() / 1000);
    }

    private File download(AuthenticatedUpdateVerifier verifier, AuthenticatedDownload permit, File target) {
        return verifier.download(permit, target, partial -> {
            transferCalls++;
            try {
                HttpsURLConnection connection = connection(permit.getUrl());
                try (InputStream input = connection.getInputStream(); java.io.FileOutputStream output = new java.io.FileOutputStream(partial)) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                } finally { connection.disconnect(); }
                return Unit.INSTANCE;
            } catch (Exception error) { throw new IllegalStateException("Controlled transfer failed", error); }
        }, file -> {
            try {
                PackageInfo info = archive(file);
                return new UpdatePackageIdentity(info.packageName, info.getLongVersionCode(),
                        hashes(info.signingInfo.getApkContentsSigners()));
            } catch (Exception error) { throw new IllegalStateException(error); }
        });
    }

    private UpdatePackageIdentity installed() throws Exception {
        PackageInfo info = getPackageManager().getPackageInfo(FIXTURE, PackageManager.GET_SIGNING_CERTIFICATES);
        return new UpdatePackageIdentity(info.packageName, info.getLongVersionCode(), hashes(info.signingInfo.getApkContentsSigners()));
    }
    private PackageInfo archive(File file) {
        PackageInfo info = getPackageManager().getPackageArchiveInfo(file.getPath(), PackageManager.GET_SIGNING_CERTIFICATES);
        require(info != null && info.signingInfo != null, "archive signer lookup");
        return info;
    }
    private static Set<String> hashes(android.content.pm.Signature[] signatures) throws Exception {
        Set<String> result = new TreeSet<>();
        for (android.content.pm.Signature signature : signatures) result.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));
        return result;
    }
    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        for (byte value : bytes) text.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return text.toString();
    }
    private byte[] wire(byte[] apk, Set<String> signers, long sequence) throws Exception {
        long now = System.currentTimeMillis() / 1000;
        String payload = "cinema-update-auth/1\nkey=runtime-test\npackage=" + FIXTURE + "\nsequence=" + sequence
                + "\nversion=1002\nsize=" + apk.length + "\nsha256=" + hex(MessageDigest.getInstance("SHA-256").digest(apk))
                + "\nsigners=" + String.join(",", signers) + "\nmin-sdk=30\nmax-sdk=30\nabis=arm64-v8a"
                + "\nnot-before=" + (now - 60) + "\nexpires=" + (now + 3600) + "\nurl=" + BASE + "/fixture.apk\n";
        byte[] bytes = payload.getBytes(StandardCharsets.US_ASCII);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey); signature.update(bytes);
        byte[] signed = signature.sign();
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(wire);
        out.writeBytes("AUTHUPD1"); out.writeInt(bytes.length); out.write(bytes); out.writeShort(signed.length); out.write(signed);
        return wire.toByteArray();
    }
    private HttpsURLConnection connection(String url) throws Exception {
        require(url.startsWith(BASE + "/"), "test endpoint boundary");
        HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
        connection.setSSLSocketFactory(tls.getSocketFactory());
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
        return connection;
    }
    private byte[] get(String path, int limit) throws Exception {
        HttpsURLConnection connection = connection(BASE + path);
        try {
            require(connection.getResponseCode() == 200, "controlled endpoint response");
            if (connection.getContentLengthLong() > limit) throw new java.io.IOException("Envelope exceeds bounded read");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > limit) throw new java.io.IOException("Envelope exceeds bounded read");
                    output.write(buffer, 0, count); buffered = output.size();
                }
                return output.toByteArray();
            }
        } finally { connection.disconnect(); }
    }
    private void put(byte[] wire) throws Exception {
        HttpsURLConnection connection = connection(BASE + "/envelope");
        try {
            connection.setRequestMethod("POST"); connection.setDoOutput(true); connection.setFixedLengthStreamingMode(wire.length);
            try (java.io.OutputStream output = connection.getOutputStream()) { output.write(wire); }
            require(connection.getResponseCode() == 204, "test envelope provisioning");
        } finally { connection.disconnect(); }
    }
    private File directory(String name) {
        File file = new File(root, name);
        require(file.isDirectory() || file.mkdir(), "test directory");
        return file;
    }
    private interface Action { void run() throws Exception; }
    private static void rejected(Action action, boolean authentication) throws Exception {
        try { action.run(); }
        catch (Exception error) {
            if (authentication && !(error instanceof UpdateAuthenticationException)) throw error;
            return;
        }
        throw new AssertionError("Expected rejection");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
