/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package org.apache.cordova.localtunnel;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.provider.Browser;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.HttpAuthHandler;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.Config;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaHttpAuthHandler;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginManager;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

@SuppressLint("SetJavaScriptEnabled")
public class LocalTunnel extends CordovaPlugin {

    private static final String NULL = "null";
    protected static final String LOG_TAG = "LocalTunnel";
    private static final String SELF = "_self";
    private static final String SYSTEM = "_system";
    private static final String CAPTCHA = "_captcha";
    private static final String HTTP_REQUEST = "_httprequest";
    private static final String CLEAR_COOKIES = "_clearcookies";
    private static final String EXIT_EVENT = "exit";
    private static final String LOCATION = "location";
    private static final String ZOOM = "zoom";
    private static final String HIDDEN = "hidden";
    private static final String LOAD_START_EVENT = "loadstart";
    private static final String LOAD_STOP_EVENT = "loadstop";
    private static final String LOAD_ERROR_EVENT = "loaderror";
    private static final String LOAD_RESOURCE_EVENT = "loadresource";
    private static final String CAPTCHA_DONE_EVENT = "captchadone";
    private static final String HTTP_REQUEST_DONE = "requestdone";
    private static final String CLEAR_ALL_CACHE = "clearcache";
    private static final String CLEAR_SESSION_CACHE = "clearsessioncache";
    private static final String HARDWARE_BACK_BUTTON = "hardwareback";
    private static final String MEDIA_PLAYBACK_REQUIRES_USER_ACTION = "mediaPlaybackRequiresUserAction";
    private static final String SHOULD_PAUSE = "shouldPauseOnSuspend";
    private static final Boolean DEFAULT_HARDWARE_BACK = true;
    private static final String USER_WIDE_VIEW_PORT = "useWideViewPort";
    private static final String TOOLBAR_COLOR = "toolbarcolor";
    private static final String CLOSE_BUTTON_CAPTION = "closebuttoncaption";
    private static final String CLOSE_BUTTON_COLOR = "closebuttoncolor";
    private static final String HIDE_NAVIGATION = "hidenavigationbuttons";
    private static final String NAVIGATION_COLOR = "navigationbuttoncolor";
    private static final String HIDE_URL = "hideurlbar";
    private static final String FOOTER = "footer";
    private static final String FOOTER_COLOR = "footercolor";

    // Default pattern will not match any URLs 
    private static final String DEFAULT_REQUEST_BLOCK_PATTERN = "(?!)";

    private static final List customizableOptions = Arrays.asList(CLOSE_BUTTON_CAPTION, TOOLBAR_COLOR, NAVIGATION_COLOR, CLOSE_BUTTON_COLOR, FOOTER_COLOR);

    private LocalTunnelDialog dialog;
    private WebView localTunnelWebView;
    private EditText edittext;
    private CallbackContext callbackContext;
    private boolean showLocationBar = true;
    private boolean showZoomControls = true;
    private boolean openWindowHidden = false;
    private boolean clearAllCache = false;
    private boolean clearSessionCache = false;
    private boolean hadwareBackButton = true;
    private boolean mediaPlaybackRequiresUserGesture = false;
    private boolean shouldPauseLocalTunnel = false;
    private boolean useWideViewPort = true;
    private ValueCallback<Uri> mUploadCallback;
    private ValueCallback<Uri[]> mUploadCallbackLollipop;
    private final static int FILECHOOSER_REQUESTCODE = 1;
    private final static int FILECHOOSER_REQUESTCODE_LOLLIPOP = 2;
    private String closeButtonCaption = "";
    private String closeButtonColor = "";
    private int toolbarColor = android.graphics.Color.LTGRAY;
    private boolean hideNavigationButtons = false;
    private String navigationButtonColor = "";
    private boolean hideUrlBar = false;
    private boolean showFooter = false;
    private String footerColor = "";

    protected String captchaUrl = null;
    protected String requestUrl = null;
    protected String lastRequestUrl = null;
    protected boolean enableRequestBlocking = false;
    protected Pattern requestBlockPattern = Pattern.compile(DEFAULT_REQUEST_BLOCK_PATTERN);

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action the action to execute.
     * @param args JSONArry of arguments for the plugin.
     * @param callbackContext the callbackContext used when calling back into JavaScript.
     * @return A PluginResult object with a status and message.
     */
    public boolean execute(String action, final CordovaArgs args, final CallbackContext callbackContext) throws JSONException {
        if (action.equals("open")) {
            this.callbackContext = callbackContext;
            final String url = args.getString(0);
            String t = args.optString(1);
            if (t == null || t.equals("") || t.equals(NULL)) {
                t = SELF;
            }
            final String target = t;
            final HashMap<String, String> features = parseFeature(args.optString(2));

            LOG.d(LOG_TAG, "target = " + target);

            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String result = "";
                    // SELF
                    if (SELF.equals(target)) {
                        LOG.d(LOG_TAG, "in self");
                        /* This code exists for compatibility between 3.x and 4.x versions of Cordova.
                         * Previously the Config class had a static method, isUrlWhitelisted(). That
                         * responsibility has been moved to the plugins, with an aggregating method in
                         * PluginManager.
                         */
                        Boolean shouldAllowNavigation = null;
                        if (url.startsWith("javascript:")) {
                            shouldAllowNavigation = true;
                        }
                        if (shouldAllowNavigation == null) {
                            try {
                                Method iuw = Config.class.getMethod("isUrlWhiteListed", String.class);
                                shouldAllowNavigation = (Boolean)iuw.invoke(null, url);
                            } catch (NoSuchMethodException e) {
                                LOG.d(LOG_TAG, e.getLocalizedMessage());
                            } catch (IllegalAccessException e) {
                                LOG.d(LOG_TAG, e.getLocalizedMessage());
                            } catch (InvocationTargetException e) {
                                LOG.d(LOG_TAG, e.getLocalizedMessage());
                            }
                        }
                        if (shouldAllowNavigation == null) {
                            try {
                                Method gpm = webView.getClass().getMethod("getPluginManager");
                                PluginManager pm = (PluginManager)gpm.invoke(webView);
                                Method san = pm.getClass().getMethod("shouldAllowNavigation", String.class);
                                shouldAllowNavigation = (Boolean)san.invoke(pm, url);
                            } catch (NoSuchMethodException e) {
                                LOG.d(LOG_TAG, e.getLocalizedMessage());
                            } catch (IllegalAccessException e) {
                                LOG.d(LOG_TAG, e.getLocalizedMessage());
                            } catch (InvocationTargetException e) {
                                LOG.d(LOG_TAG, e.getLocalizedMessage());
                            }
                        }
                        // load in webview
                        if (Boolean.TRUE.equals(shouldAllowNavigation)) {
                            LOG.d(LOG_TAG, "loading in webview");
                            webView.loadUrl(url);
                        }
                        //Load the dialer
                        else if (url.startsWith(WebView.SCHEME_TEL))
                        {
                            try {
                                LOG.d(LOG_TAG, "loading in dialer");
                                Intent intent = new Intent(Intent.ACTION_DIAL);
                                intent.setData(Uri.parse(url));
                                cordova.getActivity().startActivity(intent);
                            } catch (android.content.ActivityNotFoundException e) {
                                LOG.e(LOG_TAG, "Error dialing " + url + ": " + e.toString());
                            }
                        }
                        // load in LocalTunnel
                        else {
                            LOG.d(LOG_TAG, "loading in LocalTunnel");
                            result = showWebPage(url, features);
                        }
                    }
                    // SYSTEM
                    else if (SYSTEM.equals(target)) {
                        LOG.d(LOG_TAG, "in system");
                        result = openExternal(url);
                    }
                    else if (CAPTCHA.equals(target)) {
                        LOG.d(LOG_TAG, "loading captcha in LocalTunnel");
                        try {
                            result = showCaptchaPage(url, features, args);
                        } catch (JSONException ex) {
                            LOG.e(LOG_TAG, "Should never happen", ex);
                        }
                    }
                    else if (HTTP_REQUEST.equals(target)) {
                        LOG.d(LOG_TAG, "Making http request in LocalTunnel");
                        try {
                            result = makeHttpRequest(url, features, args);
                        } catch (JSONException ex) {
                            LOG.e(LOG_TAG, "Should never happen", ex);
                        }
                    }
                    else if (CLEAR_COOKIES.equals(target)) {
                        LOG.d(LOG_TAG, "Clearing cookies");
                        CookieManager.getInstance().removeAllCookie();
                        result = "success";
                    }
                    // BLANK - or anything else
                    else {
                        LOG.d(LOG_TAG, "in blank");
                        result = showWebPage(url, features);
                    }

                    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result);
                    pluginResult.setKeepCallback(true);
                    callbackContext.sendPluginResult(pluginResult);
                }
            });
        }
        else if (action.equals("close")) {
            closeDialog();
        }
        else if (action.equals("injectScriptCode")) {
            String jsWrapper = null;
            if (args.getBoolean(1)) {
                jsWrapper = String.format("(function(){prompt(JSON.stringify([eval(%%s)]), 'gap-iab://%s')})()", callbackContext.getCallbackId());
            }
            injectDeferredObject(args.getString(0), jsWrapper);
        }
        else if (action.equals("injectScriptFile")) {
            String jsWrapper;
            if (args.getBoolean(1)) {
                jsWrapper = String.format("(function(d) { var c = d.createElement('script'); c.src = %%s; c.onload = function() { prompt('', 'gap-iab://%s'); }; d.body.appendChild(c); })(document)", callbackContext.getCallbackId());
            } else {
                jsWrapper = "(function(d) { var c = d.createElement('script'); c.src = %s; d.body.appendChild(c); })(document)";
            }
            injectDeferredObject(args.getString(0), jsWrapper);
        }
        else if (action.equals("injectStyleCode")) {
            String jsWrapper;
            if (args.getBoolean(1)) {
                jsWrapper = String.format("(function(d) { var c = d.createElement('style'); c.innerHTML = %%s; d.body.appendChild(c); prompt('', 'gap-iab://%s');})(document)", callbackContext.getCallbackId());
            } else {
                jsWrapper = "(function(d) { var c = d.createElement('style'); c.innerHTML = %s; d.body.appendChild(c); })(document)";
            }
            injectDeferredObject(args.getString(0), jsWrapper);
        }
        else if (action.equals("injectStyleFile")) {
            String jsWrapper;
            if (args.getBoolean(1)) {
                jsWrapper = String.format("(function(d) { var c = d.createElement('link'); c.rel='stylesheet'; c.type='text/css'; c.href = %%s; d.head.appendChild(c); prompt('', 'gap-iab://%s');})(document)", callbackContext.getCallbackId());
            } else {
                jsWrapper = "(function(d) { var c = d.createElement('link'); c.rel='stylesheet'; c.type='text/css'; c.href = %s; d.head.appendChild(c); })(document)";
            }
            injectDeferredObject(args.getString(0), jsWrapper);
        }
        else if (action.equals("show")) {
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dialog.show();
                }
            });
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
            pluginResult.setKeepCallback(true);
            this.callbackContext.sendPluginResult(pluginResult);
        }
        else if (action.equals("hide")) {
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dialog.hide();
                }
            });
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
            pluginResult.setKeepCallback(true);
            this.callbackContext.sendPluginResult(pluginResult);
        }
        else {
            return false;
        }
        return true;
    }

    /**
     * Called when the view navigates.
     */
    @Override
    public void onReset() {
        closeDialog();
    }

    /**
     * Called when the system is about to start resuming a previous activity.
     */
    @Override
    public void onPause(boolean multitasking) {
        if (shouldPauseLocalTunnel) {
            localTunnelWebView.onPause();
        }
    }

    /**
     * Called when the activity will start interacting with the user.
     */
    @Override
    public void onResume(boolean multitasking) {
        if (shouldPauseLocalTunnel) {
            localTunnelWebView.onResume();
        }
    }

    /**
     * Called by AccelBroker when listener is to be shut down.
     * Stop listener.
     */
    public void onDestroy() {
        closeDialog();
    }

    /**
     * Inject an object (script or style) into the LocalTunnel WebView.
     *
     * This is a helper method for the inject{Script|Style}{Code|File} API calls, which
     * provides a consistent method for injecting JavaScript code into the document.
     *
     * If a wrapper string is supplied, then the source string will be JSON-encoded (adding
     * quotes) and wrapped using string formatting. (The wrapper string should have a single
     * '%s' marker)
     *
     * @param source      The source object (filename or script/style text) to inject into
     *                    the document.
     * @param jsWrapper   A JavaScript string to wrap the source string in, so that the object
     *                    is properly injected, or null if the source string is JavaScript text
     *                    which should be executed directly.
     */
    private void injectDeferredObject(String source, String jsWrapper) {
        if (localTunnelWebView!=null) {
            String scriptToInject;
            if (jsWrapper != null) {
                org.json.JSONArray jsonEsc = new org.json.JSONArray();
                jsonEsc.put(source);
                String jsonRepr = jsonEsc.toString();
                String jsonSourceString = jsonRepr.substring(1, jsonRepr.length()-1);
                scriptToInject = String.format(jsWrapper, jsonSourceString);
            } else {
                scriptToInject = source;
            }
            final String finalScriptToInject = scriptToInject;
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @SuppressLint("NewApi")
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                        // This action will have the side-effect of blurring the currently focused element
                        localTunnelWebView.loadUrl("javascript:" + finalScriptToInject);
                    } else {
                        localTunnelWebView.evaluateJavascript(finalScriptToInject, null);
                    }
                }
            });
        } else {
            LOG.d(LOG_TAG, "Can't inject code into the system browser");
        }
    }

    /**
     * Put the list of features into a hash map
     *
     * @param optString
     * @return
     */
    private HashMap<String, String> parseFeature(String optString) {
        if (optString.equals(NULL)) {
            return null;
        } else {
            HashMap<String, String> map = new HashMap<String, String>();
            StringTokenizer features = new StringTokenizer(optString, ",");
            StringTokenizer option;
            while(features.hasMoreElements()) {
                option = new StringTokenizer(features.nextToken(), "=");
                if (option.hasMoreElements()) {
                    String key = option.nextToken();
                    String value = option.nextToken();
                    if (!customizableOptions.contains(key)){
                        value = value.equals("yes") || value.equals("no") ? value : "yes";
                    }
                    map.put(key, value);
                }
            }
            return map;
        }
    }

    /**
     * Display a new browser with the specified URL.
     *
     * @param url the url to load.
     * @return "" if ok, or error message.
     */
    public String openExternal(String url) {
        try {
            Intent intent = null;
            intent = new Intent(Intent.ACTION_VIEW);
            // Omitting the MIME type for file: URLs causes "No Activity found to handle Intent".
            // Adding the MIME type to http: URLs causes them to not be handled by the downloader.
            Uri uri = Uri.parse(url);
            if ("file".equals(uri.getScheme())) {
                intent.setDataAndType(uri, webView.getResourceApi().getMimeType(uri));
            } else {
                intent.setData(uri);
            }
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, cordova.getActivity().getPackageName());
            this.cordova.getActivity().startActivity(intent);
            return "";
            // not catching FileUriExposedException explicitly because buildtools<24 doesn't know about it
        } catch (java.lang.RuntimeException e) {
            LOG.d(LOG_TAG, "LocalTunnel: Error loading url "+url+":"+ e.toString());
            return e.toString();
        }
    }

    /**
     * Closes the dialog
     */
    public void closeDialog() {
        this.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final WebView childView = localTunnelWebView;
                // The JS protects against multiple calls, so this should happen only when
                // closeDialog() is called by other native code.
                if (childView == null) {
                    return;
                }

                childView.setWebViewClient(new WebViewClient() {
                    // NB: wait for about:blank before dismissing
                    public void onPageFinished(WebView view, String url) {
                        if (dialog != null) {
                            // https://stackoverflow.com/questions/22924825/view-not-attached-to-window-manager-crash
                            if (Build.VERSION.SDK_INT < 17 && !cordova.getActivity().isFinishing()) {
                                dialog.dismiss();
                            } else if (Build.VERSION.SDK_INT >= 17 && !cordova.getActivity().isDestroyed()) {
                                dialog.dismiss();
                            }
                            dialog = null;
                        }
                        // NOTE(Alex) This used to be outside of this onPageFinished callback but it led to race conditions.
                        // Specifically, we would await an exit event but that event would occur before dialog had been set
                        // to null (which happens above). If you then called open again quickly enough, it became possible
                        // for the dialog of the new instance to be set to null by the above call. These leads to tons of
                        // wonkiness for the new dialog. The fix is to only say that we have exited after dialog is set to
                        // null
                        try {
                            JSONObject obj = new JSONObject();
                            obj.put("type", EXIT_EVENT);
                            sendUpdate(obj, false);
                        } catch (JSONException ex) {
                            LOG.d(LOG_TAG, "Should never happen");
                        }
                    }
                });
                // NB: From SDK 19: "If you call methods on WebView from any thread
                // other than your app's UI thread, it can cause unexpected results."
                // http://developer.android.com/guide/webapps/migrating.html#Threads
                childView.loadUrl("about:blank");
            }
        });
    }

    /**
     * Checks to see if it is possible to go back one page in history, then does so.
     */
    public void goBack() {
        if (this.localTunnelWebView.canGoBack()) {
            this.localTunnelWebView.goBack();
        }
    }

    /**
     * Can the web browser go back?
     * @return boolean
     */
    public boolean canGoBack() {
        return this.localTunnelWebView.canGoBack();
    }

    /**
     * Has the user set the hardware back button to go back
     * @return boolean
     */
    public boolean hardwareBack() {
        return hadwareBackButton;
    }

    /**
     * Checks to see if it is possible to go forward one page in history, then does so.
     */
    private void goForward() {
        if (this.localTunnelWebView.canGoForward()) {
            this.localTunnelWebView.goForward();
        }
    }

    /**
     * Navigate to the new page
     *
     * @param url to load
     */
    private void navigate(String url) {
        InputMethodManager imm = (InputMethodManager)this.cordova.getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(edittext.getWindowToken(), 0);

        if (!url.startsWith("http") && !url.startsWith("file:")) {
            this.localTunnelWebView.loadUrl("http://" + url);
        } else {
            this.localTunnelWebView.loadUrl(url);
        }
        this.localTunnelWebView.requestFocus();
    }


    /**
     * Should we show the location bar?
     *
     * @return boolean
     */
    private boolean getShowLocationBar() {
        return this.showLocationBar;
    }

    private LocalTunnel getLocalTunnel(){
        return this;
    }

    /**
     * Display a new browser with the specified URL.
     *
     * @param url the url to load.
     * @param features jsonObject
     */
    public String showWebPage(final String url, HashMap<String, String> features) {
        // Determine if we should hide the location bar.
        showLocationBar = true;
        showZoomControls = true;
        openWindowHidden = false;
        mediaPlaybackRequiresUserGesture = false;

        captchaUrl = null;
        requestUrl = null;
        lastRequestUrl = null;
        enableRequestBlocking = false;

        if (features != null) {
            String show = features.get(LOCATION);
            if (show != null) {
                showLocationBar = show.equals("yes") ? true : false;
            }
            if(showLocationBar) {
                String hideNavigation = features.get(HIDE_NAVIGATION);
                String hideUrl = features.get(HIDE_URL);
                if(hideNavigation != null) hideNavigationButtons = hideNavigation.equals("yes") ? true : false;
                if(hideUrl != null) hideUrlBar = hideUrl.equals("yes") ? true : false;
            }
            String zoom = features.get(ZOOM);
            if (zoom != null) {
                showZoomControls = zoom.equals("yes") ? true : false;
            }
            String hidden = features.get(HIDDEN);
            if (hidden != null) {
                openWindowHidden = hidden.equals("yes") ? true : false;
            }
            String hardwareBack = features.get(HARDWARE_BACK_BUTTON);
            if (hardwareBack != null) {
                hadwareBackButton = hardwareBack.equals("yes") ? true : false;
            } else {
                hadwareBackButton = DEFAULT_HARDWARE_BACK;
            }
            String mediaPlayback = features.get(MEDIA_PLAYBACK_REQUIRES_USER_ACTION);
            if (mediaPlayback != null) {
                mediaPlaybackRequiresUserGesture = mediaPlayback.equals("yes") ? true : false;
            }
            String cache = features.get(CLEAR_ALL_CACHE);
            if (cache != null) {
                clearAllCache = cache.equals("yes") ? true : false;
            } else {
                cache = features.get(CLEAR_SESSION_CACHE);
                if (cache != null) {
                    clearSessionCache = cache.equals("yes") ? true : false;
                }
            }
            String shouldPause = features.get(SHOULD_PAUSE);
            if (shouldPause != null) {
                shouldPauseLocalTunnel = shouldPause.equals("yes") ? true : false;
            }
            String wideViewPort = features.get(USER_WIDE_VIEW_PORT);
            if (wideViewPort != null ) {
                useWideViewPort = wideViewPort.equals("yes") ? true : false;
            }
            String closeButtonCaptionSet = features.get(CLOSE_BUTTON_CAPTION);
            if (closeButtonCaptionSet != null) {
                closeButtonCaption = closeButtonCaptionSet;
            }
            String closeButtonColorSet = features.get(CLOSE_BUTTON_COLOR);
            if (closeButtonColorSet != null) {
                closeButtonColor = closeButtonColorSet;
            }
            String toolbarColorSet = features.get(TOOLBAR_COLOR);
            if (toolbarColorSet != null) {
                toolbarColor = android.graphics.Color.parseColor(toolbarColorSet);
            }
            String navigationButtonColorSet = features.get(NAVIGATION_COLOR);
            if (navigationButtonColorSet != null) {
                navigationButtonColor = navigationButtonColorSet;
            }
            String showFooterSet = features.get(FOOTER);
            if (showFooterSet != null) {
                showFooter = showFooterSet.equals("yes") ? true : false;
            }
            String footerColorSet = features.get(FOOTER_COLOR);
            if (footerColorSet != null) {
                footerColor = footerColorSet;
            }
        }

        final CordovaWebView thatWebView = this.webView;
        final LocalTunnel thatIAB = this;

        // Create dialog in new thread
        Runnable runnable = new Runnable() {
            /**
             * Convert our DIP units to Pixels
             *
             * @return int
             */
            private int dpToPixels(int dipValue) {
                int value = (int) TypedValue.applyDimension( TypedValue.COMPLEX_UNIT_DIP,
                        (float) dipValue,
                        cordova.getActivity().getResources().getDisplayMetrics()
                );

                return value;
            }

            private View createCloseButton(int id){
                View _close;
                Resources activityRes = cordova.getActivity().getResources();

                if (closeButtonCaption != "") {
                    // Use TextView for text
                    TextView close = new TextView(cordova.getActivity());
                    close.setText(closeButtonCaption);
                    close.setTextSize(20);
                    if (closeButtonColor != "") close.setTextColor(android.graphics.Color.parseColor(closeButtonColor));
                    close.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    close.setPadding(this.dpToPixels(10), 0, this.dpToPixels(10), 0);
                    _close = close;
                }
                else {
                    ImageButton close = new ImageButton(cordova.getActivity());
                    int closeResId = activityRes.getIdentifier("ic_action_remove", "drawable", cordova.getActivity().getPackageName());
                    Drawable closeIcon = activityRes.getDrawable(closeResId);
                    if (closeButtonColor != "") close.setColorFilter(android.graphics.Color.parseColor(closeButtonColor));
                    close.setImageDrawable(closeIcon);
                    close.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    if (Build.VERSION.SDK_INT >= 16)
                        close.getAdjustViewBounds();

                    _close = close;
                }

                RelativeLayout.LayoutParams closeLayoutParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
                closeLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                _close.setLayoutParams(closeLayoutParams);

                if (Build.VERSION.SDK_INT >= 16)
                    _close.setBackground(null);
                else
                    _close.setBackgroundDrawable(null);

                _close.setContentDescription("Close Button");
                _close.setId(Integer.valueOf(id));
                _close.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        closeDialog();
                    }
                });

                return _close;
            }

            @SuppressLint("NewApi")
            public void run() {

                // CB-6702 LocalTunnel hangs when opening more than one instance
                if (dialog != null) {
                    dialog.dismiss();
                };

                // Let's create the main dialog
                dialog = new LocalTunnelDialog(cordova.getActivity(), android.R.style.Theme_NoTitleBar);
                dialog.getWindow().getAttributes().windowAnimations = android.R.style.Animation_Dialog;
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setCancelable(true);
                dialog.setLocalTunnel(getLocalTunnel());

                // Main container layout
                LinearLayout main = new LinearLayout(cordova.getActivity());
                main.setOrientation(LinearLayout.VERTICAL);

                // Toolbar layout
                RelativeLayout toolbar = new RelativeLayout(cordova.getActivity());
                //Please, no more black!
                toolbar.setBackgroundColor(toolbarColor);
                toolbar.setLayoutParams(new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, this.dpToPixels(44)));
                toolbar.setPadding(this.dpToPixels(2), this.dpToPixels(2), this.dpToPixels(2), this.dpToPixels(2));
                toolbar.setHorizontalGravity(Gravity.LEFT);
                toolbar.setVerticalGravity(Gravity.TOP);

                // Action Button Container layout
                RelativeLayout actionButtonContainer = new RelativeLayout(cordova.getActivity());
                actionButtonContainer.setLayoutParams(new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
                actionButtonContainer.setHorizontalGravity(Gravity.LEFT);
                actionButtonContainer.setVerticalGravity(Gravity.CENTER_VERTICAL);
                actionButtonContainer.setId(Integer.valueOf(1));

                // Back button
                ImageButton back = new ImageButton(cordova.getActivity());
                RelativeLayout.LayoutParams backLayoutParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
                backLayoutParams.addRule(RelativeLayout.ALIGN_LEFT);
                back.setLayoutParams(backLayoutParams);
                back.setContentDescription("Back Button");
                back.setId(Integer.valueOf(2));
                Resources activityRes = cordova.getActivity().getResources();
                int backResId = activityRes.getIdentifier("ic_action_previous_item", "drawable", cordova.getActivity().getPackageName());
                Drawable backIcon = activityRes.getDrawable(backResId);
                if (navigationButtonColor != "") back.setColorFilter(android.graphics.Color.parseColor(navigationButtonColor));
                if (Build.VERSION.SDK_INT >= 16)
                    back.setBackground(null);
                else
                    back.setBackgroundDrawable(null);
                back.setImageDrawable(backIcon);
                back.setScaleType(ImageView.ScaleType.FIT_CENTER);
                back.setPadding(0, this.dpToPixels(10), 0, this.dpToPixels(10));
                if (Build.VERSION.SDK_INT >= 16)
                    back.getAdjustViewBounds();

                back.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        goBack();
                    }
                });

                // Forward button
                ImageButton forward = new ImageButton(cordova.getActivity());
                RelativeLayout.LayoutParams forwardLayoutParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
                forwardLayoutParams.addRule(RelativeLayout.RIGHT_OF, 2);
                forward.setLayoutParams(forwardLayoutParams);
                forward.setContentDescription("Forward Button");
                forward.setId(Integer.valueOf(3));
                int fwdResId = activityRes.getIdentifier("ic_action_next_item", "drawable", cordova.getActivity().getPackageName());
                Drawable fwdIcon = activityRes.getDrawable(fwdResId);
                if (navigationButtonColor != "") forward.setColorFilter(android.graphics.Color.parseColor(navigationButtonColor));
                if (Build.VERSION.SDK_INT >= 16)
                    forward.setBackground(null);
                else
                    forward.setBackgroundDrawable(null);
                forward.setImageDrawable(fwdIcon);
                forward.setScaleType(ImageView.ScaleType.FIT_CENTER);
                forward.setPadding(0, this.dpToPixels(10), 0, this.dpToPixels(10));
                if (Build.VERSION.SDK_INT >= 16)
                    forward.getAdjustViewBounds();

                forward.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        goForward();
                    }
                });

                // Edit Text Box
                edittext = new EditText(cordova.getActivity());
                RelativeLayout.LayoutParams textLayoutParams = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
                textLayoutParams.addRule(RelativeLayout.RIGHT_OF, 1);
                textLayoutParams.addRule(RelativeLayout.LEFT_OF, 5);
                edittext.setLayoutParams(textLayoutParams);
                edittext.setId(Integer.valueOf(4));
                edittext.setSingleLine(true);
                edittext.setText(url);
                edittext.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
                edittext.setImeOptions(EditorInfo.IME_ACTION_GO);
                edittext.setInputType(InputType.TYPE_NULL); // Will not except input... Makes the text NON-EDITABLE
                edittext.setOnKeyListener(new View.OnKeyListener() {
                    public boolean onKey(View v, int keyCode, KeyEvent event) {
                        // If the event is a key-down event on the "enter" button
                        if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                            navigate(edittext.getText().toString());
                            return true;
                        }
                        return false;
                    }
                });


                // Header Close/Done button
                View close = createCloseButton(5);
                toolbar.addView(close);

                // Footer
                RelativeLayout footer = new RelativeLayout(cordova.getActivity());
                int _footerColor;
                if(footerColor != ""){
                    _footerColor = Color.parseColor(footerColor);
                }else{
                    _footerColor = android.graphics.Color.LTGRAY;
                }
                footer.setBackgroundColor(_footerColor);
                RelativeLayout.LayoutParams footerLayout = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, this.dpToPixels(44));
                footerLayout.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
                footer.setLayoutParams(footerLayout);
                if (closeButtonCaption != "") footer.setPadding(this.dpToPixels(8), this.dpToPixels(8), this.dpToPixels(8), this.dpToPixels(8));
                footer.setHorizontalGravity(Gravity.LEFT);
                footer.setVerticalGravity(Gravity.BOTTOM);

                View footerClose = createCloseButton(7);
                footer.addView(footerClose);


                // WebView
                localTunnelWebView = new WebView(cordova.getActivity());
                // By default navigator.onLine is false.
                localTunnelWebView.setNetworkAvailable(true);
                localTunnelWebView.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                localTunnelWebView.setId(Integer.valueOf(6));
                // File Chooser Implemented ChromeClient
                localTunnelWebView.setWebChromeClient(new LocalTunnelChromeClient(thatWebView, getLocalTunnel()) {
                    // For Android 5.0+
                    public boolean onShowFileChooser (WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams)
                    {
                        LOG.d(LOG_TAG, "File Chooser 5.0+");
                        // If callback exists, finish it.
                        if(mUploadCallbackLollipop != null) {
                            mUploadCallbackLollipop.onReceiveValue(null);
                        }
                        mUploadCallbackLollipop = filePathCallback;

                        // Create File Chooser Intent
                        Intent content = new Intent(Intent.ACTION_GET_CONTENT);
                        content.addCategory(Intent.CATEGORY_OPENABLE);
                        content.setType("*/*");

                        // Run cordova startActivityForResult
                        cordova.startActivityForResult(LocalTunnel.this, Intent.createChooser(content, "Select File"), FILECHOOSER_REQUESTCODE_LOLLIPOP);
                        return true;
                    }

                    // For Android 4.1+
                    public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture)
                    {
                        LOG.d(LOG_TAG, "File Chooser 4.1+");
                        // Call file chooser for Android 3.0+
                        openFileChooser(uploadMsg, acceptType);
                    }

                    // For Android 3.0+
                    public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType)
                    {
                        LOG.d(LOG_TAG, "File Chooser 3.0+");
                        mUploadCallback = uploadMsg;
                        Intent content = new Intent(Intent.ACTION_GET_CONTENT);
                        content.addCategory(Intent.CATEGORY_OPENABLE);

                        // run startActivityForResult
                        cordova.startActivityForResult(LocalTunnel.this, Intent.createChooser(content, "Select File"), FILECHOOSER_REQUESTCODE);
                    }

                });
                WebViewClient client = new LocalTunnelClient(thatWebView, edittext);
                localTunnelWebView.setWebViewClient(client);
                WebSettings settings = localTunnelWebView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setJavaScriptCanOpenWindowsAutomatically(true);
                settings.setBuiltInZoomControls(showZoomControls);
                settings.setPluginState(android.webkit.WebSettings.PluginState.ON);
                // localTunnelWebView.addJavascriptInterface(new HTMLViewerJavaScriptInterface(thatIAB), "HtmlViewer");

                if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    settings.setMediaPlaybackRequiresUserGesture(mediaPlaybackRequiresUserGesture);
                }

                String overrideUserAgent = preferences.getString("OverrideUserAgent", null);
                String appendUserAgent = preferences.getString("AppendUserAgent", null);

                if (overrideUserAgent != null) {
                    settings.setUserAgentString(overrideUserAgent);
                }
                if (appendUserAgent != null) {
                    settings.setUserAgentString(settings.getUserAgentString() + appendUserAgent);
                }

                //Toggle whether this is enabled or not!
                Bundle appSettings = cordova.getActivity().getIntent().getExtras();
                boolean enableDatabase = appSettings == null ? true : appSettings.getBoolean("LocalTunnelStorageEnabled", true);
                if (enableDatabase) {
                    String databasePath = cordova.getActivity().getApplicationContext().getDir("localTunnelDB", Context.MODE_PRIVATE).getPath();
                    settings.setDatabasePath(databasePath);
                    settings.setDatabaseEnabled(true);
                }
                settings.setDomStorageEnabled(true);

                if (clearAllCache) {
                    CookieManager.getInstance().removeAllCookie();
                } else if (clearSessionCache) {
                    CookieManager.getInstance().removeSessionCookie();
                }

                // Enable Thirdparty Cookies on >=Android 5.0 device
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().setAcceptThirdPartyCookies(localTunnelWebView,true);
                }

                localTunnelWebView.loadUrl(url);
                localTunnelWebView.setId(Integer.valueOf(6));
                localTunnelWebView.getSettings().setLoadWithOverviewMode(true);
                localTunnelWebView.getSettings().setUseWideViewPort(useWideViewPort);
                localTunnelWebView.requestFocus();
                localTunnelWebView.requestFocusFromTouch();

                // Add the back and forward buttons to our action button container layout
                actionButtonContainer.addView(back);
                actionButtonContainer.addView(forward);

                // Add the views to our toolbar if they haven't been disabled
                if (!hideNavigationButtons) toolbar.addView(actionButtonContainer);
                if (!hideUrlBar) toolbar.addView(edittext);

                // Don't add the toolbar if its been disabled
                if (getShowLocationBar()) {
                    // Add our toolbar to our main view/layout
                    main.addView(toolbar);
                }

                // Add our webview to our main view/layout
                RelativeLayout webViewLayout = new RelativeLayout(cordova.getActivity());
                webViewLayout.addView(localTunnelWebView);
                main.addView(webViewLayout);

                // Don't add the footer unless it's been enabled
                if (showFooter) {
                    webViewLayout.addView(footer);
                }

                WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                lp.copyFrom(dialog.getWindow().getAttributes());
                lp.width = WindowManager.LayoutParams.MATCH_PARENT;
                lp.height = WindowManager.LayoutParams.MATCH_PARENT;

                dialog.setContentView(main);
                dialog.show();
                dialog.getWindow().setAttributes(lp);
                // the goal of openhidden is to load the url and not display it
                // Show() needs to be called to cause the URL to be loaded
                if(openWindowHidden) {
                    dialog.hide();
                }
            }
        };
        this.cordova.getActivity().runOnUiThread(runnable);
        return "";
    }

    public void clearCookiesForDomain(String domain) {
        CookieManager cookieManager = CookieManager.getInstance();
        String cookiestring = cookieManager.getCookie(domain);
        if (cookiestring != null) {
            String[] cookies =  cookiestring.split(";");
            for (int i=0; i<cookies.length; i++) {
                String[] cookieparts = cookies[i].split("=");
                cookieManager.setCookie(domain, cookieparts[0].trim()+"=; Expires=Wed, 31 Dec 2025 23:59:59 GMT");
            }
        }
    }

    /**
     * Display a new browser with the specified URL.
     *
     * @param url the url to load.
     * @param features jsonObject
     */
    public String showCaptchaPage(final String url, HashMap<String, String> features, CordovaArgs args) throws JSONException {
        final String captchaOptionsJson = args.optString(3);
        final org.json.JSONObject captchaOptions = new org.json.JSONObject(captchaOptionsJson);
        final org.json.JSONObject captchaCookies = captchaOptions.getJSONObject("cookies");
        final String content = captchaOptions.getString("content");
        final String userAgent = captchaOptions.getString("useragent");
        final Boolean captchaHidden = captchaOptions.getBoolean("hidden");
        final CordovaWebView thatWebView = this.webView;
        final LocalTunnel thatIAB = this;
        captchaUrl = url;

        // Create dialog in new thread
        Runnable runnable = new Runnable() {
            private int dpToPixels(int dipValue) {
                return (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        (float) dipValue,
                        cordova.getActivity().getResources().getDisplayMetrics());
            }

            @SuppressLint("NewApi")
            public void run() {
                CookieManager cookieManager = CookieManager.getInstance();

                // CB-6702 LocalTunnel hangs when opening more than one instance
                if (dialog != null) {
                    dialog.dismiss();
                    dialog = null;
                }

                if (dialog == null) {
                    // Edit Text Box
                    edittext = new EditText(cordova.getActivity());

                    // Let's create the main dialog
                    dialog = new LocalTunnelDialog(cordova.getActivity(), android.R.style.Theme_NoTitleBar);
                    dialog.getWindow().getAttributes().windowAnimations = android.R.style.Animation_Dialog;
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    dialog.setCancelable(true);
                    dialog.setLocalTunnel(getLocalTunnel());

                    // Main container layout
                    LinearLayout main = new LinearLayout(cordova.getActivity());
                    main.setOrientation(LinearLayout.VERTICAL);

                    // WebView
                    localTunnelWebView = new WebView(cordova.getActivity());
                    localTunnelWebView.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                    localTunnelWebView.setId(Integer.valueOf(6));

                    localTunnelWebView.setWebChromeClient(new LocalTunnelChromeClient(thatWebView, getLocalTunnel()));
                    WebViewClient client = new LocalTunnelClient(thatWebView, edittext);
                    localTunnelWebView.setWebViewClient(client);
                    WebSettings settings = localTunnelWebView.getSettings();
                    settings.setJavaScriptEnabled(true);
                    settings.setJavaScriptCanOpenWindowsAutomatically(true);
                    settings.setBuiltInZoomControls(false);
                    settings.setPluginState(android.webkit.WebSettings.PluginState.ON);
                    settings.setUserAgentString(userAgent);

                    if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        settings.setMediaPlaybackRequiresUserGesture(mediaPlaybackRequiresUserGesture);
                    }

                    //Toggle whether this is enabled or not!
                    Bundle appSettings = cordova.getActivity().getIntent().getExtras();
                    boolean enableDatabase = appSettings == null ? true : appSettings.getBoolean("LocalTunnelStorageEnabled", true);
                    if (enableDatabase) {
                        String databasePath = cordova.getActivity().getApplicationContext().getDir("localTunnelDB", Context.MODE_PRIVATE).getPath();
                        settings.setDatabasePath(databasePath);
                        settings.setDatabaseEnabled(true);
                    }
                    settings.setDomStorageEnabled(true);

                    captchaUrl = "about:blank";
                    localTunnelWebView.loadDataWithBaseURL(url, content, "text/html", "UTF-8", null);
                    localTunnelWebView.setId(Integer.valueOf(6));
                    localTunnelWebView.getSettings().setLoadWithOverviewMode(true);
                    localTunnelWebView.getSettings().setUseWideViewPort(useWideViewPort);
                    localTunnelWebView.requestFocus();
                    localTunnelWebView.requestFocusFromTouch();

                    // Add our webview to our main view/layout
                    RelativeLayout webViewLayout = new RelativeLayout(cordova.getActivity());
                    webViewLayout.addView(localTunnelWebView);
                    main.addView(webViewLayout);

                    WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                    lp.copyFrom(dialog.getWindow().getAttributes());
                    lp.width = WindowManager.LayoutParams.MATCH_PARENT;
                    lp.height = WindowManager.LayoutParams.MATCH_PARENT;

                    dialog.setContentView(main);
                    dialog.getWindow().setAttributes(lp);
                }

                if (!captchaHidden) {
                    dialog.show();
                }
            }
        };
        this.cordova.getActivity().runOnUiThread(runnable);
        return "";
    }



    /**
     * Display a new browser with the specified URL.
     *
     * @param url the url to load.
     * @param features jsonObject
     */
    public String makeHttpRequest(final String url, HashMap<String, String> features, CordovaArgs args) throws JSONException {
        final String requestOptionsJson = args.optString(3);
        final org.json.JSONObject requestOptions = new org.json.JSONObject(requestOptionsJson);
        final org.json.JSONObject requestParams = requestOptions.getJSONObject("params");
        final org.json.JSONObject requestHeaders = requestOptions.getJSONObject("headers");
        final String requestCookies = requestOptions.getString("cookies");
        final String method = requestOptions.getString("method");
        final String userAgent = requestOptions.getString("useragent");

        final CordovaWebView thatWebView = this.webView;
        final LocalTunnel thatIAB = this;

        requestUrl = url;
        lastRequestUrl = url;
        openWindowHidden = true;
        enableRequestBlocking = requestOptions.getBoolean("enable_request_blocking");
        String passedFileExtensions = requestOptions.optString("file_extensions_to_block", "");
        requestBlockPattern = getRequestBlockPattern(passedFileExtensions);

        if (features != null) {
            String hidden = features.get(HIDDEN);
            if (hidden != null && hidden.equals("no")) {
                openWindowHidden = false;
            }
        }

        // Create dialog in new thread
        Runnable runnable = new Runnable() {
            private int dpToPixels(int dipValue) {
                return (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        (float) dipValue,
                        cordova.getActivity().getResources().getDisplayMetrics());
            }

            @SuppressLint("NewApi")
            public void run() {

                CookieManager cookieManager = CookieManager.getInstance();

                // CB-6702 LocalTunnel hangs when opening more than one instance
                // ram: Create a new thing called tunnel dialog.
                if (dialog == null) {
                    // Edit Text Box
                    edittext = new EditText(cordova.getActivity());

                    // Let's create the main dialog
                    dialog = new LocalTunnelDialog(cordova.getActivity(), android.R.style.Theme_NoTitleBar);
                    dialog.getWindow().getAttributes().windowAnimations = android.R.style.Animation_Dialog;
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    dialog.setCancelable(true);
                    dialog.setLocalTunnel(getLocalTunnel());

                    // Main container layout
                    LinearLayout main = new LinearLayout(cordova.getActivity());
                    main.setOrientation(LinearLayout.VERTICAL);

                    // WebView
                    localTunnelWebView = new WebView(cordova.getActivity());
                    localTunnelWebView.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                    localTunnelWebView.setId(Integer.valueOf(6));

                    localTunnelWebView.setWebChromeClient(new LocalTunnelChromeClient(thatWebView, getLocalTunnel()));
                    WebViewClient client = new LocalTunnelClient(thatWebView, edittext);
                    localTunnelWebView.setWebViewClient(client);
                    WebSettings settings = localTunnelWebView.getSettings();
                    settings.setJavaScriptEnabled(true);
                    // localTunnelWebView.addJavascriptInterface(new HTMLViewerJavaScriptInterface(), "HtmlViewer");
                    settings.setJavaScriptCanOpenWindowsAutomatically(true);
                    settings.setBuiltInZoomControls(false);
                    settings.setPluginState(android.webkit.WebSettings.PluginState.ON);
                    settings.setUserAgentString(userAgent);

                    if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        settings.setMediaPlaybackRequiresUserGesture(mediaPlaybackRequiresUserGesture);
                    }

                    //Toggle whether this is enabled or not!
                    Bundle appSettings = cordova.getActivity().getIntent().getExtras();
                    boolean enableDatabase = appSettings == null ? true : appSettings.getBoolean("LocalTunnelStorageEnabled", true);
                    if (enableDatabase) {
                        String databasePath = cordova.getActivity().getApplicationContext().getDir("localTunnelDB", Context.MODE_PRIVATE).getPath();
                        settings.setDatabasePath(databasePath);
                        settings.setDatabaseEnabled(true);
                    }
                    settings.setDomStorageEnabled(true);

                    localTunnelWebView.setId(Integer.valueOf(6));
                    localTunnelWebView.getSettings().setLoadWithOverviewMode(true);
                    localTunnelWebView.getSettings().setUseWideViewPort(useWideViewPort);
                    localTunnelWebView.requestFocus();
                    localTunnelWebView.requestFocusFromTouch();

                    // Add our webview to our main view/layout
                    RelativeLayout webViewLayout = new RelativeLayout(cordova.getActivity());
                    webViewLayout.addView(localTunnelWebView);
                    main.addView(webViewLayout);

                    WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                    lp.copyFrom(dialog.getWindow().getAttributes());
                    lp.width = WindowManager.LayoutParams.MATCH_PARENT;
                    lp.height = WindowManager.LayoutParams.MATCH_PARENT;

                    // Enable Thirdparty Cookies on >=Android 5.0 device
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        cookieManager.setAcceptThirdPartyCookies(localTunnelWebView, true);
                    }

                    dialog.setContentView(main);
                    dialog.show();
                    dialog.getWindow().setAttributes(lp);
                }

                Boolean isContentJSON = null;
                try {
                    // TODO(ram): check substring instead of equals
                    isContentJSON = requestHeaders.getString("Content-Type").equals("application/json");
                } catch(JSONException ex) {
                    isContentJSON = false;
                }

                StringBuilder headersBuilder = new StringBuilder();
                Iterator<String> keys = requestHeaders.keys();
                try {
                    while (keys.hasNext()) {
                        String key = keys.next();
                        String value = requestHeaders.getString(key);
                        headersBuilder.append("oReq.setRequestHeader('" + key + "', '" + value + "');\n");
                    }
                } catch (JSONException ex) {
                    LOG.e(LOG_TAG, "Should never happen", ex);
                }

                if (method.equals("get")) {
                    localTunnelWebView.loadUrl(url);
                } else if (method.equals("post") && isContentJSON) {
                    String jsCode = String.format(
                        "var oReq = new XMLHttpRequest();" +
                        "oReq.onload = function() {" +
                        "    window._HTML = '<html><body>' + this.responseText + '</body></html>';" +
                        "    prompt(JSON.stringify([this.status, this.statusText]), 'gap-iab://requestdone');" +
                        "};" +
                        "oReq.onerror = function() {" +
                        "    window._HTML = '<html><body>' + this.responseText + '</body></html>';" +
                        "    prompt(JSON.stringify([this.status, 'Load error']), 'gap-iab://requestdone');" +
                        "};" +
                        "oReq.open('post', '%s');" +
                        headersBuilder.toString() + 
                        "oReq.send(JSON.stringify(%s));",
                        url,
                        requestParams.toString()
                    );
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @SuppressLint("NewApi")
                        @Override
                        public void run() {
                            localTunnelWebView.evaluateJavascript(jsCode, null);
                        }
                    });
                } else if (method.equals("post")) {
                    List<String> postDataList = new ArrayList<String>();
                    Iterator<String> params = requestParams.keys();
                    while( params.hasNext() ) {
                        try {
                            String param = params.next();
                            String paramValue = requestParams.getString(param);
                            postDataList.add(param + "=" + URLEncoder.encode(paramValue, "UTF-8"));
                        } catch(JSONException ex) {
                            LOG.e(LOG_TAG, "Should never happen", ex);
                        }  catch(UnsupportedEncodingException ex) {
                            LOG.e(LOG_TAG, "Should never happen", ex);
                        }
                    }

                    String postData = "";
                    for (String str : postDataList) {
                        if (postData != "") {
                            postData += "&";
                        }
                        postData += str;
                    }
                    localTunnelWebView.postUrl(url, postData.getBytes());
                }

                if(openWindowHidden) {
                    dialog.hide();
                }
            }
        };
        this.cordova.getActivity().runOnUiThread(runnable);
        return "";
    }

    /**
     * Generate a regex pattern to match URLs with specific file extensions.
     *
     * @param extensions comma seperated list of file extensions
     */
    protected Pattern getRequestBlockPattern(String extensions) {
        if (!extensions.isEmpty()) {
            String[] fileExtensions = extensions.split(",");
            StringBuilder patternBuilder = new StringBuilder();
            
            patternBuilder.append("(");
            for (int i = 0; i < fileExtensions.length; i++) {
                patternBuilder.append("\\.");
                patternBuilder.append(fileExtensions[i]);
                if (i < fileExtensions.length - 1) {
                    patternBuilder.append("|");
                }
            }
            patternBuilder.append(")");
            patternBuilder.append("(\\?.*)?$");

            try {
                return Pattern.compile(patternBuilder.toString(), Pattern.CASE_INSENSITIVE);
            }
            catch (PatternSyntaxException ex) {
                LOG.e(LOG_TAG, "Failed to compile request blocking pattern for extensions: " + extensions, ex);
                return Pattern.compile(DEFAULT_REQUEST_BLOCK_PATTERN);
            }
        } else {
            return Pattern.compile(DEFAULT_REQUEST_BLOCK_PATTERN);
        }
    }

    /**
     * Create a new plugin success result and send it back to JavaScript
     *
     * @param obj a JSONObject contain event payload information
     */
    private void sendUpdate(JSONObject obj, boolean keepCallback) {
        sendUpdate(obj, keepCallback, PluginResult.Status.OK);
    }

    /**
     * Create a new plugin result and send it back to JavaScript
     *
     * @param obj a JSONObject contain event payload information
     * @param status the status code to return to the JavaScript environment
     */
    private void sendUpdate(JSONObject obj, boolean keepCallback, PluginResult.Status status) {
        if (callbackContext != null) {
            PluginResult result = new PluginResult(status, obj);
            result.setKeepCallback(keepCallback);
            callbackContext.sendPluginResult(result);
            if (!keepCallback) {
                callbackContext = null;
            }
        }
    }

    /**
     * Create a new plugin result and send it back to JavaScript
     *
     * @param obj a JSONObject contain event payload information
     * @param status the status code to return to the JavaScript environment
     */
    public void sendRequestDone(int status, String statusText) {
        if (status >= 100 && status < 600) {
            try {
                org.json.JSONObject obj = new JSONObject();
                obj.put("type", HTTP_REQUEST_DONE);
                String cookies = CookieManager.getInstance().getCookie(requestUrl);
                obj.put("cookies", cookies);
                obj.put("url", requestUrl);
                obj.put("status", status);
                sendUpdate(obj, true);
            } catch (JSONException ex) {
                LOG.e(LOG_TAG, "Should never happen", ex);
            }
        } else {
            try {
                JSONObject obj = new JSONObject();
                obj.put("type", LOAD_ERROR_EVENT);
                obj.put("url", requestUrl);
                obj.put("code", status);
                obj.put("message", statusText);
                sendUpdate(obj, true);
            } catch (JSONException ex) {
                LOG.e(LOG_TAG, "Should never happen", ex);
            }
        }
    }

    /**
     * Receive File Data from File Chooser
     *
     * @param requestCode the requested code from chromeclient
     * @param resultCode the result code returned from android system
     * @param intent the data from android file chooser
     */
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        // For Android >= 5.0
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            LOG.d(LOG_TAG, "onActivityResult (For Android >= 5.0)");
            // If RequestCode or Callback is Invalid
            if(requestCode != FILECHOOSER_REQUESTCODE_LOLLIPOP || mUploadCallbackLollipop == null) {
                super.onActivityResult(requestCode, resultCode, intent);
                return;
            }
            mUploadCallbackLollipop.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, intent));
            mUploadCallbackLollipop = null;
        }
        // For Android < 5.0
        else {
            LOG.d(LOG_TAG, "onActivityResult (For Android < 5.0)");
            // If RequestCode or Callback is Invalid
            if(requestCode != FILECHOOSER_REQUESTCODE || mUploadCallback == null) {
                super.onActivityResult(requestCode, resultCode, intent);
                return;
            }

            if (null == mUploadCallback) return;
            Uri result = intent == null || resultCode != cordova.getActivity().RESULT_OK ? null : intent.getData();

            mUploadCallback.onReceiveValue(result);
            mUploadCallback = null;
        }
    }

    /**
     * The webview client receives notifications about appView
     */
    public class LocalTunnelClient extends WebViewClient {
        EditText edittext;
        CordovaWebView webView;

        /**
         * Constructor.
         *
         * @param webView
         * @param mEditText
         */
        public LocalTunnelClient(CordovaWebView webView, EditText mEditText) {
            this.webView = webView;
            this.edittext = mEditText;
        }

        /**
         * Override the URL that should be loaded
         *
         * This handles a small subset of all the URIs that would be encountered.
         *
         * @param webView
         * @param url
         */
        @Override
        public WebResourceResponse shouldInterceptRequest (final WebView webView, String url) {
            LOG.d(LOG_TAG, "INSPECTING REQUEST. requestUrl: " + requestUrl + ". url: " + url + ". enableRequestBlocking: " + enableRequestBlocking);

            try {
                if ((enableRequestBlocking && !requestUrl.equals(url)) || isBlockedByPattern(url)) {
                    LOG.d(LOG_TAG, "REQUEST BLOCKED: " + url);
                    InputStream data = new ByteArrayInputStream("REQUEST BLOCKED".getBytes("UTF-8"));
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        return new WebResourceResponse("text/html", "UTF-8", data);
                    } else {
                        Map<String, String> responseHeaders = new HashMap<String,String>();
                        return new WebResourceResponse(
                            "text/html", "UTF-8", 500, "Request blocked.",
                            responseHeaders, data);
                    }
                }
            } catch (UnsupportedEncodingException ex) {
                LOG.e(LOG_TAG, "Should never happen", ex);
            }
            return super.shouldInterceptRequest(webView, url);
        }

        /**
         * Determine if a URL should be blocked based on request blocking pattern.
         *
         * @param url
         */
        protected boolean isBlockedByPattern(String url) {
            if (url != null) {
                return requestBlockPattern.matcher(url).find();
            }
            return false;
        }

        /**
         * Override the URL that should be loaded
         *
         * This handles a small subset of all the URIs that would be encountered.
         *
         * @param webView
         * @param url
         */
        @Override
        public boolean shouldOverrideUrlLoading(WebView webView, String url) {
            LOG.d(LOG_TAG, "shouldOverrideUrlLoading: " + url);
            if (url.contains("chfs.non-pci.portmapper.vip")) {
                String actualUrl = url.replaceAll("chfs.non-pci.portmapper.vip:[0-9]+", "www.connectebt.com");
                requestUrl = actualUrl;
                lastRequestUrl = actualUrl;
                webView.loadUrl(actualUrl);
                return true;
            }
            else if (requestUrl != null && !requestUrl.equals(url)) {
                LOG.d(LOG_TAG, "Handle page redirect from: " + requestUrl);
                requestUrl = url;
                lastRequestUrl = url;
            }
            else if (captchaUrl != null) {
                LOG.d(LOG_TAG, "Closing the captcha loop");
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("type", CAPTCHA_DONE_EVENT);
                    String cookies = CookieManager.getInstance().getCookie(captchaUrl);
                    obj.put("cookies", cookies);
                    obj.put("url", captchaUrl);
                    sendUpdate(obj, true);
                } catch (JSONException ex) {
                    LOG.e(LOG_TAG, "URI passed in has caused a JSON error.");
                }
                captchaUrl = null;
                // closeDialog();
                return true;
            }
            else if (url.startsWith(WebView.SCHEME_TEL)) {
                try {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse(url));
                    cordova.getActivity().startActivity(intent);
                    return true;
                } catch (android.content.ActivityNotFoundException e) {
                    LOG.e(LOG_TAG, "Error dialing " + url + ": " + e.toString());
                }
            }
            else if (url.startsWith("geo:") || url.startsWith(WebView.SCHEME_MAILTO) || url.startsWith("market:") || url.startsWith("intent:")) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(url));
                    cordova.getActivity().startActivity(intent);
                    return true;
                } catch (android.content.ActivityNotFoundException e) {
                    LOG.e(LOG_TAG, "Error with " + url + ": " + e.toString());
                }
            }
            // If sms:5551212?body=This is the message
            else if (url.startsWith("sms:")) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);

                    // Get address
                    String address = null;
                    int parmIndex = url.indexOf('?');
                    if (parmIndex == -1) {
                        address = url.substring(4);
                    } else {
                        address = url.substring(4, parmIndex);

                        // If body, then set sms body
                        Uri uri = Uri.parse(url);
                        String query = uri.getQuery();
                        if (query != null) {
                            if (query.startsWith("body=")) {
                                intent.putExtra("sms_body", query.substring(5));
                            }
                        }
                    }
                    intent.setData(Uri.parse("sms:" + address));
                    intent.putExtra("address", address);
                    intent.setType("vnd.android-dir/mms-sms");
                    cordova.getActivity().startActivity(intent);
                    return true;
                } catch (android.content.ActivityNotFoundException e) {
                    LOG.e(LOG_TAG, "Error sending sms " + url + ":" + e.toString());
                }
            }
            return false;
        }

        /*
         * onPageStarted fires the LOAD_RESOURCE_EVENT
         *
         * @param view
         * @param url
         */
        @Override
        public void onLoadResource(WebView view, String url) {
            LOG.d(LOG_TAG, "LOADING RESOURCE: " + url);
            try {
                JSONObject obj = new JSONObject();
                obj.put("type", LOAD_RESOURCE_EVENT);
                obj.put("url", url);
                sendUpdate(obj, true);
            } catch (JSONException ex) {
                LOG.e(LOG_TAG, "URI passed in has caused a JSON error.");
            }
            super.onLoadResource(view, url);
        }

        /*
         * onPageStarted fires the LOAD_START_EVENT
         *
         * @param view
         * @param url
         * @param favicon
         */
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            String newloc = "";
            if (url.startsWith("http:") || url.startsWith("https:") || url.startsWith("file:")) {
                newloc = url;
            }
            else
            {
                // Assume that everything is HTTP at this point, because if we don't specify,
                // it really should be.  Complain loudly about this!!!
                LOG.e(LOG_TAG, "Possible Uncaught/Unknown URI");
                newloc = "http://" + url;
            }

            // Update the UI if we haven't already
            if (!newloc.equals(edittext.getText().toString())) {
                edittext.setText(newloc);
            }

            try {
                JSONObject obj = new JSONObject();
                obj.put("type", LOAD_START_EVENT);
                obj.put("url", newloc);
                sendUpdate(obj, true);
            } catch (JSONException ex) {
                LOG.e(LOG_TAG, "URI passed in has caused a JSON error.");
            }
        }



        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);

            // CB-10395 LocalTunnel's WebView not storing cookies reliable to local device storage
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().flush();
            } else {
                CookieSyncManager.getInstance().sync();
            }

            LOG.d(LOG_TAG, "PAGE FINISHED: " + url);
            if (url.equals(requestUrl)) {
                // Note: Android APIs don't give us a way to access the real HTTP response code, so
                // in this case we always return 200. It may be possible to get the real status code
                // only for >= 400 and only in API Level >= 23.
                // https://stackoverflow.com/questions/11889020/get-http-status-code-in-android-webview
                sendRequestDone(200, "");
                requestUrl = null;
                enableRequestBlocking = false;
            }

            // https://issues.apache.org/jira/browse/CB-11248
            view.clearFocus();
            view.requestFocus();

            try {
                JSONObject obj = new JSONObject();
                obj.put("type", LOAD_STOP_EVENT);
                obj.put("url", url);

                sendUpdate(obj, true);
            } catch (JSONException ex) {
                LOG.d(LOG_TAG, "Should never happen");
            }
        }

        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            LOG.d(LOG_TAG, "RECEIVED ERROR. errorCode: " + errorCode + ". description: " + description + ". failingUrl: " + failingUrl);
            super.onReceivedError(view, errorCode, description, failingUrl);

            // Note: errorCode corresponds to an ERROR_* constant in the range of ~ [-16,4]
            // https://developer.android.com/reference/android/webkit/WebViewClient#constants_1
            sendRequestDone(errorCode, description);
        }

        /**
         * On received http auth request.
         */
        @Override
        public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {

            // Check if there is some plugin which can resolve this auth challenge
            PluginManager pluginManager = null;
            try {
                Method gpm = webView.getClass().getMethod("getPluginManager");
                pluginManager = (PluginManager)gpm.invoke(webView);
            } catch (NoSuchMethodException e) {
                LOG.d(LOG_TAG, e.getLocalizedMessage());
            } catch (IllegalAccessException e) {
                LOG.d(LOG_TAG, e.getLocalizedMessage());
            } catch (InvocationTargetException e) {
                LOG.d(LOG_TAG, e.getLocalizedMessage());
            }

            if (pluginManager == null) {
                try {
                    Field pmf = webView.getClass().getField("pluginManager");
                    pluginManager = (PluginManager)pmf.get(webView);
                } catch (NoSuchFieldException e) {
                    LOG.d(LOG_TAG, e.getLocalizedMessage());
                } catch (IllegalAccessException e) {
                    LOG.d(LOG_TAG, e.getLocalizedMessage());
                }
            }

            if (pluginManager != null && pluginManager.onReceivedHttpAuthRequest(webView, new CordovaHttpAuthHandler(handler), host, realm)) {
                return;
            }

            // By default handle 401 like we'd normally do!
            super.onReceivedHttpAuthRequest(view, handler, host, realm);
        }
    }
}
