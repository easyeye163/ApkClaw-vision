package com.apk.claw.android.floating.voice;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.util.Locale;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.Regex;
import kotlin.text.StringsKt;

/* compiled from: TtsManager.kt */
@Metadata(d1 = {"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0007\u0018\u0000 \u00142\u00020\u0001:\u0001\u0014B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003¢\u0006\u0002\u0010\u0004J\u0010\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000eH\u0016J\u0010\u0010\u000f\u001a\u00020\b2\u0006\u0010\u0010\u001a\u00020\bH\u0002J\u0006\u0010\u0011\u001a\u00020\fJ\u000e\u0010\u0012\u001a\u00020\f2\u0006\u0010\u0010\u001a\u00020\bJ\u0006\u0010\u0013\u001a\u00020\fR\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010\t\u001a\u0004\u0018\u00010\nX\u0082\u000e¢\u0006\u0002\n\u0000¨\u0006\u0015"}, d2 = {"Lcom/apk/claw/android/floating/voice/TtsManager;", "Landroid/speech/tts/TextToSpeech$OnInitListener;", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "isReady", "", "lastSpokenText", "", "tts", "Landroid/speech/tts/TextToSpeech;", "onInit", "", NotificationCompat.CATEGORY_STATUS, "", "sanitizeForTts", "text", "shutdown", "speak", "stop", "Companion", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes.dex */
public final class TtsManager implements TextToSpeech.OnInitListener {
    private static final String TAG = "TtsManager";
    private boolean isReady;
    private volatile String lastSpokenText;
    private TextToSpeech tts;

    public TtsManager(Context context) {
        Intrinsics.checkNotNullParameter(context, "context");
        this.tts = new TextToSpeech(context.getApplicationContext(), this);
        this.lastSpokenText = "";
    }

    @Override // android.speech.tts.TextToSpeech.OnInitListener
    public void onInit(int status) {
        if (status != 0) {
            Log.e(TAG, "TTS init failed: " + status);
            return;
        }
        TextToSpeech textToSpeech = this.tts;
        if (textToSpeech != null) {
            textToSpeech.setLanguage(Locale.CHINESE);
        }
        TextToSpeech textToSpeech2 = this.tts;
        if (textToSpeech2 != null) {
            textToSpeech2.setSpeechRate(0.92f);
        }
        TextToSpeech textToSpeech3 = this.tts;
        if (textToSpeech3 != null) {
            textToSpeech3.setPitch(1.0f);
        }
        this.isReady = true;
        Log.i(TAG, "TTS initialized");
    }

    public final void speak(String text) {
        Intrinsics.checkNotNullParameter(text, "text");
        if (!this.isReady) {
            Log.w(TAG, "TTS not ready");
            return;
        }
        String sanitizeForTts = sanitizeForTts(text);
        String str = sanitizeForTts;
        if (str.length() == 0 || Intrinsics.areEqual(sanitizeForTts, this.lastSpokenText)) {
            return;
        }
        this.lastSpokenText = sanitizeForTts;
        TextToSpeech textToSpeech = this.tts;
        if (textToSpeech != null) {
            textToSpeech.speak(str, 0, null, "tts_stream");
        }
    }

    public final void stop() {
        this.lastSpokenText = "";
        TextToSpeech textToSpeech = this.tts;
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    public final void shutdown() {
        TextToSpeech textToSpeech = this.tts;
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
        TextToSpeech textToSpeech2 = this.tts;
        if (textToSpeech2 != null) {
            textToSpeech2.shutdown();
        }
        this.tts = null;
        this.isReady = false;
    }

    private final String sanitizeForTts(String text) {
        return StringsKt.trim((CharSequence) new Regex("\\s+").replace(new Regex("\\{[^{}]*\"action\"[^{}]*\\}").replace(new Regex("```[\\s\\S]*?```").replace(new Regex("```json[\\s\\S]*?```").replace(text, ""), ""), ""), " ")).toString();
    }
}
