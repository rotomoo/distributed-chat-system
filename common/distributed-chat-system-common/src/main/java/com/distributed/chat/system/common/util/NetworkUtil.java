package com.distributed.chat.system.common.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NetworkUtil {

    public static String getServerIp() {
        String ip = null;
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            boolean isExit = false;
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (networkInterface == null || !networkInterface.isUp()
                    || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress addr = inetAddresses.nextElement();
                    if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()) {
                        continue;
                    }
                    if (addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                    if (addr.getHostAddress() != null && addr.getHostAddress().indexOf(".") != -1) {
                        ip = addr.getHostAddress();
                        isExit = true;
                        break;
                    }
                }
                if (isExit) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("error : {}", e.getMessage());
        }
        return ip;
    }
}
