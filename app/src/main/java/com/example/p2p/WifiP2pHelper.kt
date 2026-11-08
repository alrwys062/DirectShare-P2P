package com.example.p2p

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import com.example.model.P2PConnectionState
import com.example.model.PeerDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

class WifiP2pHelper(private val context: Context) {

    private val tag = "WifiP2pHelper"

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager

    private var channel: WifiP2pManager.Channel? = null

    private val _connectionState = MutableStateFlow(P2PConnectionState())
    val connectionState: StateFlow<P2PConnectionState> = _connectionState.asStateFlow()

    private val _peers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val peers: StateFlow<List<PeerDevice>> = _peers.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private var receiver: BroadcastReceiver? = null
    private var isRegistered = false

    init {
        manager?.let {
            channel = it.initialize(context, context.mainLooper, null)
        }
    }

    fun register() {
        if (isRegistered || manager == null || channel == null) return

        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        val isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                        _connectionState.value = _connectionState.value.copy(
                            isWifiP2pEnabled = isEnabled
                        )
                        Log.d(tag, "Wi-Fi P2P State Changed: enabled=$isEnabled")
                    }

                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        manager.requestPeers(channel) { peersList: WifiP2pDeviceList? ->
                            val list = peersList?.deviceList?.map { device ->
                                PeerDevice(
                                    deviceAddress = device.deviceAddress,
                                    deviceName = device.deviceName.ifBlank { "Android Device" },
                                    status = device.status,
                                    isGroupOwner = device.isGroupOwner
                                )
                            } ?: emptyList()
                            _peers.value = list
                            Log.d(tag, "Peers updated: count=${list.size}")
                        }
                    }

                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                        }

                        if (networkInfo?.isConnected == true) {
                            manager.requestConnectionInfo(channel) { info: WifiP2pInfo? ->
                                onConnectionInfoAvailable(info)
                            }
                            manager.requestGroupInfo(channel) { group: WifiP2pGroup? ->
                                if (group != null) {
                                    _connectionState.value = _connectionState.value.copy(
                                        groupNetworkName = group.networkName,
                                        groupPassphrase = group.passphrase
                                    )
                                }
                            }
                        } else {
                            _connectionState.value = _connectionState.value.copy(
                                isConnected = false,
                                isGroupOwner = false,
                                groupOwnerAddress = null,
                                localIpAddress = getDeviceLocalIpAddress(),
                                groupNetworkName = null,
                                groupPassphrase = null
                            )
                        }
                    }

                    WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE, WifiP2pDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                        }
                        device?.let {
                            _connectionState.value = _connectionState.value.copy(
                                thisDeviceName = it.deviceName.ifBlank { "My Device" },
                                thisDeviceAddress = it.deviceAddress
                            )
                        }
                    }
                }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, intentFilter)
            }
            isRegistered = true
        } catch (e: Exception) {
            Log.e(tag, "Failed to register receiver: ${e.message}")
        }

        // Initialize local IP
        _connectionState.value = _connectionState.value.copy(
            localIpAddress = getDeviceLocalIpAddress()
        )
    }

    fun unregister() {
        if (isRegistered && receiver != null) {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.e(tag, "Failed to unregister receiver: ${e.message}")
            }
            isRegistered = false
        }
    }

    private fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
        if (info == null) return
        val groupOwnerAddress = info.groupOwnerAddress?.hostAddress
        val isGroupOwner = info.isGroupOwner
        val localIp = getDeviceLocalIpAddress()

        Log.d(tag, "Connected! isGroupOwner=$isGroupOwner, groupOwner=$groupOwnerAddress, localIp=$localIp")

        _connectionState.value = _connectionState.value.copy(
            isConnected = info.groupFormed,
            isGroupOwner = isGroupOwner,
            groupOwnerAddress = groupOwnerAddress,
            localIpAddress = localIp
        )
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers(onSuccess: () -> Unit = {}, onFailure: (Int) -> Unit = {}) {
        val mgr = manager ?: run { onFailure(-1); return }
        val ch = channel ?: run { onFailure(-1); return }

        _isDiscovering.value = true
        mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _isDiscovering.value = true
                Log.d(tag, "Discovery initiated successfully")
                onSuccess()
            }

            override fun onFailure(reason: Int) {
                _isDiscovering.value = false
                Log.e(tag, "Discovery failed: reason=$reason")
                onFailure(reason)
            }
        })
    }

    fun stopPeerDiscovery() {
        manager?.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _isDiscovering.value = false
            }
            override fun onFailure(reason: Int) {
                _isDiscovering.value = false
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connectToPeer(deviceAddress: String, onSuccess: () -> Unit = {}, onFailure: (Int) -> Unit = {}) {
        val mgr = manager ?: return
        val ch = channel ?: return

        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
        }

        mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(tag, "Connecting to peer: $deviceAddress")
                onSuccess()
            }

            override fun onFailure(reason: Int) {
                Log.e(tag, "Failed to connect to peer: reason=$reason")
                onFailure(reason)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun createGroup(onSuccess: () -> Unit = {}, onFailure: (Int) -> Unit = {}) {
        val mgr = manager ?: return
        val ch = channel ?: return

        mgr.createGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(tag, "P2P Group created successfully")
                onSuccess()
            }

            override fun onFailure(reason: Int) {
                Log.e(tag, "Failed to create P2P group: reason=$reason")
                onFailure(reason)
            }
        })
    }

    fun removeGroup(onSuccess: () -> Unit = {}, onFailure: (Int) -> Unit = {}) {
        val mgr = manager ?: return
        val ch = channel ?: return

        mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(tag, "P2P Group removed successfully")
                _connectionState.value = _connectionState.value.copy(
                    isConnected = false,
                    isGroupOwner = false,
                    groupOwnerAddress = null
                )
                onSuccess()
            }

            override fun onFailure(reason: Int) {
                Log.e(tag, "Failed to remove group: reason=$reason")
                onFailure(reason)
            }
        })
    }

    /**
     * Resolves the device's local IPv4 address across active network interfaces.
     * Checks P2P interfaces first (p2p-wlan0, p2p0), then Wi-Fi (wlan0), then any non-loopback.
     */
    fun getDeviceLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            // 1. Look for Wi-Fi Direct interface (usually starting with p2p)
            for (intf in interfaces) {
                if (intf.name.contains("p2p", ignoreCase = true) && intf.isUp) {
                    for (addr in intf.inetAddresses) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }
            // 2. Look for standard Wi-Fi (wlan)
            for (intf in interfaces) {
                if (intf.name.contains("wlan", ignoreCase = true) && intf.isUp) {
                    for (addr in intf.inetAddresses) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }
            // 3. Fallback: Any valid non-loopback IPv4
            for (intf in interfaces) {
                if (intf.isUp && !intf.isLoopback) {
                    for (addr in intf.inetAddresses) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error resolving IP: ${e.message}")
        }
        return null
    }
}
