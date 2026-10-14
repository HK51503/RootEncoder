/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.rtmp.utils

class AcknowledgementTracker(private val maxSamples: Int = 2000) {

  private class Sample(val sequence: Long, val timeMicro: Long)

  private val lock = Any()
  private val samples = ArrayDeque<Sample>()
  private var bytesSent = 0L
  private var sequenceLow = 0L
  private var sequenceHigh = 0L
  private var rttMicro = 0L
  private var acknowledgements = 0L
  private var lastAcknowledgementMicro = 0L

  fun onBytesSent(totalBytes: Long, timeMicro: Long) {
    synchronized(lock) {
      if (totalBytes <= bytesSent) return
      bytesSent = totalBytes
      if (samples.size >= maxSamples) samples.removeFirst()
      samples.addLast(Sample(totalBytes, timeMicro))
    }
  }

  fun onAcknowledgement(sequenceNumber: Int, timeMicro: Long) {
    synchronized(lock) {
      val sequence = sequenceNumber.toLong() and 0xFFFFFFFFL
      if (sequence < sequenceLow) {
        sequenceHigh += if (sequenceLow <= Int.MAX_VALUE) Int.MAX_VALUE.toLong() + 1 else 0x100000000L
      }
      sequenceLow = sequence
      acknowledgements++
      lastAcknowledgementMicro = timeMicro
      val acknowledged = sequenceHigh + sequenceLow
      var matched: Sample? = null
      while (samples.isNotEmpty() && samples.first().sequence <= acknowledged) {
        matched = samples.removeFirst()
      }
      matched?.let { rttMicro = timeMicro - it.timeMicro }
    }
  }

  val rtt: Long
    get() = synchronized(lock) { rttMicro }

  val bytesInFlight: Long
    get() = synchronized(lock) {
      if (acknowledgements == 0L) 0L else (bytesSent - sequenceHigh - sequenceLow).coerceAtLeast(0L)
    }

  val acknowledgementsReceived: Long
    get() = synchronized(lock) { acknowledgements }

  fun timeSinceLastAcknowledgement(nowMicro: Long): Long? = synchronized(lock) {
    if (acknowledgements == 0L) null else nowMicro - lastAcknowledgementMicro
  }

  fun reset() {
    synchronized(lock) {
      samples.clear()
      bytesSent = 0
      sequenceLow = 0
      sequenceHigh = 0
      rttMicro = 0
      acknowledgements = 0
      lastAcknowledgementMicro = 0
    }
  }
}
