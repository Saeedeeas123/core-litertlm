package com.saeed.core.litertlm;

import android.content.Context;
import android.os.Looper;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Channel;
import com.google.ai.edge.litertlm.Content;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.MessageCallback;
import com.google.ai.edge.litertlm.SamplerConfig;
import com.google.ai.edge.litertlm.ToolProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Loads and runs one {@code .litertlm} model fully on-device through LiteRT-LM.
 *
 * <p>Design notes (all consequences of verified library behaviour, 2026-10-10):
 * <ul>
 *   <li>Consumed from Java because LiteRT-LM ships Kotlin 2.4 metadata + Java 21 bytecode.</li>
 *   <li>Kotlin's {@code $default} bridges are synthetic and unusable from javac, so every optional
 *       API parameter is passed explicitly here (see {@link #conversationConfig}).</li>
 *   <li>The model file is validated in Java (extension + {@code LITERTLM} magic) before the native
 *       layer sees it, because a bad file makes the native engine abort rather than throw.</li>
 *   <li>Loading and generation are forbidden on the main thread, and generations are serialized —
 *       the two ways an on-device engine normally takes an app down.</li>
 * </ul>
 *
 * <p>Instances are not cheap: one engine can hold gigabytes. Prefer {@link LitertLmRuntime} so the
 * model is loaded once per process.
 */
public final class LitertLmEngine implements AutoCloseable {

    private final Context appContext;
    private final LitertLmConfig config;
    private final String cacheDir;

    private final ReentrantLock generationLock = new ReentrantLock();
    private final Object lifecycleLock = new Object();

    private Engine engine;
    private Conversation conversation;
    private volatile boolean closed;

    public LitertLmEngine(Context context, LitertLmConfig config) {
        if (context == null) {
            throw new IllegalArgumentException("context == null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config == null");
        }
        this.appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        this.config = config.normalized();
        this.cacheDir = this.config.cacheDir() != null
                ? this.config.cacheDir()
                : this.appContext.getCacheDir().getAbsolutePath();
    }

    public LitertLmConfig config() {
        return config;
    }

    /** @return true when the native engine is initialized and a conversation is ready. */
    public boolean isLoaded() {
        if (closed) {
            return false;
        }
        Engine current = engine;
        if (current == null || conversation == null) {
            return false;
        }
        try {
            return current.isInitialized();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Loads the model. Blocking and slow (seconds to tens of seconds for a multi-GB model), so it
     * must be called from a worker thread.
     *
     * @throws LitertLmException MODEL_MISSING / MODEL_UNREADABLE / MODEL_NOT_LITERTLM when the file
     *     is unusable, ON_MAIN_THREAD when called on the UI thread, NOT_ENOUGH_MEMORY only when
     *     {@link LitertLmConfig#failFastOnLowMemory()} is enabled, LOAD_FAILED when the native layer
     *     fails.
     */
    public void load() {
        requireNotClosed();
        if (isLoaded()) {
            return;
        }
        if (isMainThread()) {
            throw new LitertLmException(
                    LitertLmException.Kind.ON_MAIN_THREAD,
                    "load() blocks for seconds and must not run on the main thread. Call it from a "
                            + "background thread or coroutine.");
        }
        config.validate();

        File model = LitertLmModels.requireLoadableModel(config.modelPath());
        if (config.failFastOnLowMemory()) {
            LitertLmMemory.requireEnoughRam(model);
        }

        synchronized (lifecycleLock) {
            requireNotClosed();
            if (isLoaded()) {
                return;
            }
            try {
                EngineConfig engineConfig = new EngineConfig(
                        config.modelPath(),
                        toSdkBackend(config.backend()),
                        null,   // visionBackend
                        null,   // audioBackend
                        null,   // maxNumTokens
                        null,   // maxNumImages
                        cacheDir,
                        null);  // activationDataType
                Engine newEngine = new Engine(engineConfig);
                newEngine.initialize();
                Conversation newConversation =
                        newEngine.createConversation(conversationConfig(config));
                this.engine = newEngine;
                this.conversation = newConversation;
            } catch (LitertLmException e) {
                throw e;
            } catch (Throwable t) {
                throw new LitertLmException(
                        LitertLmException.Kind.LOAD_FAILED,
                        "Could not load " + config.modelFileName() + " on the "
                                + config.backend() + " backend: " + t
                                + ". If the GPU backend fails, retry with "
                                + "LitertLmConfig.Backend.CPU.",
                        t);
            }
        }
    }

    /**
     * Sends one prompt and blocks until the answer is complete.
     *
     * @return the full answer text.
     * @throws LitertLmException NOT_LOADED before {@link #load()}, ON_MAIN_THREAD on the UI thread,
     *     GENERATION_FAILED when the native layer fails.
     */
    public String chat(String prompt) {
        return generate(prompt, null);
    }

    /**
     * Sends one prompt and streams the answer through {@code listener}. Blocking until the answer
     * is complete (the listener receives the incremental updates), so call it from a worker thread.
     */
    public void chatStreaming(String prompt, LitertLmChatListener listener) {
        generate(prompt, listener);
    }

    /** Asks the native layer to stop the current generation; safe to call from any thread. */
    public void cancel() {
        Conversation current = conversation;
        if (current == null) {
            return;
        }
        try {
            current.cancelProcess();
        } catch (Throwable ignored) {
            // Best effort: cancelling is advisory.
        }
    }

    private String generate(String prompt, LitertLmChatListener listener) {
        requireNotClosed();
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new LitertLmException(
                    LitertLmException.Kind.GENERATION_FAILED,
                    "Empty prompt.");
        }
        if (isMainThread()) {
            throw new LitertLmException(
                    LitertLmException.Kind.ON_MAIN_THREAD,
                    "Generation blocks until the model finishes and must not run on the main "
                            + "thread. Call it from a background thread or coroutine.");
        }
        Conversation active = conversation;
        if (active == null || !isLoaded()) {
            throw new LitertLmException(
                    LitertLmException.Kind.NOT_LOADED,
                    "Engine is not loaded. Call load() first.");
        }

        final StreamingAccumulator accumulator = new StreamingAccumulator();
        final CountDownLatch finished = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        MessageCallback callback = new MessageCallback() {
            @Override
            public void onMessage(Message message) {
                String chunk = textOf(message);
                if (chunk.isEmpty()) {
                    return;
                }
                String soFar = accumulator.accept(chunk);
                if (listener != null) {
                    listener.onDelta(soFar);
                }
            }

            @Override
            public void onDone() {
                finished.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                failure.set(throwable);
                finished.countDown();
            }
        };

        // One generation at a time: the native session is not safe for concurrent calls.
        generationLock.lock();
        try {
            try {
                active.sendMessageAsync(
                        prompt,
                        callback,
                        new HashMap<String, Object>(),   // extraContext must be non-null
                        null,                            // RepetitionPenaltyConfig
                        null,                            // NoRepeatNgramConfig
                        null,                            // SuppressTokensConfig
                        null,                            // max output tokens
                        null,                            // ThinkingConfig
                        null);                           // ResponseFormat
            } catch (Throwable t) {
                throw new LitertLmException(
                        LitertLmException.Kind.GENERATION_FAILED,
                        "Could not start generation: " + t,
                        t);
            }

            try {
                // No timeout: a 3B-class model on a phone CPU legitimately takes minutes. The
                // caller owns cancellation (cancel() / thread interrupt).
                finished.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                cancel();
                throw new LitertLmException(
                        LitertLmException.Kind.GENERATION_FAILED,
                        "Interrupted while waiting for the model.",
                        interrupted);
            }

            Throwable throwable = failure.get();
            if (throwable != null) {
                if (listener != null) {
                    listener.onError(throwable);
                }
                throw new LitertLmException(
                        LitertLmException.Kind.GENERATION_FAILED,
                        "Generation failed: " + throwable,
                        throwable);
            }
            String answer = accumulator.text();
            if (listener != null) {
                listener.onComplete(answer);
            }
            return answer;
        } finally {
            generationLock.unlock();
        }
    }

    /** Releases the native engine. Idempotent and safe to call from any thread. */
    @Override
    public void close() {
        Conversation activeConversation;
        Engine activeEngine;
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            closed = true;
            activeConversation = conversation;
            activeEngine = engine;
            conversation = null;
            engine = null;
        }
        try {
            if (activeConversation != null) {
                activeConversation.close();
            }
        } catch (Throwable ignored) {
            // Best effort during shutdown.
        }
        try {
            if (activeEngine != null) {
                activeEngine.close();
            }
        } catch (Throwable ignored) {
            // Best effort during shutdown.
        }
    }

    private void requireNotClosed() {
        if (closed) {
            throw new LitertLmException(
                    LitertLmException.Kind.CLOSED,
                    "This engine was closed. Create a new instance, or use LitertLmRuntime which "
                            + "reloads on demand.");
        }
    }

    private static boolean isMainThread() {
        try {
            return Looper.myLooper() != null && Looper.myLooper() == Looper.getMainLooper();
        } catch (Throwable t) {
            return false;
        }
    }

    private static Backend toSdkBackend(LitertLmConfig.Backend backend) {
        return backend == LitertLmConfig.Backend.GPU ? new Backend.GPU() : new Backend.CPU();
    }

    /**
     * Builds the conversation config explicitly. Kotlin's default-argument bridge
     * ({@code createConversation$default}) is synthetic and cannot be called from Java, and
     * {@code initialMessages}, {@code tools} and {@code extraContext} are non-null in the API.
     */
    private static ConversationConfig conversationConfig(LitertLmConfig config) {
        return new ConversationConfig(
                config.systemInstruction() == null
                        ? null
                        : com.google.ai.edge.litertlm.Contents.Companion.of(config.systemInstruction()),
                new ArrayList<Message>(),                       // initialMessages (non-null)
                new ArrayList<ToolProvider>(),                  // tools (non-null)
                new SamplerConfig(
                        config.topK(),
                        config.topP(),
                        config.temperature(),
                        config.seed()),
                true,                                           // automaticToolCalling
                new ArrayList<Channel>(),                       // channels
                new HashMap<String, Object>(),                  // extraContext (non-null)
                null,                                           // loraConfig
                true,                                           // prefillPrefaceOnInit
                config.maxOutputTokens(),                       // maxOutputToken (nullable)
                null,                                           // thinkingConfig
                false,                                          // enableResponseFormat
                null,                                           // enableSpeculativeDecoding
                null);                                          // chatTemplate
    }

    /** Extracts the text of a (possibly partial) model message. */
    private static String textOf(Message message) {
        StringBuilder text = new StringBuilder();
        try {
            List<Content> contents = message.getContents().getContents();
            if (contents != null) {
                for (Content content : contents) {
                    if (content instanceof Content.Text) {
                        text.append(((Content.Text) content).getText());
                    }
                }
            }
        } catch (Throwable ignored) {
            // A message may legitimately carry no text (e.g. a tool-call-only message).
        }
        return text.toString();
    }
}