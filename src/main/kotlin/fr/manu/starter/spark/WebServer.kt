package fr.manu.starter.spark

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.instance
import org.slf4j.LoggerFactory
import spark.*
import java.io.IOException
import java.lang.reflect.Method
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import kotlin.reflect.KClass


class WebServer(port: Int = Service.SPARK_DEFAULT_PORT) {
    companion object {
        val log = LoggerFactory.getLogger(WebServer.javaClass)!!
    }

    val port: Int
    val sparkServer: Service
    var depsContainer: Kodein? = null

    init {
        if (!isInitializable(port)) {
            throw IllegalStateException("Port already in use " + port)
        }
        sparkServer = Service.ignite()
        sparkServer.port(port)
        sparkServer.init()
        sparkServer.awaitInitialization()
        this.port = sparkServer.port()
    }

    fun configure(): Service {
        return sparkServer
    }

    val baseUrl: String
        get() = "http://localhost:" + sparkServer.port()

    fun stop() {
        sparkServer.stop()
    }

    fun resource(path: String, controller: ResourceController, override: Boolean = false) {
        convertResourceToRoutes(path, controller, override)
    }

    fun <T : ResourceController> resource(path: String, controllerClass: KClass<T>, override: Boolean = false) {
        if (depsContainer == null) {
            throw UnsupportedOperationException("Cannot bind controller per classe name without setting any dependency container into server")
        }

        try {
            convertResourceToRoutes(path, controllerClass, override)
        } catch (e: IllegalStateException) {
            log.error("Skip Spark route resolution : {}", e.message)
        }
    }

    private fun convertResourceToRoutes(path: String, controller: ResourceController, override: Boolean) {
        for (classMethod in controller::class.java.declaredMethods) {
            val methodName = classMethod.name

            ResourceController::class.java.declaredMethods
                    .filter { interfaceMethod -> areEquals(classMethod, interfaceMethod, methodName) }
                    .forEach { addRoute(methodName.toLowerCase(), path, controller, override) }
        }
    }

    private fun <T : ResourceController> convertResourceToRoutes(path: String, controllerClass: KClass<T>, override: Boolean) {
        for (classMethod in controllerClass.java.declaredMethods) {
            val methodName = classMethod.name

            ResourceController::class.java.declaredMethods
                    .filter { interfaceMethod -> areEquals(classMethod, interfaceMethod, methodName) }
                    .forEach { addRoute(methodName.toLowerCase(), path, controllerClass, override) }
        }
    }

    private fun areEquals(classMethod: Method, interfaceMethod: Method, methodName: String?) =
            methodName == interfaceMethod.name && classMethod.returnType == interfaceMethod.returnType && Arrays.deepEquals(classMethod.parameterTypes, interfaceMethod.parameterTypes)

    private fun addRoute(httpMethod: String, path: String, controller: ResourceController, override: Boolean) {
        val method = ResourceController::class.java.getMethod(httpMethod, Request::class.java, Response::class.java)

        val sparkRoute = fun(request: Request, response: Response): Any {
            // Appel méthode correspondante
            method.invoke(controller, request, response)
            // Parce qu'il faut bien répondre qque chose
            return response.body()
        }

        if (override) {
            log.info("Replace Spark route .{}(\"{}\", {...}) from controller {}", httpMethod, path, controller.javaClass.canonicalName)
            // Obliger de passer par un hack (update non dispo)
            updateRoute(sparkServer, httpMethod, path, sparkRoute)

        } else {
            log.info("Create Spark route .{}(\"{}\", {...}) from controller {}", httpMethod, path, controller.javaClass.canonicalName)
            sparkServer.addRoute(httpMethod, createRoute(path, sparkRoute))
        }
    }

    private fun <T : ResourceController> addRoute(httpMethod: String, path: String, controllerClass: KClass<T>, override: Boolean) {
        val method = ResourceController::class.java.getMethod(httpMethod, Request::class.java, Response::class.java)
        if (!isBindingPresent(depsContainer!!, controllerClass)) {
            throw IllegalStateException("No provider found for bind<ResourceController>(\"class $controllerClass\"")
        }

        val sparkRoute = fun(request: Request, response: Response): Any {
            // On instancie le controlleur le plus tardivement possible (pour tenir compte des éventuelles surcharge de conf)
            val controller = depsContainer!!.instance<ResourceController>(controllerClass)
            // Appel méthode correspondante
            method.invoke(controller, request, response)
            // Parce qu'il faut bien répondre qque chose
            return response.body()
        }

        if (override) {
            log.info("Replace Spark route .{}(\"{}\", {...}) from controller {}", httpMethod, path, controllerClass.java.canonicalName)
            // Obliger de passer par un hack (update non dispo)
            updateRoute(sparkServer, httpMethod, path, sparkRoute)

        } else {
            log.info("Create Spark route .{}(\"{}\", {...}) from controller {}", httpMethod, path, controllerClass.java.canonicalName)
            sparkServer.addRoute(httpMethod, createRoute(path, sparkRoute))
        }
    }

    private fun <T : ResourceController> isBindingPresent(depsContainer: Kodein, controllerClass: KClass<T>): Boolean {
        val binding = depsContainer.container.bindings.keys
                .filter { key: Kodein.Key -> key.bind.type.typeName == ResourceController::class.java.canonicalName }
                .findLast { key: Kodein.Key -> key.bind.tag == controllerClass }

        return binding != null
    }

    fun withContainer(depsContainer: Kodein): WebServer {
        this.depsContainer = depsContainer
        return this
    }


}

fun isInitializable(port: Int): Boolean {
    Socket().use {
        try {
            it.connect(InetSocketAddress("127.0.0.1", port), 500)
            return false
        } catch (e: IOException) {
            return true // Either timeout or unreachable or failed DNS lookup.
        }
    }
}

fun randomPort(): Int {
    ServerSocket(0).use { s -> return s.localPort }
}
