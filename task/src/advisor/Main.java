package advisor;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Main {
    public static final String DEFAULT_SPOTIFY_URL = "https://accounts.spotify.com";
    public static final String DEFAULT_RESOURCE_URL = "https://api.spotify.com";
    public static final String OAUTH_CLIENT_ID = "1f84daa480ae4e2288a8644ab59e96b0";
    public static final String OAUTH_CLIENT_SECRET = "...";
    public static final String CODE_RECEIVED = "Got the code. Return back to your program.";
    public static final String ERROR_NO_CODE = "Authorization code not found. Try again.";
    public static final String REDIRECT_URI = "http://localhost:8080";
    private boolean userAuthenticated = false;
    private String spotifyUrl;
    private String resourceUrl;
    private OauthToken userToken;
    private Map<String, String> categories;
    private int itemsPerPage = 5;
    private List<String> currentResults;
    private Integer currentPage;

    public static void main(String[] args) {
        Main app = new Main();

        if (args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                switch(args[i]) {
                    case "-access":
                        String access = args[++i];
                        app.setSpotifyUrl(access);
                        break;

                    case "-resource":
                        String resource = args[++i];
                        app.setResourceUrl(resource);
                        break;

                    case "-page":
                        app.setItemsPerPage(Integer.parseInt(args[++i]));
                        break;

                    default:
                        System.out.println("ERROR Unknown Parameter: " + args[i]);
                }
            }
        }

        Scanner in = new Scanner(System.in);
        String inp = null;

        while (!"exit".equals(inp) && in.hasNext()) {
            inp = in.nextLine();
            String[] parts = inp.split("\\s", 2);
            switch(parts[0]) {
                case "auth":
                    app.handleAuth();
                    break;

                case "featured":
                    app.handleFeatured();
                    break;

                case "new":
                    app.handleNewReleases();
                    break;

                case "categories":
                    app.handleCategories();
                    break;

                case "playlists":
                    app.handlePlaylists(parts[1]);
                    break;

                case "exit":
                    app.handleExit(app);
                    break;

                case "next":
                    app.printNext();
                    break;

                case "prev":
                    app.printPrev();
                    break;

                default:

            }
        }
    }

    private void printPrev() {
        if (currentResults != null) {
            if (currentPage > 1) {
                currentPage--;
                printCurrentResultsPage();
            } else {
                println("No more pages.");
            }
        }
    }

    private void printNext() {
        if (currentResults != null) {
            if (currentPage < currentPageCount()) {
                currentPage++;
                printCurrentResultsPage();
            } else {
                println("No more pages.");
            }
        }
    }

    private void handleExit(Main app) {
        app.println("---GOODBYE!---");
    }

    public String getSpotifyUrl() {
        if (spotifyUrl == null) {
            return DEFAULT_SPOTIFY_URL;
        }
        return spotifyUrl;
    }

    public String getResourceUrl() {
        if (resourceUrl == null) {
            return DEFAULT_RESOURCE_URL;
        }
        return resourceUrl;
    }

    public int getItemsPerPage() {
        return itemsPerPage;
    }

    public void setItemsPerPage(int itemsPerPage) {
        this.itemsPerPage = itemsPerPage;
    }

    public void setResourceUrl(String resourceUrl) { this.resourceUrl = resourceUrl; }

    public void setSpotifyUrl(String spotifyUrl) {
        this.spotifyUrl = spotifyUrl;
    }

    public OauthToken getUserToken() {
        return userToken;
    }

    public void setUserToken(OauthToken userToken) {
        this.userToken = userToken;
    }

    private void handlePlaylists(String category) {
        if (isUserAuthenticated()) {
            if (categories == null) {
                loadCategoriesMap();
            }

            if (categories.get(category) != null) {
                // user entered category name instead
                category = categories.get(category);
            } else if (!categories.containsValue(category)) {
                println("Unknown category name.");
                return;
            }

            String playlistsUrl = getResourceUrl() + "/v1/browse/categories/" +
                    category +
                    "/playlists";
            HttpRequest request = buildHttpRequestForApi(playlistsUrl);
            HttpClient httpClient = HttpClient.newHttpClient();

            try {
                HttpResponse<String> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofString());

                //println("Status Code: " + response.statusCode());
                //println(response.body());

                currentResults = buildResultsFromJson(response.body(), "playlists");
                currentPage = 1;
                printCurrentResultsPage();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            needAuth();
        }
    }

    private void handleCategories() {
        if (isUserAuthenticated()) {

            if (categories == null) {
                loadCategoriesMap();
            }

            if (!categories.isEmpty()) {
                currentResults = new ArrayList<>(categories.keySet());
                currentPage = 1;
                printCurrentResultsPage();
            }
        } else {
            needAuth();
        }
    }

    private void loadCategoriesMap() {
        String categoriesUrl = getResourceUrl() + "/v1/browse/categories";
        HttpRequest request = buildHttpRequestForApi(categoriesUrl);
        HttpClient httpClient = HttpClient.newHttpClient();

        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            //println("Status Code: " + response.statusCode());
            if (response.statusCode() == 200) {
                //println(response.body());
                categories = parseCategories(response.body());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void handleNewReleases() {
        String newReleasesUrl = getResourceUrl() + "/v1/browse/new-releases";
        if (isUserAuthenticated()) {
            HttpRequest request = buildHttpRequestForApi(newReleasesUrl);
            HttpClient httpClient = HttpClient.newHttpClient();

            try {
                HttpResponse<String> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofString());

                //println("Status Code: " + response.statusCode());
                if (response.statusCode() == 200) {
                    //println(response.body());
                    currentResults = buildResultsFromJson(response.body(), "albums");
                    currentPage = 1;
                    printCurrentResultsPage();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            needAuth();
        }
    }

    private String extractSpotifyAlbumUrl(JsonObject obj) {
        JsonObject externalUrls = obj.get("external_urls").getAsJsonObject();
        return externalUrls.get("spotify").getAsString();
    }

    private Map<String, String> parseCategories(String body) {
        JsonObject jsonObject = JsonParser.parseString(body).getAsJsonObject();
        JsonObject albums = jsonObject.getAsJsonObject("categories");
        JsonArray items = albums.getAsJsonArray("items");
        Map<String, String> catIdsByName = new LinkedHashMap<>();

        for (JsonElement itemElement : items) {
            JsonObject category = itemElement.getAsJsonObject();
            String name = category.get("name").getAsString();
            String id = category.get("id").getAsString();
            catIdsByName.put(name, id);
        }

        return catIdsByName;
    }

    private List<String> buildResultsFromJson(String json, String rootObjectName) {
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        JsonObject rootObject = jsonObject.getAsJsonObject(rootObjectName);
        if (rootObject == null) {
            println("Null rootObject for rootObjectName: " + rootObjectName);
            println("JSON:\n" + json);
            return null;
        }
        JsonArray items = rootObject.getAsJsonArray("items");
        List<String> results = new ArrayList<>();
        for (JsonElement itemElement : items) {
            StringBuilder result = new StringBuilder();
            JsonObject item = itemElement.getAsJsonObject();
            String name = item.get("name").getAsString();
            String spotifyAlbumUrl = extractSpotifyAlbumUrl(item);

            result.append(name).append('\n');
            JsonArray artists = item.getAsJsonArray("artists");
            if (artists != null) {
                result.append("[");
                int i = 0;
                for(JsonElement artistElement : artists) {
                    if (i > 0) {
                        print(", ");
                    }
                    JsonObject artist = artistElement.getAsJsonObject();

                    result.append(artist.get("name").getAsString());
                    i++;
                }
                result.append("]").append('\n');
            }
            result.append(spotifyAlbumUrl).append("\n\n");
            results.add(result.toString());
        }

        return results;
    }

    private void needAuth() {
        println("Please, provide access for application.");
    }

    private void handleFeatured() {
        String featuredUrl = getResourceUrl() + "/v1/browse/featured-playlists";
        if (isUserAuthenticated()) {
            HttpRequest request = buildHttpRequestForApi(featuredUrl);
            HttpClient httpClient = HttpClient.newHttpClient();

            try {
                HttpResponse<String> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofString());

                currentResults = buildResultsFromJson(response.body(), "playlists");
                currentPage = 1;
                printCurrentResultsPage();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            needAuth();
        }
    }

    private void printCurrentResultsPage() {
        if (currentResults != null) {
            if (currentPage <= currentPageCount()) {
                for (int i = (currentPage - 1) * itemsPerPage;
                     i < currentPage * itemsPerPage && i < currentResults.size();
                     i++) {
                    println(currentResults.get(i));
                }
                println("---PAGE " + currentPage + " OF " + currentPageCount() + "---");
            }
        }
    }

    private Integer currentPageCount() {
        if (currentResults == null || currentResults.isEmpty()) {
            return 0;
        }

        return currentResults.size() % itemsPerPage == 0 ?
            currentResults.size() / itemsPerPage :
            currentResults.size() / itemsPerPage + 1;
    }

    private HttpRequest buildHttpRequestForApi(String featuredUrl) {
        return HttpRequest.newBuilder()
                .header("Authorization", generateUserAuthHeader())
                .uri(URI.create(featuredUrl))
                .GET()
                .build();
    }

    private void handleAuth() {
        // userAuthenticated = true;
        println("use this link to request the access code:");
        println(getSpotifyUrl() +
                "/authorize?client_id=" +
                OAUTH_CLIENT_ID +
                "&redirect_uri=" +
                REDIRECT_URI +
                "&response_type=code");

        CodeReceiver receiver = new CodeReceiver();
        HttpServer server = startAuthServer(receiver);
        server.start();
        while(receiver.getCode() == null) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        setUserAuthenticated(true);
        server.stop(1);
        String tokenString = retrieveTokenForCode(receiver.getCode());
        if (tokenString != null) {
            OauthToken userAccessToken = parseAccessToken(tokenString);
            setUserToken(userAccessToken);
            println("---SUCCESS---");
        } else {
            println("ERROR Token is null");
        }


    }

    private @NotNull OauthToken parseAccessToken(String token) {
        /*
        Take this value and turn it into usable tokens:
        {
        "access_token":"BQDxyIu5Q9nsglsuF_R97FyMmRM_ejDw6yQe_ZOT99ZfMvQD-Mg7FMT4m8TIKGXxj-8f8n1yR6uWFvSvN8FGc79brbgK8gjvOOfAH9H1ohpAtOv2360ygBwLnwTMQ9YdMYy3QWf4ygVxvFHb6nZm76Gen0J7NK8qVmOLrw",
        "token_type":"Bearer",
        "expires_in":3600,
        "refresh_token":"AQDYLLiYDXNSbuMlrUbNTuq_jpytje9iGUQOvy4btMhe7_BV6wdN94EjPSktkQuxkX88h_hFKaSwNxSpSrqumbkCVjQ26GY-QSEGNOgRiaaT_x077kgx3q_KmaQOdzdUld0"
        }
         */

        JsonObject jo = JsonParser.parseString(token).getAsJsonObject();

        long expires = jo.get("expires_in").getAsInt() * 1000 + System.currentTimeMillis() - 100;
        String accessToken = jo.get("access_token").getAsString();
        String refreshToken = jo.get("refresh_token").getAsString();

        return new OauthToken(accessToken, expires, refreshToken);
    }

    private String retrieveTokenForCode(String code) {
        HttpClient httpClient = HttpClient.newHttpClient();
        String authHeader = generateClientAuthHeader();
        System.out.println("Header: Authorization = " + authHeader);
        HttpRequest request = HttpRequest.newBuilder()
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", authHeader)
                .uri(URI.create(getSpotifyUrl() + "/api/token"))
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=authorization_code" +
                        "&code=" + code +
                        "&redirect_uri=" + REDIRECT_URI))
                .build();

        HttpResponse<String> response = null;
        try {
            response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Token Request Results:");
            System.out.println(response.statusCode()); // 200 if everything is OK
            System.out.println(response.body());       // a long HTML text
        } catch (Exception e) {
            System.out.println("We cannot access the site. Please, try later.");
        } finally {
            if (response == null) {
                return null;
            }

            return response.body();
        }


    }

    private String generateClientAuthHeader() {
        String clientAuthHeader = new String(Base64.getEncoder().encode((OAUTH_CLIENT_ID + ":" + OAUTH_CLIENT_SECRET).getBytes()));
        return "Basic " + clientAuthHeader;
    }

    private String generateUserAuthHeader() {
        long currentTime = System.currentTimeMillis();
        long expires = userToken.getExpires();
        if (currentTime > expires) {
            updateExpiredUserToken();
        }
        return "Bearer " + userToken.getAccessToken();
    }

    private void updateExpiredUserToken() {
        println("ERROR:  Expired user token, update logic not built");
        // TODO
    }

    private HttpServer startAuthServer(CodeReceiver receiver) {
        try {
            HttpServer server = HttpServer.create();

            server.bind(new InetSocketAddress(8080), 0);
            server.createContext("/", getCodeHandler(receiver));
            return server;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private HttpHandler getCodeHandler(CodeReceiver receiver) {
        return exchange -> {
            String query = exchange.getRequestURI().getQuery();
            if (query == null || query.isBlank()) {
                sendNoCodeErrorResponse(exchange);
            } else {
                Map<String, String> params = paramsFromQuery(query);

                String code = params.get("code");
                if (code == null || code.isBlank()) {
                    sendNoCodeErrorResponse(exchange);
                } else {
                    sendCodeReceivedResponse(exchange);
                    receiver.setCode(code);
                }
            }
        };
    }

    private Map<String, String> paramsFromQuery(String query) {
        Map<String, String> params = Stream.of(query.split("&"))
                .filter(s -> !s.isEmpty())
                .map(kv -> kv.split("=", 2))
                .collect(Collectors.toMap(x -> x[0], x -> x[1]));
        return params;
    }

    private void sendCodeReceivedResponse(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(200, CODE_RECEIVED.length());
        exchange.getResponseBody().write(CODE_RECEIVED.getBytes());
        exchange.getResponseBody().close();
    }

    private void sendNoCodeErrorResponse(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(400, ERROR_NO_CODE.length());
        exchange.getResponseBody().write(ERROR_NO_CODE.getBytes());
        exchange.getResponseBody().close();
    }

    private void println(String s) {
        System.out.println(s);
    }

    private void print(String s) {
        System.out.print(s);
    }

    public boolean isUserAuthenticated() {
        return userAuthenticated;
    }

    public void setUserAuthenticated(boolean userAuthenticated) {
        this.userAuthenticated = userAuthenticated;
    }

    private class CodeReceiver {
        volatile private String code;

        protected synchronized void setCode(String code) {
            // only allow to be set once
            if (this.code == null) {
                System.out.println("Received code: " + code);
                this.code = code;
            }
        }

        protected synchronized String getCode() {
            return code;
        }
    }

    private class OauthToken {
        private String accessToken;
        private long expires;
        private String refreshToken;

        public OauthToken(String accessToken, long expires, String refreshToken) {
            this.accessToken = accessToken;
            this.expires = expires;
            this.refreshToken = refreshToken;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public long getExpires() {
            return expires;
        }

        public String getRefreshToken() {
            return refreshToken;
        }
    }
}


