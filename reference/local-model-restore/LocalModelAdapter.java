package com.apk.claw.android.ui.settings;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.apk.claw.android.R;
import java.util.Iterator;
import java.util.List;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: LocalModelAdapter.kt */
@Metadata(d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001:\u0001\u0016B/\u0012\f\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0012\u0010\b\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\n0\t¢\u0006\u0002\u0010\u000bJ\b\u0010\r\u001a\u00020\u000eH\u0016J\u0018\u0010\u000f\u001a\u00020\n2\u0006\u0010\u0010\u001a\u00020\u00022\u0006\u0010\u0011\u001a\u00020\u000eH\u0016J\u0018\u0010\u0012\u001a\u00020\u00022\u0006\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u000eH\u0016R\u000e\u0010\f\u001a\u00020\u0007X\u0082\u000e¢\u0006\u0002\n\u0000R\u0014\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0082\u0004¢\u0006\u0002\n\u0000R\u001a\u0010\b\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\n0\tX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004¢\u0006\u0002\n\u0000¨\u0006\u0017"}, d2 = {"Lcom/apk/claw/android/ui/settings/LocalModelAdapter;", "Landroidx/recyclerview/widget/RecyclerView$Adapter;", "Lcom/apk/claw/android/ui/settings/LocalModelAdapter$ViewHolder;", "models", "", "Lcom/apk/claw/android/ui/settings/LocalModelInfo;", "selectedModelId", "", "onModelSelected", "Lkotlin/Function1;", "", "(Ljava/util/List;Ljava/lang/String;Lkotlin/jvm/functions/Function1;)V", "currentSelectedId", "getItemCount", "", "onBindViewHolder", "holder", "position", "onCreateViewHolder", "parent", "Landroid/view/ViewGroup;", "viewType", "ViewHolder", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes.dex */
public final class LocalModelAdapter extends RecyclerView.Adapter<ViewHolder> {
    private String currentSelectedId;
    private final List<LocalModelInfo> models;
    private final Function1<LocalModelInfo, Unit> onModelSelected;
    private final String selectedModelId;

    /* JADX WARN: Multi-variable type inference failed */
    public LocalModelAdapter(List<LocalModelInfo> models, String selectedModelId, Function1<? super LocalModelInfo, Unit> onModelSelected) {
        Intrinsics.checkNotNullParameter(models, "models");
        Intrinsics.checkNotNullParameter(selectedModelId, "selectedModelId");
        Intrinsics.checkNotNullParameter(onModelSelected, "onModelSelected");
        this.models = models;
        this.selectedModelId = selectedModelId;
        this.onModelSelected = onModelSelected;
        this.currentSelectedId = selectedModelId;
    }

    /* compiled from: LocalModelAdapter.kt */
    @Metadata(d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\t\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003¢\u0006\u0002\u0010\u0004R\u0011\u0010\u0005\u001a\u00020\u0006¢\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\t\u001a\u00020\u0006¢\u0006\b\n\u0000\u001a\u0004\b\n\u0010\bR\u0011\u0010\u000b\u001a\u00020\u0006¢\u0006\b\n\u0000\u001a\u0004\b\f\u0010\bR\u0011\u0010\r\u001a\u00020\u0006¢\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\b¨\u0006\u000f"}, d2 = {"Lcom/apk/claw/android/ui/settings/LocalModelAdapter$ViewHolder;", "Landroidx/recyclerview/widget/RecyclerView$ViewHolder;", "itemView", "Landroid/view/View;", "(Landroid/view/View;)V", "tvDesc", "Landroid/widget/TextView;", "getTvDesc", "()Landroid/widget/TextView;", "tvName", "getTvName", "tvSelected", "getTvSelected", "tvSize", "getTvSize", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public static final class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvDesc;
        private final TextView tvName;
        private final TextView tvSelected;
        private final TextView tvSize;

        /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
        public ViewHolder(View itemView) {
            super(itemView);
            Intrinsics.checkNotNullParameter(itemView, "itemView");
            View findViewById = itemView.findViewById(R.id.tv_model_name);
            Intrinsics.checkNotNullExpressionValue(findViewById, "findViewById(...)");
            this.tvName = (TextView) findViewById;
            View findViewById2 = itemView.findViewById(R.id.tv_model_desc);
            Intrinsics.checkNotNullExpressionValue(findViewById2, "findViewById(...)");
            this.tvDesc = (TextView) findViewById2;
            View findViewById3 = itemView.findViewById(R.id.tv_model_size);
            Intrinsics.checkNotNullExpressionValue(findViewById3, "findViewById(...)");
            this.tvSize = (TextView) findViewById3;
            View findViewById4 = itemView.findViewById(R.id.iv_selected);
            Intrinsics.checkNotNullExpressionValue(findViewById4, "findViewById(...)");
            this.tvSelected = (TextView) findViewById4;
        }

        public final TextView getTvName() {
            return this.tvName;
        }

        public final TextView getTvDesc() {
            return this.tvDesc;
        }

        public final TextView getTvSize() {
            return this.tvSize;
        }

        public final TextView getTvSelected() {
            return this.tvSelected;
        }
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Intrinsics.checkNotNullParameter(parent, "parent");
        View inflate = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_local_model_card, parent, false);
        Intrinsics.checkNotNull(inflate);
        return new ViewHolder(inflate);
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public void onBindViewHolder(ViewHolder holder, final int position) {
        Intrinsics.checkNotNullParameter(holder, "holder");
        final LocalModelInfo localModelInfo = this.models.get(position);
        holder.getTvName().setText(localModelInfo.getDisplayName());
        holder.getTvDesc().setText(localModelInfo.getDescription());
        holder.getTvSize().setText(localModelInfo.getModelSize());
        boolean areEqual = Intrinsics.areEqual(localModelInfo.getId(), this.currentSelectedId);
        holder.getTvSelected().setText(areEqual ? "●" : "○");
        holder.getTvSelected().setAlpha(areEqual ? 1.0f : 0.4f);
        holder.itemView.setOnClickListener(new View.OnClickListener() { // from class: com.apk.claw.android.ui.settings.LocalModelAdapter$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LocalModelAdapter.onBindViewHolder$lambda$1(LocalModelInfo.this, this, position, view);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void onBindViewHolder$lambda$1(LocalModelInfo model, LocalModelAdapter this$0, int i, View view) {
        Intrinsics.checkNotNullParameter(model, "$model");
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        if (Intrinsics.areEqual(model.getId(), this$0.currentSelectedId)) {
            return;
        }
        Iterator<LocalModelInfo> it = this$0.models.iterator();
        int i2 = 0;
        while (true) {
            if (!it.hasNext()) {
                i2 = -1;
                break;
            } else if (Intrinsics.areEqual(it.next().getId(), this$0.currentSelectedId)) {
                break;
            } else {
                i2++;
            }
        }
        this$0.currentSelectedId = model.getId();
        if (i2 >= 0) {
            this$0.notifyItemChanged(i2);
        }
        this$0.notifyItemChanged(i);
        this$0.onModelSelected.invoke(model);
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public int getItemCount() {
        return this.models.size();
    }
}
