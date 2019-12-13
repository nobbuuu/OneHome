/*
 * Copyright 2011 David Simmons
 * http://cafbit.com/entry/testing_multicast_support_on_android
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
package com.cafbit.netlib.dns;

import android.net.wifi.WifiManager.MulticastLock;
import android.util.Log;

import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.NetworkError;
import com.aylanetworks.aylasdk.error.PreconditionError;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.aylanetworks.aylasdk.lan.AylaLanModule.MDNSListener;

/**
 * This thread runs in the background while the user has our
 * program in the foreground, and handles sending mDNS queries
 * and processing incoming mDNS packets.
 * @author simmons
 */
public class NetThread extends Thread {

    public static final String TAG = "MDNS";
    
    // the standard mDNS multicast address
    private static final byte[] MDNS_ADDR =
        new byte[] {(byte) 224,(byte) 0,(byte) 0,(byte) 251};
    private static final int BUFFER_SIZE = 4096;
    private static final int MDNS_STD_PORT = 5353;
    private static final int MDNS_AYLA_PORT = 10276;

    private NetworkInterface networkInterface;
    private InetAddress groupAddress;
    private MulticastSocket multicastSocket;
    private NetUtil netUtil;
    private MDNSListener mdnsListener;
    private String hostName;

    /**
     * Construct the network thread.
     * @param listener MDNS listener
     */
    public NetThread(MDNSListener listener, String hostName) {
        super("net");
        this.mdnsListener = listener;
        netUtil = new NetUtil(AylaNetworks.sharedInstance().getContext());
        this.hostName = hostName;
    }

    /**
     * Open a multicast socket on the mDNS address and port.
     * @throws IOException
     *
     */
    private void openSocket() throws IOException {
        multicastSocket = new MulticastSocket(MDNS_STD_PORT);
        multicastSocket.setTimeToLive(2);
        multicastSocket.setReuseAddress(true);
        multicastSocket.setNetworkInterface(networkInterface);
        multicastSocket.joinGroup(groupAddress);
    }

    /**
     * The main network loop.  Multicast DNS packets are received,
     * processed, and sent to the UI.
     *
     * This loop may be interrupted by closing the multicastSocket,
     * at which time any commands in the commandQueue will be
     * processed.
     */
    @Override
    public void run() {
        Log.d(TAG, "starting MDNS thread for host "+hostName);

        Set<InetAddress> localAddresses = NetUtil.getLocalAddresses();
        MulticastLock multicastLock = null;

        // initialize the network
        try {
            networkInterface = netUtil.getFirstWifiOrEthernetInterface();
            if (networkInterface == null) {
                throw new IOException("Your WiFi is not enabled.");
            }
            groupAddress = InetAddress.getByAddress(MDNS_ADDR);

        } catch (IOException e1) {
            mdnsListener.failed(new PreconditionError("WiFi is not enabled.", e1));
            return;
        }
        try {
            multicastLock = netUtil.getWifiManager().createMulticastLock("unmote");
            multicastLock.acquire();
            openSocket();
        } catch (IOException e) {
            mdnsListener.failed(new NetworkError("Cannot open socket " +
                    "on port " + MDNS_AYLA_PORT, e));
            return;
        }
        mdnsListener.ready();
        // set up the buffer for incoming packets
        byte[] responseBuffer = new byte[BUFFER_SIZE];
        DatagramPacket response = new DatagramPacket(responseBuffer, BUFFER_SIZE);

        // loop!
        while (true) {
            // zero the incoming buffer for good measure.
            java.util.Arrays.fill(responseBuffer, (byte) 0); // clear buffer

            // receive a packet (or process an incoming command)
            try {
                multicastSocket.receive(response);
            } catch (IOException e) {
                // check for commands to be run
                Command cmd = commandQueue.poll();
                if (cmd == null) {
                    AylaLog.e("MDNS", "No commands in the MDNS queue");
                    mdnsListener.failed(new NetworkError("IOException in receive response ", e));
                    return;
                }

                // reopen the socket
                try {
                    openSocket();
                } catch (IOException e1) {
                    mdnsListener.failed(new NetworkError("IOException during socket reopen ", e1));
                    return;
                }

                // process commands
                if (cmd instanceof QueryCommand) {
                    try {
                        query(((QueryCommand)cmd).host);
                    } catch (IOException e1) {
                        mdnsListener.failed(new NetworkError("IOException during query command ", e1));
                    }
                } else if (cmd instanceof QuitCommand) {
                    break;
                }

                continue;
            }

            // ignore our own packet transmissions.
            if (localAddresses.contains(response.getAddress())) {
                continue;
            }

            // parse the DNS packet
            try{
                DNSMessage message = new DNSMessage(response.getData(), response.getOffset(), response
                        .getLength());
                if(message.toString().contains(hostName)){
                    //This is the packet we are looking for
                    Packet packet = new Packet(response, multicastSocket);
                    String description = message.toString().trim();

                    int forwardSlash = description.indexOf('/');
                    String ipAddress = description.substring(++forwardSlash);
                    mdnsListener.success(ipAddress);
                    break;
                } else{
                    continue;
                }
            } catch(Exception e){
                continue;
            }
        }
        
        // release the multicast lock
        multicastLock.release();
        multicastLock = null;
        return;
    }
    
    /**
     * Transmit an mDNS query on the local network.
     * @param host
     * @throws IOException
     */
    private void query(String host) throws IOException {
        byte[] requestData = (new DNSMessage(host)).serialize();
        DatagramPacket requestToStdPort =
            new DatagramPacket(requestData, requestData.length, InetAddress.getByAddress
                    (MDNS_ADDR), MDNS_STD_PORT);
        DatagramPacket requestToAylaPort =
                new DatagramPacket(requestData, requestData.length, InetAddress.getByAddress
                        (MDNS_ADDR), MDNS_AYLA_PORT);
        multicastSocket.send(requestToStdPort);
        multicastSocket.send(requestToAylaPort);
    }

    private Queue<Command> commandQueue = new ConcurrentLinkedQueue<Command>();
    private static abstract class Command {
    }
    private static class QuitCommand extends Command {}
    private static class QueryCommand extends Command {
        public QueryCommand(String host) { this.host = host; }
        public String host;
    }
    public void submitQuery() {
        commandQueue.offer(new QueryCommand(hostName));
        if(multicastSocket != null){
            multicastSocket.close();
        }
    }
    public void submitQuit() {
        commandQueue.offer(new QuitCommand());
        if (multicastSocket != null) {
            multicastSocket.close();
        }
    }

}

class Util {

   public static String hexDump(byte[] bytes) {
       return hexDump(bytes, 0, bytes.length);
   }

   public static String hexDump(byte[] bytes, int offset, int length) {
       StringBuilder sb = new StringBuilder();
       for (int i=0; i<length; i+=16) {
           int rowSize = length - i;
           if (rowSize > 16) { rowSize = 16; }
           byte[] row = new byte[rowSize];
           System.arraycopy(bytes, offset+i, row, 0, rowSize);
           hexDumpRow(sb, row, i);
       }
       return sb.toString();
   }

   private static void hexDumpRow(StringBuilder sb, byte[] bytes, int offset) {
       sb.append(String.format("%04X: ",offset));
       for (int i=0; i<16; i++) {
           if (bytes.length > i) {
               sb.append(String.format("%02X ",bytes[i]));
           } else {
               sb.append("   ");
           }
       }
       for (int i=0; i<16; i++) {
           if (bytes.length > i) {
               char c = '.';
               int v = (int)bytes[i];
               if ((v > 0x20) && (v < 0x7F)) {
                   c = (char)v;
               }
               sb.append(c);
           }
       }
       sb.append('\n');
   }

}
