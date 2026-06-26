package com.apk.claw.android.ui.settings;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.LifecycleOwnerKt;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.apk.claw.android.R;
import com.apk.claw.android.base.BaseActivity;
import com.apk.claw.android.utils.KVUtils;
import com.apk.claw.android.widget.CommonToolbar;
import com.apk.claw.android.widget.KButton;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CancellationException;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.StringCompanionObject;
import kotlin.ranges.RangesKt;
import kotlin.text.StringsKt;
import kotlinx.coroutines.BuildersKt__Builders_commonKt;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.Job;
import okhttp3.internal.ws.RealWebSocket;

/* compiled from: LocalModelConfigActivity.kt */
@Metadata(d1 = {"\u0000p\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\n\n\u0002\u0010\t\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\t\u0018\u0000 ?2\u00020\u0001:\u0001?B\u0005¢\u0006\u0002\u0010\u0002J\u000e\u0010!\u001a\b\u0012\u0004\u0012\u00020#0\"H\u0002J\u000e\u0010$\u001a\b\u0012\u0004\u0012\u00020#0\"H\u0002J\b\u0010%\u001a\u00020&H\u0002J\b\u0010'\u001a\u00020&H\u0002J\u000e\u0010(\u001a\u00020&H\u0082@¢\u0006\u0002\u0010)J,\u0010*\u001a\u00020&2\u0006\u0010+\u001a\u00020#2\u0006\u0010,\u001a\u00020\u00112\f\u0010-\u001a\b\u0012\u0004\u0012\u00020#0\"H\u0082@¢\u0006\u0002\u0010.J\u0010\u0010/\u001a\u00020#2\u0006\u00100\u001a\u000201H\u0002J\b\u00102\u001a\u00020&H\u0002J\b\u00103\u001a\u00020&H\u0002J\b\u00104\u001a\u00020&H\u0002J\u0012\u00105\u001a\u00020&2\b\u00106\u001a\u0004\u0018\u000107H\u0014J\b\u00108\u001a\u00020&H\u0014J\b\u00109\u001a\u00020&H\u0002J\b\u0010:\u001a\u00020&H\u0002J\b\u0010;\u001a\u00020&H\u0002J\b\u0010<\u001a\u00020&H\u0002J\b\u0010=\u001a\u00020&H\u0002J\b\u0010>\u001a\u00020&H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0082.¢\u0006\u0002\n\u0000R\u0010\u0010\t\u001a\u0004\u0018\u00010\nX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\fX\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u000fX\u0082\u000e¢\u0006\u0002\n\u0000R\u0014\u0010\u0010\u001a\u00020\u00118BX\u0082\u0004¢\u0006\u0006\u001a\u0004\b\u0012\u0010\u0013R\u000e\u0010\u0014\u001a\u00020\u0015X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0016\u001a\u00020\u0017X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0018\u001a\u00020\u0017X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0019\u001a\u00020\u001aX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u001b\u001a\u00020\u001cX\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u001d\u001a\u00020\u001cX\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u001e\u001a\u00020\u001cX\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u001f\u001a\u00020\u001cX\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010 \u001a\u00020\u001cX\u0082.¢\u0006\u0002\n\u0000¨\u0006@"}, d2 = {"Lcom/apk/claw/android/ui/settings/LocalModelConfigActivity;", "Lcom/apk/claw/android/base/BaseActivity;", "()V", "btnDeleteModel", "Lcom/apk/claw/android/widget/KButton;", "btnDownload", "btnLoadModel", "btnSaveParams", "btnSaveServerConfig", "downloadJob", "Lkotlinx/coroutines/Job;", "etApiKey", "Landroid/widget/EditText;", "etBaseUrl", "isModelLoaded", "", "modelsBaseDir", "Ljava/io/File;", "getModelsBaseDir", "()Ljava/io/File;", "progressDownload", "Landroid/widget/ProgressBar;", "seekbarMaxTokens", "Landroid/widget/SeekBar;", "seekbarTemperature", "selectedModel", "Lcom/apk/claw/android/ui/settings/LocalModelInfo;", "tvDownloadProgress", "Landroid/widget/TextView;", "tvMaxTokensValue", "tvModelInfo", "tvModelStatus", "tvTemperatureValue", "buildGgufUrls", "", "", "buildMmprojUrls", "confirmDelete", "", "deleteModelFiles", "downloadFiles", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "downloadSingleFile", "fileName", "targetDir", "urls", "(Ljava/lang/String;Ljava/io/File;Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "formatFileSize", "bytes", "", "loadModel", "loadSavedParams", "loadServerConfig", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "onDestroy", "saveParams", "saveServerConfig", "setupModelList", "setupSeekBarListeners", "startDownload", "updateUI", "Companion", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes.dex */
public final class LocalModelConfigActivity extends BaseActivity {
    private static final String TAG = "LocalModelConfig";
    private KButton btnDeleteModel;
    private KButton btnDownload;
    private KButton btnLoadModel;
    private KButton btnSaveParams;
    private KButton btnSaveServerConfig;
    private Job downloadJob;
    private EditText etApiKey;
    private EditText etBaseUrl;
    private boolean isModelLoaded;
    private ProgressBar progressDownload;
    private SeekBar seekbarMaxTokens;
    private SeekBar seekbarTemperature;
    private LocalModelInfo selectedModel = LocalModelInfo.INSTANCE.getDEFAULT_MODEL();
    private TextView tvDownloadProgress;
    private TextView tvMaxTokensValue;
    private TextView tvModelInfo;
    private TextView tvModelStatus;
    private TextView tvTemperatureValue;

    /* JADX INFO: Access modifiers changed from: private */
    public final File getModelsBaseDir() {
        return new File(getFilesDir(), "local_models");
    }

    @Override // com.apk.claw.android.base.BaseActivity, androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_model_config);
        CommonToolbar commonToolbar = (CommonToolbar) findViewById(R.id.toolbar);
        commonToolbar.setTitle(getString(R.string.local_model_config_title));
        commonToolbar.showBackButton(true, new Function0<Unit>() { // from class: com.apk.claw.android.ui.settings.LocalModelConfigActivity$onCreate$1$1
            {
                super(0);
            }

            @Override // kotlin.jvm.functions.Function0
            public /* bridge */ /* synthetic */ Unit invoke() {
                invoke2();
                return Unit.INSTANCE;
            }

            /* renamed from: invoke, reason: avoid collision after fix types in other method */
            public final void invoke2() {
                LocalModelConfigActivity.this.finish();
            }
        });
        View findViewById = findViewById(R.id.tv_model_status);
        Intrinsics.checkNotNullExpressionValue(findViewById, "findViewById(...)");
        this.tvModelStatus = (TextView) findViewById;
        View findViewById2 = findViewById(R.id.tv_model_info);
        Intrinsics.checkNotNullExpressionValue(findViewById2, "findViewById(...)");
        this.tvModelInfo = (TextView) findViewById2;
        View findViewById3 = findViewById(R.id.progress_download);
        Intrinsics.checkNotNullExpressionValue(findViewById3, "findViewById(...)");
        this.progressDownload = (ProgressBar) findViewById3;
        View findViewById4 = findViewById(R.id.tv_download_progress);
        Intrinsics.checkNotNullExpressionValue(findViewById4, "findViewById(...)");
        this.tvDownloadProgress = (TextView) findViewById4;
        View findViewById5 = findViewById(R.id.btn_download);
        Intrinsics.checkNotNullExpressionValue(findViewById5, "findViewById(...)");
        this.btnDownload = (KButton) findViewById5;
        View findViewById6 = findViewById(R.id.btn_load_model);
        Intrinsics.checkNotNullExpressionValue(findViewById6, "findViewById(...)");
        this.btnLoadModel = (KButton) findViewById6;
        View findViewById7 = findViewById(R.id.btn_delete_model);
        Intrinsics.checkNotNullExpressionValue(findViewById7, "findViewById(...)");
        this.btnDeleteModel = (KButton) findViewById7;
        View findViewById8 = findViewById(R.id.btn_save_params);
        Intrinsics.checkNotNullExpressionValue(findViewById8, "findViewById(...)");
        this.btnSaveParams = (KButton) findViewById8;
        View findViewById9 = findViewById(R.id.btn_save_server_config);
        Intrinsics.checkNotNullExpressionValue(findViewById9, "findViewById(...)");
        this.btnSaveServerConfig = (KButton) findViewById9;
        View findViewById10 = findViewById(R.id.et_base_url);
        Intrinsics.checkNotNullExpressionValue(findViewById10, "findViewById(...)");
        this.etBaseUrl = (EditText) findViewById10;
        View findViewById11 = findViewById(R.id.et_api_key);
        Intrinsics.checkNotNullExpressionValue(findViewById11, "findViewById(...)");
        this.etApiKey = (EditText) findViewById11;
        View findViewById12 = findViewById(R.id.seekbar_temperature);
        Intrinsics.checkNotNullExpressionValue(findViewById12, "findViewById(...)");
        this.seekbarTemperature = (SeekBar) findViewById12;
        View findViewById13 = findViewById(R.id.seekbar_max_tokens);
        Intrinsics.checkNotNullExpressionValue(findViewById13, "findViewById(...)");
        this.seekbarMaxTokens = (SeekBar) findViewById13;
        View findViewById14 = findViewById(R.id.tv_temperature_value);
        Intrinsics.checkNotNullExpressionValue(findViewById14, "findViewById(...)");
        this.tvTemperatureValue = (TextView) findViewById14;
        View findViewById15 = findViewById(R.id.tv_max_tokens_value);
        Intrinsics.checkNotNullExpressionValue(findViewById15, "findViewById(...)");
        this.tvMaxTokensValue = (TextView) findViewById15;
        setupModelList();
        setupSeekBarListeners();
        loadSavedParams();
        loadServerConfig();
        updateUI();
        KButton kButton = this.btnDownload;
        KButton kButton2 = null;
        if (kButton == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnDownload");
            kButton = null;
        }
        kButton.setOnClickListener(new View.OnClickListener() { // from class: com.apk.claw.android.ui.settings.LocalModelConfigActivity$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LocalModelConfigActivity.onCreate$lambda$1(LocalModelConfigActivity.this, view);
            }
        });
        KButton kButton3 = this.btnLoadModel;
        if (kButton3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnLoadModel");
            kButton3 = null;
        }
        kButton3.setOnClickListener(new View.OnClickListener() { // from class: com.apk.claw.android.ui.settings.LocalModelConfigActivity$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LocalModelConfigActivity.onCreate$lambda$2(LocalModelConfigActivity.this, view);
            }
        });
        KButton kButton4 = this.btnDeleteModel;
        if (kButton4 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnDeleteModel");
            kButton4 = null;
        }
        kButton4.setOnClickListener(new View.OnClickListener() { // from class: com.apk.claw.android.ui.settings.LocalModelConfigActivity$$ExternalSyntheticLambda3
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LocalModelConfigActivity.onCreate$lambda$3(LocalModelConfigActivity.this, view);
            }
        });
        KButton kButton5 = this.btnSaveParams;
        if (kButton5 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnSaveParams");
            kButton5 = null;
        }
        kButton5.setOnClickListener(new View.OnClickListener() { // from class: com.apk.claw.android.ui.settings.LocalModelConfigActivity$$ExternalSyntheticLambda4
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LocalModelConfigActivity.onCreate$lambda$4(LocalModelConfigActivity.this, view);
            }
        });
        KButton kButton6 = this.btnSaveServerConfig;
        if (kButton6 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnSaveServerConfig");
        } else {
            kButton2 = kButton6;
        }
        kButton2.setOnClickListener(new View.OnClickListener() { // from class: com.apk.claw.android.ui.settings.LocalModelConfigActivity$$ExternalSyntheticLambda5
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LocalModelConfigActivity.onCreate$lambda$5(LocalModelConfigActivity.this, view);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void onCreate$lambda$1(LocalModelConfigActivity this$0, View view) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        this$0.startDownload();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void onCreate$lambda$2(LocalModelConfigActivity this$0, View view) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        this$0.loadModel();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void onCreate$lambda$3(LocalModelConfigActivity this$0, View view) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        this$0.confirmDelete();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void onCreate$lambda$4(LocalModelConfigActivity this$0, View view) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        this$0.saveParams();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void onCreate$lambda$5(LocalModelConfigActivity this$0, View view) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        this$0.saveServerConfig();
    }

    private final void setupModelList() {
        Object obj;
        String localModelId = KVUtils.INSTANCE.getLocalModelId();
        Iterator<T> it = LocalModelInfo.INSTANCE.getAVAILABLE_MODELS().iterator();
        while (true) {
            if (!it.hasNext()) {
                obj = null;
                break;
            } else {
                obj = it.next();
                if (Intrinsics.areEqual(((LocalModelInfo) obj).getId(), localModelId)) {
                    break;
                }
            }
        }
        LocalModelInfo localModelInfo = (LocalModelInfo) obj;
        if (localModelInfo == null) {
            localModelInfo = LocalModelInfo.INSTANCE.getDEFAULT_MODEL();
        }
        this.selectedModel = localModelInfo;
        LocalModelAdapter localModelAdapter = new LocalModelAdapter(LocalModelInfo.INSTANCE.getAVAILABLE_MODELS(), localModelInfo.getId(), new Function1<LocalModelInfo, Unit>() { // from class: com.apk.claw.android.ui.settings.LocalModelConfigActivity$setupModelList$adapter$1
            {
                super(1);
            }

            @Override // kotlin.jvm.functions.Function1
            public /* bridge */ /* synthetic */ Unit invoke(LocalModelInfo localModelInfo2) {
                invoke2(localModelInfo2);
                return Unit.INSTANCE;
            }

            /* renamed from: invoke, reason: avoid collision after fix types in other method */
            public final void invoke2(LocalModelInfo m) {
                Intrinsics.checkNotNullParameter(m, "m");
                LocalModelConfigActivity.this.selectedModel = m;
                KVUtils.INSTANCE.setLocalModelId(m.getId());
                LocalModelConfigActivity.this.updateUI();
                Toast.makeText(LocalModelConfigActivity.this, "已选择: " + m.getDisplayName(), 0).show();
            }
        });
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_models);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(localModelAdapter);
    }

    private final void setupSeekBarListeners() {
        SeekBar seekBar = this.seekbarTemperature;
        SeekBar seekBar2 = null;
        if (seekBar == null) {
            Intrinsics.throwUninitializedPropertyAccessException("seekbarTemperature");
            seekBar = null;
        }
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: com.apk.claw.android.ui.settings.LocalModelConfigActivity$setupSeekBarListeners$1
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar seekBar3) {
                Intrinsics.checkNotNullParameter(seekBar3, "seekBar");
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar seekBar3) {
                Intrinsics.checkNotNullParameter(seekBar3, "seekBar");
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar seekBar3, int progress, boolean fromUser) {
                TextView textView;
                Intrinsics.checkNotNullParameter(seekBar3, "seekBar");
                double d = progress / 100.0d;
                textView = LocalModelConfigActivity.this.tvTemperatureValue;
                if (textView == null) {
                    Intrinsics.throwUninitializedPropertyAccessException("tvTemperatureValue");
                    textView = null;
                }
                StringCompanionObject stringCompanionObject = StringCompanionObject.INSTANCE;
                String format = String.format("%.2f", Arrays.copyOf(new Object[]{Double.valueOf(d)}, 1));
                Intrinsics.checkNotNullExpressionValue(format, "format(...)");
                textView.setText(format);
            }
        });
        SeekBar seekBar3 = this.seekbarMaxTokens;
        if (seekBar3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("seekbarMaxTokens");
        } else {
            seekBar2 = seekBar3;
        }
        seekBar2.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: com.apk.claw.android.ui.settings.LocalModelConfigActivity$setupSeekBarListeners$2
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar seekBar4) {
                Intrinsics.checkNotNullParameter(seekBar4, "seekBar");
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar seekBar4) {
                Intrinsics.checkNotNullParameter(seekBar4, "seekBar");
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar seekBar4, int progress, boolean fromUser) {
                TextView textView;
                Intrinsics.checkNotNullParameter(seekBar4, "seekBar");
                textView = LocalModelConfigActivity.this.tvMaxTokensValue;
                if (textView == null) {
                    Intrinsics.throwUninitializedPropertyAccessException("tvMaxTokensValue");
                    textView = null;
                }
                textView.setText(String.valueOf(progress));
            }
        });
    }

    private final void loadSavedParams() {
        double localModelTemperature = KVUtils.INSTANCE.getLocalModelTemperature();
        int localModelMaxTokens = KVUtils.INSTANCE.getLocalModelMaxTokens();
        SeekBar seekBar = this.seekbarTemperature;
        TextView textView = null;
        if (seekBar == null) {
            Intrinsics.throwUninitializedPropertyAccessException("seekbarTemperature");
            seekBar = null;
        }
        seekBar.setProgress(RangesKt.coerceIn((int) (100 * localModelTemperature), 0, 200));
        SeekBar seekBar2 = this.seekbarMaxTokens;
        if (seekBar2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("seekbarMaxTokens");
            seekBar2 = null;
        }
        seekBar2.setProgress(RangesKt.coerceIn(localModelMaxTokens, 1, 4096));
        TextView textView2 = this.tvTemperatureValue;
        if (textView2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("tvTemperatureValue");
            textView2 = null;
        }
        StringCompanionObject stringCompanionObject = StringCompanionObject.INSTANCE;
        String format = String.format("%.2f", Arrays.copyOf(new Object[]{Double.valueOf(localModelTemperature)}, 1));
        Intrinsics.checkNotNullExpressionValue(format, "format(...)");
        textView2.setText(format);
        TextView textView3 = this.tvMaxTokensValue;
        if (textView3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("tvMaxTokensValue");
        } else {
            textView = textView3;
        }
        textView.setText(String.valueOf(localModelMaxTokens));
    }

    private final void saveParams() {
        SeekBar seekBar = this.seekbarTemperature;
        SeekBar seekBar2 = null;
        if (seekBar == null) {
            Intrinsics.throwUninitializedPropertyAccessException("seekbarTemperature");
            seekBar = null;
        }
        double progress = seekBar.getProgress() / 100.0d;
        SeekBar seekBar3 = this.seekbarMaxTokens;
        if (seekBar3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("seekbarMaxTokens");
        } else {
            seekBar2 = seekBar3;
        }
        int progress2 = seekBar2.getProgress();
        KVUtils.INSTANCE.setLocalModelTemperature(progress);
        KVUtils.INSTANCE.setLocalModelMaxTokens(progress2);
        Toast.makeText(this, getString(R.string.local_model_params_saved), 0).show();
    }

    private final void loadServerConfig() {
        EditText editText = this.etBaseUrl;
        EditText editText2 = null;
        if (editText == null) {
            Intrinsics.throwUninitializedPropertyAccessException("etBaseUrl");
            editText = null;
        }
        editText.setText(KVUtils.INSTANCE.getLocalModelBaseUrl());
        EditText editText3 = this.etApiKey;
        if (editText3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("etApiKey");
        } else {
            editText2 = editText3;
        }
        editText2.setText(KVUtils.INSTANCE.getLocalModelApiKey());
    }

    private final void saveServerConfig() {
        EditText editText = this.etBaseUrl;
        EditText editText2 = null;
        if (editText == null) {
            Intrinsics.throwUninitializedPropertyAccessException("etBaseUrl");
            editText = null;
        }
        String obj = StringsKt.trim((CharSequence) editText.getText().toString()).toString();
        EditText editText3 = this.etApiKey;
        if (editText3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("etApiKey");
        } else {
            editText2 = editText3;
        }
        String obj2 = StringsKt.trim((CharSequence) editText2.getText().toString()).toString();
        if (obj.length() > 0) {
            KVUtils.INSTANCE.setLocalModelBaseUrl(obj);
        }
        if (obj2.length() > 0) {
            KVUtils.INSTANCE.setLocalModelApiKey(obj2);
        }
        Toast.makeText(this, getString(R.string.local_model_server_config_saved), 0).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void updateUI() {
        String string;
        String string2;
        boolean isDownloaded = this.selectedModel.isDownloaded(getModelsBaseDir());
        long downloadedSize = this.selectedModel.downloadedSize(getModelsBaseDir());
        KButton kButton = null;
        if (this.isModelLoaded) {
            TextView textView = this.tvModelStatus;
            if (textView == null) {
                Intrinsics.throwUninitializedPropertyAccessException("tvModelStatus");
                textView = null;
            }
            textView.setText(getString(R.string.local_model_status_ready));
            TextView textView2 = this.tvModelStatus;
            if (textView2 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("tvModelStatus");
                textView2 = null;
            }
            textView2.setTextColor(getColor(R.color.colorTextSecondary));
        } else if (isDownloaded) {
            TextView textView3 = this.tvModelStatus;
            if (textView3 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("tvModelStatus");
                textView3 = null;
            }
            textView3.setText(getString(R.string.local_model_status_downloaded));
            TextView textView4 = this.tvModelStatus;
            if (textView4 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("tvModelStatus");
                textView4 = null;
            }
            textView4.setTextColor(getColor(R.color.colorTextSecondary));
        } else {
            TextView textView5 = this.tvModelStatus;
            if (textView5 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("tvModelStatus");
                textView5 = null;
            }
            textView5.setText(getString(R.string.local_model_status_not_ready));
            TextView textView6 = this.tvModelStatus;
            if (textView6 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("tvModelStatus");
                textView6 = null;
            }
            textView6.setTextColor(getColor(R.color.colorTextSecondary));
        }
        if (isDownloaded) {
            TextView textView7 = this.tvModelInfo;
            if (textView7 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("tvModelInfo");
                textView7 = null;
            }
            textView7.setVisibility(0);
            TextView textView8 = this.tvModelInfo;
            if (textView8 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("tvModelInfo");
                textView8 = null;
            }
            StringCompanionObject stringCompanionObject = StringCompanionObject.INSTANCE;
            String format = String.format("%s\n%s: %s", Arrays.copyOf(new Object[]{this.selectedModel.getGgufFileName(), getString(R.string.local_model_file_size), formatFileSize(downloadedSize)}, 3));
            Intrinsics.checkNotNullExpressionValue(format, "format(...)");
            textView8.setText(format);
            KButton kButton2 = this.btnDeleteModel;
            if (kButton2 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("btnDeleteModel");
                kButton2 = null;
            }
            kButton2.setVisibility(0);
        } else {
            TextView textView9 = this.tvModelInfo;
            if (textView9 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("tvModelInfo");
                textView9 = null;
            }
            textView9.setVisibility(8);
            KButton kButton3 = this.btnDeleteModel;
            if (kButton3 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("btnDeleteModel");
                kButton3 = null;
            }
            kButton3.setVisibility(8);
        }
        KButton kButton4 = this.btnDownload;
        if (kButton4 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnDownload");
            kButton4 = null;
        }
        if (isDownloaded) {
            string = getString(R.string.local_model_redownload);
        } else {
            string = getString(R.string.local_model_download);
        }
        kButton4.setText(string);
        KButton kButton5 = this.btnLoadModel;
        if (kButton5 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnLoadModel");
            kButton5 = null;
        }
        kButton5.setEnabled(isDownloaded);
        KButton kButton6 = this.btnLoadModel;
        if (kButton6 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnLoadModel");
        } else {
            kButton = kButton6;
        }
        if (this.isModelLoaded) {
            string2 = getString(R.string.local_model_reload);
        } else {
            string2 = getString(R.string.local_model_load);
        }
        kButton.setText(string2);
    }

    private final void startDownload() {
        Job launch$default;
        Job job = this.downloadJob;
        if (job != null && job.isActive()) {
            Toast.makeText(this, "正在下载中...", 0).show();
            return;
        }
        File modelDir = this.selectedModel.modelDir(getModelsBaseDir());
        if (!modelDir.exists()) {
            modelDir.mkdirs();
        }
        KButton kButton = this.btnDownload;
        if (kButton == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnDownload");
            kButton = null;
        }
        kButton.setEnabled(false);
        KButton kButton2 = this.btnLoadModel;
        if (kButton2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnLoadModel");
            kButton2 = null;
        }
        kButton2.setEnabled(false);
        ProgressBar progressBar = this.progressDownload;
        if (progressBar == null) {
            Intrinsics.throwUninitializedPropertyAccessException("progressDownload");
            progressBar = null;
        }
        progressBar.setVisibility(0);
        TextView textView = this.tvDownloadProgress;
        if (textView == null) {
            Intrinsics.throwUninitializedPropertyAccessException("tvDownloadProgress");
            textView = null;
        }
        textView.setVisibility(0);
        TextView textView2 = this.tvDownloadProgress;
        if (textView2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("tvDownloadProgress");
            textView2 = null;
        }
        textView2.setText("准备下载...");
        TextView textView3 = this.tvModelStatus;
        if (textView3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("tvModelStatus");
            textView3 = null;
        }
        textView3.setText("正在下载...");
        launch$default = BuildersKt__Builders_commonKt.launch$default(LifecycleOwnerKt.getLifecycleScope(this), Dispatchers.getIO(), null, new LocalModelConfigActivity$startDownload$1(this, null), 2, null);
        this.downloadJob = launch$default;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Removed duplicated region for block: B:23:0x0096 A[RETURN] */
    /* JADX WARN: Removed duplicated region for block: B:24:0x003d  */
    /* JADX WARN: Removed duplicated region for block: B:8:0x0025  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public final java.lang.Object downloadFiles(kotlin.coroutines.Continuation<? super kotlin.Unit> r8) {
        /*
            r7 = this;
            boolean r0 = r8 instanceof com.apk.claw.android.ui.settings.LocalModelConfigActivity$downloadFiles$1
            if (r0 == 0) goto L14
            r0 = r8
            com.apk.claw.android.ui.settings.LocalModelConfigActivity$downloadFiles$1 r0 = (com.apk.claw.android.ui.settings.LocalModelConfigActivity$downloadFiles$1) r0
            int r1 = r0.label
            r2 = -2147483648(0xffffffff80000000, float:-0.0)
            r1 = r1 & r2
            if (r1 == 0) goto L14
            int r8 = r0.label
            int r8 = r8 - r2
            r0.label = r8
            goto L19
        L14:
            com.apk.claw.android.ui.settings.LocalModelConfigActivity$downloadFiles$1 r0 = new com.apk.claw.android.ui.settings.LocalModelConfigActivity$downloadFiles$1
            r0.<init>(r7, r8)
        L19:
            java.lang.Object r8 = r0.result
            java.lang.Object r1 = kotlin.coroutines.intrinsics.IntrinsicsKt.getCOROUTINE_SUSPENDED()
            int r2 = r0.label
            r3 = 2
            r4 = 1
            if (r2 == 0) goto L3d
            if (r2 == r4) goto L35
            if (r2 != r3) goto L2d
            kotlin.ResultKt.throwOnFailure(r8)
            goto L97
        L2d:
            java.lang.IllegalStateException r8 = new java.lang.IllegalStateException
            java.lang.String r0 = "call to 'resume' before 'invoke' with coroutine"
            r8.<init>(r0)
            throw r8
        L35:
            java.lang.Object r2 = r0.L$0
            com.apk.claw.android.ui.settings.LocalModelConfigActivity r2 = (com.apk.claw.android.ui.settings.LocalModelConfigActivity) r2
            kotlin.ResultKt.throwOnFailure(r8)
            goto L60
        L3d:
            kotlin.ResultKt.throwOnFailure(r8)
            com.apk.claw.android.ui.settings.LocalModelInfo r8 = r7.selectedModel
            java.lang.String r8 = r8.getGgufFileName()
            com.apk.claw.android.ui.settings.LocalModelInfo r2 = r7.selectedModel
            java.io.File r5 = r7.getModelsBaseDir()
            java.io.File r2 = r2.modelDir(r5)
            java.util.List r5 = r7.buildGgufUrls()
            r0.L$0 = r7
            r0.label = r4
            java.lang.Object r8 = r7.downloadSingleFile(r8, r2, r5, r0)
            if (r8 != r1) goto L5f
            return r1
        L5f:
            r2 = r7
        L60:
            com.apk.claw.android.ui.settings.LocalModelInfo r8 = r2.selectedModel
            java.lang.String r8 = r8.getMmprojFileName()
            if (r8 == 0) goto L97
            java.io.File r4 = new java.io.File
            com.apk.claw.android.ui.settings.LocalModelInfo r5 = r2.selectedModel
            java.io.File r6 = r2.getModelsBaseDir()
            java.io.File r5 = r5.modelDir(r6)
            r4.<init>(r5, r8)
            boolean r4 = r4.exists()
            if (r4 != 0) goto L97
            com.apk.claw.android.ui.settings.LocalModelInfo r4 = r2.selectedModel
            java.io.File r5 = r2.getModelsBaseDir()
            java.io.File r4 = r4.modelDir(r5)
            java.util.List r5 = r2.buildMmprojUrls()
            r6 = 0
            r0.L$0 = r6
            r0.label = r3
            java.lang.Object r8 = r2.downloadSingleFile(r8, r4, r5, r0)
            if (r8 != r1) goto L97
            return r1
        L97:
            kotlin.Unit r8 = kotlin.Unit.INSTANCE
            return r8
        */
        throw new UnsupportedOperationException("Method not decompiled: com.apk.claw.android.ui.settings.LocalModelConfigActivity.downloadFiles(kotlin.coroutines.Continuation):java.lang.Object");
    }

    private final List<String> buildGgufUrls() {
        ArrayList arrayList = new ArrayList();
        String directGgufUrl = this.selectedModel.getDirectGgufUrl();
        if (directGgufUrl != null) {
            arrayList.add(directGgufUrl);
        }
        String hfRepo = this.selectedModel.getHfRepo();
        if (hfRepo != null) {
            arrayList.add("https://huggingface.co/" + hfRepo + "/resolve/main/" + this.selectedModel.getGgufFileName());
        }
        String msRepo = this.selectedModel.getMsRepo();
        if (msRepo != null) {
            arrayList.add("https://modelscope.cn/models/" + msRepo + "/resolve/master/" + this.selectedModel.getGgufFileName());
        }
        return arrayList;
    }

    private final List<String> buildMmprojUrls() {
        String mmprojFileName = this.selectedModel.getMmprojFileName();
        if (mmprojFileName == null) {
            return CollectionsKt.emptyList();
        }
        ArrayList arrayList = new ArrayList();
        String directMmprojUrl = this.selectedModel.getDirectMmprojUrl();
        if (directMmprojUrl != null) {
            arrayList.add(directMmprojUrl);
        }
        String hfRepo = this.selectedModel.getHfRepo();
        if (hfRepo != null) {
            arrayList.add("https://huggingface.co/" + hfRepo + "/resolve/main/" + mmprojFileName);
        }
        String msRepo = this.selectedModel.getMsRepo();
        if (msRepo != null) {
            arrayList.add("https://modelscope.cn/models/" + msRepo + "/resolve/master/" + mmprojFileName);
        }
        return arrayList;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Code restructure failed: missing block: B:166:0x03b9, code lost:
    
        r5 = r40;
        r6 = r41;
        r23 = r1;
        r24 = r9;
        r2 = r20;
        r11 = r21;
        r7 = r22;
        r26 = r31;
        r22 = r8;
     */
    /* JADX WARN: Code restructure failed: missing block: B:168:0x03ce, code lost:
    
        r0 = kotlin.Unit.INSTANCE;
     */
    /* JADX WARN: Code restructure failed: missing block: B:171:0x03d2, code lost:
    
        kotlin.io.CloseableKt.closeFinally(r7, r33);
        r0 = kotlin.Unit.INSTANCE;
     */
    /* JADX WARN: Code restructure failed: missing block: B:174:0x03d9, code lost:
    
        kotlin.io.CloseableKt.closeFinally(r6, r34);
        r10.renameTo(r12);
        r0 = kotlinx.coroutines.Dispatchers.getMain();
     */
    /* JADX WARN: Code restructure failed: missing block: B:178:0x03eb, code lost:
    
        r1 = new com.apk.claw.android.ui.settings.LocalModelConfigActivity$downloadSingleFile$4(r5, r14, null);
        r4.L$0 = r5;
        r4.L$1 = r14;
        r4.L$2 = r12;
        r4.L$3 = r10;
     */
    /* JADX WARN: Code restructure failed: missing block: B:181:0x03f7, code lost:
    
        r4.L$4 = r24;
     */
    /* JADX WARN: Code restructure failed: missing block: B:182:0x03f9, code lost:
    
        r13 = r22;
     */
    /* JADX WARN: Code restructure failed: missing block: B:184:0x03fb, code lost:
    
        r4.L$5 = r13;
        r4.L$6 = r3;
        r4.L$7 = r23;
     */
    /* JADX WARN: Code restructure failed: missing block: B:185:0x0403, code lost:
    
        r15 = null;
     */
    /* JADX WARN: Code restructure failed: missing block: B:187:0x0404, code lost:
    
        r4.L$8 = null;
        r4.L$9 = null;
        r4.L$10 = null;
        r4.L$11 = null;
        r4.L$12 = null;
        r4.L$13 = null;
        r4.L$14 = null;
     */
    /* JADX WARN: Code restructure failed: missing block: B:188:0x0412, code lost:
    
        r24 = r24;
        r8 = r26;
     */
    /* JADX WARN: Code restructure failed: missing block: B:190:0x0416, code lost:
    
        r4.J$0 = r8;
        r4.I$0 = r11;
     */
    /* JADX WARN: Code restructure failed: missing block: B:191:0x041a, code lost:
    
        r6 = 3;
     */
    /* JADX WARN: Code restructure failed: missing block: B:193:0x041b, code lost:
    
        r4.label = 3;
     */
    /* JADX WARN: Code restructure failed: missing block: B:194:0x0421, code lost:
    
        if (kotlinx.coroutines.BuildersKt.withContext(r0, r1, r4) != r2) goto L155;
     */
    /* JADX WARN: Code restructure failed: missing block: B:195:0x0423, code lost:
    
        return r2;
     */
    /* JADX WARN: Code restructure failed: missing block: B:196:0x0424, code lost:
    
        r0 = r23;
        r19 = r8;
        r9 = r10;
        r10 = r12;
        r7 = r13;
        r8 = r24;
        r12 = r5;
     */
    /* JADX WARN: Code restructure failed: missing block: B:198:0x043f, code lost:
    
        r0 = e;
     */
    /* JADX WARN: Code restructure failed: missing block: B:199:0x045e, code lost:
    
        r1 = r2;
        r2 = r11;
        r7 = r13;
        r11 = r14;
        r6 = r3;
        r37 = r12;
        r12 = r5;
        r14 = r8;
        r9 = r10;
        r10 = r37;
        r8 = r24;
     */
    /* JADX WARN: Code restructure failed: missing block: B:201:0x0441, code lost:
    
        r0 = e;
     */
    /* JADX WARN: Code restructure failed: missing block: B:202:0x0455, code lost:
    
        r6 = 3;
     */
    /* JADX WARN: Code restructure failed: missing block: B:204:0x0443, code lost:
    
        r0 = e;
     */
    /* JADX WARN: Code restructure failed: missing block: B:205:0x0444, code lost:
    
        r24 = r24;
     */
    /* JADX WARN: Code restructure failed: missing block: B:206:0x0453, code lost:
    
        r8 = r26;
     */
    /* JADX WARN: Code restructure failed: missing block: B:208:0x0447, code lost:
    
        r0 = e;
     */
    /* JADX WARN: Code restructure failed: missing block: B:209:0x0448, code lost:
    
        r24 = r24;
     */
    /* JADX WARN: Code restructure failed: missing block: B:210:0x045a, code lost:
    
        r8 = r26;
        r6 = 3;
        r15 = null;
     */
    /* JADX WARN: Code restructure failed: missing block: B:212:0x044b, code lost:
    
        r0 = e;
     */
    /* JADX WARN: Code restructure failed: missing block: B:213:0x044c, code lost:
    
        r24 = r24;
     */
    /* JADX WARN: Code restructure failed: missing block: B:214:0x0458, code lost:
    
        r13 = r22;
     */
    /* JADX WARN: Code restructure failed: missing block: B:216:0x044f, code lost:
    
        r0 = e;
     */
    /* JADX WARN: Code restructure failed: missing block: B:217:0x0450, code lost:
    
        r15 = null;
        r13 = r22;
     */
    /* JADX WARN: Code restructure failed: missing block: B:219:0x0457, code lost:
    
        r0 = e;
     */
    /* JADX WARN: Code restructure failed: missing block: B:221:0x0471, code lost:
    
        r0 = move-exception;
     */
    /* JADX WARN: Code restructure failed: missing block: B:222:0x0472, code lost:
    
        r1 = r0;
        r7 = r22;
        r25 = r14;
        r13 = r6;
        r6 = r3;
        r37 = r12;
        r12 = r5;
        r14 = r26;
        r9 = r10;
        r10 = r37;
        r8 = r24;
     */
    /* JADX WARN: Code restructure failed: missing block: B:224:0x048b, code lost:
    
        r0 = th;
     */
    /* JADX WARN: Code restructure failed: missing block: B:225:0x048c, code lost:
    
        r13 = r22;
        r8 = r26;
        r15 = null;
     */
    /* JADX WARN: Code restructure failed: missing block: B:226:0x04a5, code lost:
    
        r1 = r0;
        r20 = r3;
        r26 = r5;
        r23 = r10;
        r21 = r13;
        r25 = r14;
        r22 = r24;
        r13 = r6;
        r14 = r7;
        r24 = r12;
     */
    /* JADX WARN: Removed duplicated region for block: B:165:0x03b9 A[EDGE_INSN: B:165:0x03b9->B:166:0x03b9 BREAK  A[LOOP:0: B:59:0x0260->B:72:0x0260], SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:250:0x0545  */
    /* JADX WARN: Removed duplicated region for block: B:26:0x0196  */
    /* JADX WARN: Removed duplicated region for block: B:270:0x011d  */
    /* JADX WARN: Removed duplicated region for block: B:63:0x026b A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:8:0x002e  */
    /* JADX WARN: Unsupported multi-entry loop pattern (BACK_EDGE: B:128:0x04ff -> B:22:0x0514). Please report as a decompilation issue!!! */
    /* JADX WARN: Unsupported multi-entry loop pattern (BACK_EDGE: B:230:0x0505 -> B:22:0x0514). Please report as a decompilation issue!!! */
    /* JADX WARN: Unsupported multi-entry loop pattern (BACK_EDGE: B:238:0x0069 -> B:22:0x0514). Please report as a decompilation issue!!! */
    /* JADX WARN: Unsupported multi-entry loop pattern (BACK_EDGE: B:91:0x0424 -> B:15:0x042d). Please report as a decompilation issue!!! */
    /* JADX WARN: Unsupported multi-entry loop pattern (BACK_EDGE: B:94:0x045e -> B:22:0x0514). Please report as a decompilation issue!!! */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public final java.lang.Object downloadSingleFile(java.lang.String r40, java.io.File r41, java.util.List<java.lang.String> r42, kotlin.coroutines.Continuation<? super kotlin.Unit> r43) {
        /*
            Method dump skipped, instructions count: 1397
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.apk.claw.android.ui.settings.LocalModelConfigActivity.downloadSingleFile(java.lang.String, java.io.File, java.util.List, kotlin.coroutines.Continuation):java.lang.Object");
    }

    private final void loadModel() {
        if (!this.selectedModel.isDownloaded(getModelsBaseDir())) {
            Toast.makeText(this, getString(R.string.local_model_please_download), 1).show();
            return;
        }
        KButton kButton = this.btnLoadModel;
        if (kButton == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnLoadModel");
            kButton = null;
        }
        kButton.setEnabled(false);
        TextView textView = this.tvModelStatus;
        if (textView == null) {
            Intrinsics.throwUninitializedPropertyAccessException("tvModelStatus");
            textView = null;
        }
        textView.setText("正在加载模型...");
        TextView textView2 = this.tvModelStatus;
        if (textView2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("tvModelStatus");
            textView2 = null;
        }
        textView2.setTextColor(getColor(R.color.colorTextSecondary));
        BuildersKt__Builders_commonKt.launch$default(LifecycleOwnerKt.getLifecycleScope(this), null, null, new LocalModelConfigActivity$loadModel$1(this, null), 3, null);
    }

    private final void confirmDelete() {
        new AlertDialog.Builder(this).setTitle(getString(R.string.local_model_delete_confirm_title)).setMessage(getString(R.string.local_model_delete_confirm_msg, new Object[]{this.selectedModel.getDisplayName()})).setPositiveButton(getString(R.string.common_confirm), new DialogInterface.OnClickListener() { // from class: com.apk.claw.android.ui.settings.LocalModelConfigActivity$$ExternalSyntheticLambda0
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                LocalModelConfigActivity.confirmDelete$lambda$19(LocalModelConfigActivity.this, dialogInterface, i);
            }
        }).setNegativeButton(getString(R.string.common_cancel), (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void confirmDelete$lambda$19(LocalModelConfigActivity this$0, DialogInterface dialogInterface, int i) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        this$0.deleteModelFiles();
    }

    private final void deleteModelFiles() {
        BuildersKt__Builders_commonKt.launch$default(LifecycleOwnerKt.getLifecycleScope(this), Dispatchers.getIO(), null, new LocalModelConfigActivity$deleteModelFiles$1(this, null), 2, null);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final String formatFileSize(long bytes) {
        if (bytes < RealWebSocket.DEFAULT_MINIMUM_DEFLATE_SIZE) {
            return bytes + " B";
        }
        if (bytes < 1048576) {
            StringCompanionObject stringCompanionObject = StringCompanionObject.INSTANCE;
            String format = String.format("%.1f KB", Arrays.copyOf(new Object[]{Double.valueOf(bytes / 1024.0d)}, 1));
            Intrinsics.checkNotNullExpressionValue(format, "format(...)");
            return format;
        }
        if (bytes < 1073741824) {
            StringCompanionObject stringCompanionObject2 = StringCompanionObject.INSTANCE;
            String format2 = String.format("%.1f MB", Arrays.copyOf(new Object[]{Double.valueOf(bytes / 1048576.0d)}, 1));
            Intrinsics.checkNotNullExpressionValue(format2, "format(...)");
            return format2;
        }
        StringCompanionObject stringCompanionObject3 = StringCompanionObject.INSTANCE;
        String format3 = String.format("%.2f GB", Arrays.copyOf(new Object[]{Double.valueOf(bytes / 1.073741824E9d)}, 1));
        Intrinsics.checkNotNullExpressionValue(format3, "format(...)");
        return format3;
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onDestroy() {
        super.onDestroy();
        Job job = this.downloadJob;
        if (job != null) {
            Job.DefaultImpls.cancel$default(job, (CancellationException) null, 1, (Object) null);
        }
    }
}
