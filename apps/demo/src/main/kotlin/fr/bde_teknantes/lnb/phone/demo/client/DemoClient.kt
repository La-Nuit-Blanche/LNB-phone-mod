package fr.bde_teknantes.lnb.phone.demo.client

import fr.bde_teknantes.lnb.phone.demo.client.capture.CaptureRunner

/** Client-only parts of the demo. Loaded only on the physical client. */
object DemoClient {
    fun init() {
        CaptureRunner.startFromSystemProperties()
    }
}
