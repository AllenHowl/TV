"""Minimal Android/service doubles for exercising the production BaseConfig queue on a JVM.

Only the service boundary is replaced: BaseConfig itself is compiled unchanged.
These doubles are never part of an Android build.
"""
STUBS = {
    'com/fongmi/android/tv/utils/Task.java': 'package com.fongmi.android.tv.utils; public class Task extends com.github.catvod.utils.Task {}',
    'android/text/TextUtils.java': 'package android.text; public class TextUtils { public static boolean isEmpty(CharSequence s) { return s == null || s.length() == 0; } }',
    'com/fongmi/android/tv/App.java': 'package com.fongmi.android.tv; public class App { public static void post(Runnable task) { task.run(); } }',
    'com/fongmi/android/tv/R.java': 'package com.fongmi.android.tv; public class R { public static class string { public static int error_config_get = 1; } }',
    'com/fongmi/android/tv/bean/Config.java': '''package com.fongmi.android.tv.bean;
        public class Config {
            public static java.util.List<String> updates = new java.util.concurrent.CopyOnWriteArrayList<>();
            private String url;
            public Config(String url) { this.url = url; }
            public static Config objectFrom(String url) { return new Config(url); }
            public String getUrl() { return url; }
            public String getNotice() { return ""; }
            public boolean isEmpty() { return url == null || url.isEmpty(); }
            public Config update() { updates.add(url); com.github.catvod.utils.Prefers.put("config_0", url); return this; }
            public String toString() { return url; }
        }''',
    'com/fongmi/android/tv/event/ConfigEvent.java': 'package com.fongmi.android.tv.event; public class ConfigEvent { public static void common() {} }',
    'com/fongmi/android/tv/impl/Callback.java': 'package com.fongmi.android.tv.impl; public class Callback { public void start() {} public void success() {} public void error(String text) {} }',
    'com/fongmi/android/tv/server/Server.java': 'package com.fongmi.android.tv.server; public class Server { private static final Server INSTANCE = new Server(); public static Server get() {return INSTANCE;} public void start() {} }',
    'com/fongmi/android/tv/utils/Notify.java': 'package com.fongmi.android.tv.utils; public class Notify { public static void show(String text) {} public static String getError(int id, Throwable error) { return error.getMessage(); } }',
    'com/fongmi/android/tv/utils/UrlUtil.java': 'package com.fongmi.android.tv.utils; public class UrlUtil { public static String convert(String url) { return url; } }',
    'com/github/catvod/bean/Header.java': 'package com.github.catvod.bean; public class Header {}',
    'com/github/catvod/bean/Proxy.java': 'package com.github.catvod.bean; public class Proxy {}',
    'com/github/catvod/net/OkHttp.java': '''package com.github.catvod.net;
        public class OkHttp {
            public static class Policy { public void clear() {} public void addAll(java.util.List<?> items) {} }
            private static final Policy POLICY = new Policy();
            public static Policy responseInterceptor() {return POLICY;}
            public static Policy selector() {return POLICY;}
            public static Policy dns() {return POLICY;}
            public static void cancel(String tag) {}
            public static String string(String url) {return "[]";}
        }''',
    'com/github/catvod/utils/Json.java': 'package com.github.catvod.utils; public class Json { public static com.google.gson.JsonElement parse(String json) { return com.google.gson.JsonParser.parseString(json); } }',
    'com/github/catvod/utils/Task.java': 'package com.github.catvod.utils; public class Task { public static void execute(Runnable task) { task.run(); } }',
    'com/github/catvod/utils/Prefers.java': '''package com.github.catvod.utils; public class Prefers {
        private static final java.util.Map<String,String> VALUES = new java.util.concurrent.ConcurrentHashMap<>();
        public static String getString(String key) {return VALUES.getOrDefault(key, "");}
        public static void put(String key, String value) {VALUES.put(key, value);}
        }''',
    'com/fongmi/android/tv/api/config/RuleConfig.java': 'package com.fongmi.android.tv.api.config; public class RuleConfig { private static final RuleConfig INSTANCE = new RuleConfig(); public static RuleConfig get() {return INSTANCE;} public void invalidate() {} }',
    'com/fongmi/android/tv/api/loader/BaseLoader.java': '''package com.fongmi.android.tv.api.loader; public class BaseLoader {
        private static BaseLoader instance = new BaseLoader();
        public static BaseLoader get() {return instance;}
        public static BaseLoader beginSwitch() {BaseLoader old = instance; instance = new BaseLoader(); return old;}
        public static void rollbackSwitch(BaseLoader old) {instance = old;}
        public void clear() {}
        }''',
}


def write_stubs(root):
    for name in ['VodConfig', 'LiveConfig']:
        STUBS[f'com/fongmi/android/tv/api/config/{name}.java'] = f'''package com.fongmi.android.tv.api.config;
        import com.fongmi.android.tv.bean.Config;
        public class {name} extends BaseConfig {{
            protected static {name} instance = new {name}();
            public static {name} get() {{return instance;}}
            protected String getTag() {{return "{name}";}}
            protected Config defaultConfig() {{return new Config("");}}
            protected void load(Config config) throws Throwable {{}}
            protected boolean isLoaded() {{return false;}}
        }}'''
    paths = []
    for name, text in STUBS.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        paths.append(str(path))
    return paths
