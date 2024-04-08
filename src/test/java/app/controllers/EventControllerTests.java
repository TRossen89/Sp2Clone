package app.controllers;

import app.entities.Event;
import app.entities.Status;
import io.restassured.internal.path.json.JSONAssertion;

import static io.restassured.RestAssured.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import app.config.ApplicationConfig;
import app.config.HibernateConfig;
import app.dtos.TokenDTO;
import app.utils.Routes;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.response.Response;
import jakarta.persistence.EntityManagerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import app.config.ApplicationConfig;
import app.config.HibernateConfig;
import app.dtos.EventDTO;
import app.dtos.TokenDTO;
import app.entities.Event;
import app.entities.User;
import app.utils.Routes;
import app.utils.TestUtils;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.response.Response;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;


import java.time.LocalDate;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.matchesPattern;

@Nested
class EventControllerTests {
    private static ApplicationConfig appConfig;
    private static final String BASE_URL = "http://localhost:7777/api";
    private static EntityManagerFactory emfTest;
    private static ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    public static void beforeAll() {
        RestAssured.baseURI = BASE_URL;
        objectMapper.findAndRegisterModules();

        // Setup test database using docker testcontainers
        emfTest = HibernateConfig.getEntityManagerFactory(true);

        Routes routes = Routes.getInstance(emfTest);
        // Start server
        appConfig = ApplicationConfig.getInstance(emfTest);
        appConfig
                .initiateServer()
                .setExceptionHandling()
                .checkSecurityRoles()
                .setRoute(routes.eventResources())
                .setRoute(routes.testResources())
                .setRoute(routes.securityResources())
                .setRoute(routes.securedRoutes())
                .setRoute(routes.unsecuredRoutes())
                .startServer(7777)
        ;
    }

    @BeforeEach
    public void setUpEach() {
        // Setup test database for each test
        TestUtils.createUsersAndRoles(emfTest);
        TestUtils.createEvents(emfTest);
        TestUtils.addEventToUser(emfTest);
    }


    @AfterAll
    static void afterAll() {
        emfTest.close();
        appConfig.stopServer();
    }


    @Test
    public void getEventById() throws JsonMappingException, JsonProcessingException {
        Event first = TestUtils.getEvents(emfTest).values().stream().findFirst().get();
        Response response = given().when()
                .get("/events/" + first.getId()).peek();

        EventDTO actualEvent = objectMapper.readValue(response.body().asString(), EventDTO.class);

        assertEquals(first.getTitle(), actualEvent.getTitle());
    }


    @Test
    void registerUserToEvent() {
        String requestLoginBody = "{\"email\": \"user\",\"password\": \"user\"}";
        TokenDTO token = given()
                .contentType("application/json")
                .body(requestLoginBody)
                .when()
                .post("/auth/login")
                .then()
                .extract()
                .as(TokenDTO.class);

        Header header = new Header("Authorization", "Bearer " + token.getToken());

        String requestBody = "{\"email\": \"user\",\"id\": \"1\"}";
        RestAssured.given()
                .contentType("application/json")
                .header(header)
                .body(requestBody)
                .when()
                .put("/event/registerUser")
                .then()
                .statusCode(200);

        try (EntityManager em = emfTest.createEntityManager()) {
            Event event = em.createQuery("FROM Event e WHERE e.id = 1", Event.class).getSingleResult();
            assertEquals(1, event.getUsers().size());
        }
    }

    @Test
    void cancelRegistration() {
        String requestLoginBody = "{\"email\": \"user\",\"password\": \"user\"}";
        TokenDTO token = RestAssured
                .given()
                .contentType("application/json")
                .body(requestLoginBody)
                .when()
                .post("/auth/login")
                .then()
                .extract()
                .as(TokenDTO.class);

        Header header = new Header("Authorization", "Bearer " + token.getToken());

        String requestBody = "{\"password\": \"user\",\"title\": \"title1\"}";

        RestAssured.given()
                .contentType("application/json")
                .header(header)
                .body(requestBody)
                .when()
                .put("/event/cancelRegistration")
                .then()
                .statusCode(200);

    }

    @Test
    void createEvent() {
        String requestLoginBody = "{\"email\": \"user\",\"password\": \"user\"}";
        TokenDTO token = RestAssured
                .given()
                .contentType("application/json")
                .body(requestLoginBody)
                .when()
                .post("/auth/login")
                .then()
                .extract()
                .as(TokenDTO.class);

        Header header = new Header("Authorization", "Bearer " + token.getToken());
        String requestBody = "{\"title\": \"title\", \"startTime\": \"startTime\", \"description\": \"description\", \"LocalDate\": \"2024-04-22\", \"durationInHour\": \"100\", \"maxNumberOfStudents\": \"10\", \"locationOfEvent\": \"locationOfEvent\", \"instructor\": \"instructor\", \"price\": \"100\", \"category\": \"category\", \"image\": \"image\", \"Status\": \"Status.UPCOMING\"}";

        RestAssured.given()
                .contentType("application/json")
                .header(header)
                .body(requestBody)
                .when()
                .put("/event")
                .then()
                .statusCode(200);
    }

    @Test
    void getUpcomingEvents() {
        String dateAsString =
                given()
                        .when()
                        .get("event/upcoming")
                        .then()
                        .statusCode(200)
                        .body("[0].dateOfEvent", notNullValue())
                        //.body("[0].dateOfEvent", matchesPattern("yyyy-MM-dd"))
                        .extract()
                        .path("[0].dateOfEvent");

        LocalDate date = LocalDate.parse(dateAsString);

        assertThat(date, greaterThan(LocalDate.now()));

    }
}



