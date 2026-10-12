package kata.rt;

/** Shows a web page. Using it adds the internet permission to the app. */
public final class WebView implements Ui {
    public final String url;

    public WebView(String url) {
        this.url = url;
    }
}
