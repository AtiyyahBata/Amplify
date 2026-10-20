package com.amplify.app.data;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;

/**
 * Shows a profile photo inside a round card, or hides the card when the person
 * has no photo (the initials circle underneath then shows instead).
 *
 * Use it with: AvatarLoader.bind(avatarUrl, cardView, imageView);
 */
public final class AvatarLoader {

    private AvatarLoader() {}

    public static void bind(String url, MaterialCardView card, ImageView image) {
        Context context = image.getContext();
        if (context instanceof Activity) {
            Activity a = (Activity) context;
            if (a.isFinishing() || a.isDestroyed()) return;
        }

        if (url == null || url.trim().isEmpty()) {
            Glide.with(context).clear(image);
            image.setImageDrawable(null);
            card.setVisibility(View.GONE);
            return;
        }

        card.setVisibility(View.VISIBLE);
        Glide.with(context)
                .load(url)
                .override(400, 400)
                .centerCrop()
                .into(image);
    }
}
