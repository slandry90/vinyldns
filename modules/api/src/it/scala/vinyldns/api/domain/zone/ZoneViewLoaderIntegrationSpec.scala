/*
 * Copyright 2018 Comcast Cable Communications Management, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package vinyldns.api.domain.zone

import cats.effect.{ContextShift, IO}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.xbill.DNS.ZoneTransferException
import vinyldns.api.VinylDNSConfig
import vinyldns.api.backend.dns.DnsBackend
import vinyldns.core.domain.backend.{BackendConfigs, BackendResolver}
import vinyldns.core.domain.zone.{Zone, ZoneConnection}

import scala.concurrent.ExecutionContext

class ZoneViewLoaderIntegrationSpec extends AnyWordSpec with Matchers {
  private implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  private implicit val cs: ContextShift[IO] = IO.contextShift(ec)
  private val backendResolver =
    BackendResolver
      .apply(BackendConfigs.load(VinylDNSConfig.apiBackend).unsafeRunSync())
      .unsafeRunSync()

  "ZoneViewLoader" should {
    "return a ZoneView upon success" in {
      val zone = Zone("vinyldns.", "test@test.com")
      DnsZoneViewLoader(zone, backendResolver.resolve(zone))
        .load()
        .unsafeRunSync() shouldBe a[ZoneView]
    }

    "return a failure if the transfer connection is bad" in {
      assertThrows[IllegalArgumentException] {
        val zone = Zone(
          "vinyldns.",
          "bad@transfer.connection",
          connection = Some(
            ZoneConnection(
              "vinyldns.",
              "vinyldns.",
              "nzisn+4G2ldMn0q1CV3vsg==",
              "127.0.0.1:19001"
            )
          ),
          transferConnection =
            Some(ZoneConnection("invalid-connection.", "bad-key", "invalid-key", "10.1.1.1"))
        )
        val backend = backendResolver.resolve(zone).asInstanceOf[DnsBackend]
        println(s"${backend.id}, ${backend.xfrInfo}, ${backend.resolver.getAddress}")
        DnsZoneViewLoader(zone, backendResolver.resolve(zone))
          .load()
          .unsafeRunSync()
      }
    }

    "return a failure if the zone doesn't exist in the DNS backend" in {
      assertThrows[ZoneTransferException] {
        val zone = Zone("non-existent-zone", "bad@zone.test")
        DnsZoneViewLoader(zone, backendResolver.resolve(zone))
          .load()
          .unsafeRunSync()
      }
    }

    "return a failure if the zone is larger than the max zone size" in {
      assertThrows[ZoneTooLargeError] {
        val zone = Zone(
          "vinyldns.",
          "test@test.com",
          connection = Some(
            ZoneConnection(
              "vinyldns.",
              "vinyldns.",
              "nzisn+4G2ldMn0q1CV3vsg==",
              "127.0.0.1:19001"
            )
          )
        )
        DnsZoneViewLoader(zone, backendResolver.resolve(zone), 1)
          .load()
          .unsafeRunSync()
      }
    }
  }
}
