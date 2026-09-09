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

import java.util.ArrayList;
import java.util.List;

/**
 * Squema Hook home screen — black-gold glass card layout.
 *
 * <p>UI state is driven by {@link ScopeStatus} (per-target OFF / SCOPED / LIVE),
 * refreshed whenever the {@link HookApp.StatusListener} fires.
 *
 * <p>Target cards are driven by a single {@code Card[]} table — adding a new
 * target means: (a) append one entry here, (b) add the layout include + the
 * card id constant. No other place in this class needs editing.
 */
public class MainActivity extends Activity implements HookApp.StatusListener {

    private static final long ENTRANCE_STAGGER_MS = 40L;
    private static final long ENTRANCE_DURATION_MS = 380L;
    private static final float ENTRANCE_OFFSET_DP = 18f;
    private static final float FROSTED_BLUR_RADIUS_DP = 36f;
    private static final float GLASS_BLUR_ALPHA = 0.2f;

    private final List<Card> allCards = new ArrayList<>();

    private View atmosphereLayer;
    private View glassBlurLayer;
    private ImageView grainLayer;
    private TextView brandTitle;
    private View brandSubtitle;
    private TextView hintText;
    private boolean entrancePlayed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyImmersiveChrome();
        setContentView(R.layout.activity_main);

        bindAmbientLayers();
        bindCards();
        wireQuarkCardClick();

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

    private void bindAmbientLayers() {
        atmosphereLayer = findViewById(R.id.atmosphere_layer);
        glassBlurLayer  = findViewById(R.id.glass_blur_layer);
        grainLayer      = findViewById(R.id.grain_layer);
        brandTitle      = findViewById(R.id.brand_title);
        brandSubtitle   = findViewById(R.id.brand_subtitle);
        hintText        = findViewById(R.id.hint_text);
    }

    /** Single source of truth for the 5 status cards. */
    private void bindCards() {
        allCards.clear();
        allCards.add(bindCard(R.id.card_fanqie,     R.string.target_fanqie,     Constants.PKG_FANQIE,     false));
        allCards.add(bindCard(R.id.card_hongguo,    R.string.target_hongguo,    Constants.PKG_HONGGUO,    false));
        allCards.add(bindCard(R.id.card_quark,      R.string.target_quark,      Constants.PKG_QUARK,      true));
        allCards.add(bindCard(R.id.card_xiaox,      R.string.target_xiaox,      Constants.PKG_XIAOX,      false));
        allCards.add(bindCard(R.id.card_cherrygram, R.string.target_cherrygram, Constants.PKG_CHERRYGRAM, false));
    }

    private Card bindCard(int rootId, int titleRes, String pkg, boolean openable) {
        Card c = new Card();
        c.root = findViewById(rootId);
        c.pkg = pkg;
        c.openable = openable;
        c.titleView  = c.root.findViewById(R.id.status_card_title);
        c.subtitle   = c.root.findViewById(R.id.status_card_subtitle);
        c.icon       = c.root.findViewById(R.id.status_card_icon);
        c.chip       = c.root.findViewById(R.id.status_card_chip);
        c.targetsRow = c.root.findViewById(R.id.status_card_targets);
        if (c.titleView != null) c.titleView.setText(titleRes);
        return c;
    }

    private void wireQuarkCardClick() {
        for (Card c : allCards) {
            if (c.root == null) continue;
            c.root.setClickable(c.openable);
            c.root.setFocusable(c.openable);
            if (c.pkg.equals(Constants.PKG_QUARK)) {
                c.root.setOnClickListener(v ->
                        startActivity(new Intent(this, QuarkSettingsActivity.class)));
            }
        }
    }

    private void applyImmersiveChrome() {
        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.bg_void));
        window.setNavigationBarColor(getColor(R.color.bg_void));
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        decor.setSystemUiVisibility(flags);
    }

    private void applyFrostedBackdrop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || atmosphereLayer == null) return;
        float radius = FROSTED_BLUR_RADIUS_DP * getResources().getDisplayMetrics().density;
        atmosphereLayer.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
        if (glassBlurLayer != null) glassBlurLayer.setAlpha(GLASS_BLUR_ALPHA);
        if (grainLayer != null) grainLayer.setVisibility(View.GONE);
    }

    private void prepareEntrance() {
        for (View view : allEntranceViews()) hideForEntrance(view);
    }

    private void playEntrance() {
        View[] views = allEntranceViews();
        float offsetPx = ENTRANCE_OFFSET_DP * getResources().getDisplayMetrics().density;
        for (int i = 0; i < views.length; i++) {
            View view = views[i];
            if (view == null) continue;
            ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f);
            ObjectAnimator ty    = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.getTranslationY(), 0f);
            AnimatorSet set = new AnimatorSet();
            set.playTogether(alpha, ty);
            set.setDuration(ENTRANCE_DURATION_MS);
            set.setStartDelay(ENTRANCE_STAGGER_MS * i);
            set.setInterpolator(new DecelerateInterpolator(1.4f));
            set.start();
        }
    }

    /** Both ambient text nodes + every card view participate in the entrance stagger. */
    private View[] allEntranceViews() {
        int extra = (brandTitle != null ? 1 : 0) + (brandSubtitle != null ? 1 : 0) + (hintText != null ? 1 : 0);
        View[] arr = new View[extra + allCards.size()];
        int i = 0;
        if (brandTitle != null) arr[i++] = brandTitle;
        if (brandSubtitle != null) arr[i++] = brandSubtitle;
        for (Card c : allCards) arr[i++] = c.root;
        if (hintText != null) arr[i++] = hintText;
        return arr;
    }

    private static void hideForEntrance(View view) {
        if (view == null) return;
        view.setAlpha(0f);
    }

    private void refreshCards() {
        for (Card c : allCards) {
            ScopeStatus.State state = ScopeStatus.resolve(this, c.pkg);
            applyCardState(c, state);
        }
        if (hintText != null) {
            hintText.setVisibility(View.VISIBLE);
            hintText.setText(ScopeStatus.isServiceBound()
                    ? R.string.status_hint
                    : R.string.status_hint_no_service);
        }
    }

    private void applyCardState(Card c, ScopeStatus.State state) {
        if (c == null || c.root == null) return;
        boolean lit  = state != ScopeStatus.State.OFF;
        boolean live = state == ScopeStatus.State.LIVE;

        c.root.setBackgroundResource(lit ? R.drawable.bg_glass_card_active
                                        : R.drawable.bg_glass_card_idle);
        c.root.setElevation(0f);

        if (c.icon != null) {
            c.icon.setText(lit ? "\u2713" : "\u00B7");
            c.icon.setTextColor(getColor(lit ? R.color.gold_bright : R.color.text_muted));
        }
        if (c.titleView != null) {
            c.titleView.setTextColor(getColor(R.color.text_primary));
        }
        if (c.subtitle != null) {
            int textRes = live ? R.string.status_live
                            : lit  ? R.string.status_scoped
                                   : R.string.status_not_hooked;
            c.subtitle.setText(textRes);
            c.subtitle.setTextColor(getColor(lit ? R.color.text_gold : R.color.text_secondary));
        }
        if (c.targetsRow != null) c.targetsRow.setVisibility(View.GONE);
        if (c.chip != null) {
            c.chip.setText(chipTextFor(state, c.openable));
            c.chip.setBackgroundResource(lit || c.openable
                    ? R.drawable.bg_status_chip_active
                    : R.drawable.bg_status_chip_idle);
            c.chip.setTextColor(getColor(lit || c.openable
                    ? R.color.text_primary
                    : R.color.text_secondary));
        }
    }

    private static int chipTextFor(ScopeStatus.State state, boolean openable) {
        if (openable) return R.string.status_chip_open;
        switch (state) {
            case LIVE:   return R.string.status_chip_on;
            case SCOPED: return R.string.status_chip_scope;
            default:     return R.string.status_chip_off;
        }
    }

    private static final class Card {
        View root;
        String pkg;
        boolean openable;
        TextView icon, titleView, subtitle, chip, targetsRow;
    }
}
