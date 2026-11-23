package com.example.vpn

import com.wireguard.android.backend.Tunnel

class FastXVpnTunnel(
    private val tunnelName: String = "fastxvpn0",
    private val onStateChanged: (Tunnel.State) -> Unit
) : Tunnel {
    override fun getName(): String = tunnelName

    override fun onStateChange(newState: Tunnel.State) {
        onStateChanged(newState)
    }
}
