package fr.manu.starter.spark

import spark.Request
import spark.Response

abstract class ResourceController {
    open fun get(request: Request, response: Response) {}

    open fun post(request: Request, response: Response) {}

    open fun put(request: Request, response: Response) {}

    open fun delete(request: Request, response: Response) {}

    open fun patch(request: Request, response: Response) {}

    open fun head(request: Request, response: Response) {}

}
