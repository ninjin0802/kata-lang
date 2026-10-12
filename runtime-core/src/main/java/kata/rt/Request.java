package kata.rt;

/** While shown, performs one HTTP request and reports the response body or the failure. */
public final class Request implements Ui {
    public final String method;
    public final String url;
    public final String body;
    public final Handler onResult;
    public final Handler onError;
    public final Ui child;

    public Request(String method, String url, String body, Handler onResult, Handler onError, Ui child) {
        this.method = method;
        this.url = url;
        this.body = body;
        this.onResult = onResult;
        this.onError = onError;
        this.child = child;
    }
}
