package com.apollographql.android.impl;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.io.Files;

import com.apollographql.android.ApolloCall;
import com.apollographql.android.CustomTypeAdapter;
import com.apollographql.android.api.graphql.Error;
import com.apollographql.android.api.graphql.Response;
import com.apollographql.android.impl.type.CustomType;
import com.apollographql.android.impl.util.AndroidExecutor;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

import javax.annotation.Nonnull;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static com.google.common.truth.Truth.assertThat;

public class IntegrationTest {
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);

  private ApolloClient apolloClient;
  private CustomTypeAdapter<Date> dateCustomTypeAdapter;

  @Rule public final MockWebServer server = new MockWebServer();

  @Before public void setUp() {
    dateCustomTypeAdapter = new CustomTypeAdapter<Date>() {
      @Override public Date decode(String value) {
        try {
          return DATE_FORMAT.parse(value);
        } catch (ParseException e) {
          throw new RuntimeException(e);
        }
      }

      @Override public String encode(Date value) {
        return DATE_FORMAT.format(value);
      }
    };

    apolloClient = ApolloClient.builder()
        .serverUrl(server.url("/"))
        .okHttpClient(new OkHttpClient.Builder().build())
        .withCustomTypeAdapter(CustomType.DATETIME, dateCustomTypeAdapter)
        .build();
  }

  @SuppressWarnings("ConstantConditions") @Test public void allPlanetQuery() throws Exception {
    server.enqueue(mockResponse("src/test/graphql/allPlanetsResponse.json"));

    ApolloCall call = apolloClient.newCall(new AllPlanets());
    Response<AllPlanets.Data> body = call.execute();
    assertThat(body.isSuccessful()).isTrue();

    assertThat(server.takeRequest().getBody().readString(Charsets.UTF_8))
        .isEqualTo("{\"query\":\"query TestQuery {  "
            + "allPlanets(first: 300) {"
            + "    planets {"
            + "      ...PlanetFragment"
            + "      filmConnection {"
            + "        totalCount"
            + "        films {"
            + "          title"
            + "          ...FilmFragment"
            + "        }"
            + "      }"
            + "    }"
            + "  }"
            + "}"
            + "fragment PlanetFragment on Planet {"
            + "  name"
            + "  climates"
            + "  surfaceWater"
            + "}"
            + "fragment FilmFragment on Film {"
            + "  title"
            + "  producers"
            + "}\",\"variables\":{}}");

    AllPlanets.Data data = body.data();
    assertThat(data.allPlanets().planets().size()).isEqualTo(60);

    List<String> planets = FluentIterable.from(data.allPlanets().planets())
        .transform(new Function<AllPlanets.Data.AllPlanet.Planet, String>() {
          @Override public String apply(AllPlanets.Data.AllPlanet.Planet planet) {
            return planet.fragments().planetFargment().name();
          }
        }).toList();
    assertThat(planets).isEqualTo(Arrays.asList(("Tatooine, Alderaan, Yavin IV, Hoth, Dagobah, Bespin, Endor, Naboo, "
        + "Coruscant, Kamino, Geonosis, Utapau, Mustafar, Kashyyyk, Polis Massa, Mygeeto, Felucia, Cato Neimoidia, "
        + "Saleucami, Stewjon, Eriadu, Corellia, Rodia, Nal Hutta, Dantooine, Bestine IV, Ord Mantell, unknown, "
        + "Trandosha, Socorro, Mon Cala, Chandrila, Sullust, Toydaria, Malastare, Dathomir, Ryloth, Aleen Minor, "
        + "Vulpter, Troiken, Tund, Haruun Kal, Cerea, Glee Anselm, Iridonia, Tholoth, Iktotch, Quermia, Dorin, "
        + "Champala, Mirial, Serenno, Concord Dawn, Zolan, Ojom, Skako, Muunilinst, Shili, Kalee, Umbara")
        .split("\\s*,\\s*")
    ));

    AllPlanets.Data.AllPlanet.Planet firstPlanet = data.allPlanets().planets().get(0);
    assertThat(firstPlanet.fragments().planetFargment().climates()).isEqualTo(Collections.singletonList("arid"));
    assertThat(firstPlanet.fragments().planetFargment().surfaceWater()).isWithin(1d);
    assertThat(firstPlanet.filmConnection().totalCount()).isEqualTo(5);
    assertThat(firstPlanet.filmConnection().films().size()).isEqualTo(5);
    assertThat(firstPlanet.filmConnection().films().get(0).fragments().filmFragment().title()).isEqualTo("A New Hope");
    assertThat(firstPlanet.filmConnection().films().get(0).fragments().filmFragment().producers()).isEqualTo(Arrays
        .asList("Gary Kurtz", "Rick McCallum"));
  }

  @Test public void errorResponse() throws Exception {
    server.enqueue(mockResponse("src/test/graphql/errorResponse.json"));
    Response<AllPlanets.Data> body = apolloClient.newCall(new AllPlanets()).execute();
    assertThat(body.isSuccessful()).isFalse();
    //noinspection ConstantConditions
    assertThat(body.errors()).containsExactly(new Error(
        "Cannot query field \"names\" on type \"Species\".",
        Collections.singletonList(new Error.Location(3, 5))));
  }

  @Test public void productsWithDates() throws Exception {
    server.enqueue(mockResponse("src/test/graphql/productsWithDate.json"));

    ApolloCall call = apolloClient.newCall(new ProductsWithDate());
    Response<ProductsWithDate.Data> body = call.execute();
    assertThat(body.isSuccessful()).isTrue();

    assertThat(server.takeRequest().getBody().readString(Charsets.UTF_8))
        .isEqualTo("{\"query\":\"query ProductsWithDate {" +
            "  shop {" +
            "    products(first: 10) {" +
            "      edges {" +
            "        node {" +
            "          title" +
            "          createdAt" +
            "        }" +
            "      }" +
            "    }" +
            "  }}\",\"variables\":{}}");

    ProductsWithDate.Data data = body.data();
    assertThat(data.shop().products().edges().size()).isEqualTo(11);

    List<String> dates = FluentIterable.from(data.shop().products().edges())
        .transform(new Function<ProductsWithDate.Data.Shop.Product.Edge, String>() {
          @Override public String apply(ProductsWithDate.Data.Shop.Product.Edge productEdge) {
            Date createdAt = productEdge.node().createdAt();
            if(createdAt == null) {
              return null;
            }
            return dateCustomTypeAdapter.encode(createdAt);
          }
        }).copyInto(new ArrayList<String>());

    assertThat(dates).isEqualTo(Arrays.asList(
        "2013-11-18T19:35:35Z", "2013-11-18T19:35:40Z", "2013-11-18T19:35:54Z", "2013-11-18T19:35:56Z",
        "2013-11-18T19:36:33Z", "2013-11-18T19:36:45Z", "2013-11-18T19:37:08Z", "2013-11-18T19:37:24Z",
        "2013-11-18T19:37:26Z", "2013-11-18T19:37:28Z", null));
  }

  @Test public void productsWithUnsupportedCustomScalarTypes() throws Exception {
    server.enqueue(mockResponse("src/test/graphql/productsWithUnsupportedCustomScalarTypes.json"));

    ApolloCall call = apolloClient.newCall(new ProductsWithUnsupportedCustomScalarTypes());
    Response<ProductsWithUnsupportedCustomScalarTypes.Data> body = call.execute();
    assertThat(body.isSuccessful()).isTrue();

    ProductsWithUnsupportedCustomScalarTypes.Data data = body.data();
    assertThat(data.shop().products().edges().size()).isEqualTo(1);
    assertThat(data.shop().products().edges().get(0).node().unsupportedCustomScalarTypeNumber()).isInstanceOf(BigDecimal.class);
    assertThat(data.shop().products().edges().get(0).node().unsupportedCustomScalarTypeNumber()).isEqualTo
        (BigDecimal.valueOf(1));
    assertThat(data.shop().products().edges().get(0).node().unsupportedCustomScalarTypeBool()).isInstanceOf(Boolean.class);
    assertThat(data.shop().products().edges().get(0).node().unsupportedCustomScalarTypeBool()).isEqualTo(Boolean.TRUE);
    assertThat(data.shop().products().edges().get(0).node().unsupportedCustomScalarTypeString()).isInstanceOf(String.class);
    assertThat(data.shop().products().edges().get(0).node().unsupportedCustomScalarTypeString()).isEqualTo("something");
  }

  @Test public void allPlanetQueryAsync() throws Exception {

    String contents = streamResourceToStringWithClose(getClass(), "/allPlanetsResponse.json");
    MockResponse mockResponse = new MockResponse().setChunkedBody(contents, 32);
    server.enqueue(mockResponse);
    //server.enqueue(mockResponse("src/test/graphql/allPlanetsResponse.json"));

    final CountDownLatch latch = new CountDownLatch(1);
    ApolloCall call = apolloClient.newCall(new AllPlanets());
    call.enqueue(new ApolloCall.Callback<AllPlanets.Data>() {
      @Override public void onResponse(@Nonnull Response<AllPlanets.Data> response) {
        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.data().allPlanets().planets().size()).isEqualTo(60);
        latch.countDown();
      }

      @Override public void onFailure(@Nonnull Exception e) {
        latch.countDown();
        Assert.fail("expected success");
      }
    });
    latch.await();
  }

  @Test public void allPlanetQueryExecutor() throws Exception {
    server.enqueue(mockResponse("src/test/graphql/allPlanetsResponse.json"));
    final CountDownLatch latch = new CountDownLatch(1);

    apolloClient = ApolloClient.builder()
        .serverUrl(server.url("/"))
        .executor(AndroidExecutor.create())
        .okHttpClient(new OkHttpClient.Builder().build())
        .withCustomTypeAdapter(CustomType.DATETIME, dateCustomTypeAdapter)
        .build();

    ApolloCall call = apolloClient.newCall(new AllPlanets());
    call.enqueue(new ApolloCall.Callback<AllPlanets.Data>() {
      @Override public void onResponse(@Nonnull Response<AllPlanets.Data> response) {
        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.data().allPlanets().planets().size()).isEqualTo(60);
        latch.countDown();
      }

      @Override public void onFailure(@Nonnull Exception e) {
        latch.countDown();
        Assert.fail("expected success");
      }
    });
    latch.await();
  }

  private static MockResponse mockResponse(String fileName) throws IOException {
    return new MockResponse().setChunkedBody(Files.toString(new File(fileName), Charsets.UTF_8), 32);
  }

  public static String streamResourceToStringWithClose(final Class contextClass,
      final String streamIdentifier) throws IOException {
    InputStream inputStream = null;
    inputStream = contextClass.getResourceAsStream(streamIdentifier);


    //(InputStream inputStream = contextClass.getResourceAsStream(streamIdentifier)) {
      //return IOUtils.toString(inputStream);
      return IOUtils.toString(inputStream);

  }

}
