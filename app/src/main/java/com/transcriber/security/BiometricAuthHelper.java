package com.transcriber.security;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

/**
 * Biometric authentication helper for unlocking encryption keys.
 */
public class BiometricAuthHelper {

    private static final String TAG = "BiometricAuthHelper";

    private FragmentActivity activity;
    private Executor executor;

    public BiometricAuthHelper(FragmentActivity activity) {
        this.activity = activity;
        this.executor = ContextCompat.getMainExecutor(activity);
    }

    /**
     * Prompt user for biometric authentication.
     */
    public void authenticate(String title, String subtitle, AuthCallback callback) {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG |
                        androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Log.e(TAG, "Authentication error: " + errString);
                if (callback != null) {
                    callback.onAuthFailure("Authentication error: " + errString);
                }
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                Log.i(TAG, "Authentication succeeded");
                if (callback != null) {
                    callback.onAuthSuccess();
                }
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Log.w(TAG, "Authentication failed");
            }
        });

        biometricPrompt.authenticate(promptInfo);
    }

    /**
     * Callback interface for authentication results.
     */
    public interface AuthCallback {
        void onAuthSuccess();
        void onAuthFailure(String error);
    }
}
