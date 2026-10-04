package ohi.andre.consolelauncher.compose

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ohi.andre.consolelauncher.BatteryViewModel
import ohi.andre.consolelauncher.BlueToothViewModel
import ohi.andre.consolelauncher.MemoryViewModel
import ohi.andre.consolelauncher.MobileViewModel
import ohi.andre.consolelauncher.NetworkViewModel
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.StorageViewModel
import ohi.andre.consolelauncher.TimeViewModel
import ohi.andre.consolelauncher.UnlockTimeViewModel
import ohi.andre.consolelauncher.WeatherViewModel
import ohi.andre.consolelauncher.WifiViewModel
import ohi.andre.consolelauncher.colorString
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui

/**
 * Owns everything behind the status rows at the top of the home screen: the view models feeding
 * each row, the persisted row layout and the Compose view they're drawn into. The view models are
 * scoped to [activity], so they survive configuration changes like the activity's own would.
 */
class StatusPanel(private val activity: ComponentActivity) {
    private val timeViewModel by activity.viewModels<TimeViewModel>()
    private val memoryViewModel by activity.viewModels<MemoryViewModel>()
    private val storageViewModel by activity.viewModels<StorageViewModel>()
    private val networkViewModel by activity.viewModels<NetworkViewModel>()
    private val wifiViewModel by activity.viewModels<WifiViewModel>()
    private val mobileViewModel by activity.viewModels<MobileViewModel>()
    private val bluetoothViewModel by activity.viewModels<BlueToothViewModel>()
    private val batteryViewModel by activity.viewModels<BatteryViewModel>()
    private val weatherViewModel by activity.viewModels<WeatherViewModel>()
    private val unlockTimeViewModel by activity.viewModels<UnlockTimeViewModel>()

    private val rowsStore = StatusRowsStore(activity)

    /** Builds the row sources and renders the rows into R.id.status_labels_compose_view. */
    fun load() {
        val sources = buildSources()
        val hintColor = Color(XMLPrefsManager.getColor(Theme.device_color))

        activity.findViewById<ComposeView>(R.id.status_labels_compose_view).setContent {
            var rows by remember { mutableStateOf(rowsStore.load()) }
            StatusLabelsScreen(
                rows = rows,
                onRowsChange = {
                    rows = it
                    rowsStore.save(it)
                },
                sources = sources,
                hintColor = hintColor,
            )
        }
    }

    private fun buildSources(): Map<StatusRowType, StateFlow<CharSequence>> {
        val network: StateFlow<CharSequence> = combine(
            networkViewModel.connectionStatus, wifiViewModel.ssid, mobileViewModel.status,
            bluetoothViewModel.status,
        ) { ntwk, wifi, mobile, bth -> colorString("$ntwk | Wifi: $wifi | Mob: $mobile | Bth: $bth",
            XMLPrefsManager.getColor(Theme.network_info_color))}.stateIn(activity.lifecycleScope,
            SharingStarted.WhileSubscribed(5000), "Loading...")

        val username = XMLPrefsManager.get(Ui.username)
        val deviceName = XMLPrefsManager.get(Ui.deviceName)
        val device = MutableStateFlow<CharSequence>(
            colorString("$username: $deviceName", XMLPrefsManager.getColor(Theme.device_color))
        )

        return mapOf(
            StatusRowType.TIME to timeViewModel.currentText,
            StatusRowType.MEMORY to memoryViewModel.currentText,
            StatusRowType.BATTERY to batteryViewModel.batteryStatus,
            StatusRowType.STORAGE to storageViewModel.currentText,
            StatusRowType.NETWORK to network,
            StatusRowType.DEVICE to device,
            StatusRowType.WEATHER to weatherViewModel.weather,
            StatusRowType.UNLOCKS to unlockTimeViewModel.currentText,
        )
    }
}
