package com.fongmi.android.tv.api.config;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Task;
import com.github.catvod.utils.Prefers;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.concurrent.atomic.AtomicInteger;

abstract class BaseConfig {

    public static final int VOD = 0;
    public static final int LIVE = 1;
    public static final int WALL = 2;

    // All configuration mutations share one queue: cancellation cannot leave an older
    // parser writing into the singleton after a newer selection has started.
    private static final Object STATE_LOCK = new Object();
    private static final ExecutorService LOADS = Executors.newSingleThreadExecutor();
    private List<Header> headers = List.of();
    private List<Proxy> proxies = List.of();
    private List<String> hosts = List.of();

    private final AtomicInteger taskId = new AtomicInteger(0);
    private final Object loadLock = new Object();

    protected boolean sync;
    protected volatile Config config;
    private volatile Future<?> future;

    protected abstract String getTag();

    protected abstract Config defaultConfig();

    protected abstract void load(Config config) throws Throwable;

    protected abstract boolean isLoaded();

    protected void cancelLoad() {
    }

    public synchronized void ensureLoaded() {
        try {
            if (isLoaded()) return;
            if (config == null) config = defaultConfig();
            Server.get().start();
            synchronized (STATE_LOCK) { load(config); }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    protected void postEvent() {
        ConfigEvent.common();
    }

    public boolean needSync(String url) {
        Config selected = getConfig();
        return sync || selected.isEmpty() || url.equals(selected.getUrl());
    }

    public Config getConfig() {
        return config == null ? defaultConfig() : config;
    }

    protected void setHeaders(List<Header> headers) {
        this.headers = headers;
        refreshNetwork();
    }

    protected void setProxy(List<Proxy> proxy) {
        this.proxies = proxy;
        refreshNetwork();
    }

    protected void setHosts(List<String> hosts) {
        this.hosts = hosts;
        refreshNetwork();
    }

    private static void refreshNetwork() {
        OkHttp.responseInterceptor().clear();
        OkHttp.selector().clear();
        OkHttp.dns().clear();
        for (BaseConfig source : List.of(VodConfig.get(), LiveConfig.get())) {
            OkHttp.responseInterceptor().addAll(source.headers);
            OkHttp.selector().addAll(source.proxies);
            OkHttp.dns().addAll(source.hosts);
        }
    }

    protected Runnable snapshotState() {
        Config previous = this.config == null ? null : Config.objectFrom(this.config.toString());
        boolean previousSync = sync;
        int type = this instanceof VodConfig ? VOD : this instanceof LiveConfig ? LIVE : WALL;
        String selected = Prefers.getString("config_" + type);
        List<Header> previousHeaders = headers;
        List<Proxy> previousProxies = proxies;
        List<String> previousHosts = hosts;
        return () -> {
            this.config = previous;
            sync = previousSync;
            Prefers.put("config_" + type, selected);
            headers = previousHeaders;
            proxies = previousProxies;
            hosts = previousHosts;
            refreshNetwork();
            RuleConfig.get().invalidate();
        };
    }

    protected void prepare(Config target) {
        this.config = target;
        headers = List.of();
        proxies = List.of();
        hosts = List.of();
        refreshNetwork();
    }

    protected void afterLoaded() {
    }

    public void load(Callback callback) {
        submit(getConfig(), callback, false, () -> {});
    }

    protected void switchConfig(Config target, Callback callback) {
        submit(target, callback, true, () -> {});
    }

    protected void switchConfig(Config target, Callback callback, Runnable committed) {
        submit(target, callback, true, committed);
    }

    private void submit(Config target, Callback callback, boolean switching, Runnable committed) {
        synchronized (loadLock) {
            int id = taskId.incrementAndGet();
            if (future != null && !future.isDone()) {
                cancelLoad();
                OkHttp.cancel(getTag());
                future.cancel(true);
            }
            callback.start();
            future = LOADS.submit(() -> loadConfig(id, target, callback, switching, committed));
        }
    }

    public void cancel() {
        synchronized (loadLock) {
            taskId.incrementAndGet();
            cancelLoad();
            OkHttp.cancel(getTag());
            if (future != null && !future.isDone()) future.cancel(true);
        }
    }

    private void loadConfig(int id, Config target, Callback callback, boolean switching, Runnable committed) {
        synchronized (STATE_LOCK) {
            if (taskId.get() != id) return;
            Runnable restore = snapshotState();
            Runnable restoreLive = this instanceof VodConfig ? LiveConfig.get().snapshotState() : () -> {};
            BaseLoader previousLoader = null;
            try {
                Server.get().start();
                OkHttp.cancel(getTag());
                if (switching) {
                    if (this instanceof VodConfig) previousLoader = BaseLoader.beginSwitch();
                    prepare(target);
                }
                load(target);
                synchronized (loadLock) {
                    if (taskId.get() != id || Thread.currentThread().isInterrupted()) throw new CancellationException();
                    afterLoaded();
                    if (this.config != null && !this.config.isEmpty()) this.config.update();
                    committed.run();
                }
                if (previousLoader != null) {
                    BaseLoader retired = previousLoader;
                    Task.execute(retired::clear);
                }
                App.post(() -> {
                    if (taskId.get() != id) return;
                    Notify.show(target.getNotice());
                    callback.success();
                    postEvent();
                });
            } catch (Throwable e) {
                // Restore references, request policy and the original spider loader even
                // when the canceled attempt is no longer allowed to notify its caller.
                restoreLive.run();
                restore.run();
                if (previousLoader != null) BaseLoader.rollbackSwitch(previousLoader);
                if (isCanceled(e) || taskId.get() != id) return;
                e.printStackTrace();
                App.post(() -> {
                    if (taskId.get() != id) return;
                    callback.error(TextUtils.isEmpty(target.getUrl()) ? "" : Notify.getError(R.string.error_config_get, e));
                });
            }
        }
    }

    protected boolean isCanceled(Throwable e) {
        if (Thread.currentThread().isInterrupted()) return true;
        boolean canceled = false;
        boolean timedOut = false;
        for (Throwable current = e; current != null; current = current.getCause()) {
            if (current instanceof InterruptedException || current instanceof CancellationException) return true;
            canceled |= current instanceof InterruptedIOException || "Canceled".equals(current.getMessage());
            timedOut |= current instanceof SocketTimeoutException;
        }
        return canceled && !timedOut;
    }

    protected JsonArray fetchArray(JsonObject object, String key) {
        if (!object.has(key)) return new JsonArray();
        JsonElement element = object.get(key);
        if (element.isJsonObject()) return new JsonArray();
        if (element.isJsonPrimitive()) element = fetch(element.getAsString());
        JsonArray result = new JsonArray();
        for (JsonElement item : element.getAsJsonArray()) {
            if (item.isJsonPrimitive()) result.addAll(fetch(item.getAsString()));
            else if (item.isJsonObject()) result.add(item);
        }
        return result;
    }

    private JsonArray fetch(String url) {
        try {
            JsonElement parsed = Json.parse(OkHttp.string(UrlUtil.convert(url)));
            return parsed.isJsonArray() ? parsed.getAsJsonArray() : new JsonArray();
        } catch (Exception e) {
            return new JsonArray();
        }
    }
}
