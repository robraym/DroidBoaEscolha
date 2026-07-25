package br.com.boaescolha;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

final class FloatingSearchController {
    private static final String PREFS = "boa_escolha";
    private static final String POSITION_X_SUFFIX = "_search_position_x";
    private static final String POSITION_Y_SUFFIX = "_search_position_y";

    private final AppCompatActivity activity;
    private final String positionKey;
    private final FrameLayout contentArea;
    private final FrameLayout searchBar;
    private final View searchIcon;
    private final EditText searchInput;
    private final View actionButton;

    private ValueAnimator animator;
    private boolean expanded;
    private boolean dragging;
    private boolean actionRequestedVisible;
    private float touchDownX;
    private float touchDownY;
    private int dragStartX;
    private int dragStartY;
    private int collapsedX;
    private int collapsedY;
    private int collapsedBottomOffset;
    private int keyboardCoveredHeight;

    private FloatingSearchController(AppCompatActivity activity, String positionKey) {
        this.activity = activity;
        this.positionKey = positionKey;
        contentArea = activity.findViewById(R.id.floatingSearchContentArea);
        searchBar = activity.findViewById(R.id.searchContainer);
        searchIcon = activity.findViewById(R.id.iconFloatingSearch);
        searchInput = activity.findViewById(R.id.editSearch);
        actionButton = activity.findViewById(R.id.btnSearch);

        searchBar.setOnClickListener(view -> expand());
        searchBar.setOnTouchListener(this::handleTouch);
        searchInput.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus && expanded) {
                collapse(true);
            }
        });
        contentArea.addOnLayoutChangeListener((view, left, top, right, bottom,
                                               oldLeft, oldTop, oldRight, oldBottom) -> {
            boolean sizeChanged = right - left != oldRight - oldLeft
                    || bottom - top != oldBottom - oldTop;
            if (expanded && sizeChanged) {
                positionAboveKeyboard();
            } else if (!expanded && !dragging && sizeChanged) {
                positionCollapsed();
            }
        });
        if (actionButton != null) {
            actionButton.setVisibility(View.GONE);
        }
        contentArea.post(this::positionCollapsed);
    }

    static FloatingSearchController attach(AppCompatActivity activity, String positionKey) {
        return new FloatingSearchController(activity, positionKey);
    }

    void setVisible(boolean visible) {
        if (!visible) {
            collapse(false);
        }
        searchBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            searchBar.post(this::positionCollapsed);
        }
    }

    void setActionVisible(boolean visible) {
        actionRequestedVisible = visible;
        if (actionButton == null) {
            return;
        }
        actionButton.setVisibility(expanded && visible ? View.VISIBLE : View.GONE);
    }

    void setCollapsedBottomOffset(int bottomOffset) {
        collapsedBottomOffset = Math.max(0, bottomOffset);
        if (searchBar.getVisibility() == View.VISIBLE && !expanded && !dragging) {
            searchBar.post(this::positionCollapsed);
        }
    }

    void setKeyboardCoveredHeight(int coveredHeight) {
        keyboardCoveredHeight = Math.max(0, coveredHeight);
        if (expanded) {
            searchBar.post(this::positionAboveKeyboard);
        }
    }

    boolean collapseIfExpanded() {
        if (!expanded) {
            return false;
        }
        collapse(true);
        return true;
    }

    void collapse(boolean animate) {
        expanded = false;
        InputMethodManager inputMethodManager =
                (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
        searchInput.clearFocus();
        if (searchInput.length() > 0) {
            searchInput.setText("");
        }
        searchInput.animate().cancel();
        if (actionButton != null) {
            actionButton.setVisibility(View.GONE);
        }
        if (!animate) {
            if (animator != null) {
                animator.cancel();
            }
            setBounds(dp(48), collapsedX, collapsedY);
            searchInput.setAlpha(0f);
            searchInput.setVisibility(View.GONE);
            return;
        }
        searchInput.animate()
                .alpha(0f)
                .setDuration(90L)
                .withEndAction(() -> {
                    if (!expanded) {
                        searchInput.setVisibility(View.GONE);
                    }
                })
                .start();
        animateBounds(dp(48), collapsedX, collapsedY, 160L);
    }

    private void expand() {
        if (expanded || contentArea.getWidth() <= 0) {
            return;
        }
        FrameLayout.LayoutParams currentParams =
                (FrameLayout.LayoutParams) searchBar.getLayoutParams();
        collapsedX = currentParams.leftMargin;
        collapsedY = currentParams.topMargin;
        int parentWidth = contentArea.getWidth();
        int desiredWidth = Math.min(dp(actionRequestedVisible ? 324 : 300), parentWidth - dp(16));
        int rightCapacity = parentWidth - collapsedX;
        int leftCapacity = collapsedX + dp(48);
        boolean expandsRight = rightCapacity >= leftCapacity;
        int targetWidth = Math.min(desiredWidth, expandsRight ? rightCapacity : leftCapacity);
        int targetLeft = expandsRight ? collapsedX : collapsedX + dp(48) - targetWidth;

        FrameLayout.LayoutParams iconParams =
                (FrameLayout.LayoutParams) searchIcon.getLayoutParams();
        iconParams.gravity = (expandsRight ? Gravity.START : Gravity.END) | Gravity.CENTER_VERTICAL;
        searchIcon.setLayoutParams(iconParams);
        searchInput.setPaddingRelative(
                expandsRight ? dp(48) : dp(actionRequestedVisible ? 56 : 15),
                0,
                expandsRight ? dp(actionRequestedVisible ? 56 : 15) : dp(48),
                0
        );
        expanded = true;
        searchInput.setVisibility(View.VISIBLE);
        searchInput.setAlpha(0f);
        if (actionButton != null) {
            FrameLayout.LayoutParams actionParams =
                    (FrameLayout.LayoutParams) actionButton.getLayoutParams();
            actionParams.gravity = (expandsRight ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL;
            actionButton.setLayoutParams(actionParams);
            actionButton.setVisibility(actionRequestedVisible ? View.VISIBLE : View.GONE);
        }
        animateBounds(targetWidth, targetLeft, currentParams.topMargin, 190L);
        searchInput.animate().alpha(1f).setDuration(150L).start();
        searchInput.setFocusableInTouchMode(true);
        searchInput.setShowSoftInputOnFocus(true);
        searchInput.postDelayed(() -> {
            if (!expanded) {
                return;
            }
            searchInput.requestFocus();
            searchInput.setSelection(searchInput.length());
            showKeyboard();
            searchInput.postDelayed(this::showKeyboard, 250L);
            searchInput.postDelayed(this::showKeyboard, 500L);
            searchInput.postDelayed(this::positionAboveKeyboard, 300L);
            searchInput.postDelayed(this::positionAboveKeyboard, 600L);
        }, 200L);
    }

    private void showKeyboard() {
        if (!expanded || searchInput.getVisibility() != View.VISIBLE || !searchInput.hasFocus()) {
            return;
        }
        WindowCompat.getInsetsController(activity.getWindow(), searchInput)
                .show(WindowInsetsCompat.Type.ime());
        InputMethodManager inputMethodManager =
                (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private boolean handleTouch(View view, MotionEvent event) {
        if (expanded) {
            return false;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) searchBar.getLayoutParams();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (animator != null) {
                    animator.cancel();
                }
                touchDownX = event.getRawX();
                touchDownY = event.getRawY();
                dragStartX = params.leftMargin;
                dragStartY = params.topMargin;
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float deltaX = event.getRawX() - touchDownX;
                float deltaY = event.getRawY() - touchDownY;
                int touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
                if (!dragging && Math.hypot(deltaX, deltaY) >= touchSlop) {
                    dragging = true;
                }
                if (dragging) {
                    int maxX = Math.max(0, contentArea.getWidth() - dp(48));
                    int maxY = collapsedMaxY();
                    int x = Math.max(0, Math.min(maxX, dragStartX + Math.round(deltaX)));
                    int y = Math.max(0, Math.min(maxY, dragStartY + Math.round(deltaY)));
                    setBounds(dp(48), x, y);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    persistPosition();
                    dragging = false;
                } else {
                    view.performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
            default:
                return false;
        }
    }

    private void persistPosition() {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) searchBar.getLayoutParams();
        int maxX = Math.max(1, contentArea.getWidth() - dp(48));
        int maxY = Math.max(1, collapsedMaxY());
        prefs().edit()
                .putFloat(positionKey + POSITION_X_SUFFIX, params.leftMargin / (float) maxX)
                .putFloat(positionKey + POSITION_Y_SUFFIX, params.topMargin / (float) maxY)
                .apply();
        collapsedX = params.leftMargin;
        collapsedY = params.topMargin;
    }

    private void positionCollapsed() {
        int width = contentArea.getWidth();
        int height = contentArea.getHeight();
        if (width <= 0 || height <= 0 || expanded || dragging) {
            return;
        }
        int maxX = Math.max(0, width - dp(48));
        int maxY = collapsedMaxY();
        SharedPreferences preferences = prefs();
        int x;
        int y;
        if (preferences.contains(positionKey + POSITION_X_SUFFIX)
                && preferences.contains(positionKey + POSITION_Y_SUFFIX)) {
            float fractionX = clamp(preferences.getFloat(positionKey + POSITION_X_SUFFIX, 1f));
            float fractionY = clamp(preferences.getFloat(positionKey + POSITION_Y_SUFFIX, 1f));
            x = Math.round(maxX * fractionX);
            y = Math.round(maxY * fractionY);
        } else {
            x = Math.max(0, maxX - dp(12));
            y = Math.max(0, maxY - dp(10));
        }
        collapsedX = x;
        collapsedY = y;
        setBounds(dp(48), x, y);
    }

    private int collapsedMaxY() {
        return Math.max(0, contentArea.getHeight() - dp(48) - collapsedBottomOffset);
    }

    private void positionAboveKeyboard() {
        if (!expanded || contentArea.getHeight() <= 0) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) searchBar.getLayoutParams();
        int availableBottom = keyboardCoveredHeight > 0 ? keyboardCoveredHeight : 0;
        int safeTop = Math.max(
                dp(8),
                contentArea.getHeight() - availableBottom - searchBar.getHeight() - dp(12)
        );
        if (params.topMargin > safeTop) {
            animateBounds(params.width, params.leftMargin, safeTop, 150L);
        }
    }

    private void animateBounds(int targetWidth, int targetLeft, int targetTop, long duration) {
        if (animator != null) {
            animator.cancel();
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) searchBar.getLayoutParams();
        int startWidth = params.width;
        int startLeft = params.leftMargin;
        int startTop = params.topMargin;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.addUpdateListener(animation -> {
            float progress = (Float) animation.getAnimatedValue();
            setBounds(
                    startWidth + Math.round((targetWidth - startWidth) * progress),
                    startLeft + Math.round((targetLeft - startLeft) * progress),
                    startTop + Math.round((targetTop - startTop) * progress)
            );
        });
        animator.start();
    }

    private void setBounds(int width, int left, int top) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) searchBar.getLayoutParams();
        params.width = width;
        params.leftMargin = left;
        params.topMargin = top;
        params.gravity = Gravity.START | Gravity.TOP;
        searchBar.setLayoutParams(params);
    }

    private SharedPreferences prefs() {
        return activity.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private int dp(int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
