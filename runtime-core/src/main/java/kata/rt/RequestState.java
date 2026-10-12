package kata.rt;

/**
 * Decides when a Request component sends its exchange. A request is sent once. It is sent again only when
 * its reply could not be handed to the app and it is a GET: repeating any other method could repeat its
 * effect on the server (a second order, a second deletion).
 */
public final class RequestState {
    private String started;
    private boolean inFlight;
    private boolean delivered;

    /**
     * @param key identifies the exchange (method, address and body)
     * @return true when the exchange must be sent now
     */
    public boolean shouldStart(String method, String key) {
        if (!key.equals(started)) {
            started = key;
            inFlight = true;
            delivered = false;
            return true;
        }
        if (!inFlight && !delivered && method.equals("GET")) {
            inFlight = true;
            return true;
        }
        return false;
    }

    /**
     * @param attached whether the component is on screen to receive the reply
     * @return true when the reply must be handed to the app
     */
    public boolean replyArrived(String key, boolean attached) {
        if (!key.equals(started)) {
            return false;
        }
        inFlight = false;
        if (!attached) {
            return false;
        }
        delivered = true;
        return true;
    }
}
