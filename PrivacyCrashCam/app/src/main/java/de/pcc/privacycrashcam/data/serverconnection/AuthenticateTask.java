package de.pcc.privacycrashcam.data.serverconnection;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import de.pcc.privacycrashcam.data.Account;

/**
 * Task to asynchronously authenticate the user. This class already knows hwo to pass the params to
 * the REST interface, the API method call and how to parse the result.
 *
 * @author Giorgio Gross, David Laubenstein
 */
public class AuthenticateTask extends AsyncTask<String, Integer, AuthenticationState> {
    private final static String TAG = AuthenticateTask.class.getName();

    /**
     * Function call which will be appended to the domain name
     */
    private static final String API_CALL = "authenticate";
    // responses to be expected
    private static final String API_RESPONSE_FAILURE_MISSING = "NOT EXISTING";
    private static final String API_RESPONSE_FAILURE_MISMATCH = "WRONG PASSWORD";
    private static final String API_RESPONSE_NOT_VERIFIED = "NOT VERIFIED";
    private static final String API_RESPONSE_SUCCESS = "SUCCESS";
    private Context context;

    private Account account;
    private ServerResponseCallback<AuthenticationState> callback;

    /**
     * Sets up a new task to authenticate the user with the passed parameters
     *
     * @param account  Account which will be used for upload
     * @param callback Observer which is notified about errors and state changes
     * @param context  Application context
     */
    public AuthenticateTask(Account account, ServerResponseCallback<AuthenticationState> callback, Context context) {
        this.account = account;
        this.callback = callback;
        this.context = context;
    }

    /**
     * @param params Domain to access the API
     * @return {@link AuthenticationState}
     */
    @Override
    protected AuthenticationState doInBackground(String... params) {
        if (!ServerHelper.IsNetworkAvailable())
            return AuthenticationState.FAILURE_NETWORK;

        AuthenticationState resultState;
        String domain = params[0];

        Form form = new Form();
        form.param("account", account.getAsJSON());
        Client client = ClientBuilder.newClient();
        WebTarget webTarget = client.target(domain).path(API_CALL);
        Log.i(TAG, "URI: " + webTarget.getUri().toASCIIString());
        Response response = webTarget.request().post(Entity.entity(form,
                MediaType.APPLICATION_FORM_URLENCODED_TYPE), Response.class);
        String responseContent = response.readEntity(String.class);
        Log.d(responseContent, responseContent);
        Log.i(TAG, "response: " + responseContent);

        switch (responseContent) {
            case API_RESPONSE_FAILURE_MISSING:
                resultState = AuthenticationState.FAILURE_MISSING;
                break;
            case API_RESPONSE_FAILURE_MISMATCH:
                resultState = AuthenticationState.FAILURE_MISMATCH;
                break;
            case API_RESPONSE_NOT_VERIFIED:
                resultState = AuthenticationState.NOT_VERIFIED;
                break;
            case API_RESPONSE_SUCCESS:
                resultState = AuthenticationState.SUCCESS;
                break;
            default:
                resultState = AuthenticationState.FAILURE_OTHER;
                break;
        }

        return resultState;
    }

    /**
     * Called after authentication was executed.
     *
     * @param requestState the result state or null if the network was unavailable
     */
    @Override
    protected void onPostExecute(AuthenticationState requestState) {
        super.onPostExecute(requestState);

        if (requestState != AuthenticationState.FAILURE_NETWORK)
            callback.onResponse(requestState);
        else callback.onError("No network available");
    }
}
