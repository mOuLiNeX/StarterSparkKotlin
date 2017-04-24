package fr.manu.spark

import fr.manu.starter.spark.WebServer
import fr.manu.starter.spark.randomPort
import io.restassured.RestAssured
import org.hamcrest.Matchers.*
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import spark.Spark
import java.io.IOException

class ErrorHandlingTest {
    var baseUrl = server.baseUrl

    companion object {
        lateinit var server: WebServer
        @BeforeClass @JvmStatic fun init() {
            server = WebServer(randomPort());
            server.configure().get("/404", { request, response -> server.configure().halt(404) })
            server.configure().get("/409", { request, response -> server.configure().halt(409) })
            server.configure().get("/error", { request, response -> throw UnsupportedOperationException() })
            server.configure().exception(UnsupportedOperationException::class.java, { exception, request, response ->
                response.body("Not yet implemented");
                response.status(503)
            })

        }

        @AfterClass @JvmStatic fun clean() {
            server.stop()
        }
    }


    @Test
    fun intercept_not_found() {
        RestAssured.`when`().get("$baseUrl/404").then().assertThat()
                .statusCode(404)
    }

    @Test
    fun intercept_custom_error() {
        RestAssured.`when`().get("$baseUrl/409").then().assertThat()
                .statusCode(409)
    }

    @Test
    fun intercept_from_exception() {
        RestAssured.`when`().get("$baseUrl/error").then().assertThat()
                .statusCode(503)
                .body(`is`("Not yet implemented"))
    }

}