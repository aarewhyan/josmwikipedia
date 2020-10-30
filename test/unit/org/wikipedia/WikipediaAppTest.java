// License: GPL. For details, see LICENSE file.
package org.wikipedia;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.wikipedia.data.WikidataEntry;
import org.wikipedia.data.WikipediaEntry;

public class WikipediaAppTest {

    /**
     * Setup test.
     */
    @Rule
    public JOSMTestRules rules = new JOSMTestRules().preferences().timeout(20_000);

    @Test
    public void testMediawikiLocale() throws Exception {
        assertThat(WikipediaApp.getMediawikiLocale(Locale.GERMANY), is("de-de"));
        assertThat(WikipediaApp.getMediawikiLocale(Locale.GERMAN), is("de"));
        assertThat(WikipediaApp.getMediawikiLocale(Locale.UK), is("en-gb"));
        assertThat(WikipediaApp.getMediawikiLocale(Locale.CANADA), is("en-ca"));
    }

    @Test
    public void testPartitionList() {
        assertThat(
                WikipediaApp.partitionList(Arrays.asList(1, 2, 3, 4, 5), 2),
                is(Arrays.asList(
                        Arrays.asList(1, 2),
                        Arrays.asList(3, 4),
                        Arrays.asList(5)
                ))
        );
    }

    @Test
    public void testGetInterwikiArticles1() {
        final Collection<WikipediaEntry> iw = WikipediaApp.forLanguage("de").getInterwikiArticles("Österreich");
        assertThat(iw, hasItem(new WikipediaEntry("en", "Austria")));
        assertThat(iw, hasItem(new WikipediaEntry("nb", "Østerrike")));
        assertThat(iw, hasItem(new WikipediaEntry("ko", "오스트리아")));
    }

    @Test
    public void testGetInterwikiArticles2() {
        final Collection<WikipediaEntry> iw = WikipediaApp.forLanguage("en").getInterwikiArticles("Ampersand");
        assertThat(iw, hasItem(new WikipediaEntry("fi", "&")));
    }

    @Test
    public void testGetCoordinates() throws Exception {
        assertThat(WikipediaApp.forLanguage("de").getCoordinateForArticle("Marchreisenspitze"), is(new LatLon(47.1725, 11.30833333)));
        assertThat(WikipediaApp.forLanguage("en").getCoordinateForArticle("Austria"), is(new LatLon(47.33333333, 13.33333333)));
        assertThat(WikipediaApp.forLanguage("en").getCoordinateForArticle("Foobar2000"), nullValue());
    }

    @Test
    public void testFromCoordinates() throws Exception {
        final List<WikipediaEntry> entries = WikipediaApp.forLanguage("de")
                .getEntriesFromCoordinates(new LatLon(52.5179786, 13.3753321), new LatLon(52.5192215, 13.3768705));
        final long c = entries.stream()
                .filter(entry -> "Reichstagsgebäude".equals(entry.article) && "de".equals(entry.lang))
                .count();
        assertEquals(1, c);
    }

    @Test
    public void testForQuery() throws Exception {
        final List<WikidataEntry> de = WikipediaApp.getWikidataEntriesForQuery("de", "Österreich", Locale.GERMAN);
        final List<WikidataEntry> en = WikipediaApp.getWikidataEntriesForQuery("de", "Österreich", Locale.ENGLISH);
        assertThat(de.get(0).article, is("Q40"));
        assertThat(de.get(0).lang, is("wikidata"));
        assertThat(de.get(0).label, is("Österreich"));
        assertThat(de.get(0).description, is("Staat in Mitteleuropa"));
        assertThat(en.get(0).label, is("Austria"));
        assertThat(en.get(0).description, is("sovereign state in Central Europe"));
    }

    @Test
    public void testFromCoordinatesWikidata() throws Exception {
        final List<WikipediaEntry> entries = WikipediaApp.forLanguage("wikidata")
                .getEntriesFromCoordinates(new LatLon(47.20, 11.30), new LatLon(47.22, 11.32));
        final long c = entries.stream()
                .map(WikidataEntry.class::cast)
                .filter(entry -> "Q865406".equals(entry.article) && "wikidata".equals(entry.lang) && "Birgitzer Alm".equals(entry.label))
                .count();
        assertEquals(1, c);
    }

    @Test
    public void testGetWikidataForArticles() throws Exception {
        final Map<String, String> map = WikipediaApp.forLanguage("en")
                .getWikidataForArticles(Arrays.asList("London", "Vienna", "Völs, Tyrol", "a-non-existing-article"));
        assertThat(map.get("London"), is("Q84"));
        assertThat(map.get("Vienna"), is("Q1741"));
        assertThat(map.get("Völs, Tyrol"), is("Q278250"));
        assertThat(map.get("a-non-existing-article"), nullValue());
        assertThat(map.size(), is(3));
    }

    @Test
    public void testGetWikidataForArticlesResolveRedirects() throws Exception {
        final Map<String, String> map = WikipediaApp.forLanguage("en")
                .getWikidataForArticles(Arrays.asList("einstein", "USA"));
        assertThat(map.get("einstein"), is("Q937"));
        assertThat(map.get("USA"), is("Q30"));
        assertThat(map.size(), is(2));
    }

    @Test
    public void testTicket13991() {
        final Map<String, String> map = WikipediaApp.forLanguage("en")
                .getWikidataForArticles(Stream.iterate("London", x -> x).limit(100).collect(Collectors.toList()));
        assertThat(map, is(Collections.singletonMap("London", "Q84")));
        final List<String> articles = IntStream.range(0, 200)
                .mapToObj(i -> "a-non-existing-article-" + i)
                .collect(Collectors.toList());
        assertTrue(WikipediaApp.forLanguage("en").getWikidataForArticles(articles).isEmpty());
    }

    @Test
    public void testGetLabelForWikidata() throws Exception {
        assertThat(WikipediaApp.getLabelForWikidata("Q1741", Locale.GERMAN), is("Wien"));
        assertThat(WikipediaApp.getLabelForWikidata("Q1741", Locale.ENGLISH), is("Vienna"));
        // fallback to any label
        assertThat(WikipediaApp.getLabelForWikidata("Q21849466", new Locale("aa")), is("Leoben - Straßennamen mit Geschichte"));
        // not found -> null
        assertThat(WikipediaApp.getLabelForWikidata("Q" + Integer.MAX_VALUE, Locale.ENGLISH), nullValue());
        final WikidataEntry q84 = new WikidataEntry("Q84", null, null, null);
        final WikidataEntry q1741 = new WikidataEntry("Q1741", null, null, null);
        final List<WikidataEntry> twoLabels = WikipediaApp.getLabelForWikidata(Arrays.asList(q84, q1741), Locale.GERMAN);
        assertThat(twoLabels.get(0).label, is("London"));
        assertThat(twoLabels.get(1).label, is("Wien"));
    }

    @Test(expected = RuntimeException.class)
    public void testGetLabelForWikidataInvalidId() throws Exception {
        WikipediaApp.getLabelForWikidata("Qxyz", Locale.ENGLISH);
    }

    @Test
    public void testWIWOSMStatus() throws Exception {
        final WikipediaEntry entry1 = new WikipediaEntry("en", "Vienna");
        final WikipediaEntry entry2 = new WikipediaEntry("en", "London");
        final WikipediaEntry entry3 = new WikipediaEntry("en", "a-non-existing-article");
        WikipediaApp.forLanguage("en").updateWIWOSMStatus(Arrays.asList(entry1, entry2, entry3));
        assertThat(entry1.getWiwosmStatus(), is(true));
        assertThat(entry2.getWiwosmStatus(), is(true));
        assertThat(entry3.getWiwosmStatus(), is(false));
    }

    @Test
    public void testCategoriesForPrefix() throws Exception {
        final List<String> categories = WikipediaApp.forLanguage("de").getCategoriesForPrefix("Gemeinde in Öster");
        assertTrue(categories.contains("Gemeinde in Österreich"));
    }
}
