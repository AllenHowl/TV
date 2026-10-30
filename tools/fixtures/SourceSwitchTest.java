package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.github.catvod.utils.Prefers;
import org.junit.Test;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Runs the real BaseConfig, with JVM doubles only for Android/services. */
public class SourceSwitchTest {
    private static final class Result extends Callback {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicInteger successes = new AtomicInteger();
        final AtomicInteger failures = new AtomicInteger();
        public void success() { successes.incrementAndGet(); done.countDown(); }
        public void error(String message) { failures.incrementAndGet(); done.countDown(); }
        void await() throws InterruptedException { assertTrue("Load did not complete", done.await(5, TimeUnit.SECONDS)); }
    }

    private static final class Source extends VodConfig {
        volatile String value = "initial";
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        Source() {
            instance = this;
            config = new Config("initial");
            Config.updates.clear();
            Prefers.put("config_0", "initial");
        }
        void choose(String url, Result callback) { switchConfig(new Config(url), callback); }
        @Override protected void load(Config target) throws Throwable {
            value = target.getUrl();
            if (value.equals("slow")) {
                started.countDown();
                // Model a third-party parser which does not stop promptly on cancel.
                while (true) {
                    try { release.await(); break; }
                    catch (InterruptedException ignored) {}
                }
            }
            if (target.getUrl().equals("bad")) throw new IOException("Invalid remote configuration");
        }
        @Override protected Runnable snapshotState() {
            Runnable base = super.snapshotState();
            String previous = value;
            return () -> { value = previous; base.run(); };
        }
    }

    @Test public void failureRestoresSelectionParsedStateAndLoader() throws Exception {
        Source source = new Source();
        Result good = new Result(); source.choose("good", good); good.await();
        BaseLoader working = BaseLoader.get();
        Result bad = new Result(); source.choose("bad", bad); bad.await();
        assertEquals("good", source.value);
        assertEquals("good", source.getConfig().getUrl());
        assertEquals("good", Prefers.getString("config_0"));
        assertSame(working, BaseLoader.get());
        assertEquals(1, bad.failures.get());
        assertEquals(List.of("good"), Config.updates);
    }

    @Test public void lastSelectionWinsEvenWhenCanceledParserKeepsRunning() throws Exception {
        Source source = new Source();
        Result slow = new Result(); source.choose("slow", slow);
        assertTrue(source.started.await(5, TimeUnit.SECONDS));
        Result middle = new Result(); source.choose("middle", middle);
        Result latest = new Result(); source.choose("latest", latest);
        source.release.countDown(); latest.await();
        assertEquals("latest", source.value);
        assertEquals("latest", source.getConfig().getUrl());
        assertEquals(List.of("latest"), Config.updates);
        assertEquals(0, slow.successes.get() + slow.failures.get());
        assertEquals(0, middle.successes.get() + middle.failures.get());
    }
}
