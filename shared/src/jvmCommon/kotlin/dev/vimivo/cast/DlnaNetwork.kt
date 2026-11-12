package dev.vimivo.cast

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URI

/** The private IPv4 addresses of this device's up, non-virtual network interfaces. Needs no platform permission. */
fun localPrivateIpv4Addresses(): List<Inet4Address> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.isPointToPoint }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .filter { it.isSiteLocalAddress }
}.getOrDefault(emptyList())

/** Finds DLNA renderers on the local network. Blocking: call it from a background thread. */
object DlnaDiscovery {
    private const val MAX_DESCRIPTION_BYTES = 256 * 1024
    private const val POLL_MILLIS = 300

    fun discover(
        timeoutMillis: Int = 2_500,
        localAddresses: List<InetAddress> = localPrivateIpv4Addresses(),
        searchAddress: InetSocketAddress = InetSocketAddress(Ssdp.MULTICAST_ADDRESS, Ssdp.PORT),
    ): List<DlnaRenderer> {
        val locations = LinkedHashSet<String>()
        val request = Ssdp.searchRequest().toByteArray(Charsets.US_ASCII)
        for (address in localAddresses) {
            try {
                DatagramSocket(InetSocketAddress(address, 0)).use { socket ->
                    socket.soTimeout = POLL_MILLIS
                    socket.send(DatagramPacket(request, request.size, searchAddress))
                    collect(socket, timeoutMillis, locations)
                }
            } catch (_: IOException) {
                // An interface that cannot send (no route, permission) just contributes nothing.
            }
        }
        return locations.mapNotNull(::describe).distinctBy { it.id }
    }

    private fun collect(socket: DatagramSocket, timeoutMillis: Int, into: MutableSet<String>) {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        val buffer = ByteArray(2048)
        while (System.nanoTime() < deadline) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (_: SocketTimeoutException) {
                continue
            }
            Ssdp.parseResponse(String(packet.data, 0, packet.length, Charsets.UTF_8))?.let { into += it.location }
        }
    }

    private fun describe(location: String): DlnaRenderer? = try {
        val connection = URI(location).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 2_000
        connection.readTimeout = 2_000
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                val bytes = connection.inputStream.use { it.readNBytes(MAX_DESCRIPTION_BYTES) }
                DlnaDescription.parse(String(bytes, Charsets.UTF_8), location)
            }
        } finally {
            connection.disconnect()
        }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

/** Tells one renderer what to play. Blocking calls; each returns a failure with a readable message. */
class DlnaClient(private val renderer: DlnaRenderer) {
    fun load(uri: String, title: String, mimeType: String): Result<Unit> = send(AvTransport.setUri(uri, title, mimeType))
    fun play(): Result<Unit> = send(AvTransport.play())
    fun pause(): Result<Unit> = send(AvTransport.pause())
    fun stop(): Result<Unit> = send(AvTransport.stop())
    fun seek(positionMillis: Long): Result<Unit> = send(AvTransport.seek(positionMillis))

    private fun send(call: SoapCall): Result<Unit> = try {
        val connection = URI(renderer.avTransportControlUrl).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 3_000
        connection.readTimeout = 5_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        connection.setRequestProperty("SOAPACTION", call.action)
        try {
            connection.outputStream.use { it.write(call.body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_OK) Result.success(Unit)
            else Result.failure(IOException("${renderer.name} refused the request (HTTP $code)"))
        } finally {
            connection.disconnect()
        }
    } catch (e: IOException) {
        Result.failure(IOException("Could not reach ${renderer.name}: ${e.message}", e))
    }
}
