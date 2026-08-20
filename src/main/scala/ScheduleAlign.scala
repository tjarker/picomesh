import s4noc.Schedule

object ScheduleAlign extends App {


  val sched = Schedule(3)
  val len = sched.schedule.length

  val offsets = Seq.range(0, len)
  val accDelay = Array.fill(offsets.length)(0)
  val avgDelay = Array.fill(offsets.length)(0.0)
  val maxDelay = Array.fill(offsets.length)((0,0) -> 0)

  for (offset <- offsets) {
    
    println(s"Offset: $offset")

    val reqSlots = Seq.range(0, len)
    val respSlots = reqSlots.map(s => (s + offset) % len)

    //println(s"reqSlots: ${reqSlots.mkString(", ")}")
    //println(s"respSlots: ${respSlots.mkString(", ")}")



    val processDelayAtDestForSrc = Array.fill(9, 9)(-1)

    for (node <- 0 until 9) {
      //println(s"Node $node:")
      //println(s"${reqSlots.map(s => sched.timeToSource(node, s)).mkString(", ")}")
      //println(s"${respSlots.map(s => sched.timeToSource(node, s)).mkString(", ")}")
      
      // print header for table
      //print("Slot | reqSource | reqDest | respSource | respDest | nextRespForReq | delay\n")

      for (reqSlot <- 0 until len) {
        val respSlot = (reqSlot + offset) % len
        
        val reqSource = sched.timeToSource(node, reqSlot)
        val reqDest = sched.timeToDest(node, reqSlot).dest
        val respSource = sched.timeToSource(node, respSlot)
        val respDest = sched.timeToDest(node, respSlot).dest

        var nextRespForReq = -1
        for (slot <- 0 until len) {
          val respDest_ = sched.timeToDest(node, (slot + offset) % len).dest
          if (respDest_ == reqSource) {
            nextRespForReq = slot
          }
        }

        if (reqSource != -1) {
          // internal delay is 3 cycles, so response time is T_arrival + 3 + (wait for next slot)
          val internalDelay = 3
          val waitingForTdmSlot = reqSlot + internalDelay
          val delay = if (nextRespForReq < waitingForTdmSlot) {
            len - (waitingForTdmSlot - nextRespForReq)
          } else {
            nextRespForReq - waitingForTdmSlot
          } + internalDelay

          processDelayAtDestForSrc(reqSource)(node) = delay

          //println(f"$reqSlot%4d | $reqSource%9d | $reqDest%7d | $respSource%10d | $respDest%8d | $nextRespForReq%12d | $delay%5d")
        } else {
          //println(f"$reqSlot%4d | $reqSource%9d | $reqDest%7d | $respSource%10d | $respDest%8d |")
        }


        

      }


      
    }
    //println(processDelayAtDestForSrc.map(_.mkString(", ")).mkString("\n"))


    for (core <- 3 until 9) {
      print(s"Core $core: ")
      for (endPoint <- Seq(0, 1, 2)) {
        val sendSlot = sched.coreToTimeSlot(core, endPoint)
        val pathLength = sched.timeToDest(core, sendSlot).pathLength

        val respSlot = sched.coreToTimeSlot(endPoint, core)
        val pathLengthResp = sched.timeToDest(endPoint, respSlot).pathLength

        val processDelay = processDelayAtDestForSrc(core)(endPoint)
        
        //println(s"Core $core to EndPoint $endPoint: sendSlot=$sendSlot, pathLength=$pathLength, respSlot=$respSlot, pathLengthResp=$pathLengthResp, processDelay=$processDelay")
        val totalDelay = processDelay + pathLength + pathLengthResp
        print(s"to $endPoint -> $totalDelay, ")
        accDelay(offsets.indexOf(offset)) += totalDelay
        val currMax = maxDelay(offsets.indexOf(offset))
        if (totalDelay > currMax._2) {
          maxDelay(offsets.indexOf(offset)) = (core, endPoint) -> totalDelay
        }
      }
      println()
    }

    avgDelay(offsets.indexOf(offset)) = accDelay(offsets.indexOf(offset)).toDouble / (6 * 3).toDouble // 6 cores, 3 endpoints

  }


  println(s"accDelay: ${accDelay.mkString(", ")}")
  println(s"avgDelay: ${avgDelay.mkString(", ")}")
  println(s"maxDelay: ${maxDelay.mkString(", ")}")
  println("Best offset for acc delay: " + offsets(accDelay.indexOf(accDelay.min)) + ", min delay: " + accDelay.min)

  println("Best offset for avg delay: " + offsets(avgDelay.indexOf(avgDelay.min)) + ", min delay: " + avgDelay.min)

  println("Best offset for max delay: " + offsets(maxDelay.indexOf(maxDelay.minBy(_._2))) + ", min delay: " + maxDelay.minBy(_._2)._2 + ", for core/endPoint: " + maxDelay.minBy(_._2)._1)

    
}
