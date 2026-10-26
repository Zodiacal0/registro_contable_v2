package com.registrogastos.sincronizacion;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Averigua con qué IP se puede llegar a esta PC desde el teléfono en la misma red. */
final class RedLocal {

    private RedLocal() {
    }

    /**
     * La primera IPv4 privada de una interfaz activa que no sea virtual. Las de VirtualBox,
     * Hyper‑V o VPN existen pero el teléfono no las alcanza, así que se dejan al final.
     */
    static List<String> direcciones() {
        var buenas = new ArrayList<String>();
        var dudosas = new ArrayList<String>();
        try {
            for (NetworkInterface red : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!red.isUp() || red.isLoopback()) {
                    continue;
                }
                String nombre = red.getDisplayName().toLowerCase();
                boolean virtual = red.isVirtual() || nombre.contains("virtual") || nombre.contains("vethernet")
                        || nombre.contains("hyper-v") || nombre.contains("vmware") || nombre.contains("vpn")
                        || nombre.contains("tap") || nombre.contains("wsl");
                for (var direccion : Collections.list(red.getInetAddresses())) {
                    if (direccion instanceof Inet4Address && direccion.isSiteLocalAddress()) {
                        (virtual ? dudosas : buenas).add(direccion.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            return List.of();
        }
        buenas.addAll(dudosas);
        return buenas;
    }
}
