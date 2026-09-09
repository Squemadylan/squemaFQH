package com.byterax.phoenix.read;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.byterax.phoenix.read.quark.QuarkSettingsActivity;
import com.byterax.phoenix.read.service.ServiceClient;

public class MainActivity extends Activity implements HookApp.StatusListener {

    private View atmosphereLayer;
    private View glassBlurLayer;
    private ImageView grainLayer;
    private TextView brandTitle;
    private View brandSubtitle;
    private View cardFanqie;
    private View cardHongguo;
    private View cardQuark;
    private View cardXiaox;
    private View cardCherrygram;
    private TextView hintText;
    private boolean entrancePlayed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyImmersiveChrome();
        setContentView(R.layout.activity_main);

        atmosphereLayer = findViewById(R.id.atmosphere_layer);
        glassBlurLayer = findViewById(R.id.glass_blur_layer);
        grainLayer = findViewById(R.id.grain_layer);
        brandTitle = findViewById(R.id.brand_title);
        brandSubtitle = findViewById(R.id.brand_subtitle);
        cardFanqie = findViewById(R.id.card_fanqie);
        cardHongguo = findViewById(R.id.card_hongguo);
        cardQuark = findViewById(R.id.card_quark);
        cardXiaox = findViewById(R.id.card_xiaox);
        cardCherrygram = findViewById(R.id.card_cherrygram);
        hintText = findViewById(R.id.hint_text);

        if (cardQuark != null) {
            cardQuark.setOnClickListener(v ->
                    startActivity(new Intent(this, QuarkSettingsActivity.class)));
        }

        applyFrostedBackdrop();
        prepareEntrance();
    }

    @Override
    protected void onStart() {
        super.onStart();
        HookApp.addStatusListener(this);
    }

    @Override
    protected void onStop() {
        HookApp.removeStatusListener(this);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ServiceClient.get().tryConnect();
        LspScopeReader.invalidate();
        refreshCards();
        if (!entrancePlayed) {
            entrancePlayed = true;
            playEntrance();
        }
    }

    @Override
    public void onXposedStatusChanged() {
        refreshCards();
    }

    private void applyImmersiveChrome() {
        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.bg_void));
        window.setNavigationBarColor(getColor(R.color.bg_void));
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        decor.setSystemUiVisibility(flags);
    }

    private void applyFrostedBackdrop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || atmosphereLayer == null) {
            return;
        }
        float radius = 36f * getResources().getDisplayMetrics().density;
        atmosphereLayer.setRenderEffect(
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
        if (glassBlurLayer != null) {
            glassBlurLayer.setAlpha(0.2f);
        }
        if (grainLayer != null) {
            grainLayer.setVisibility(View.GONE);
        }
    }

    private void prepareEntrance() {
        View[] views = {
                brandTitle, brandSubtitle,
                cardFanqie, cardHongguo, cardQuark, cardXiaox, cardCherrygram,
                hintText
        };
        for (View view : views) {
            if (view == null) {
                continue;
            }
            view.setAlpha(0f);
            view.setTranslationY(18f * getResources().getDisplayMetrics().density);
        }
    }

    private void playEntrance() {
        View[] views = {
                brandTitle, brandSubtitle,
                cardFanqie, cardHongguo, cardQuark, cardXiaox, cardCherrygram,
                hintText
        };
        long delay = 40L;
        for (int i = 0; i < views.length; i++) {
            View view = views[i];
            if (view == null) {
                continue;
            }
            ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f);
            ObjectAnimator ty = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.getTranslationY(), 0f);
            AnimatorSet set = new AnimatorSet();
            set.playTogether(alpha, ty);
            set.setDuration(380L);
            set.setStartDelay(delay * i);
            set.setInterpolator(new DecelerateInterpolator(1.4f));
            set.start();
        }
    }

    private void refreshCards() {
        ScopeStatus.State fanqie     = ScopeStatus.resolve(this, Constants.PKG_FANQIE);
        ScopeStatus.State hongguo    = ScopeStatus.resolve(this, Constants.PKG_HONGGUO);
        ScopeStatus.State quark      = ScopeStatus.resolve(this, Constants.PKG_QUARK);
        ScopeStatus.State xiaox      = ScopeStatus.resolve(this, Constants.PKG_XIAOX);
        ScopeStatus.State cherrygram = ScopeStatus.resolve(this, Constants.PKG_CHERRYGRAM);

        bindCard(cardFanqie,     getString(R.string.target_fanqie),     fanqie,     false);
        bindCard(cardHongguo,    getString(R.string.target_hongguo),    hongguo,    false);
        bindCard(cardQuark,      getString(R.string.target_quark),      quark,      true);
        bindCard(cardXiaox,      getString(R.string.target_xiaox),      xiaox,      false);
        bindCard(cardCherrygram, getString(R.string.target_cherrygram), cherrygram, false);

        if (hintText != null) {
            hintText.setVisibility(View.VISIBLE);
            boolean service = ScopeStatus.isServiceBound();
            hintText.setText(service ? R.string.status_hint : R.string.status_hint_no_service);
        }
    }

    private void bindCard(View card, String title, ScopeStatus.State state, boolean openable) {
        if (card == null) {
            return;
        }
        boolean lit = state != ScopeStatus.State.OFF;
        boolean live = state == ScopeStatus.State.LIVE;

        card.setBackgroundResource(lit
                ? R.drawable.bg_glass_card_active
                : R.drawable.bg_glass_card_idle);
        card.setElevation(0f);
        card.setClickable(openable);
        card.setFocusable(openable);

        TextView icon = card.findViewById(R.id.status_card_icon);
        TextView titleView = card.findViewById(R.id.status_card_title);
        TextView subtitle = card.findViewById(R.id.status_card_subtitle);
        TextView targets = card.findViewById(R.id.status_card_targets);
        TextView chip = card.findViewById(R.id.status_card_chip);

        if (icon != null) {
            icon.setText(lit ? "\u2713" : "\u00B7");
            icon.setTextColor(getColor(lit ? R.color.gold_bright : R.color.text_muted));
        }
        if (titleView != null) {
            titleView.setText(title);
            titleView.setTextColor(getColor(R.color.text_primary));
        }
        if (subtitle != null) {
            if (live) {
                subtitle.setText(R.string.status_live);
            } else if (lit) {
                subtitle.setText(R.string.status_scoped);
            } else {
                subtitle.setText(R.string.status_not_hooked);
            }
            subtitle.setTextColor(getColor(lit ? R.color.text_gold : R.color.text_secondary));
        }
        if (targets != null) {
            targets.setVisibility(View.GONE);
        }
        if (chip != null) {
            if (openable) {
                chip.setText(R.string.status_chip_open);
            } else if (live) {
                chip.setText(R.string.status_chip_on);
            } else if (lit) {
                chip.setText(R.string.status_chip_scope);
            } else {
                chip.setText(R.string.status_chip_off);
            }
            chip.setBackgroundResource(lit || openable
                    ? R.drawable.bg_status_chip_active
                    : R.drawable.bg_status_chip_idle);
            chip.setTextColor(getColor(lit || openable ? R.color.text_primary : R.color.text_secondary));
        }
    }
}