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

import com.theokanning.openai.completion.chat.anthropic.AnthropicHttpException;
import com.theokanning.openai.completion.chat.anthropic.ChatACompletionRequest;
import com.theokanning.openai.completion.chat.anthropic.ChatACompletionResponse;
import com.theokanning.openai.completion.chat.anthropic.ChatAMessage;
import com.theokanning.openai.completion.chat.anthropic.ChatAMessageRole;
import com.theokanning.openai.completion.chat.anthropic.ChatARequestMessage;
import com.theokanning.openai.service.LLMType;
import com.theokanning.openai.service.OpenAiService;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by flyun on 2024/5/26.
 */
public class ChangeClaudeApiServerActivity extends BaseFragment {

    private EditTextBoldCursor firstNameField;
    private View doneButton;
    private View findKeyButton;

    private Theme.ResourcesProvider resourcesProvider;

    private final static int done_button = 1;
    private final static int find_key_button = 2;

    private OpenAiService openAiService;

    private volatile boolean isReq;

    public ChangeClaudeApiServerActivity(Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
    }

    @Override
    public boolean onFragmentCreate() {

        String token = UserConfig.getInstance(currentAccount).apiKeyClaude;
        String apiServer = UserConfig.getInstance(currentAccount).apiServerClaude;

        openAiService = new OpenAiService(token, 5, apiServer, LLMType.anthropic);

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
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_avatar_actionBarSelectorBlue,
                resourcesProvider), false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon, resourcesProvider),
                false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("ChangeApiServer", R.string.ChangeApiServer));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (firstNameField.getText() != null) {
                        saveApiServer();
                    }
                } else if (id == find_key_button) {
                    if (firstNameField != null) {
                        firstNameField.setText(UserConfig.defaultApiServerClaude);
                        firstNameField.setSelection(UserConfig.defaultApiServerClaude.length());
//                        AlertsCreator.showSimpleToast(ChangeApiServerActivity.this,
//                                LocaleController.getString("DefaultApiServe", R.string.DefaultApiServe));
                    }
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        findKeyButton = menu.addItemWithWidth(find_key_button, R.drawable.msg_link, AndroidUtilities.dp(56), LocaleController.getString("FindClaudeKeyUrl", R.string.FindClaudeKeyUrl));
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_ab_done, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));

        LinearLayout linearLayout = new LinearLayout(context);
        fragmentView = linearLayout;
        fragmentView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
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
        firstNameField.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField),
                getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated),
                getThemedColor(Theme.key_windowBackgroundWhiteRedText3));
        firstNameField.setMaxLines(1);
        firstNameField.setLines(1);
        firstNameField.setSingleLine(true);
        firstNameField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        firstNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        String apiServer = UserConfig.getInstance(currentAccount).apiServerClaude;
        firstNameField.setHint(UserConfig.defaultApiServerClaude);
        firstNameField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        firstNameField.setCursorSize(AndroidUtilities.dp(20));
        firstNameField.setCursorWidth(1.5f);

        if (UserConfig.defaultApiServerClaude.equals(apiServer)) {
            firstNameField.setText("");
        } else {
            firstNameField.setText(apiServer);
            firstNameField.setSelection(apiServer.length());
        }
        linearLayout.addView(firstNameField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                36, 24, 24, 24, 0));

        TextView helpTextView = new TextView(context);
        helpTextView.setFocusable(true);
        helpTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        helpTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText8));
        helpTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        helpTextView.setText(AndroidUtilities.replaceTags(LocaleController
                .getString("ChangeClaudeApiServerTips", R.string.ChangeClaudeApiServerTips)));
        linearLayout.addView(helpTextView,LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT,
                24, 10, 24, 0));

        TextView buttonTextView = new TextView(context);

        //因为服务器地址和密钥绑定，容易对用户造成混淆
        buttonTextView.setVisibility(View.GONE);

        buttonTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        buttonTextView.setText(LocaleController.getString("ValidateTitle", R.string.ValidateTitle));

        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        buttonTextView.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(
                AndroidUtilities.dp(6), Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));

        buttonTextView.setOnClickListener(view -> {
            if (getParentActivity() == null) {
                return;
            }
            verifyKey();
        });

        linearLayout.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                48, Gravity.BOTTOM, 16, 15, 16, 16));


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

    private void saveApiServer() {
        if (firstNameField.getText() == null) {
            return;
        }

        String newFirst = firstNameField.getText().toString().replace("\n", "");
        if (TextUtils.isEmpty(newFirst)) return;

        String formatUrl = formatUrl(newFirst);
        if (TextUtils.isEmpty(formatUrl)) {
            AlertsCreator.processError(LocaleController.getString("MalformedUrl", R.string.MalformedUrl),
                    ChangeClaudeApiServerActivity.this);
            return;
        }
        if (!newFirst.equals(formatUrl)) {
            newFirst = formatUrl;
            firstNameField.setText(formatUrl);
            firstNameField.setSelection(formatUrl.length());
        }

        String apiServer = UserConfig.getInstance(currentAccount).apiServerClaude;
        if (apiServer != null && apiServer.equals(newFirst)) {
            return;
        }

        UserConfig.getInstance(currentAccount).apiServerClaude = newFirst;
        UserConfig.getInstance(currentAccount).saveConfig(false);

        NotificationCenter.getInstance(currentAccount)
                .postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_CLAUDE_API_SERVER);

        finishFragment();
    }

    private void verifyKey() {
        if (isReq) return;
        String newFirst;
        if (firstNameField.getText() == null) {
            return;
        } else if (firstNameField.getText().length() > 0) {
            newFirst = firstNameField.getText().toString().replace("\n", "");
        } else {
            newFirst = UserConfig.getInstance(currentAccount).apiServerClaude;

        }

        if (TextUtils.isEmpty(newFirst)) return;

        String formatUrl = formatUrl(newFirst);
        if (formatUrl == null) {
            isReq = false;
            AlertsCreator.processError(LocaleController.getString("MalformedUrl", R.string.MalformedUrl),
                    ChangeClaudeApiServerActivity.this);
            return;
        }
        if (!newFirst.equals(formatUrl)) {
            newFirst = formatUrl;
            firstNameField.setText(formatUrl);
            firstNameField.setSelection(formatUrl.length());
        }

        openAiService.changeLLMServer(newFirst, LLMType.anthropic);

        isReq = true;

        ChatACompletionRequest completionRequest = ChatACompletionRequest.builder()
                .temperature(1.0)
                .maxTokens(256)
                .stream(false)
                .model("claude-3-opus-20240229")
                .build();

        List<ChatARequestMessage> chatMessageList = new ArrayList<>();
        ChatARequestMessage systemUserMessage = new ChatARequestMessage();
        systemUserMessage.setRole(ChatAMessageRole.USER.value());
        List<ChatAMessage> contents = new ArrayList<>();
        systemUserMessage.setContentText(contents, "hi");
        chatMessageList.add(systemUserMessage);
        completionRequest.setMessages(chatMessageList);

        openAiService.createChatACompletion(completionRequest, new OpenAiService.ResultACallBack() {
            @Override
            public void onSuccess(ChatACompletionResponse result) {
                AndroidUtilities.runOnUIThread(() -> {
                    isReq = false;

                    AlertsCreator.showSimpleAlert(ChangeClaudeApiServerActivity.this,
                            LocaleController.getString("ValidateSuccess", R.string.ValidateSuccess));
                });
            }

            @Override
            public void onError(AnthropicHttpException error, Throwable throwable) {
                AndroidUtilities.runOnUIThread(() -> {
                    String errorTx;
                    isReq = false;
                    if (error != null) {
                        errorTx = error.getMessage();
                    } else {
                        errorTx = SendMessagesHelper.formatError(throwable);
                    }

                    AlertsCreator.processError(errorTx, ChangeClaudeApiServerActivity.this);
                });
            }

            @Override
            public void onLoading(boolean isLoading) {

            }
        });

    }

    private String formatUrl (String url) {

        //添加、格式化https
        String formatUrl = LocaleController.formatApiUrl(url);

        if (TextUtils.isEmpty(formatUrl)) return null;

        //https协议格式整理
        //todo 因为okHttp拦截链更换host后缀问题，暂时省略host后缀。初始化则没有问题，但是后缀最后必须/结尾，否则出错
        String httpUrl = OpenAiService.formatUrl(formatUrl);

        if (TextUtils.isEmpty(httpUrl)) return null;

        return httpUrl;

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
