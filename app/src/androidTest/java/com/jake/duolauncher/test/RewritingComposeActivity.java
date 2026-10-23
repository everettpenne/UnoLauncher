package com.jake.duolauncher.test;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;

/**
 * (The box sits at the bottom, just above the keyboard, as a chat app's does, and the window resizes for the keyboard.)
 * A message box like Signal's: several lines, sentence capitals and autocorrect asked for, and a text watcher that, once the text runs onto
 * a second line, rewrites the whole of it on every change (as Signal re-processes its compose text for emoji and styling), keeping the
 * cursor where it was. Uno Keyboard once turned text in a box like this into gibberish; this is for checking that by hand.
 * Java, like the other fixtures here: the test APK's own process has no Kotlin runtime.
 */
public class RewritingComposeActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final EditText box = new EditText(this);
        box.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        box.setMinLines(1); box.setMaxLines(6); box.setTextSize(18f);
        box.setContentDescription("compose");
        box.addTextChangedListener(new TextWatcher() {
            private boolean busy;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (busy || box.getLayout() == null || box.getLayout().getLineCount() < 2) return;
                busy = true;
                int cursor = Selection.getSelectionEnd(s);
                s.replace(0, s.length(), new SpannableStringBuilder(s.toString()));
                if (cursor >= 0 && cursor <= s.length()) Selection.setSelection(s, cursor);
                busy = false;
            }
        });
        FrameLayout root = new FrameLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        lp.setMargins(32, 0, 32, 24);
        root.addView(box, lp);
        // Edge-to-edge (the default now) ignores adjustResize: like a chat app, lift the box by the keyboard's height myself.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int ime = insets.getInsets(android.view.WindowInsets.Type.ime() | android.view.WindowInsets.Type.systemBars()).bottom;
            v.setPadding(0, 0, 0, ime);
            return insets;
        });
        setContentView(root);
        box.requestFocus();
    }
}
