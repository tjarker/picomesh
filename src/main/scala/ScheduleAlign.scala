import s4noc.Schedule

object ScheduleAlign extends App {


  val sched = new Schedule(3)
  val invSched = new InvertedSchedule(3)
  val len = sched.schedule.length
  println(s"Schedule length: $len")

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
        val respSource = invSched.timeToSource(node, respSlot)
        val respDest = invSched.timeToDest(node, respSlot).dest

        var nextRespForReq = -1
        for (slot <- 0 until len) {
          val respDest_ = invSched.timeToDest(node, (slot + offset) % len).dest
          if (respDest_ == reqSource) {
            nextRespForReq = slot
          }
        }

        if (reqSource != -1) {
          // internal delay is 3 cycles, so response time is T_arrival + 3 + (wait for next slot)
          val internalDelay = 1
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

    var max = ((-1,-1), -1)

    for (core <- 3 until 9) {
      print(s"Core $core: ")
      for (endPoint <- Seq(0, 1, 2)) {
        val sendSlot = sched.coreToTimeSlot(core, endPoint)
        val pathLength = sched.timeToDest(core, sendSlot).pathLength

        val respSlot = invSched.coreToTimeSlot(endPoint, core)
        val pathLengthResp = invSched.timeToDest(endPoint, respSlot).pathLength

        val processDelay = processDelayAtDestForSrc(core)(endPoint)
        
        //println(s"Core $core to EndPoint $endPoint: sendSlot=$sendSlot, pathLength=$pathLength, respSlot=$respSlot, pathLengthResp=$pathLengthResp, processDelay=$processDelay")
        val totalDelay = processDelay + pathLength + pathLengthResp
        print(s"to $endPoint @$sendSlot -> $totalDelay, ")
        accDelay(offsets.indexOf(offset)) += totalDelay
        val currMax = maxDelay(offsets.indexOf(offset))
        if (totalDelay > currMax._2) {
          maxDelay(offsets.indexOf(offset)) = (core, endPoint) -> totalDelay
        }
        if (totalDelay > max._2) {
          max = (core, endPoint) -> totalDelay
        }
      }
      println()
    }

    println(s"Max delay for offset $offset: ${max._2} for core/endPoint: ${max._1}")

    avgDelay(offsets.indexOf(offset)) = accDelay(offsets.indexOf(offset)).toDouble / (6 * 3).toDouble // 6 cores, 3 endpoints

  }


  println(s"accDelay: ${accDelay.mkString(", ")}")
  println(s"avgDelay: ${avgDelay.mkString(", ")}")
  println(s"maxDelay: ${maxDelay.mkString(", ")}")
  println("Best offset for acc delay: " + offsets(accDelay.indexOf(accDelay.min)) + ", min delay: " + accDelay.min)

  println("Best offset for avg delay: " + offsets(avgDelay.indexOf(avgDelay.min)) + ", min delay: " + avgDelay.min)

  println("Best offset for max delay: " + offsets(maxDelay.indexOf(maxDelay.minBy(_._2))) + ", min delay: " + maxDelay.minBy(_._2)._2 + ", for core/endPoint: " + maxDelay.minBy(_._2)._1)

    
}


object NewScheduleAlign extends App {



  val reqSched = new Schedule(3)
  val respSched = new InvertedSchedule(3)


  val latencies = for (offset <- Seq.range(6,7)) yield offset -> {
    println(s"Offset: $offset")
    for (core <- Seq.range(0, 9)) yield if (core == 1 || core == 2) Seq.fill(9)(-1) else {
      for (endPoint <- Seq.range(0, 9)) yield if (core == endPoint) -1 else {

        // when can the core send to the endpoint?
        val reqTxSlot = reqSched.coreToTimeSlot(core, endPoint)
        // how long does it take to reach the endpoint?
        val reqTxTime = reqSched.timeToDest(core, reqTxSlot).pathLength
        // when does the request arrive
        val reqRxSlot = (reqTxSlot + reqTxTime) % reqSched.len
        assert(reqSched.timeToSource(endPoint, reqRxSlot) == core)

        // which timeslot can the endpoint use to send a response back to the core?
        val respTxSlot = respSched.coreToTimeSlot(endPoint, core)
        // how long does it take to reach the core?
        val respTxTime = respSched.timeToDest(endPoint, respTxSlot).pathLength
        // when does the response arrive at the core?
        val respRxSlot = (respTxSlot + respTxTime) % respSched.len
        assert(respSched.timeToSource(core, respRxSlot) == endPoint)
        val offsetRespRxSlot = (reqSched.len + respRxSlot - offset) % reqSched.len
        val offsetRespTxSlot = (reqSched.len + respTxSlot - offset) % reqSched.len

        val processTime = 1 // time to process the request at the endpoint
        val readyToRespTime = reqRxSlot + processTime
        val waitTimeToRespTxSlot = if (offsetRespTxSlot < readyToRespTime) {
          reqSched.len - (readyToRespTime - offsetRespTxSlot)
        } else {
          offsetRespTxSlot - readyToRespTime
        } + processTime

        val totalTime = reqTxTime + waitTimeToRespTxSlot + respTxTime

        println(s"$core -> $endPoint: $reqTxTime + $waitTimeToRespTxSlot + $respTxTime = $totalTime | reqTxSlot=$reqTxSlot, reqRxSlot=$reqRxSlot, respTxSlot=$respTxSlot, respRxSlot=$respRxSlot, offsetRespTxSlot=$offsetRespTxSlot, offsetRespRxSlot=$offsetRespRxSlot")

        totalTime
      }
    }
  }

  // find the offset that minimizes the maximum latency
  val (bestLatencies, bestOffset) = latencies.zipWithIndex.minBy { case ((offset, latencyMatrix), index) =>
    latencyMatrix.flatten.max
  }

  println(s"Best offset: ${bestLatencies._1}, max latency: ${bestLatencies._2.flatten.max}")

  println("Latencies for best offset:")
  println(bestLatencies._2.map(_.mkString(", ")).mkString("\n"))

}