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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AcknowledgementTrackerTest {

  @Test
  fun `no acknowledgement reports nothing in flight`() {
    val tracker = AcknowledgementTracker()
    tracker.onBytesSent(50_000, 1_000)
    assertEquals(0L, tracker.bytesInFlight)
    assertEquals(0L, tracker.rtt)
    assertNull(tracker.timeSinceLastAcknowledgement(2_000))
  }

  @Test
  fun `rtt is measured from the newest acknowledged write`() {
    val tracker = AcknowledgementTracker()
    tracker.onBytesSent(100_000, 1_000)
    tracker.onBytesSent(200_000, 5_000)
    tracker.onBytesSent(300_000, 9_000)
    tracker.onAcknowledgement(250_000, 40_000)
    assertEquals(35_000L, tracker.rtt)
    assertEquals(50_000L, tracker.bytesInFlight)
    assertEquals(1L, tracker.acknowledgementsReceived)
    assertEquals(5_000L, tracker.timeSinceLastAcknowledgement(45_000))
  }

  @Test
  fun `acknowledgement without a matching write keeps the previous rtt`() {
    val tracker = AcknowledgementTracker()
    tracker.onBytesSent(100_000, 1_000)
    tracker.onAcknowledgement(100_000, 21_000)
    tracker.onBytesSent(200_000, 30_000)
    tracker.onAcknowledgement(150_000, 60_000)
    assertEquals(20_000L, tracker.rtt)
    assertEquals(50_000L, tracker.bytesInFlight)
  }

  @Test
  fun `sequence number wrapping at 32 bits keeps counting`() {
    val tracker = AcknowledgementTracker()
    val beforeWrap = 0xFFFF0000L
    tracker.onBytesSent(beforeWrap, 1_000)
    tracker.onAcknowledgement(beforeWrap.toInt(), 2_000)
    tracker.onBytesSent(0x100000000L + 0x20000L, 3_000)
    tracker.onAcknowledgement(0x10000, 4_000)
    assertEquals(0x10000L, tracker.bytesInFlight)
  }

  @Test
  fun `sequence number wrapping at 31 bits keeps counting`() {
    val tracker = AcknowledgementTracker()
    tracker.onBytesSent(Int.MAX_VALUE.toLong(), 1_000)
    tracker.onAcknowledgement(Int.MAX_VALUE - 100, 2_000)
    tracker.onBytesSent(Int.MAX_VALUE.toLong() + 1_000, 3_000)
    tracker.onAcknowledgement(500, 4_000)
    assertEquals(499L, tracker.bytesInFlight)
  }

  @Test
  fun `reset forgets everything`() {
    val tracker = AcknowledgementTracker()
    tracker.onBytesSent(100_000, 1_000)
    tracker.onAcknowledgement(50_000, 2_000)
    tracker.reset()
    assertEquals(0L, tracker.bytesInFlight)
    assertEquals(0L, tracker.acknowledgementsReceived)
    tracker.onBytesSent(10_000, 3_000)
    tracker.onAcknowledgement(10_000, 4_000)
    assertEquals(1_000L, tracker.rtt)
  }
}
