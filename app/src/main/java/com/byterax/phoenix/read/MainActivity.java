package com.byterax.phoenix.read;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.byterax.phoenix.read.service.ServiceClient;

public class MainActivity extends Activity {

    /** 一张状态卡片。 */
    private static final class Card {
        View root;
        TextView emoji;
        TextView title;
        TextView subtitle;
    }

    private Card cardFanqie, cardHongguo, cardQuark, cardXiaox, cardCherrygram;
    private TextView hintText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cardFanqie     = bindCard(R.id.card_fanqie,     "\uD83C\uDF45", R.string.target_fanqie_title,     R.string.target_fanqie_subtitle);
        cardHongguo    = bindCard(R.id.card_hongguo,    "\uD83C\uDFAC", R.string.target_hongguo_title,    R.string.target_hongguo_subtitle);
        cardQuark      = bindCard(R.id.card_quark,      "\uD83D\uDD0D", R.string.target_quark_title,      R.string.target_quark_subtitle);
        cardXiaox      = bindCard(R.id.card_xiaox,      "\uD83E\uDDEC", R.string.target_xiaox_title,      R.string.target_xiaox_subtitle);
        cardCherrygram = bindCard(R.id.card_cherrygram, "\u2708\uFE0F",  R.string.target_cherrygram_title, R.string.target_cherrygram_subtitle);

        hintText = findViewById(R.id.hint_text);
    }

    private Card bindCard(int rootId, String emoji, int titleRes, int subtitleRes) {
        Card c = new Card();
        c.root = findViewById(rootId);
        c.emoji = c.root.findViewById(R.id.status_card_emoji);
        c.title = c.root.findViewById(R.id.status_card_title);
        c.subtitle = c.root.findViewById(R.id.status_card_subtitle);
        c.emoji.setText(emoji);
        c.title.setText(titleRes);
        c.subtitle.setText(subtitleRes);
        return c;
    }

    @Override
    protected void onResume() {
        super.onResume();
        ServiceClient.get().tryConnect();
        refreshStatusCards();
    }

    private void refreshStatusCards() {
        boolean libxposed = RuntimeDetector.isLibxposedLoaded();
        boolean root = RuntimeDetector.hasRoot();
        boolean framework = RuntimeDetector.isFrameworkInstalled();
        boolean alive = libxposed || root || framework;

        applyCard(cardFanqie,     alive && HookStatusFiles.isFanqieHooked());
        applyCard(cardHongguo,    alive && HookStatusFiles.isHongguoHooked());
        applyCard(cardQuark,      alive && HookStatusFiles.isQuarkHooked());
        applyCard(cardXiaox,      alive && HookStatusFiles.isXiaoxHooked());
        applyCard(cardCherrygram, alive && HookStatusFiles.isCherrygramHooked());

        hintText.setVisibility(alive ? View.GONE : View.VISIBLE);
    }

    private static void applyCard(Card c, boolean hooked) {
        if (c == null || c.root == null) return;
        c.root.setBackgroundResource(hooked
                ? R.drawable.bg_glass_card_active
                : R.drawable.bg_glass_card_idle);
    }
}