package com.ichichuang.cinema.migrationfixture;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** JVM test of the fixture's own seeding policy. It does not emulate Android package installation. */
public final class FixtureStateTest {
    public static void main(String[] args) {
        Map<String, Object> values = new HashMap<>();
        SharedPreferences.Editor editor = (SharedPreferences.Editor) Proxy.newProxyInstance(
                FixtureStateTest.class.getClassLoader(), new Class<?>[]{SharedPreferences.Editor.class},
                (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "putString": case "putLong": values.put((String) arguments[0], arguments[1]); return proxy;
                        case "commit": return true;
                        default: throw new AssertionError("Unexpected editor operation");
                    }
                });
        SharedPreferences prefs = (SharedPreferences) Proxy.newProxyInstance(
                FixtureStateTest.class.getClassLoader(), new Class<?>[]{SharedPreferences.class},
                (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "contains": return values.containsKey(arguments[0]);
                        case "edit": return editor;
                        default: throw new AssertionError("Unexpected preference operation");
                    }
                });
        FixtureActivity.seedInitialData(prefs, 1001);
        if (!"migration-synthetic-data".equals(values.get("marker")) || !Long.valueOf(12345).equals(values.get("progress")))
            throw new AssertionError("Initial installation did not seed synthetic state");
        values.put("progress", 999L);
        FixtureActivity.seedInitialData(prefs, 1002);
        if (!Long.valueOf(999).equals(values.get("progress")))
            throw new AssertionError("Upgrade changed existing synthetic state");
        values.clear();
        FixtureActivity.seedInitialData(prefs, 1002);
        if (!values.isEmpty()) throw new AssertionError("Lost preferences were silently recreated during upgrade");
        System.out.println("Fixture state policy: 3 checks passed (JVM only; no platform evidence).");
    }
}
