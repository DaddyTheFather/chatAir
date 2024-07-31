/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.TopicsFragment;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import androidx.core.content.ContextCompat;

//actionBar中间显示UI
public class ChatAvatarContainer extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private BackupImageView avatarImageView;
    private SimpleTextView titleTextView;
    private AtomicReference<SimpleTextView> titleTextLargerCopyView = new AtomicReference<>();
    private SimpleTextView subtitleTextView;
    private AtomicReference<SimpleTextView> subtitleTextLargerCopyView = new AtomicReference<>();
    private ImageView timeItem;
    private TimerDrawable timerDrawable;
    private ChatActivity parentFragment;
    private StatusDrawable[] statusDrawables = new StatusDrawable[6];
    private AvatarDrawable avatarDrawable = new AvatarDrawable();
    private int currentAccount = UserConfig.selectedAccount;
    private boolean occupyStatusBar = true;
    private int leftPadding = AndroidUtilities.dp(8);
    StatusDrawable currentTypingDrawable;

    private int lastWidth = -1;
    private int largerWidth = -1;


    private AnimatorSet titleAnimation;

    private boolean[] isOnline = new boolean[1];
    public boolean[] statusMadeShorter = new boolean[1];

    private boolean secretChatTimer;

    private int onlineCount = -1;
    private int currentConnectionState;
    private CharSequence lastSubtitle;
    private String lastSubtitleColorKey;
    private Integer overrideSubtitleColor;

    private SharedMediaLayout.SharedMediaPreloader sharedMediaPreloader;
    private Theme.ResourcesProvider resourcesProvider;

    public boolean allowShorterStatus = false;
    public boolean premiumIconHiddable = false;

    private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emojiStatusDrawable;

    private class SimpleTextConnectedView extends SimpleTextView {

        private AtomicReference<SimpleTextView> reference;
        public SimpleTextConnectedView(Context context, AtomicReference<SimpleTextView> reference) {
            super(context);
            this.reference = reference;
        }

        @Override
        public void setTranslationY(float translationY) {
            if (reference != null) {
                SimpleTextView connected = reference.get();
                if (connected != null) {
                    connected.setTranslationY(translationY);
                }
            }
            super.setTranslationY(translationY);
        }

        @Override
        public boolean setText(CharSequence value) {
            if (reference != null) {
                SimpleTextView connected = reference.get();
                if (connected != null) {
                    connected.setText(value);
                }
            }
            return super.setText(value);
        }
    }

    public ChatAvatarContainer(Context context, BaseFragment baseFragment, boolean needTime) {
        this(context, baseFragment, needTime, null);
    }
    
    public ChatAvatarContainer(Context context, BaseFragment baseFragment, boolean needTime, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        if (baseFragment instanceof ChatActivity) {
            parentFragment = (ChatActivity) baseFragment;
        }

        //标题头像
        final boolean avatarClickable = parentFragment != null && parentFragment.getChatMode() == 0 && !UserObject.isReplyUser(parentFragment.getCurrentUser());
        avatarImageView = new BackupImageView(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                if (avatarClickable && getImageReceiver().hasNotThumb()) {
                    info.setText(LocaleController.getString("AccDescrProfilePicture", R.string.AccDescrProfilePicture));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, LocaleController.getString("Open", R.string.Open)));
                    }
                } else {
                    info.setVisibleToUser(false);
                }
            }
        };
        if (baseFragment instanceof ChatActivity || baseFragment instanceof TopicsFragment) {
            sharedMediaPreloader = new SharedMediaLayout.SharedMediaPreloader(baseFragment);
            if (parentFragment != null && (parentFragment.isThreadChat() || parentFragment.getChatMode() == 2)) {
                avatarImageView.setVisibility(GONE);
            }
        }
        avatarImageView.setContentDescription(LocaleController.getString("AccDescrProfilePicture", R.string.AccDescrProfilePicture));
        avatarImageView.setRoundRadius(AndroidUtilities.dp(21));
        addView(avatarImageView);
        if (avatarClickable) {
            //头像点击
            avatarImageView.setOnClickListener(v -> {
                if (!onAvatarClick()) {
                    openProfile(true);
                }
            });
        }

        titleTextView = new SimpleTextConnectedView(context, titleTextLargerCopyView);
        titleTextView.setEllipsizeByGradient(true);
        titleTextView.setTextColor(getThemedColor(Theme.key_actionBarDefaultTitle));
        titleTextView.setTextSize(18);
        titleTextView.setGravity(Gravity.LEFT);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextView.setLeftDrawableTopPadding(-AndroidUtilities.dp(1.3f));
        titleTextView.setCanHideRightDrawable(false);
        titleTextView.setRightDrawableOutside(true);
        titleTextView.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(12));
        addView(titleTextView);

        subtitleTextView = new SimpleTextConnectedView(context, subtitleTextLargerCopyView);
        subtitleTextView.setEllipsizeByGradient(true);
        subtitleTextView.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
        subtitleTextView.setTag(Theme.key_actionBarDefaultSubtitle);
        subtitleTextView.setTextSize(14);
        subtitleTextView.setGravity(Gravity.LEFT);
        subtitleTextView.setPadding(0, 0, AndroidUtilities.dp(10), 0);
        addView(subtitleTextView);

        if (parentFragment != null) {
            timeItem = new ImageView(context);
            timeItem.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(5), AndroidUtilities.dp(5));
            timeItem.setScaleType(ImageView.ScaleType.CENTER);
            timeItem.setAlpha(0.0f);
            timeItem.setScaleY(0.0f);
            timeItem.setScaleX(0.0f);
            timeItem.setVisibility(GONE);
            timeItem.setImageDrawable(timerDrawable = new TimerDrawable(context, resourcesProvider));
            addView(timeItem);
            secretChatTimer = needTime;

            timeItem.setOnClickListener(v -> {
                if (secretChatTimer) {
                    parentFragment.showDialog(AlertsCreator.createTTLAlert(getContext(), parentFragment.getCurrentEncryptedChat(), resourcesProvider).create());
                } else {
                    openSetTimer();
                }
            });
            if (secretChatTimer) {
                timeItem.setContentDescription(LocaleController.getString("SetTimer", R.string.SetTimer));
            } else {
                timeItem.setContentDescription(LocaleController.getString("AccAutoDeleteTimer", R.string.AccAutoDeleteTimer));
            }
        }

        if (parentFragment != null && parentFragment.getChatMode() == 0) {
            //整个标题界面点击
            if ((!parentFragment.isThreadChat() || parentFragment.isTopic) && !UserObject.isReplyUser(parentFragment.getCurrentUser())&& !BuildVars.IS_CHAT_AIR) {
                setOnClickListener(v -> openProfile(false));
            }

            TLRPC.Chat chat = parentFragment.getCurrentChat();
            statusDrawables[0] = new TypingDotsDrawable(true);//正在输入
            statusDrawables[1] = new RecordStatusDrawable(true);//正在录制声音
            statusDrawables[2] = new SendingFileDrawable(true);//正在发送文件
            statusDrawables[3] = new PlayingGameDrawable(false, resourcesProvider);//正在游戏
            statusDrawables[4] = new RoundStatusDrawable(true);//正在录制视频
            statusDrawables[5] = new ChoosingStickerStatusDrawable(true);//正在选择贴纸表情
            for (int a = 0; a < statusDrawables.length; a++) {
                statusDrawables[a].setIsChat(chat != null);
            }
        }

        emojiStatusDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(titleTextView, AndroidUtilities.dp(24));
    }

    protected boolean onAvatarClick() {
        if (!BuildVars.IS_CHAT_AIR) {
            return false;
        } else {
            return parentFragment.getCurrentUser() != null && parentFragment.getCurrentUser().self;
        }
    }

    public void setTitleExpand(boolean titleExpand) {
        int newRightPadding = titleExpand ? AndroidUtilities.dp(10) : 0;
        if (titleTextView.getPaddingRight() != newRightPadding) {
            titleTextView.setPadding(0, AndroidUtilities.dp(6), newRightPadding, AndroidUtilities.dp(12));
            requestLayout();
            invalidate();
        }
    }

    public void setOverrideSubtitleColor(Integer overrideSubtitleColor) {
        this.overrideSubtitleColor = overrideSubtitleColor;
    }

    public boolean openSetTimer() {
        if (parentFragment.getParentActivity() == null) {
            return false;
        }
        TLRPC.Chat chat = parentFragment.getCurrentChat();
        if (chat != null && !ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_DELETE_MESSAGES)) {
            if (timeItem.getTag() != null) {
                parentFragment.showTimerHint();
            }
            return false;
        }
        TLRPC.ChatFull chatInfo = parentFragment.getCurrentChatInfo();
        TLRPC.UserFull userInfo = parentFragment.getCurrentUserInfo();
        int ttl = 0;
        if (userInfo != null) {
            ttl = userInfo.ttl_period;
        } else if (chatInfo != null) {
            ttl = chatInfo.ttl_period;
        }

        ActionBarPopupWindow[] scrimPopupWindow = new ActionBarPopupWindow[1];
        AutoDeletePopupWrapper autoDeletePopupWrapper = new AutoDeletePopupWrapper(getContext(), null, new AutoDeletePopupWrapper.Callback() {
            @Override
            public void dismiss() {
                if (scrimPopupWindow[0] != null) {
                    scrimPopupWindow[0].dismiss();
                }
            }

            @Override
            public void setAutoDeleteHistory(int time, int action) {
                if (parentFragment == null) {
                    return;
                }
                parentFragment.getMessagesController().setDialogHistoryTTL(parentFragment.getDialogId(), time);
                TLRPC.ChatFull chatInfo = parentFragment.getCurrentChatInfo();
                TLRPC.UserFull userInfo = parentFragment.getCurrentUserInfo();
                if (userInfo != null || chatInfo != null) {
                    UndoView undoView = parentFragment.getUndoView();
                    if (undoView != null) {
                        undoView.showWithAction(parentFragment.getDialogId(), action, parentFragment.getCurrentUser(), userInfo != null ? userInfo.ttl_period : chatInfo.ttl_period, null, null);
                    }
                }

            }
        }, true, 0, resourcesProvider);
        autoDeletePopupWrapper.updateItems(ttl);

        scrimPopupWindow[0] = new ActionBarPopupWindow(autoDeletePopupWrapper.windowLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                if (parentFragment != null) {
                    parentFragment.dimBehindView(false);
                }
            }
        };
        scrimPopupWindow[0].setPauseNotifications(true);
        scrimPopupWindow[0].setDismissAnimationDuration(220);
        scrimPopupWindow[0].setOutsideTouchable(true);
        scrimPopupWindow[0].setClippingEnabled(true);
        scrimPopupWindow[0].setAnimationStyle(R.style.PopupContextAnimation);
        scrimPopupWindow[0].setFocusable(true);
        autoDeletePopupWrapper.windowLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
        scrimPopupWindow[0].setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        scrimPopupWindow[0].getContentView().setFocusableInTouchMode(true);
        scrimPopupWindow[0].showAtLocation(avatarImageView, 0, (int) (avatarImageView.getX() + getX()), (int) avatarImageView.getY());
        parentFragment.dimBehindView(true);
        return true;
    }

    public void openProfile(boolean byAvatar) {
        openProfile(byAvatar, true);
    }

    //点击标题头像
    public void openProfile(boolean byAvatar, boolean fromChatAnimation) {
        if (byAvatar && (AndroidUtilities.isTablet() || AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y || !avatarImageView.getImageReceiver().hasNotThumb())) {
            byAvatar = false;
        }
        TLRPC.User user = parentFragment.getCurrentUser();
        TLRPC.Chat chat = parentFragment.getCurrentChat();
        ImageReceiver imageReceiver = avatarImageView.getImageReceiver();
        String key = imageReceiver.getImageKey();
        ImageLoader imageLoader = ImageLoader.getInstance();
        if (key != null && !imageLoader.isInMemCache(key, false)) {
            Drawable drawable = imageReceiver.getDrawable();
            if (drawable instanceof BitmapDrawable && !(drawable instanceof AnimatedFileDrawable)) {
                imageLoader.putImageToCache((BitmapDrawable) drawable, key, false);
            }
        }

        if (user != null) {
            Bundle args = new Bundle();
            if (UserObject.isUserSelf(user)) {
                args.putLong("dialog_id", parentFragment.getDialogId());
                int[] media = new int[MediaDataController.MEDIA_TYPES_COUNT];
                System.arraycopy(sharedMediaPreloader.getLastMediaCount(), 0, media, 0, media.length);
                MediaActivity fragment = new MediaActivity(args, sharedMediaPreloader);
                fragment.setChatInfo(parentFragment.getCurrentChatInfo());
                parentFragment.presentFragment(fragment);
            } else {
                args.putLong("user_id", user.id);
                args.putBoolean("reportSpam", parentFragment.hasReportSpam());
                args.putBoolean("isChatAirUser", BuildVars.IS_CHAT_AIR);
                if (timeItem != null) {
                    args.putLong("dialog_id", parentFragment.getDialogId());
                }
                args.putInt("actionBarColor", getThemedColor(Theme.key_actionBarDefault));
                ProfileActivity fragment = new ProfileActivity(args, sharedMediaPreloader);
                fragment.setUserInfo(parentFragment.getCurrentUserInfo());
                if (fromChatAnimation) {
                    fragment.setPlayProfileAnimation(byAvatar ? 2 : 1);
                }
                parentFragment.presentFragment(fragment);
            }
        } else if (chat != null) {
            Bundle args = new Bundle();
            args.putLong("chat_id", chat.id);
            if (parentFragment.isTopic) {
                args.putInt("topic_id", parentFragment.getThreadMessage().getId());
            }
            ProfileActivity fragment = new ProfileActivity(args, sharedMediaPreloader);
            fragment.setChatInfo(parentFragment.getCurrentChatInfo());
            if (fromChatAnimation) {
                fragment.setPlayProfileAnimation(byAvatar ? 2 : 1);
            }
            parentFragment.presentFragment(fragment);
        }
    }

    public void setOccupyStatusBar(boolean value) {
        occupyStatusBar = value;
    }

    public void setTitleColors(int title, int subtitle) {
        titleTextView.setTextColor(title);
        subtitleTextView.setTextColor(subtitle);
        subtitleTextView.setTag(subtitle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec) + titleTextView.getPaddingRight();
        int availableWidth = width - AndroidUtilities.dp((avatarImageView.getVisibility() == VISIBLE ? 54 : 0) + 16);
        avatarImageView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(42), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(42), MeasureSpec.EXACTLY));
        titleTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24 + 8) + titleTextView.getPaddingRight(), MeasureSpec.AT_MOST));
        subtitleTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.AT_MOST));
        if (timeItem != null) {
            timeItem.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(34), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(34), MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec));
        if (lastWidth != -1 && lastWidth != width && lastWidth > width) {
            fadeOutToLessWidth(lastWidth);
        }
        SimpleTextView titleTextLargerCopyView = this.titleTextLargerCopyView.get();
        if (titleTextLargerCopyView != null) {
            int largerAvailableWidth = largerWidth - AndroidUtilities.dp((avatarImageView.getVisibility() == VISIBLE ? 54 : 0) + 16);
            titleTextLargerCopyView.measure(MeasureSpec.makeMeasureSpec(largerAvailableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.AT_MOST));
        }
        lastWidth = width;
    }

    private void fadeOutToLessWidth(int largerWidth) {
        this.largerWidth = largerWidth;
        SimpleTextView titleTextLargerCopyView = this.titleTextLargerCopyView.get();
        if (titleTextLargerCopyView != null) {
            removeView(titleTextLargerCopyView);
        }
        titleTextLargerCopyView = new SimpleTextView(getContext());
        this.titleTextLargerCopyView.set(titleTextLargerCopyView);
        titleTextLargerCopyView.setTextColor(getThemedColor(Theme.key_actionBarDefaultTitle));
        titleTextLargerCopyView.setTextSize(18);
        titleTextLargerCopyView.setGravity(Gravity.LEFT);
        titleTextLargerCopyView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextLargerCopyView.setLeftDrawableTopPadding(-AndroidUtilities.dp(1.3f));
        titleTextLargerCopyView.setRightDrawable(titleTextView.getRightDrawable());
        titleTextLargerCopyView.setRightDrawableOutside(titleTextView.getRightDrawableOutside());
        titleTextLargerCopyView.setLeftDrawable(titleTextView.getLeftDrawable());
        titleTextLargerCopyView.setText(titleTextView.getText());
        titleTextLargerCopyView.animate().alpha(0).setDuration(350).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).withEndAction(() -> {
            SimpleTextView titleTextLargerCopyView2 = this.titleTextLargerCopyView.get();
            if (titleTextLargerCopyView2 != null) {
                removeView(titleTextLargerCopyView2);
                this.titleTextLargerCopyView.set(null);
            }
        }).start();
        addView(titleTextLargerCopyView);

        SimpleTextView subtitleTextLargerCopyView = this.subtitleTextLargerCopyView.get();
        if (subtitleTextLargerCopyView != null) {
            removeView(subtitleTextLargerCopyView);
        }
        subtitleTextLargerCopyView = new SimpleTextView(getContext());
        this.subtitleTextLargerCopyView.set(subtitleTextLargerCopyView);
        subtitleTextLargerCopyView.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
        subtitleTextLargerCopyView.setTag(Theme.key_actionBarDefaultSubtitle);
        subtitleTextLargerCopyView.setTextSize(14);
        subtitleTextLargerCopyView.setGravity(Gravity.LEFT);
        subtitleTextLargerCopyView.setText(subtitleTextView.getText());
        subtitleTextLargerCopyView.animate().alpha(0).setDuration(350).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).withEndAction(() -> {
            SimpleTextView subtitleTextLargerCopyView2 = this.subtitleTextLargerCopyView.get();
            if (subtitleTextLargerCopyView2 != null) {
                removeView(subtitleTextLargerCopyView2);
                this.subtitleTextLargerCopyView.set(null);
                setClipChildren(true);
            }
        }).start();
        addView(subtitleTextLargerCopyView);

        setClipChildren(false);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int actionBarHeight = ActionBar.getCurrentActionBarHeight();
        int viewTop = (actionBarHeight - AndroidUtilities.dp(42)) / 2 + (Build.VERSION.SDK_INT >= 21 && occupyStatusBar ? AndroidUtilities.statusBarHeight : 0);
        avatarImageView.layout(leftPadding, viewTop + 1, leftPadding + AndroidUtilities.dp(42), viewTop + 1 + AndroidUtilities.dp(42));
        int l = leftPadding + (avatarImageView.getVisibility() == VISIBLE ? AndroidUtilities.dp( 54) : 0);
        SimpleTextView titleTextLargerCopyView = this.titleTextLargerCopyView.get();
        if (subtitleTextView.getVisibility() != GONE) {
            titleTextView.layout(l, viewTop + AndroidUtilities.dp(1.3f) - titleTextView.getPaddingTop(), l + titleTextView.getMeasuredWidth(), viewTop + titleTextView.getTextHeight() + AndroidUtilities.dp(1.3f) - titleTextView.getPaddingTop() + titleTextView.getPaddingBottom());
            if (titleTextLargerCopyView != null) {
                titleTextLargerCopyView.layout(l, viewTop + AndroidUtilities.dp(1.3f), l + titleTextLargerCopyView.getMeasuredWidth(), viewTop + titleTextLargerCopyView.getTextHeight() + AndroidUtilities.dp(1.3f));
            }
        } else {
            titleTextView.layout(l, viewTop + AndroidUtilities.dp(11) - titleTextView.getPaddingTop(), l + titleTextView.getMeasuredWidth(), viewTop + titleTextView.getTextHeight() + AndroidUtilities.dp(11) - titleTextView.getPaddingTop() + titleTextView.getPaddingBottom());
            if (titleTextLargerCopyView != null) {
                titleTextLargerCopyView.layout(l, viewTop + AndroidUtilities.dp(11), l + titleTextLargerCopyView.getMeasuredWidth(), viewTop + titleTextLargerCopyView.getTextHeight() + AndroidUtilities.dp(11));
            }
        }
        if (timeItem != null) {
            timeItem.layout(leftPadding + AndroidUtilities.dp(16), viewTop + AndroidUtilities.dp(15), leftPadding + AndroidUtilities.dp(16 + 34), viewTop + AndroidUtilities.dp(15 + 34));
        }
        subtitleTextView.layout(l, viewTop + AndroidUtilities.dp(24), l + subtitleTextView.getMeasuredWidth(), viewTop + subtitleTextView.getTextHeight() + AndroidUtilities.dp(24));
        SimpleTextView subtitleTextLargerCopyView = this.subtitleTextLargerCopyView.get();
        if (subtitleTextLargerCopyView != null) {
            subtitleTextLargerCopyView.layout(l, viewTop + AndroidUtilities.dp(24), l + subtitleTextLargerCopyView.getMeasuredWidth(), viewTop + subtitleTextLargerCopyView.getTextHeight() + AndroidUtilities.dp(24));
        }
    }

    public void setLeftPadding(int value) {
        leftPadding = value;
    }

    public void showTimeItem(boolean animated) {
        if (timeItem == null || timeItem.getTag() != null || avatarImageView.getVisibility() != View.VISIBLE) {
            return;
        }
        timeItem.clearAnimation();
        timeItem.setVisibility(VISIBLE);
        timeItem.setTag(1);
        if (animated) {
            timeItem.animate().setDuration(180).alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setListener(null).start();
        } else {
            timeItem.setAlpha(1.0f);
            timeItem.setScaleY(1.0f);
            timeItem.setScaleX(1.0f);
        }
    }

    public void hideTimeItem(boolean animated) {
        if (timeItem == null || timeItem.getTag() == null) {
            return;
        }
        timeItem.clearAnimation();
        timeItem.setTag(null);
        if (animated) {
            timeItem.animate().setDuration(180).alpha(0.0f).scaleX(0.0f).scaleY(0.0f).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    timeItem.setVisibility(GONE);
                    super.onAnimationEnd(animation);
                }
            }).start();
        } else {
            timeItem.setVisibility(GONE);
            timeItem.setAlpha(0.0f);
            timeItem.setScaleY(0.0f);
            timeItem.setScaleX(0.0f);
        }
    }

    public void setTime(int value, boolean animated) {
        if (timerDrawable == null) {
            return;
        }
        boolean show = true;
        if (value == 0 && !secretChatTimer) {
            show = false;
            return;
        }
        if (show) {
            showTimeItem(animated);
            timerDrawable.setTime(value);
        } else {
            hideTimeItem(animated);
        }
    }

    private boolean rightDrawableIsScamOrVerified = false;
    private String rightDrawableContentDescription = null;

    public void setTitleIcons(Drawable leftIcon, Drawable mutedIcon) {
        titleTextView.setLeftDrawable(leftIcon);
        if (!rightDrawableIsScamOrVerified) {
            if (mutedIcon != null) {
                rightDrawableContentDescription = LocaleController.getString("NotificationsMuted", R.string.NotificationsMuted);
            } else {
                rightDrawableContentDescription = null;
            }
            titleTextView.setRightDrawable(mutedIcon);
        }
    }

    public void setTitle(CharSequence value) {
        setTitle(value, false, false, false, false, null, false);
    }

    public void setTitle(CharSequence value, boolean scam, boolean fake, boolean verified, boolean premium, TLRPC.EmojiStatus emojiStatus, boolean animated) {
        if (value != null) {
            value = Emoji.replaceEmoji(value, titleTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(24), false);
        }
        titleTextView.setText(value);
        if (scam || fake) {
            if (!(titleTextView.getRightDrawable() instanceof ScamDrawable)) {
                ScamDrawable drawable = new ScamDrawable(11, scam ? 0 : 1);
                drawable.setColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
                titleTextView.setRightDrawable(drawable);
//                titleTextView.setRightPadding(0);
                rightDrawableContentDescription = LocaleController.getString("ScamMessage", R.string.ScamMessage);
                rightDrawableIsScamOrVerified = true;
            }
        } else if (verified) {
            Drawable verifiedBackground = getResources().getDrawable(R.drawable.verified_area).mutate();
            verifiedBackground.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_profile_verifiedBackground), PorterDuff.Mode.MULTIPLY));
            Drawable verifiedCheck = getResources().getDrawable(R.drawable.verified_check).mutate();
            verifiedCheck.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_profile_verifiedCheck), PorterDuff.Mode.MULTIPLY));
            Drawable verifiedDrawable = new CombinedDrawable(verifiedBackground, verifiedCheck);
            titleTextView.setRightDrawable(verifiedDrawable);
//            titleTextView.setRightPadding(titleTextView.getPaddingRight());
            rightDrawableIsScamOrVerified = true;
            rightDrawableContentDescription = LocaleController.getString("AccDescrVerified", R.string.AccDescrVerified);
        } else if (premium) {
            boolean isStatus = emojiStatus instanceof TLRPC.TL_emojiStatus || emojiStatus instanceof TLRPC.TL_emojiStatusUntil && ((TLRPC.TL_emojiStatusUntil) emojiStatus).until > (int) (System.currentTimeMillis() / 1000);
//            if (premiumIconHiddable) {
//                titleTextView.setCanHideRightDrawable(!isStatus);
//            }
            if (titleTextView.getRightDrawable() instanceof AnimatedEmojiDrawable.WrapSizeDrawable &&
                ((AnimatedEmojiDrawable.WrapSizeDrawable) titleTextView.getRightDrawable()).getDrawable() instanceof AnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable) ((AnimatedEmojiDrawable.WrapSizeDrawable) titleTextView.getRightDrawable()).getDrawable()).removeView(titleTextView);
            }
            if (emojiStatus instanceof TLRPC.TL_emojiStatus) {
                emojiStatusDrawable.set(((TLRPC.TL_emojiStatus) emojiStatus).document_id, animated);
            } else if (emojiStatus instanceof TLRPC.TL_emojiStatusUntil && ((TLRPC.TL_emojiStatusUntil) emojiStatus).until > (int) (System.currentTimeMillis() / 1000)) {
                emojiStatusDrawable.set(((TLRPC.TL_emojiStatusUntil) emojiStatus).document_id, animated);
            } else {
                Drawable drawable = ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.msg_premium_liststar).mutate();
                drawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_profile_verifiedBackground), PorterDuff.Mode.MULTIPLY));
                emojiStatusDrawable.set(drawable, animated);
            }
            emojiStatusDrawable.setColor(getThemedColor(Theme.key_profile_verifiedBackground));
            titleTextView.setRightDrawable(emojiStatusDrawable);
//            titleTextView.setRightPadding(titleTextView.getPaddingRight());
            rightDrawableIsScamOrVerified = true;
            rightDrawableContentDescription = LocaleController.getString("AccDescrPremium", R.string.AccDescrPremium);
        } else if (titleTextView.getRightDrawable() instanceof ScamDrawable) {
            titleTextView.setRightDrawable(null);
//            titleTextView.setRightPadding(0);
            rightDrawableIsScamOrVerified = false;
            rightDrawableContentDescription = null;
        }
    }

    public void setSubtitle(CharSequence value) {
        if (lastSubtitle == null) {
            subtitleTextView.setText(value);
        } else {
            lastSubtitle = value;
        }
    }

    public ImageView getTimeItem() {
        return timeItem;
    }

    public SimpleTextView getTitleTextView() {
        return titleTextView;
    }

    public SimpleTextView getSubtitleTextView() {
        return subtitleTextView;
    }

    public void onDestroy() {
        if (sharedMediaPreloader != null) {
            sharedMediaPreloader.onDestroy(parentFragment);
        }
    }

    //设置标题动画
    private void setTypingAnimation(boolean start) {
        if (start) {
            //开启副标题动画
            try {
//                int type = 0;
                int type = MessagesController.getInstance(currentAccount).getPrintingStringType(parentFragment.getDialogId(), parentFragment.getThreadId());
                if (type == 5) {
                    subtitleTextView.replaceTextWithDrawable(statusDrawables[type], "**oo**");
                    statusDrawables[type].setColor(getThemedColor(Theme.key_chat_status));
                    subtitleTextView.setLeftDrawable(null);
                } else {
                    subtitleTextView.replaceTextWithDrawable(null, null);
                    statusDrawables[type].setColor(getThemedColor(Theme.key_chat_status));
                    subtitleTextView.setLeftDrawable(statusDrawables[type]);
                }
                currentTypingDrawable = statusDrawables[type];
                for (int a = 0; a < statusDrawables.length; a++) {
                    if (a == type) {
                        statusDrawables[a].start();
                    } else {
                        statusDrawables[a].stop();
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else {
            //关闭副标题动画
            currentTypingDrawable = null;
            subtitleTextView.setLeftDrawable(null);
            subtitleTextView.replaceTextWithDrawable(null, null);
            for (int a = 0; a < statusDrawables.length; a++) {
                statusDrawables[a].stop();
            }
        }
    }

    public void updateSubtitle() {
        updateSubtitle(false);
    }

    //更新副标题
    //初始化，接受消息，发送信息时回调用
    public void updateSubtitle(boolean animated) {

        if (parentFragment == null) {
            return;
        }
        TLRPC.User user = parentFragment.getCurrentUser();
        //收藏夹或者回复自己等等，则隐藏副标题
        if (UserObject.isUserSelf(user) || UserObject.isReplyUser(user) || parentFragment.getChatMode() != 0) {
            if (subtitleTextView.getVisibility() != GONE) {
                subtitleTextView.setVisibility(GONE);
            }
            return;
        }
        TLRPC.Chat chat = parentFragment.getCurrentChat();
        //当前用户是否正在输入
        CharSequence printString = MessagesController.getInstance(currentAccount).getPrintingString(parentFragment.getDialogId(), parentFragment.getThreadId(), false);
        if (printString != null) {
            printString = TextUtils.replace(printString, new String[]{"..."}, new String[]{""});
        }
        CharSequence newSubtitle;
        boolean useOnlineColor = false;
        //printString = LocaleController.getString("Typing", R.string.Typing);
        if (printString == null || printString.length() == 0 || ChatObject.isChannel(chat) && !chat.megagroup) {
            //开启正在副标题输入动画
            //当前正在输入或者通知或者不是群组
            if (parentFragment.isThreadChat() && !parentFragment.isTopic) {
                //通知或者话题
                if (titleTextView.getTag() != null) {
                    return;
                }
                titleTextView.setTag(1);
                if (titleAnimation != null) {
                    titleAnimation.cancel();
                    titleAnimation = null;
                }
                if (animated) {
                    titleAnimation = new AnimatorSet();
                    titleAnimation.playTogether(
                            ObjectAnimator.ofFloat(titleTextView, View.TRANSLATION_Y, AndroidUtilities.dp(9.7f)),
                            ObjectAnimator.ofFloat(subtitleTextView, View.ALPHA, 0.0f));
                    titleAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationCancel(Animator animation) {
                            titleAnimation = null;
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (titleAnimation == animation) {
                                subtitleTextView.setVisibility(INVISIBLE);
                                titleAnimation = null;
                            }
                        }
                    });
                    titleAnimation.setDuration(180);
                    titleAnimation.start();
                } else {
                    titleTextView.setTranslationY(AndroidUtilities.dp(9.7f));
                    subtitleTextView.setAlpha(0.0f);
                    subtitleTextView.setVisibility(INVISIBLE);
                }
                return;
            }
            //关闭标题动画
            setTypingAnimation(false);
            if (parentFragment.isTopic && chat != null) {
                TLRPC.TL_forumTopic topic = MessagesController.getInstance(currentAccount).getTopicsController().findTopic(chat.id, parentFragment.getTopicId());
                int count = 0;
                if (topic != null) {
                    count = topic.totalMessagesCount - 1;
                }
                if (count > 0) {
                    newSubtitle = LocaleController.formatPluralString("messages", count, count);
                } else {
                    newSubtitle = LocaleController.formatString("TopicProfileStatus", R.string.TopicProfileStatus, chat.title);
                }
            } else if (chat != null) {
                TLRPC.ChatFull info = parentFragment.getCurrentChatInfo();
                if (ChatObject.isChannel(chat)) {
                    if (info != null && info.participants_count != 0) {
                        if (chat.megagroup) {
                            if (onlineCount > 1) {
                                newSubtitle = String.format("%s, %s", LocaleController.formatPluralString("Members", info.participants_count), LocaleController.formatPluralString("OnlineCount", Math.min(onlineCount, info.participants_count)));
                            } else {
                                newSubtitle = LocaleController.formatPluralString("Members", info.participants_count);
                            }
                        } else {
                            int[] result = new int[1];
                            boolean ignoreShort = AndroidUtilities.isAccessibilityScreenReaderEnabled();
                            String shortNumber = ignoreShort ? String.valueOf(result[0] = info.participants_count) : LocaleController.formatShortNumber(info.participants_count, result);
                            if (chat.megagroup) {
                                newSubtitle = LocaleController.formatPluralString("Members", result[0]).replace(String.format("%d", result[0]), shortNumber);
                            } else {
                                newSubtitle = LocaleController.formatPluralString("Subscribers", result[0]).replace(String.format("%d", result[0]), shortNumber);
                            }
                        }
                    } else {
                        if (chat.megagroup) {
                            if (info == null) {
                                newSubtitle = LocaleController.getString("Loading", R.string.Loading).toLowerCase();
                            } else {
                                if (chat.has_geo) {
                                    newSubtitle = LocaleController.getString("MegaLocation", R.string.MegaLocation).toLowerCase();
                                } else if (ChatObject.isPublic(chat)) {
                                    newSubtitle = LocaleController.getString("MegaPublic", R.string.MegaPublic).toLowerCase();
                                } else {
                                    newSubtitle = LocaleController.getString("MegaPrivate", R.string.MegaPrivate).toLowerCase();
                                }
                            }
                        } else {
                            if (ChatObject.isPublic(chat)) {
                                newSubtitle = LocaleController.getString("ChannelPublic", R.string.ChannelPublic).toLowerCase();
                            } else {
                                newSubtitle = LocaleController.getString("ChannelPrivate", R.string.ChannelPrivate).toLowerCase();
                            }
                        }
                    }
                } else {
                    if (ChatObject.isKickedFromChat(chat)) {
                        newSubtitle = LocaleController.getString("YouWereKicked", R.string.YouWereKicked);
                    } else if (ChatObject.isLeftFromChat(chat)) {
                        newSubtitle = LocaleController.getString("YouLeft", R.string.YouLeft);
                    } else {
                        int count = chat.participants_count;
                        if (info != null && info.participants != null) {
                            count = info.participants.participants.size();
                        }
                        if (onlineCount > 1 && count != 0) {
                            newSubtitle = String.format("%s, %s", LocaleController.formatPluralString("Members", count), LocaleController.formatPluralString("OnlineCount", onlineCount));
                        } else {
                            newSubtitle = LocaleController.formatPluralString("Members", count);
                        }
                    }
                }
            } else if (user != null && !BuildVars.IS_CHAT_AIR) {
                //正常执行，获取当前聊天用户信息
                TLRPC.User newUser = MessagesController.getInstance(currentAccount).getUser(user.id);
                if (newUser != null) {
                    user = newUser;
                }
                String newStatus;
                if (UserObject.isReplyUser(user)) {
                    newStatus = "";
                } else if (user.id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                    newStatus = LocaleController.getString("ChatYourSelf", R.string.ChatYourSelf);
                } else if (user.id == 333000 || user.id == 777000 || user.id == 42777) {
                    newStatus = LocaleController.getString("ServiceNotifications", R.string.ServiceNotifications);
                } else if (MessagesController.isSupportUser(user)) {
                    newStatus = LocaleController.getString("SupportStatus", R.string.SupportStatus);
                } else if (user.bot) {
                    newStatus = LocaleController.getString("Bot", R.string.Bot);
                } else {
                    isOnline[0] = false;
                    //获取最近在线时间文本
                    //考虑替换为剩余token显示
                    newStatus = LocaleController.formatUserStatus(currentAccount, user, isOnline, allowShorterStatus ? statusMadeShorter : null);
                    useOnlineColor = isOnline[0];
                }
                newSubtitle = newStatus;
            } else if (BuildVars.IS_CHAT_AIR && animated) {

//                int contextNum = 0;
//                long tokens = 0;
//                long words = 0;

//                if (user != null) {
//                    contextNum = user.status.contextNum;
//                    tokens = user.status.tokens;
//                    words = user.status.words;
//                }

                //todo 暂时方案，虽然加上数据变动才会变化，但是也会频繁刷新。
                // 后期改为数据变动发送user数据，这里读取user数据，就不会出现打开聊天界面延时显示问题
                ArrayList<MessageObject> messages = parentFragment.messages;
                int tempContextNum = 0;
                long tempTokens = 0;
                long tempWords = 0;

                int contextLimit;
                if (user != null && (user.flags2
                        & MessagesController.UPDATE_MASK_CHAT_AIR_AI_CONTEXT_LIMIT) != 0) {
                    contextLimit = user.contextLimit;
                } else {
                    contextLimit = UserConfig.getInstance(currentAccount).contextLimit;
                }

                if (user != null && !TextUtils.isEmpty(user.prompt)) {
                    tempWords += user.prompt.length();
                }

                if (messages != null && !messages.isEmpty()) {

                    for (MessageObject message : messages) {

                        if (message != null) {

                            if (message.type == 10 && message.messageOwner.action
                                    instanceof TLRPC.TL_messageActionClearContext) {
                                break;
                            }

                            if (message.type == 0){
                                if (tempContextNum >= contextLimit) break;
                                tempContextNum++;

                                //todo 如果删掉聊天中间的一部分，会造成token不准
                                if (tempTokens == 0) {
                                    tempTokens = message.messageOwner.promptTokens + message.messageOwner.completionTokens;
                                }

                                tempWords = tempWords
                                        + message.messageText.length();
                            }
                        }
                    }

                    //数据变动则更新
//                    if (contextNum != tempContextNum || tokens != tempTokens || words != tempWords) {
//                        ArrayList<TLRPC.Update> arrayList = new ArrayList<>();
//
//                        TLRPC.TL_updateUserStatus userStatus = new TLRPC.TL_updateUserStatus();
//
//                        userStatus.user_id = user.id;
//                        userStatus.disableFree = user.disableFree;
//                        userStatus.networkType = user.networkType;
//                        userStatus.status =new TLRPC.TL_userStatusChatAir();
//                        userStatus.status.expires = user.status.expires;
//                        userStatus.status.contextNum = contextNum;
//                        userStatus.status.tokens = tokens;
//                        userStatus.status.words = words;
//
//                        arrayList.add(userStatus);
//
//                        parentFragment.getMessagesController().processUpdateArray(arrayList, null, null, false, 0);
//
//                    }
                }

                boolean switchSubtitleContent = UserConfig.getInstance(currentAccount).switchSubtitleContent;

                if (!switchSubtitleContent) {
                    newSubtitle = LocaleController.formatUserStatus(tempContextNum, contextLimit,
                            tempTokens, tempWords);
                } else {
                    String aiModelReal = UserConfig.getUserAiModelName(currentAccount, user.id);
                    if (!TextUtils.isEmpty(aiModelReal)) {
                        newSubtitle = aiModelReal;
                    } else {
                        newSubtitle = "";
                    }

                }
                user.status.contextNum = tempContextNum;
                user.status.tokens = tempTokens;
                user.status.words = tempWords;

            }
            else {
                newSubtitle = "";
            }
        } else {
            //正在输入的动画
            if (parentFragment.isThreadChat()) {
                if (titleTextView.getTag() != null) {
                    titleTextView.setTag(null);
                    subtitleTextView.setVisibility(VISIBLE);
                    if (titleAnimation != null) {
                        titleAnimation.cancel();
                        titleAnimation = null;
                    }
                    if (animated) {
                        titleAnimation = new AnimatorSet();
                        titleAnimation.playTogether(
                                ObjectAnimator.ofFloat(titleTextView, View.TRANSLATION_Y, 0),
                                ObjectAnimator.ofFloat(subtitleTextView, View.ALPHA, 1.0f));
                        titleAnimation.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                titleAnimation = null;
                            }
                        });
                        titleAnimation.setDuration(180);
                        titleAnimation.start();
                    } else {
                        titleTextView.setTranslationY(0.0f);
                        subtitleTextView.setAlpha(1.0f);
                    }
                }
            }
            newSubtitle = printString;
            //关闭输入表情查找
            if (MessagesController.getInstance(currentAccount).getPrintingStringType(parentFragment.getDialogId(), parentFragment.getThreadId()) == 5) {
                newSubtitle = Emoji.replaceEmoji(newSubtitle, subtitleTextView.getTextPaint().getFontMetricsInt(), AndroidUtilities.dp(15), false);
            }
            useOnlineColor = true;
            setTypingAnimation(true);
        }
        //设置子标题用户在线颜色
        lastSubtitleColorKey = useOnlineColor ? Theme.key_chat_status : Theme.key_actionBarDefaultSubtitle;
        if (lastSubtitle == null) {
            //设置子标题文字
            subtitleTextView.setText(newSubtitle);
            if (overrideSubtitleColor == null) {
                //设置子标题文字颜色
                subtitleTextView.setTextColor(getThemedColor(lastSubtitleColorKey));
                subtitleTextView.setTag(lastSubtitleColorKey);
            } else {
                subtitleTextView.setTextColor(overrideSubtitleColor);
            }
        } else {
            lastSubtitle = newSubtitle;
        }
    }

    public String getLastSubtitleColorKey() {
        return lastSubtitleColorKey;
    }

    public void setChatAvatar(TLRPC.Chat chat) {
        avatarDrawable.setInfo(chat);
        if (avatarImageView != null) {
            avatarImageView.setForUserOrChat(chat, avatarDrawable);
            avatarImageView.setRoundRadius(chat != null && chat.forum ? AndroidUtilities.dp(16) : AndroidUtilities.dp(21));
        }
    }

    public void setUserAvatar(TLRPC.User user) {
        setUserAvatar(user, false);
    }

    public void setUserAvatar(TLRPC.User user, boolean showSelf) {
        avatarDrawable.setInfo(user);
        if (UserObject.isReplyUser(user)) {
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
            avatarDrawable.setScaleSize(.8f);
            if (avatarImageView != null) {
                avatarImageView.setImage(null, null, avatarDrawable, user);
            }
        } else if (UserObject.isUserSelf(user) && !showSelf) {
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
            avatarDrawable.setScaleSize(.8f);
            if (avatarImageView != null) {
                avatarImageView.setImage(null, null, avatarDrawable, user);
            }
        } else {
            avatarDrawable.setScaleSize(1f);
            if (avatarImageView != null) {
                avatarImageView.setForUserOrChat(user, avatarDrawable);
            }
        }
    }

    public void checkAndUpdateAvatar() {
        if (parentFragment == null) {
            return;
        }
        TLRPC.User user = parentFragment.getCurrentUser();
        TLRPC.Chat chat = parentFragment.getCurrentChat();
        if (user != null) {
            avatarDrawable.setInfo(user);
            if (UserObject.isReplyUser(user)) {
                avatarDrawable.setScaleSize(.8f);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                if (avatarImageView != null) {
                    avatarImageView.setImage(null, null, avatarDrawable, user);
                }
            } else if (UserObject.isUserSelf(user)) {
                avatarDrawable.setScaleSize(.8f);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                if (avatarImageView != null) {
                    avatarImageView.setImage(null, null, avatarDrawable, user);
                }
            } else {
                avatarDrawable.setScaleSize(1f);
                if (avatarImageView != null) {
                    avatarImageView.imageReceiver.setForUserOrChat(user, avatarDrawable,  null, true, VectorAvatarThumbDrawable.TYPE_STATIC);
                }
            }
        } else if (chat != null) {
            avatarDrawable.setInfo(chat);
            if (avatarImageView != null) {
                avatarImageView.setForUserOrChat(chat, avatarDrawable);
            }
            avatarImageView.setRoundRadius(chat.forum ? AndroidUtilities.dp(16) : AndroidUtilities.dp(21));
        }
    }

    public void updateOnlineCount() {
        if (parentFragment == null) {
            return;
        }
        onlineCount = 0;
        TLRPC.ChatFull info = parentFragment.getCurrentChatInfo();
        if (info == null) {
            return;
        }
        int currentTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        if (info instanceof TLRPC.TL_chatFull || info instanceof TLRPC.TL_channelFull && info.participants_count <= 200 && info.participants != null) {
            for (int a = 0; a < info.participants.participants.size(); a++) {
                TLRPC.ChatParticipant participant = info.participants.participants.get(a);
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(participant.user_id);
                if (user != null && user.status != null && (user.status.expires > currentTime || user.id == UserConfig.getInstance(currentAccount).getClientUserId()) && user.status.expires > 10000) {
                    onlineCount++;
                }
            }
        } else if (info instanceof TLRPC.TL_channelFull && info.participants_count > 200) {
            onlineCount = info.online_count;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (parentFragment != null) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didUpdateConnectionState);
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
            currentConnectionState = ConnectionsManager.getInstance(currentAccount).getConnectionState();
            updateCurrentConnectionState();
        }
        if (emojiStatusDrawable != null) {
            emojiStatusDrawable.attach();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (parentFragment != null) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didUpdateConnectionState);
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        }
        if (emojiStatusDrawable != null) {
            emojiStatusDrawable.detach();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didUpdateConnectionState) {
            int state = ConnectionsManager.getInstance(currentAccount).getConnectionState();
            if (currentConnectionState != state) {
                currentConnectionState = state;
                updateCurrentConnectionState();
            }
        } else if (id == NotificationCenter.emojiLoaded) {
            if (titleTextView != null) {
                titleTextView.invalidate();
            }
            if (subtitleTextView != null) {
                subtitleTextView.invalidate();
            }
            invalidate();
        }
    }

    private void updateCurrentConnectionState() {
        String title = null;
        if (currentConnectionState == ConnectionsManager.ConnectionStateWaitingForNetwork) {
            title = LocaleController.getString("WaitingForNetwork", R.string.WaitingForNetwork);
        } else if (currentConnectionState == ConnectionsManager.ConnectionStateConnecting) {
            title = LocaleController.getString("Connecting", R.string.Connecting);
        } else if (currentConnectionState == ConnectionsManager.ConnectionStateUpdating) {
            title = LocaleController.getString("Updating", R.string.Updating);
        } else if (currentConnectionState == ConnectionsManager.ConnectionStateConnectingToProxy) {
            title = LocaleController.getString("ConnectingToProxy", R.string.ConnectingToProxy);
        }
        if (title == null) {
            if (lastSubtitle != null) {
                subtitleTextView.setText(lastSubtitle);
                lastSubtitle = null;
                if (overrideSubtitleColor != null) {
                    subtitleTextView.setTextColor(overrideSubtitleColor);
                } else if (lastSubtitleColorKey != null) {
                    subtitleTextView.setTextColor(getThemedColor(lastSubtitleColorKey));
                    subtitleTextView.setTag(lastSubtitleColorKey);
                }
            }
        } else {
            if (lastSubtitle == null) {
                lastSubtitle = subtitleTextView.getText();
            }
            subtitleTextView.setText(title);
            if (overrideSubtitleColor != null) {
                subtitleTextView.setTextColor(overrideSubtitleColor);
            } else {
                subtitleTextView.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
                subtitleTextView.setTag(Theme.key_actionBarDefaultSubtitle);
            }
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        StringBuilder sb = new StringBuilder();
        sb.append(titleTextView.getText());
        if (rightDrawableContentDescription != null) {
            sb.append(", ");
            sb.append(rightDrawableContentDescription);
        }
        sb.append("\n");
        sb.append(subtitleTextView.getText());
        info.setContentDescription(sb);
        if (info.isClickable() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, LocaleController.getString("OpenProfile", R.string.OpenProfile)));
        }
    }

    public SharedMediaLayout.SharedMediaPreloader getSharedMediaPreloader() {
        return sharedMediaPreloader;
    }

    public BackupImageView getAvatarImageView() {
        return avatarImageView;
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }

    public void updateColors() {
        if (currentTypingDrawable != null) {
            currentTypingDrawable.setColor(getThemedColor(Theme.key_chat_status));
        }
    }
}
