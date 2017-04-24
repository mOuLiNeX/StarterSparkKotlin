package fr.manu.starter.spark

import io.restassured.RestAssured
import org.hamcrest.Matchers.`is`
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

class MultipleServersTest {
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
    fun should_declare_another_server_with_different_port() {
        val server2 = WebServer(randomPort())
        server2.configure().get("/hello", { request, response -> "Hello World 2 !" })

        RestAssured.`when`().get("$baseUrl/hello").then().assertThat()
                .statusCode(200).body(`is`("Hello World !"))
        RestAssured.`when`().get(server2.baseUrl + "/hello").then().assertThat()
                .statusCode(200).body(`is`("Hello World 2 !"))

        server2.stop()
    }

    @Test(expected = IllegalStateException::class)
    fun should_not_declare_another_server_with_same_port() {
        val server2 = WebServer(server.port)
    }

}