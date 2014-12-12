package com.grayfox.android.activity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.foursquare.android.nativeoauth.FoursquareCancelException;
import com.foursquare.android.nativeoauth.FoursquareDenyException;
import com.foursquare.android.nativeoauth.FoursquareOAuth;
import com.foursquare.android.nativeoauth.FoursquareOAuthException;
import com.foursquare.android.nativeoauth.model.AuthCodeResponse;
import com.grayfox.android.R;
import com.grayfox.android.dao.AppAccessTokenDao;
import com.grayfox.android.client.task.RegisterAppUserAsyncTask;

import java.lang.ref.WeakReference;

import javax.inject.Inject;

import roboguice.activity.RoboActionBarActivity;
import roboguice.inject.ContentView;
import roboguice.inject.InjectView;

/**
 * Connects this app with Foursquare.
 * 
 * @author Daniel Pedraza-Arcega
 * @since version 1.0
 */
@ContentView(R.layout.activity_sign_in)
public class SignInActivity extends RoboActionBarActivity {

    private static final int REQUEST_CODE_FOURSQUARE_CONNECT = 200;

    @InjectView(R.id.connect_to_foursquare_button) private Button connectToFoursquareButton;

    @Inject private AppAccessTokenDao appAccessTokenDao;

    private ProgressDialog registerProgressDialog;
    private RegisterTask registerTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (appAccessTokenDao.fetchAccessToken() != null) finishAndGotoMainActivity();
        else {
            setTitle(R.string.title_activity_sign_in);
            connectToFoursquareButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    Intent intent = FoursquareOAuth.getConnectIntent(SignInActivity.this, getString(R.string.foursquare_client_id));
                    if (FoursquareOAuth.isPlayStoreIntent(intent)) {
                        toastMessage(R.string.foursquare_not_installed_message);
                        startActivity(intent);
                    } else startActivityForResult(intent, REQUEST_CODE_FOURSQUARE_CONNECT);
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (registerProgressDialog != null && registerProgressDialog.isShowing()) {
            registerTask.cancel(true);
            registerProgressDialog.dismiss();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_FOURSQUARE_CONNECT:
                onCompleteFoursquareConnect(resultCode, data);
                break;
            default: super.onActivityResult(requestCode, resultCode, data);
        }
    }
    
    private void onCompleteFoursquareConnect(int resultCode, Intent data) {
        AuthCodeResponse codeResponse = FoursquareOAuth.getAuthCodeFromResult(resultCode, data);
        Exception exception = codeResponse.getException();
        if (exception == null) {
            String code = codeResponse.getCode();
            registerTask = new RegisterTask(this);
            registerTask.foursquareAuthorizationCode(code).request();
        } else {
            if (exception instanceof FoursquareCancelException) toastMessage(R.string.foursquare_auth_canceled);
            else if (exception instanceof FoursquareDenyException)toastMessage(R.string.foursquare_auth_denied);
            else if (exception instanceof FoursquareOAuthException) {
                String errorMessage = exception.getMessage();
                String errorCode = ((FoursquareOAuthException) exception).getErrorCode();
                toastMessage(getString(R.string.foursquare_oauth_error_message_format, errorMessage, errorCode));
            } else toastMessage(getString(R.string.foursquare_unknown_error_message_format, exception.getMessage()));
        }
    }

    private void onPreRegister() {
        registerProgressDialog = ProgressDialog.show(this, null, getString(R.string.register_in_progress), true, false);
    }

    private void onRegisterSuccess() {
        finishAndGotoMainActivity();
    }

    private void onRegisterFinally() {
        registerProgressDialog.dismiss();
    }

    private void finishAndGotoMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void toastMessage(int messageId) {
        Toast.makeText(getApplicationContext(), 
                messageId, 
                Toast.LENGTH_SHORT)
                .show();
    }
    
    private void toastMessage(String message) {
        Toast.makeText(getApplicationContext(),
                message,
                Toast.LENGTH_SHORT)
                .show();
    }

    private static class RegisterTask extends RegisterAppUserAsyncTask {

        private WeakReference<SignInActivity> reference;

        private RegisterTask(SignInActivity activity) {
            super(activity.getApplicationContext());
            reference = new WeakReference<>(activity);
        }

        @Override
        protected void onPreExecute() throws Exception {
            SignInActivity activity = reference.get();
            if (activity != null) activity.onPreRegister();
        }

        @Override
        protected void onSuccess(Void nothing) throws Exception {
            SignInActivity activity = reference.get();
            if (activity != null) activity.onRegisterSuccess();
        }

        @Override
        protected void onFinally() throws RuntimeException {
            SignInActivity activity = reference.get();
            if (activity != null) activity.onRegisterFinally();
        }
    }
}