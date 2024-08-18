package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.flyun.base.BaseMessage;
import com.theokanning.openai.GoogleHttpException;
import com.theokanning.openai.completion.chat.ChatGCompletionRequest;
import com.theokanning.openai.completion.chat.ChatGCompletionResponse;
import com.theokanning.openai.completion.chat.ChatGGenerationConfig;
import com.theokanning.openai.completion.chat.ChatGMessage;
import com.theokanning.openai.completion.chat.ChatGMessagePart;
import com.theokanning.openai.completion.chat.ChatGMessageRole;
import com.theokanning.openai.service.LLMType;
import com.theokanning.openai.service.OpenAiService;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

import okhttp3.internal.Util;

/**
 * Created by flyun on 2023/12/23.
 */
public class ChangeGoogleApiKeyActivity extends BaseFragment {

    private EditTextBoldCursor firstNameField;
    private View doneButton;
    private View findKeyButton;

    private Theme.ResourcesProvider resourcesProvider;

    private final static int done_button = 1;
    private final static int find_key_button = 2;

    private OpenAiService openAiService;

    private volatile boolean isReq;

    public ChangeGoogleApiKeyActivity(Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
    }

    @Override
    public boolean onFragmentCreate() {

        String token = UserConfig.getInstance(currentAccount).apiKeyGoogle;
        String apiServer = UserConfig.getInstance(currentAccount).apiServerGoogle;
        openAiService = new OpenAiService(token, 5, apiServer, LLMType.google);

        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        if (openAiService != null) {
            openAiService.clean();
        }
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_avatar_actionBarSelectorBlue, resourcesProvider), false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon, resourcesProvider), false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("ChangeGoogleApiKey", R.string.ChangeGoogleApiKey));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (firstNameField.getText() != null) {
                        saveApiKey();
                    }
                } else if (id == find_key_button) {
                    Browser.openUrl(getParentActivity(),
                            LocaleController.getString("FindGoogleKeyUrl", R.string.FindGoogleKeyUrl));
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        findKeyButton = menu.addItemWithWidth(find_key_button, R.drawable.msg_link2, AndroidUtilities.dp(56), LocaleController.getString("FindKeyUrl", R.string.FindKeyUrl));
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_ab_done, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));

        LinearLayout linearLayout = new LinearLayout(context);
        fragmentView = linearLayout;
        fragmentView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ((LinearLayout) fragmentView).setOrientation(LinearLayout.VERTICAL);
        fragmentView.setOnTouchListener((v, event) -> true);

        firstNameField = new EditTextBoldCursor(context) {
            @Override
            protected Theme.ResourcesProvider getResourcesProvider() {
                return resourcesProvider;
            }
        };
        firstNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        firstNameField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider));
        firstNameField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        firstNameField.setBackgroundDrawable(null);
        firstNameField.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField), getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated), getThemedColor(Theme.key_windowBackgroundWhiteRedText3));
        firstNameField.setMaxLines(1);
        firstNameField.setLines(1);
        firstNameField.setSingleLine(true);
        firstNameField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        firstNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        firstNameField.setHint(LocaleController.formatApiKey(UserConfig.getInstance(currentAccount).apiKeyGoogle));
        firstNameField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        firstNameField.setCursorSize(AndroidUtilities.dp(20));
        firstNameField.setCursorWidth(1.5f);
        linearLayout.addView(firstNameField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 24, 24, 24, 0));

        TextView buttonTextView = new TextView(context);

        buttonTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        buttonTextView.setText(LocaleController.getString("ValidateTitle", R.string.ValidateTitle));

        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        buttonTextView.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));

        buttonTextView.setOnClickListener(view -> {
            if (getParentActivity() == null) {
                return;
            }
            verifyKey();
        });

        linearLayout.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 15, 16, 16));


        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        boolean animations = preferences.getBoolean("view_animations", true);
        if (!animations) {
            firstNameField.requestFocus();
            AndroidUtilities.showKeyboard(firstNameField);
        }
    }

    private void saveApiKey() {

        if (firstNameField.getText() == null) {
            return;
        }

        final String newFirst = firstNameField.getText().toString().replace("\n", "");
        String apiKey = UserConfig.getInstance(currentAccount).apiKeyGoogle;
        if (apiKey != null && apiKey.equals(newFirst)) {
            return;
        }
        if (newFirst.length() == 0) {

            AlertDialog alertDialog = AlertsCreator.createSimpleAlert(getContext(),
                    LocaleController.getString("ChangeGoogleApiKey", R.string.ChangeGoogleApiKey),
                    LocaleController.getString("ClearApiKey", R.string.ClearApiKey),
                    LocaleController.getString("OK", R.string.OK),
                    () -> {
                        changeApiKey(newFirst);
                    }, null).create();
            alertDialog.show();
        } else {
            changeApiKey(newFirst);
        }
    }

    private void changeApiKey(String newFirst) {

        if (!TextUtils.isEmpty(newFirst) && checkValue(newFirst)) return;

        UserConfig.getInstance(currentAccount).apiKeyGoogle = newFirst;
        UserConfig.getInstance(currentAccount).saveConfig(false);

        NotificationCenter.getInstance(currentAccount)
                .postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_GOOGLE_API_KEY);

        finishFragment();
    }

    private void verifyKey() {
        if (isReq) return;
        String newFirst;
        if (firstNameField.getText() == null) {
            return;
        } else if (firstNameField.getText().length() > 0){
            newFirst = firstNameField.getText().toString().replace("\n", "");
        } else {
            newFirst = UserConfig.getInstance(currentAccount).apiKeyGoogle;
        }

        if (TextUtils.isEmpty(newFirst) || checkValue(newFirst)) return;

        openAiService.changeLLMToken(newFirst, LLMType.google);

        isReq = true;

        ChatGCompletionRequest completionRequest = new ChatGCompletionRequest();

        // 配置模型
        ChatGGenerationConfig chatGGenerationConfig = ChatGGenerationConfig.builder()
                .temperature(1.0)
                .max_output_tokens(256)
                .build();

        completionRequest.setGenerationConfig(chatGGenerationConfig);

        List<ChatGMessage> chatGMessageList = new ArrayList<>();

        ChatGMessage systemMessage = new ChatGMessage();
        systemMessage.setRole(ChatGMessageRole.USER.value());

        List<ChatGMessagePart> parts = new ArrayList<>();
        // 添加文本
        ChatGMessagePart part = ChatGMessagePart.builder().text("hi").build();
        parts.add(part);
        systemMessage.setParts(parts);

        chatGMessageList.add(systemMessage);

        completionRequest.setContents(chatGMessageList);

        openAiService.createChatGCompletion(completionRequest, "gemini-pro",
                UserConfig.getGoogleVersion("gemini-pro"),
                new BaseMessage(), new OpenAiService.ResultGCallBack() {
                    @Override
                    public void onSuccess(ChatGCompletionResponse result) {
                        isReq = false;

                        AlertsCreator.showSimpleAlert(ChangeGoogleApiKeyActivity.this,
                                LocaleController.getString("ValidateSuccess", R.string.ValidateSuccess));
                    }

                    @Override
                    public void onError(GoogleHttpException error, Throwable throwable) {
                        AndroidUtilities.runOnUIThread(() -> {
                            String errorTx;
                            isReq = false;
                            if (error != null) {
                                errorTx = error.getMessage();
                            } else {
                                errorTx = SendMessagesHelper.formatError(throwable);
                            }

                            AlertsCreator.processError(errorTx, ChangeGoogleApiKeyActivity.this);
                        });
                    }

                    @Override
                    public void onLoading(boolean isLoading) {

                    }
                });
    }

    //检查添加的token格式是否正确参照Headers.checkValue
    private boolean checkValue(String value) {
        for (int i = 0, length = value.length(); i < length; i++) {
            char c = value.charAt(i);
            if ((c <= '\u001f' && c != '\t') || c >= '\u007f') {
                String errorTx = Util.format(
                        "Unexpected char %#04x at %d value: %s", (int) c, i, value);
                AlertsCreator.processError(errorTx, ChangeGoogleApiKeyActivity.this);
                return true;
            }
        }

        return false;
    }

    @Override
    public Theme.ResourcesProvider getResourceProvider() {
        return resourcesProvider;
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            AndroidUtilities.runOnUIThread(() -> {
                if (firstNameField != null) {
                    firstNameField.requestFocus();
                    AndroidUtilities.showKeyboard(firstNameField);
                }
            }, 100);
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));

        return themeDescriptions;
    }

}
