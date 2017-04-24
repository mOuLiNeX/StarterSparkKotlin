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

    fun resource(path: String, controller: ResourceController) {
        for (classMethod in controller::class.java.declaredMethods) {
            val methodName = classMethod.name

            ResourceController::class.java.declaredMethods
                    .filter { interfaceMethod -> areEquals(classMethod, interfaceMethod, methodName) }
                    .forEach({ addRoute(methodName.toLowerCase(), path, controller) })
        }
    }

    fun <T : ResourceController> resource(path: String, controllerClass: KClass<T>) {
        if (depsContainer == null) {
            throw UnsupportedOperationException("Cannot bind controller per classe name without setting any dependency container into server")
        }

        try {
            val controller = depsContainer!!.instance<ResourceController>(controllerClass)

            resource(path, controller)
        } catch (e: Kodein.NotFoundException) {
            log.error("Skip Spark route resolution : {}", e.message)
        }
    }


    private fun areEquals(classMethod: Method, interfaceMethod: Method, methodName: String?) =
            methodName == interfaceMethod.name && classMethod.returnType == interfaceMethod.returnType && Arrays.deepEquals(classMethod.parameterTypes, interfaceMethod.parameterTypes)

    private fun addRoute(httpMethod: String, path: String, controller: ResourceController) {
        val method = ResourceController::class.java.getMethod(httpMethod, Request::class.java, Response::class.java)

        val sparkRoute = fun(request: Request, response: Response): Any {
            // Appel méthode correspondante
            method.invoke(controller, request, response)

            // Parce qu'il faut bien répondre qque chose
            return response.body()
        }

        log.info("Create Spark route .{}(\"{}\", {...}) from controller {}", httpMethod, path, controller.javaClass.canonicalName)
        sparkServer.addRoute(httpMethod, createRoute(path, sparkRoute))
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
