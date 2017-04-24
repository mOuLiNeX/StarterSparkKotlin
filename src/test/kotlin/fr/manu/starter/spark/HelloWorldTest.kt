package fr.manu.starter.spark

import io.restassured.RestAssured
import org.hamcrest.Matchers.`is`
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

class HelloWorldTest {
    var baseUrl = server.baseUrl
    companion object {
        lateinit var server: WebServer
        @BeforeClass @JvmStatic fun init() {
            server = WebServer(randomPort())
            server.configure().get("/hello", { request, response -> "Hello World !" })
        }

        @AfterClass @JvmStatic fun clean() {
            server.stop()
        }
    }


    @Test
    fun should_declare_one_route() {
        RestAssured.`when`().get("$baseUrl/hello").then().assertThat()
                .statusCode(200).body(`is`("Hello World !"))
    }

    @Test
    fun missing_route_should_send_404() {
        RestAssured.`when`().get("$baseUrl/toto").then().assertThat()
                .statusCode(404)
    }


    @Test
    fun missing_verb_should_send_404() {
        RestAssured.`when`().post("$baseUrl/hello").then().assertThat()
                .statusCode(404)
    }
}