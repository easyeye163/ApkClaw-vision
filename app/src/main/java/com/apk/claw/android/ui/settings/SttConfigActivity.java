package com.apk.claw.android.ui.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.apk.claw.android.R;
import com.apk.claw.android.base.BaseActivity;
import com.apk.claw.android.utils.KVUtils;
import com.apk.claw.android.voice.HttpSttVoiceRecognizer;
import com.apk.claw.android.widget.CommonToolbar;
import com.apk.claw.android.widget.KButton;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lark.oapi.ws.Constant;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;

/* compiled from: SttConfigActivity.kt */
@Metadata(d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u0005¢\u0006\u0002\u0010\u0002J\b\u0010\u0005\u001a\u00020\u0006H\u0002J\b\u0010\u0007\u001a\u00020\u0006H\u0002J\u0012\u0010\b\u001a\u00020\u00062\b\u0010\t\u001a\u0004\u0018\u00010\nH\u0014J\b\u0010\u000b\u001a\u00020\u0006H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u0004¢\u0006\u0002\n\u0000¨\u0006\f"}, d2 = {"Lcom/apk/claw/android/ui/settings/SttConfigActivity;", "Lcom/apk/claw/android/base/BaseActivity;", "()V", "gson", "Lcom/google/gson/Gson;", "exportToClipboard", "", "importFromClipboard", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "showImportExportMenu", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes.dex */
public final class SttConfigActivity extends BaseActivity {
    private final Gson gson = new Gson();

    @Override // com.apk.claw.android.base.BaseActivity, androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stt_config);
        CommonToolbar commonToolbar = (CommonToolbar) findViewById(R.id.toolbar);
        commonToolbar.setTitle(getString(R.string.stt_config_title));
        commonToolbar.showBackButton(true, new Function0<Unit>() { // from class: com.apk.claw.android.ui.settings.SttConfigActivity$onCreate$1$1
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
                SttConfigActivity.this.finish();
            }
        });
        commonToolbar.setActionIcon(R.drawable.ic_export, new Function0<Unit>() { // from class: com.apk.claw.android.ui.settings.SttConfigActivity$onCreate$1$2
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
                SttConfigActivity.this.showImportExportMenu();
            }
        });
        final EditText editText = (EditText) findViewById(R.id.etBaseUrl);
        final EditText editText2 = (EditText) findViewById(R.id.etApiKey);
        final EditText editText3 = (EditText) findViewById(R.id.etModelName);
        editText.setText(KVUtils.INSTANCE.getSttBaseUrl());
        editText2.setText(KVUtils.INSTANCE.getSttApiKey());
        editText3.setText(KVUtils.INSTANCE.getSttModel());
        ((KButton) findViewById(R.id.btnSave)).setOnClickListener(new View.OnClickListener() { // from class: com.apk.claw.android.ui.settings.SttConfigActivity$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SttConfigActivity.onCreate$lambda$2(editText, editText2, editText3, this, view);
            }
        });
        ((KButton) findViewById(R.id.btnClear)).setOnClickListener(new View.OnClickListener() { // from class: com.apk.claw.android.ui.settings.SttConfigActivity$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SttConfigActivity.onCreate$lambda$3(SttConfigActivity.this, view);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void onCreate$lambda$2(EditText editText, EditText editText2, EditText editText3, SttConfigActivity this$0, View view) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        String obj = StringsKt.trim((CharSequence) editText.getText().toString()).toString();
        String obj2 = StringsKt.trim((CharSequence) editText2.getText().toString()).toString();
        String obj3 = StringsKt.trim((CharSequence) editText3.getText().toString()).toString();
        if (obj3.length() == 0) {
            obj3 = HttpSttVoiceRecognizer.DEFAULT_STT_MODEL;
        }
        String str = obj3;
        if (obj.length() == 0) {
            Toast.makeText(this$0, this$0.getString(R.string.stt_config_base_url_required), 0).show();
            return;
        }
        KVUtils.INSTANCE.setSttBaseUrl(obj);
        KVUtils.INSTANCE.setSttApiKey(obj2);
        KVUtils.INSTANCE.setSttModel(str);
        Toast.makeText(this$0, this$0.getString(R.string.stt_config_saved), 0).show();
        this$0.finish();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void onCreate$lambda$3(SttConfigActivity this$0, View view) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        KVUtils.INSTANCE.setSttBaseUrl("");
        KVUtils.INSTANCE.setSttApiKey("");
        KVUtils.INSTANCE.setSttModel(HttpSttVoiceRecognizer.DEFAULT_STT_MODEL);
        Toast.makeText(this$0, this$0.getString(R.string.stt_config_cleared), 0).show();
        this$0.finish();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void showImportExportMenu() {
        new AlertDialog.Builder(this).setTitle(getString(R.string.stt_import_export)).setItems(new String[]{getString(R.string.stt_export_clipboard), getString(R.string.stt_import_clipboard)}, new DialogInterface.OnClickListener() { // from class: com.apk.claw.android.ui.settings.SttConfigActivity$$ExternalSyntheticLambda0
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                SttConfigActivity.showImportExportMenu$lambda$4(SttConfigActivity.this, dialogInterface, i);
            }
        }).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void showImportExportMenu$lambda$4(SttConfigActivity this$0, DialogInterface dialogInterface, int i) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        if (i == 0) {
            this$0.exportToClipboard();
        } else {
            if (i != 1) {
                return;
            }
            this$0.importFromClipboard();
        }
    }

    private final void exportToClipboard() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty(Constant.HEADER_TYPE, "stt_config");
        jsonObject.addProperty("base_url", KVUtils.INSTANCE.getSttBaseUrl());
        jsonObject.addProperty("api_key", KVUtils.INSTANCE.getSttApiKey());
        jsonObject.addProperty("model", KVUtils.INSTANCE.getSttModel());
        String json = this.gson.toJson((JsonElement) jsonObject);
        Object systemService = getSystemService("clipboard");
        Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.content.ClipboardManager");
        ((ClipboardManager) systemService).setPrimaryClip(ClipData.newPlainText("stt_config", json));
        Toast.makeText(this, getString(R.string.stt_export_success), 0).show();
    }

    private final void importFromClipboard() {
        CharSequence text;
        Object systemService = getSystemService("clipboard");
        Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.content.ClipboardManager");
        ClipData primaryClip = ((ClipboardManager) systemService).getPrimaryClip();
        if (primaryClip == null || primaryClip.getItemCount() == 0) {
            Toast.makeText(this, getString(R.string.stt_import_clipboard_empty), 0).show();
            return;
        }
        ClipData.Item itemAt = primaryClip.getItemAt(0);
        String obj = (itemAt == null || (text = itemAt.getText()) == null) ? null : text.toString();
        String str = obj;
        if (str == null || StringsKt.isBlank(str)) {
            Toast.makeText(this, getString(R.string.stt_import_clipboard_empty), 0).show();
            return;
        }
        try {
            JsonObject jsonObject = (JsonObject) this.gson.fromJson(obj, JsonObject.class);
            JsonElement jsonElement = jsonObject.get("base_url");
            String asString = jsonElement != null ? jsonElement.getAsString() : null;
            String str2 = "";
            if (asString == null) {
                asString = "";
            }
            JsonElement jsonElement2 = jsonObject.get("api_key");
            String asString2 = jsonElement2 != null ? jsonElement2.getAsString() : null;
            if (asString2 == null) {
                asString2 = "";
            }
            JsonElement jsonElement3 = jsonObject.get("model");
            String asString3 = jsonElement3 != null ? jsonElement3.getAsString() : null;
            if (asString3 != null) {
                str2 = asString3;
            }
            if (asString.length() == 0) {
                Toast.makeText(this, getString(R.string.stt_import_invalid_format), 0).show();
                return;
            }
            ((EditText) findViewById(R.id.etBaseUrl)).setText(asString);
            ((EditText) findViewById(R.id.etApiKey)).setText(asString2);
            EditText editText = (EditText) findViewById(R.id.etModelName);
            String str3 = str2;
            if (str3.length() == 0) {
                str3 = HttpSttVoiceRecognizer.DEFAULT_STT_MODEL;
            }
            editText.setText(str3);
            Toast.makeText(this, getString(R.string.stt_import_filled), 0).show();
        } catch (Exception unused) {
            Toast.makeText(this, getString(R.string.stt_import_invalid_format), 0).show();
        }
    }
}
