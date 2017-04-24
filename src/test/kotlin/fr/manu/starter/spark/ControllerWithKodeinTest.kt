package fr.manu.starter.spark

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.github.salomonbrys.kodein.singleton
import io.restassured.RestAssured.`when`
import org.hamcrest.Matchers.`is`
import org.junit.After
import org.junit.Before
import org.junit.Test
import spark.Request
import spark.Response

class ControllerWithKodeinTest {
    lateinit var server: WebServer

    var resourcePath = "/resources"

    @Before
    fun setUp() {
        server = WebServer(randomPort())
    }

    @After
    fun tearDown() {
        server.stop()
    }

    val standardBindindRules = Kodein {
        // Par convention (et commodité) la résolution se fera au runtime par le nom de la classe (utilisé en tant que tag)
        bind<ResourceController>(ExampleController::class) with singleton { ExampleController() }
    }

    @Test
    fun should_call_resource_route() {
        server.withContainer(standardBindindRules).resource(resourcePath, ExampleController::class)

        val baseUrl = server.baseUrl
        `when`().get("$baseUrl$resourcePath").then().assertThat().statusCode(200).body(`is`("Coucou le monde !"))
        `when`().post("$baseUrl$resourcePath").then().assertThat().statusCode(201)
    }

    @Test
    fun wrong_kodein_resolution_send_404() {
        val emptyContainer = Kodein {
            // Pas de binding pour le controlleur
        }

        server.withContainer(emptyContainer).resource(resourcePath, ExampleController::class)
        `when`().get(server.baseUrl + resourcePath).then().assertThat().statusCode(404)
    }

    @Test
    fun can_override_controller_target() {
        server.withContainer(standardBindindRules).resource(resourcePath, ExampleController::class)
        server.configure().get("/pasbouger", { req, resp -> "Pas connerie !" })

        val baseUrl = server.baseUrl
        `when`().get("$baseUrl$resourcePath").then().assertThat()
                .statusCode(200).body(`is`("Coucou le monde !"))

        val redefineContainerWithMock = Kodein {
            extend(standardBindindRules)
            bind<ResourceController>(ExampleController::class, overrides = true) with singleton { MockExampleController() }
        }
        // On doit raffaîchir la déclaration de la resource (overrides = true) pour notifier le changement
        server.withContainer(redefineContainerWithMock).resource(resourcePath, ExampleController::class, override = true)

        // Test du mock (sur même route que précédemment)
        `when`().get("$baseUrl$resourcePath").then().assertThat().statusCode(200).body(`is`("Je suis le mock qui me moque !"))

        // Test de la partie inchangée
        `when`().post("$baseUrl$resourcePath").then().assertThat().statusCode(201)
        `when`().get("$baseUrl/pasbouger").then().assertThat().statusCode(200).body(`is`("Pas connerie !"))
    }

    @Test
    fun can_override_binding_target_inside_controller() {
        // DEBUG Config standard
        val initialConf = Kodein {
            bind<InjectableService>() with singleton { InjectableService() }
            bind<ResourceController>(DIController::class) with singleton { DIController(instance()) }
        }
        server.withContainer(initialConf).resource(resourcePath, DIController::class)
        // FIN Config standard

        // On ne re-définit que le service (pas le controlleur !)
        val redefineContainerWithMock = Kodein {
            extend(initialConf)
            bind<InjectableService>(overrides = true) with singleton {
                object : InjectableService() {
                    override val whoami = "Je suis un affreux mock pas beau !"
                }
            }
        }

        // On doit raffaîchir les routes pour utiliser la redéfinition
        server.withContainer(redefineContainerWithMock).resource(resourcePath, DIController::class, override = true)

        // Test du mock (sur même route que précédemment)
        `when`().get(server.baseUrl + resourcePath).then().assertThat().statusCode(200).body(`is`("Je suis un affreux mock pas beau !"))
    }
}

class MockExampleController : ExampleController() {
    override fun get(request: Request, response: Response) {
        response.body("Je suis le mock qui me moque !")
    }
}

class DIController(val srv: InjectableService) : ResourceController() {
    override fun get(request: Request, response: Response) {
        response.body(srv.whoami)
    }
}

open class InjectableService {
    open val whoami = "Standard"
}
