package spark

// Q&D mais apparemment Spark 2.5.6 proposera une méthode publique (ici il fallait pouvoir invoquer avec visibilité package)
fun createRoute(path: String, route: (Request, Response) -> Any): RouteImpl = RouteImpl.create(path, route)