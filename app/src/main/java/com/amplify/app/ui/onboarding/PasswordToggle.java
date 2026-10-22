package com.amplify.app.ui.onboarding;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;
import android.widget.ImageView;

import com.amplify.app.R;

public class PasswordToggle {

    public static void setup(EditText editText, ImageView iconView) {
        if (editText == null || iconView == null) return;

        // Ensure password is hidden by default
        editText.setTransformationMethod(PasswordTransformationMethod.getInstance());

        final boolean[] isVisible = {false};

        updateState(editText, iconView, isVisible[0]);

        iconView.setOnClickListener(v -> {
            isVisible[0] = !isVisible[0];
            int selectionStart = editText.getSelectionStart();
            int selectionEnd = editText.getSelectionEnd();

            updateState(editText, iconView, isVisible[0]);

            if (selectionStart >= 0 && selectionEnd >= 0 && selectionEnd <= editText.length()) {
                editText.setSelection(selectionStart, selectionEnd);
            } else {
                editText.setSelection(editText.length());
            }
        });
    }

    private static void updateState(EditText editText, ImageView iconView, boolean visible) {
        if (visible) {
            editText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            iconView.setImageResource(R.drawable.ic_visibility);
            iconView.setColorFilter(Color.parseColor("#6D4AFF"), PorterDuff.Mode.SRC_IN);
            iconView.setContentDescription("Hide password");
        } else {
            editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            iconView.setImageResource(R.drawable.ic_visibility_off);
            iconView.setColorFilter(Color.parseColor("#9993A8"), PorterDuff.Mode.SRC_IN);
            iconView.setContentDescription("Show password");
        }
    }
}
