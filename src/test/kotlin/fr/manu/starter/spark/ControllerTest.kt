package fr.manu.starter.spark

import io.restassured.RestAssured
import org.hamcrest.Matchers.`is`
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import spark.Request
import spark.Response

class ControllerTest {
    var baseUrl = server.baseUrl

    companion object {
        var resourcePath = "/resources"
        lateinit var server: WebServer
        @BeforeClass @JvmStatic fun init() {
            server = WebServer(randomPort())
            server.resource(resourcePath, ExampleController())
        }

        @AfterClass @JvmStatic fun clean() {
            server.stop()
        }
    }

    @Test
    fun should_call_resource_route() {
        RestAssured.`when`().get("$baseUrl${resourcePath}").then().assertThat()
                .statusCode(200).body(`is`("Coucou le monde !"))
        RestAssured.`when`().post("$baseUrl${resourcePath}").then().assertThat()
                .statusCode(201)
    }

    @Test
    fun missing_route_should_send_404() {
        RestAssured.`when`().get("$baseUrl/toto").then().assertThat()
                .statusCode(404)
    }


    @Test
    fun missing_verb_should_send_404() {
        RestAssured.`when`().patch("$baseUrl${resourcePath}").then().assertThat()
                .statusCode(404)
    }

}

open class ExampleController : ResourceController() {
    override fun get(request: Request, response: Response) {
        response.body("Coucou le monde !")
    }

    override fun post(request: Request, response: Response) {
        response.body("")
        response.status(201)
    }
}
