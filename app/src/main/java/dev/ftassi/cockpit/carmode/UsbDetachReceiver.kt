package dev.ftassi.cockpit.carmode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import dev.ftassi.cockpit.MainActivity
import dev.ftassi.cockpit.nowplaying.currentPlayerPackage

// Ends the drive when the splitter is unplugged after the process died, which
// took the activity's own audio callback with it. Without this, Do Not Disturb
// would stay on at home. Declared in the manifest so Android starts us for it.
class UsbDetachReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (MainActivity.alive) return
        val device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java) ?: return
        if ((0 until device.interfaceCount).none { device.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_AUDIO }) return
        val carMode = CarMode(context)
        if (!carMode.isActive) return
        carMode.lastPlayer = currentPlayerPackage(context)
        carMode.exit()
    }
}
