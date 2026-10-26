package com.meridian.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meridian.config.MeridianProperties;
import com.meridian.geo.OsmRateLimiter;
import com.meridian.place.PlacesClient.PlaceData;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs the OSM client against a fake Overpass + Nominatim server. */
class OsmPlacesClientTest {

    private static final String OVERPASS_REPLY = """
            {"elements":[
              {"type":"node","id":1,"lat":15.60,"lon":73.80,"tags":{"name":"Far Cafe","amenity":"cafe"}},
              {"type":"way","id":2,"center":{"lat":15.50,"lon":73.82},
               "tags":{"name":"Viva Panjim","amenity":"restaurant","cuisine":"goan","addr:city":"Panaji"}},
              {"type":"node","id":3,"lat":15.51,"lon":73.83,"tags":{"amenity":"restaurant"}},
              {"type":"node","id":4,"lat":15.501,"lon":73.821,
               "tags":{"name":"Heritage Hotel","tourism":"hotel","amenity":"restaurant"}}
            ]}""";

    private static final String NOMINATIM_REPLY = """
            [{"osm_type":"node","osm_id":42,"lat":"15.4989","lon":"73.8278","category":"historic","type":"fort",
              "name":"Reis Magos Fort","display_name":"Reis Magos Fort, Verem, Bardez, North Goa, Goa, India",
              "extratags":{"wikidata":"Q7310281","website":"reismagosfort.com"}},
             {"osm_type":"way","osm_id":7,"lat":"15.49","lon":"73.82","category":"highway","type":"residential",
              "name":"","display_name":"Rua de Ourém, Panaji, Goa, India","extratags":null}]""";

    private HttpServer server;
    private OsmPlacesClient client;
    private final AtomicReference<String> lastOverpassBody = new AtomicReference<>();
    private final AtomicReference<String> lastQuery = new AtomicReference<>();
    private final AtomicReference<String> lastUserAgent = new AtomicReference<>();
    private volatile int status = 200;
    private volatile int overpassStatus = 200;
    private volatile String overpassType = "application/json";
    private String base;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/interpreter", ex -> {
            lastOverpassBody.set(URLDecoder.decode(new String(ex.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8), StandardCharsets.UTF_8));
            reply(ex, OVERPASS_REPLY, overpassStatus, overpassType);
        });
        server.createContext("/search", ex -> {
            lastQuery.set(URLDecoder.decode(ex.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
            reply(ex, NOMINATIM_REPLY, status, "application/json");
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        client = clientWithOverpass(base + "/api/interpreter");
    }

    private OsmPlacesClient clientWithOverpass(String overpassUrls) {
        return new OsmPlacesClient(new MeridianProperties(null, null, null,
                new MeridianProperties.Places("osm", overpassUrls, base, 50, Duration.ofDays(7))),
                new OsmRateLimiter());
    }

    private void reply(HttpExchange ex, String body, int status, String type) throws IOException {
        lastUserAgent.set(ex.getRequestHeaders().getFirst("User-Agent"));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void nearbyKeepsNamedPlacesOfTheCategoryNearestFirst() {
        List<PlaceData> places = client.nearby(15.50, 73.82, PlaceCategory.FOOD, 10, 5);

        assertThat(places).extracting(PlaceData::name).containsExactly("Viva Panjim", "Far Cafe");
        PlaceData viva = places.getFirst();
        assertThat(viva.sourceId()).isEqualTo("way/2");
        assertThat(viva.source()).isEqualTo("osm");
        assertThat(viva.lat()).isEqualTo(15.50);
        assertThat(viva.address()).isEqualTo("Panaji");
        assertThat(viva.tags()).containsEntry("cuisine", "goan");
        assertThat(lastOverpassBody.get()).startsWith("data=[out:json]").contains("around:10000,15.500000,73.820000");
        assertThat(lastUserAgent.get()).startsWith("Meridian/");
    }

    @Test
    void searchIsBoundedToTheTripAreaAndParsesExtratags() {
        List<PlaceData> places = client.search("fort", 15.5, 73.8, 50, 10);

        assertThat(lastQuery.get()).contains("q=fort").contains("bounded=1").contains("extratags=1")
                .contains("viewbox=");
        assertThat(places).hasSize(2);
        PlaceData fort = places.getFirst();
        assertThat(fort.sourceId()).isEqualTo("node/42");
        assertThat(fort.category()).isEqualTo(PlaceCategory.SIGHTS);
        assertThat(fort.website()).isEqualTo("https://reismagosfort.com");
        assertThat(fort.popularityProxy()).isEqualTo(3);
        assertThat(fort.address()).isEqualTo("Verem, Bardez, North Goa");
        // unnamed results fall back to the first part of the display name
        assertThat(places.get(1).name()).isEqualTo("Rua de Ourém");
        assertThat(places.get(1).category()).isEqualTo(PlaceCategory.OTHER);
    }

    @Test
    void searchWithoutCoordinatesIsUnbounded() {
        client.search("fort", null, null, 50, 10);
        assertThat(lastQuery.get()).doesNotContain("bounded");
    }

    @Test
    void triesTheNextOverpassMirrorAndAcceptsAnyContentType() {
        overpassType = "application/octet-stream";
        OsmPlacesClient mirrored = clientWithOverpass("http://127.0.0.1:1/dead, " + base + "/api/interpreter");
        assertThat(mirrored.nearby(15.50, 73.82, PlaceCategory.FOOD, 10, 5)).extracting(PlaceData::name)
                .containsExactly("Viva Panjim", "Far Cafe");
    }

    @Test
    void fallsBackToNominatimCategoryPhrasesWhenOverpassIsBusy() {
        overpassStatus = 502;
        List<PlaceData> sights = client.nearby(15.50, 73.82, PlaceCategory.SIGHTS, 10, 5);
        // one fort, however many phrases returned it; the street is not a sight
        assertThat(sights).extracting(PlaceData::name).containsExactly("Reis Magos Fort");
        assertThat(lastQuery.get()).contains("bounded=1").contains("limit=40");
    }

    @Test
    void providerErrorsBecomePlacesUnavailable() {
        status = 504;
        overpassStatus = 504;
        assertThatThrownBy(() -> client.nearby(15.5, 73.8, PlaceCategory.FOOD, 10, 5))
                .isInstanceOf(PlacesClient.PlacesUnavailableException.class);
        server.stop(0);
        assertThatThrownBy(() -> client.search("fort", 15.5, 73.8, 50, 10))
                .isInstanceOf(PlacesClient.PlacesUnavailableException.class);
    }

    @Test
    void viewboxSpansTheRadius() {
        assertThat(OsmPlacesClient.viewbox(0, 0, 111)).isEqualTo("-0.99713,0.99713,0.99713,-0.99713");
    }
}
