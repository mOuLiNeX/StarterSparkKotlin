package fr.manu.spark

import com.github.salomonbrys.kodein.*
import com.github.salomonbrys.kodein.conf.ConfigurableKodein
import fr.manu.starter.spark.ResourceController
import fr.manu.starter.spark.WebServer
import fr.manu.starter.spark.randomPort
import io.restassured.RestAssured
import org.hamcrest.Matchers.*
import org.junit.*
import spark.Request
import spark.Response

class ControllerWithKodeinTest {
    lateinit var server: WebServer

    var resourcePath = "/resources"

    @Before
    fun setUp() {
        server = WebServer(randomPort());
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun should_call_resource_route() {
        // Par convention (et commodité) la résolution se fera pas le nom de la classe (utilisé en tant que tag)
        val depsContainer = Kodein {
            bind<ResourceController>(ExampleController::class) with singleton { ExampleController() }
        }

        server.withContainer(depsContainer).resource(resourcePath, ExampleController::class)
        var baseUrl = server.baseUrl

        RestAssured.`when`().get("$baseUrl$resourcePath").then().assertThat()
                .statusCode(200).body(`is`("Coucou le monde !"))
        RestAssured.`when`().post("$baseUrl$resourcePath").then().assertThat()
                .statusCode(201)
    }

    @Test
    fun wrong_kodein_resolution_send_404() {
        val depsContainer = Kodein {

        }

        server.withContainer(depsContainer).resource(resourcePath, ExampleController::class)
        var baseUrl = server.baseUrl
        RestAssured.`when`().get("$baseUrl$resourcePath").then().assertThat()
                .statusCode(404)
    }

    @Test
    fun can_override_controller_target() {
        val depsContainer = Kodein {
            bind<ResourceController>(ExampleController::class) with singleton { ExampleController() }
        }

        server.withContainer(depsContainer)

        server.resource(resourcePath, ExampleController::class)
        var baseUrl = server.baseUrl

        RestAssured.`when`().get("$baseUrl$resourcePath").then().assertThat()
                .statusCode(200).body(`is`("Coucou le monde !"))

        val depsContainerWithMock = Kodein {
            bind<ResourceController>(ExampleController::class) with singleton { MockExampleController() }
        }
        server.withContainer(depsContainerWithMock).resource(resourcePath, ExampleController::class)

        RestAssured.`when`().get("$baseUrl$resourcePath").then().assertThat()
                .statusCode(200).body(`is`("Je suis le mock qui me moque !"))
    }
}

class MockExampleController : ExampleController() {
    override fun get(request: Request, response: Response) {
        response.body("Je suis le mock qui me moque !")
    }
}
