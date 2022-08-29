package net.rcarz.jiraclient;

import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.anyString;

public class WatchesTest {

    @Test
    public void testWatchesInit() {
        Watches watches = new Watches(null, null);
    }

    @Test
    public void testWatchesJSON() {
        Watches watches = new Watches(null, Utils.getTestIssueWatchers());

        assertFalse(watches.isWatching());
        assertEquals(watches.getWatchCount(), 2);
        assertEquals(watches.getSelf(), "https://brainbubble.atlassian.net/rest/api/2/issue/FILTA-43/watchers");
        assertEquals(watches.getWatchers().size(), 2);
        assertEquals("fred", watches.getWatchers().get(0).getName());
        assertEquals("john", watches.getWatchers().get(1).getName());
    }

    @Test(expected = JiraException.class)
    public void testGetWatchersNullReturned() throws Exception {
        final RestClient restClient = Mockito.mock(RestClient.class);
        Mockito.when(restClient.get(anyString())).thenReturn(null);
        Watches.get(restClient, "someID");
    }

    @Test(expected = JiraException.class)
    public void testGetWatchersGetThrows() throws Exception {
        final RestClient restClient = Mockito.mock(RestClient.class);
        Mockito.when(restClient.get(anyString())).thenThrow(Exception.class);
        Watches.get(restClient, "someID");
    }

    @Test
    public void testGetWatchers() throws Exception {
        final RestClient restClient = Mockito.mock(RestClient.class);
        Mockito.when(restClient.get(anyString())).thenReturn(Utils.getTestIssueWatchers());
        final Watches watches = Watches.get(restClient, "someID");

        assertFalse(watches.isWatching());
        assertEquals(2, watches.getWatchCount());
        assertEquals( "https://brainbubble.atlassian.net/rest/api/2/issue/FILTA-43/watchers", watches.getSelf());
        assertEquals(2, watches.getWatchers().size());
        assertEquals("fred", watches.getWatchers().get(0).getName());
        assertEquals("john", watches.getWatchers().get(1).getName());
    }

    @Test
    public void testWatchesToString() {
        Watches watches = new Watches(null, Utils.getTestIssueWatchers());
        assertEquals(watches.toString(), "2");
    }
}
