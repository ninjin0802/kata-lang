package kata.rt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RequestStateTest {
    private final RequestState state = new RequestState();

    @Test
    void startsOncePerRequest() {
        assertTrue(state.shouldStart("POST", "a"));
        assertFalse(state.shouldStart("POST", "a"));
        assertTrue(state.replyArrived("a", true));
        assertFalse(state.shouldStart("POST", "a"));
    }

    @Test
    void aDifferentRequestStartsAgain() {
        assertTrue(state.shouldStart("GET", "a"));
        assertTrue(state.shouldStart("GET", "b"));
        assertFalse(state.replyArrived("a", true), "the reply to the replaced request is dropped");
        assertTrue(state.replyArrived("b", true));
    }

    @Test
    void anUndeliveredPostIsNeverSentAgain() {
        assertTrue(state.shouldStart("POST", "a"));
        assertFalse(state.replyArrived("a", false), "the view was away when the reply came");
        assertFalse(state.shouldStart("POST", "a"), "sending it again could repeat its effect on the server");
        assertFalse(state.shouldStart("DELETE", "a"));
        assertFalse(state.shouldStart("PUT", "a"));
    }

    @Test
    void anUndeliveredGetIsFetchedAgain() {
        assertTrue(state.shouldStart("GET", "a"));
        assertFalse(state.replyArrived("a", false));
        assertTrue(state.shouldStart("GET", "a"));
        assertFalse(state.shouldStart("GET", "a"), "not while the retry is in flight");
        assertTrue(state.replyArrived("a", true));
        assertFalse(state.shouldStart("GET", "a"));
    }
}
