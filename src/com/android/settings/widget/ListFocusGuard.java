package com.android.settings.widget;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroupAdapter;
import androidx.recyclerview.widget.RecyclerView;

/**
 * andr36oid: keeps the focus on a preference row for d-pad use. When the focused row goes away
 * (backing out of a sub page, the list being rebuilt), Android hands the focus to the list or
 * the scroll container around it, which then gets outlined over the whole page and the d-pad
 * has nothing to move from. This turns the outline off for those containers and moves the focus
 * back to the row that had it (found by preference key), or to the first row that can take it.
 */
public final class ListFocusGuard implements ViewTreeObserver.OnGlobalFocusChangeListener,
        View.OnAttachStateChangeListener {
    private final RecyclerView mList;
    private String mLastKey;
    private boolean mFixPending;

    private ListFocusGuard(RecyclerView list) {
        mList = list;
    }

    /** Call once for each preference list. */
    public static void install(RecyclerView list) {
        final ListFocusGuard guard = new ListFocusGuard(list);
        list.setDefaultFocusHighlightEnabled(false);
        // Rows get the focus before the list does when something asks the list for it
        list.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        list.addOnAttachStateChangeListener(guard);
        if (list.isAttachedToWindow()) {
            guard.onViewAttachedToWindow(list);
        }
    }

    @Override
    public void onViewAttachedToWindow(View v) {
        v.getViewTreeObserver().addOnGlobalFocusChangeListener(this);
    }

    @Override
    public void onViewDetachedFromWindow(View v) {
        v.getViewTreeObserver().removeOnGlobalFocusChangeListener(this);
    }

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (newFocus == null) {
            return;
        }
        if (isListOrContainer(newFocus)) {
            // No outline, and no stopping here: go back into the list
            newFocus.setDefaultFocusHighlightEnabled(false);
            scheduleFix();
            return;
        }
        final View item = mList.findContainingItemView(newFocus);
        if (item != null) {
            rememberKey(item);
        }
    }

    /** The list itself or something around it, never a row or a toolbar. */
    private boolean isListOrContainer(View view) {
        if (view == mList) {
            return true;
        }
        if (!(view instanceof ViewGroup)) {
            return false;
        }
        for (ViewParent p = mList.getParent(); p != null; p = p.getParent()) {
            if (p == view) {
                return true;
            }
        }
        return false;
    }

    private void rememberKey(View item) {
        final int position = mList.getChildAdapterPosition(item);
        if (position < 0 || !(mList.getAdapter() instanceof PreferenceGroupAdapter)) {
            return;
        }
        final Preference pref = ((PreferenceGroupAdapter) mList.getAdapter()).getItem(position);
        if (pref != null && pref.getKey() != null) {
            mLastKey = pref.getKey();
        }
    }

    private void scheduleFix() {
        if (mFixPending) {
            return;
        }
        mFixPending = true;
        mList.post(this::fix);
    }

    private void fix() {
        mFixPending = false;
        if (!mList.isAttachedToWindow()) {
            return;
        }
        final View focused = mList.findFocus();
        if (focused != null && focused != mList && !isListOrContainer(focused)) {
            return; // a row has it already
        }
        if (mLastKey != null && mList.getAdapter() instanceof PreferenceGroupAdapter) {
            final int position = ((PreferenceGroupAdapter) mList.getAdapter())
                    .getPreferenceAdapterPosition(mLastKey);
            if (position >= 0) {
                final RecyclerView.ViewHolder holder =
                        mList.findViewHolderForAdapterPosition(position);
                if (holder != null && holder.itemView.requestFocus()) {
                    return;
                }
                // Scrolled out or not laid out yet: bring it in and try again
                mList.scrollToPosition(position);
                mList.post(() -> {
                    final RecyclerView.ViewHolder h =
                            mList.findViewHolderForAdapterPosition(position);
                    if (h == null || !h.itemView.requestFocus()) {
                        focusFirstRow();
                    }
                });
                return;
            }
        }
        focusFirstRow();
    }

    private void focusFirstRow() {
        for (int i = 0; i < mList.getChildCount(); i++) {
            final View child = mList.getChildAt(i);
            if (child.isFocusable() && child.requestFocus()) {
                return;
            }
        }
        // Nothing to focus (a page of plain text): the list keeps the focus, to scroll
    }
}
