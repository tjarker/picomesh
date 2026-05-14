package routing

import liftoff.simulation.Time.TimeUnit.s




case class Route(from: Coord, to: Coord, path: Seq[Direction]) {

  def wrappingAdd(coord: Coord, dir: Direction): Coord = {
    val dx = dir match {
      case West => -1
      case East => 1
      case _    => 0
    }
    val dy = dir match {
      case North => -1
      case South => 1
      case _     => 0
    }
    val newX = (coord.x + dx + Routes.nx) % Routes.nx
    val newY = (coord.y + dy + Routes.ny) % Routes.ny
    Coord(newX, newY)
  }
  
  def routers: Seq[Coord] = {
    path.scanLeft(from) { case (coord, dir) => wrappingAdd(coord, dir) }
  }

  def inputDirAt(coord: Coord): Option[Direction] = {
    if (coord == from) {
      None
    } else {
      val idx = routers.indexOf(coord)
      if (idx > 0) Some(path(idx - 1).opposite) else None
    }
  }
}

object Routes extends App {

  val nx = 4
  val ny = 4

  def findPathMesh(from: Coord, to: Coord): Seq[Direction] = {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val eastbound = dx >= 0
    val southbound = dy >= 0
    Seq.fill(math.abs(dx))(if (eastbound) East else West) ++ Seq.fill(math.abs(dy))(if (southbound) South else North)
  }

  def findPathBiTorus(from: Coord, to: Coord): Seq[Direction] = {
    val dx = (to.x - from.x + nx) % nx
    val dy = (to.y - from.y + ny) % ny
    val eastbound = dx <= nx / 2
    val southbound = dy <= ny / 2
    Seq.fill(if (eastbound) dx else nx - dx)(if (eastbound) East else West) ++ Seq.fill(if (southbound) dy else ny - dy)(if (southbound) South else North)
  }

  def findPathTorus(from: Coord, to: Coord): Seq[Direction] = {
    val dx = (to.x - from.x + nx) % nx
    val dy = (to.y - from.y + ny) % ny
    // always east and south
    Seq.fill(dx)(East) ++ Seq.fill(dy)(South)
  }

  // mesh
  // val allToAllRoutes: Seq[Route] = {
  //   for {
  //     fromX <- 0 until nx
  //     fromY <- 0 until ny
  //     toX <- 0 until nx
  //     toY <- 0 until ny
  //   } yield {
  //     val from = Coord(fromX, fromY)
  //     val to = Coord(toX, toY)
  //     val dx = (to.x - from.x + nx) % nx
  //     val dy = (to.y - from.y + ny) % ny
  //     val eastbound = dx <= nx / 2
  //     val southbound = dy <= ny / 2
  //     val path = findPath(from, to)
  //     Route(from, to, path)
  //   }
  // }

  // bi torus
  // val allToAllRoutes: Seq[Route] = {
  //   for {
  //     fromX <- 0 until nx
  //     fromY <- 0 until ny
  //     toX <- 0 until nx
  //     toY <- 0 until ny
  //   } yield {
  //     val from = Coord(fromX, fromY)
  //     val to = Coord(toX, toY)
  //     val path = findPathBiTorus(from, to)
  //     Route(from, to, path)
  //   }
  // }

  val routingFuns = Seq(
    ((from: Coord, to: Coord) => findPathMesh(from, to)) -> "mesh",
    ((from: Coord, to: Coord) => findPathBiTorus(from, to)) -> "bi-torus",
    ((from: Coord, to: Coord) => findPathTorus(from, to)) -> "torus"
  )

  for ((routingFun, name) <- routingFuns) {
    println(s"All-to-all routes for ${name}:")

    // torus
    val allToAllRoutes: Seq[Route] = {
      for {
        fromX <- 0 until nx
        fromY <- 0 until ny
        toX <- 0 until nx
        toY <- 0 until ny
      } yield {
        val from = Coord(fromX, fromY)
        val to = Coord(toX, toY)
        val path = routingFun(from, to)
        Route(from, to, path)
      }
    }

    println(s"Total number of routes: ${allToAllRoutes.length}")

    val longestRoute = allToAllRoutes.maxBy(_.path.length)
    println(s"Longest Route: from ${longestRoute.from} to ${longestRoute.to} with length ${longestRoute.path.length}: ${longestRoute.path.mkString(" -> ")}")


    val inputCounts = for (y <- 0 until ny; x <- 0 until nx) yield {
      val coord = Coord(x, y)
      val route = allToAllRoutes.map(_.inputDirAt(coord)).flatten
      Seq(
        (coord, Local, route.count(_ == Local)),
        (coord, North, route.count(_ == North)),
        (coord, South, route.count(_ == South)),
        (coord, East, route.count(_ == East)),
        (coord, West, route.count(_ == West))
      )
    }

    // rank by number of inputs
    val rankedInputCounts = inputCounts.flatten.sortBy(- _._3)

    println(s"Highest number of flows per input port: ${rankedInputCounts.head._3}")
    println(s"lowest number of flows per input port: ${rankedInputCounts.last._3}")
    val totalBuffering = inputCounts.flatten.map(_._3).sum
    println(s"Total buffering requirement: ${totalBuffering}\n\n")
  }


}
