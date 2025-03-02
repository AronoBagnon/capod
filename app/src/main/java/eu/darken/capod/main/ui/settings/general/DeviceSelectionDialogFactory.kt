package eu.darken.capod.main.ui.settings.general

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.capod.R


class DeviceSelectionDialogFactory constructor(private val context: Context) {

    fun create(
        devices: List<BluetoothDevice>,
        current: BluetoothDevice?,
        callback: (BluetoothDevice?) -> Unit
    ): AlertDialog {
        return MaterialAlertDialogBuilder(context).apply {
            setTitle(R.string.settings_maindevice_address_label)

            val pairing = devices
                .map { (it.name ?: "?") to it.address }
                .plus(context.getString(R.string.settings_maindevice_address_none) to "")

            setSingleChoiceItems(
                pairing.map { it.first }.toTypedArray(),
                pairing.indexOfFirst { it.second == current?.address ?: "" },
                DialogInterface.OnClickListener { dialog, which ->
                    val selected = devices.firstOrNull { it.address == pairing[which].second }
                    callback(selected)
                    dialog.dismiss()
                }
            )

        }.create()
    }
}