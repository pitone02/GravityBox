package com.ceco.gm2.gravitybox;

import java.nio.CharBuffer;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class FixCallerIdMms {
    public static final String PACKAGE_NAME = "com.android.mms";
    public static final String TAG = "FixCallerIdMms";
    public static final String CLASS_CONTACTS_CACHE = "com.android.mms.data.Contact$ContactsCache";
    private static final int STATIC_KEY_BUFFER_MAXIMUM_LENGTH = 5;
    private static final boolean DEBUG = false;

    public static void init(final XSharedPreferences prefs, ClassLoader classLoader) {
        XposedBridge.log(TAG + ": init");

        try {
            final Class<?> contactsCacheClass = XposedHelpers.findClass(CLASS_CONTACTS_CACHE, classLoader);

            XposedHelpers.findAndHookMethod(contactsCacheClass, "key", String.class, CharBuffer.class,
                    new XC_MethodReplacement() {

                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            String phoneNumber = (String) param.args[0];
                            CharBuffer keyBuffer = (CharBuffer) param.args[1];
                            keyBuffer.clear();
                            keyBuffer.mark();

                            int position = phoneNumber.length();
                            int resultCount = 0;
                            while (--position >= 0) {
                                char c = phoneNumber.charAt(position);
                                if (Character.isDigit(c)) {
                                    keyBuffer.put(c);
                                    if (++resultCount == STATIC_KEY_BUFFER_MAXIMUM_LENGTH) {
                                        break;
                                    }
                                }
                            }
                            String retVal = phoneNumber;
                            keyBuffer.reset();
                            if (resultCount > 0) {
                                retVal = keyBuffer.toString();
                            }
                            if (DEBUG) {
                                XposedBridge.log(TAG + ": key(" + phoneNumber + "); retVal='" + retVal + "'");
                            }
                            return retVal;
                        }
            });

            XposedHelpers.findAndHookMethod(contactsCacheClass, "getKey", String.class, boolean.class,
                    new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String key = (String) param.getResult();
                    if (key.length() > STATIC_KEY_BUFFER_MAXIMUM_LENGTH)
                        key = key.substring(0, key.length() - 1);
                    if (DEBUG) {
                        XposedBridge.log(TAG + ": getKey(): original retVal='" + param.getResult() + "'; " +
                                "new retVal='" + key + "'");
                    }
                    param.setResult(key);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}