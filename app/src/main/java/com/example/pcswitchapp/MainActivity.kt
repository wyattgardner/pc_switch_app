package com.example.pcswitchapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import android.Manifest
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.EditText
import android.widget.ToggleButton
import java.net.InetSocketAddress

private const val INTERNET_PERMISSION_CODE = 1001

fun createJsonPacket(message : String ) : String
{
    val jsonData = JSONObject()
    jsonData.put("gpio", message)
    return jsonData.toString()
}

fun sendPackage(IP_address : String, port : Int, message : String, onResult: (Boolean, String?) -> Unit = { _, _ -> })
{
    Thread {
        val timeoutMillis = 2000 // Timeout in Millisecond (2 seconds)
        val jsonPacket = createJsonPacket(message)

        val socket = Socket()
        var connected = false

        try
        {
            val socketAddress = InetSocketAddress(IP_address, port)
            socket.connect(socketAddress, timeoutMillis)
            connected = true
            socket.soTimeout = timeoutMillis

            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
            writer.write(jsonPacket)
            writer.newLine()
            writer.flush()

            println("Sent successfully: $jsonPacket")

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val response = reader.readLine()
            val status = response?.let { JSONObject(it).optString("status") }

            onResult(status == "ack", status)
        }
        catch (e: java.net.ConnectException)
        {
            println("Connection refused: ${e.message}")
            onResult(false, "refused")
        }
        catch (e: java.net.SocketTimeoutException)
        {
            if (connected)
            {
                println("Connected but no ack (Pico busy?): ${e.message}")
                onResult(false, "busy")
            }
            else
            {
                println("Timed out: ${e.message}")
                onResult(false, "timeout")
            }
        }
        catch (e: java.net.SocketException)
        {
            println("Connection reset (buffer full?): ${e.message}")
            onResult(false, "full")
        }
        catch (e: Exception)
        {
            println("Error during connect or transmission: ${e.message}")
            onResult(false, "error")
        }
        finally
        {
            socket.close()
        }
    }.start()
}

class MainActivity : AppCompatActivity()
{
    private var active_profile = 1
    private var current_toast: Toast? = null

    private fun showToast(message: String)
    {
        current_toast?.cancel()
        current_toast = Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT)
        current_toast?.show()
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val sharedPreferences = getSharedPreferences("SharedPreferences", MODE_PRIVATE)
        val btn_on = findViewById<Button>(R.id.btn_on)
        val ip_input = findViewById<EditText>(R.id.textIP)
        val btn_fs = findViewById<Button>(R.id.btn_fs)
        val btn_profile1 = findViewById<Button>(R.id.btn_profile1)
        val btn_profile2 = findViewById<Button>(R.id.btn_profile2)
        val btn_profile3 = findViewById<Button>(R.id.btn_profile3)
        val port_input = findViewById<EditText>(R.id.textPort)
        // LAN = off, WAN = on
        val toggle_lan_wan = findViewById<ToggleButton>(R.id.toggle_lan_wan)
        var ip_address: String
        var port: Int

        active_profile = sharedPreferences.getInt("Active_Profile", 1)
        loadData()

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.INTERNET), INTERNET_PERMISSION_CODE)

        btn_on.setOnClickListener {
            ip_address = ip_input.text.toString()
            port = if (port_input.text.toString().trim().isEmpty())
            {
                7776
            }
            else
            {
                port_input.text.toString().toInt()
            }
            showToast("Sending command...")
            sendPackage(ip_address, port, "on") { acked, reason ->
                runOnUiThread {
                    showToast(when {
                        acked -> "Turning on"
                        reason == "refused" -> "Connection refused"
                        reason == "timeout" -> "Timed out"
                        reason == "busy" -> "Command queued..."
                        else -> "Error sending command"
                    })
                }
            }
        }

        btn_fs.setOnClickListener {
            ip_address = ip_input.text.toString()
            port = if (port_input.text.toString().trim().isEmpty())
            {
                7776
            }
            else
            {
                port_input.text.toString().toInt()
            }
            showToast("Sending command...")
            sendPackage(ip_address, port, "fs") { acked, reason ->
                runOnUiThread {
                    showToast(when {
                        acked -> "Shutting down"
                        reason == "refused" -> "Connection refused"
                        reason == "timeout" -> "Timed out"
                        reason == "busy" -> "Command queued..."
                        else -> "Error sending command"
                    })
                }
            }
        }

        btn_profile1.setOnClickListener {
            saveData()
            active_profile = 1
            btn_profile1.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#81D4FA"))
            btn_profile2.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#808080"))
            btn_profile3.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#808080"))
            loadData()
        }

        btn_profile2.setOnClickListener {
            saveData()
            active_profile = 2
            btn_profile1.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#808080"))
            btn_profile2.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#81D4FA"))
            btn_profile3.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#808080"))
            loadData()
        }

        btn_profile3.setOnClickListener {
            saveData()
            active_profile = 3
            btn_profile1.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#808080"))
            btn_profile2.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#808080"))
            btn_profile3.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#81D4FA"))
            loadData()
        }

        toggle_lan_wan.setOnClickListener {
            // LAN if it's changing from LAN to WAN
            // WAN if it's changing from WAN to LAN
            val network_mode_change = if (toggle_lan_wan.isChecked) "LAN" else "WAN"
            saveData(network_mode_change)
            loadData()
        }

        when(active_profile)
        {
            1 -> btn_profile1.performClick()
            2 -> btn_profile2.performClick()
            3 -> btn_profile3.performClick()
        }
    }

    override fun onPause()
    {
        super.onPause()

        saveData()
    }

    private fun saveData(network_mode_change : String = "none")
    {
        val ip_input = findViewById<EditText>(R.id.textIP).text.toString()
        val port_input = findViewById<EditText>(R.id.textPort).text.toString()
        // false = LAN, true = WAN
        val network_mode = findViewById<ToggleButton>(R.id.toggle_lan_wan).isChecked

        val sharedPreferences = getSharedPreferences("SharedPreferences", MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        editor.putInt("Active_Profile", active_profile)
        editor.putString("Saved_Port_$active_profile", port_input)
        editor.putBoolean("Saved_Network_Mode_$active_profile", network_mode)
        if (network_mode_change == "LAN")
        {
            editor.putString("Saved_LAN_IP_$active_profile", ip_input)
        }
        else if (network_mode_change == "WAN" || network_mode)
        {
            editor.putString("Saved_WAN_IP_$active_profile", ip_input)
        }
        else
        {
            editor.putString("Saved_LAN_IP_$active_profile", ip_input)
        }
        editor.apply()
    }

    private fun loadData()
    {
        val ip_input = findViewById<EditText>(R.id.textIP)
        val port_input = findViewById<EditText>(R.id.textPort)
        val toggle_lan_wan = findViewById<ToggleButton>(R.id.toggle_lan_wan)

        val sharedPreferences = getSharedPreferences("SharedPreferences", MODE_PRIVATE)
        // false = LAN, true = WAN
        val network_mode = sharedPreferences.getBoolean("Saved_Network_Mode_$active_profile", false)

        if (network_mode)
        {
            ip_input.setText(sharedPreferences.getString("Saved_WAN_IP_$active_profile", ""))
        }
        else
        {
            ip_input.setText(sharedPreferences.getString("Saved_LAN_IP_$active_profile", ""))
        }
        port_input.setText(sharedPreferences.getString("Saved_Port_$active_profile", ""))
        toggle_lan_wan.isChecked = network_mode
    }
}
