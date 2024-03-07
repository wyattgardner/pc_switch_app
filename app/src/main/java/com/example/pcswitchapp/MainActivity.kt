package com.example.pcswitchapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.Socket
import android.Manifest
import android.widget.EditText
import java.net.InetSocketAddress

private const val INTERNET_PERMISSION_CODE = 1001

fun createJsonPacket(message : String ) : String
{
    val jsonData = JSONObject()
    jsonData.put("status", "ok")
    jsonData.put("gpio", message)
    return jsonData.toString()
}

fun send_package(IP_address : String, port : Int, message : String)
{
    Thread {
        val timeoutMillis = 1000 // Timeout in Millisecond (1 second)
        val jsonPacket = createJsonPacket(message)

        val socket = Socket()

        try
        {
            val socketAddress = InetSocketAddress(IP_address, port)
            socket.connect(socketAddress, timeoutMillis)

            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
            writer.write(jsonPacket)
            writer.newLine()
            writer.flush()

            println("Sent successfully: $jsonPacket")
        }
        catch (e: Exception)
        {
            println("Error during connect or transmission: ${e.message}")
        }
        finally
        {
            socket.close()
        }
    }.start()
}


class MainActivity : AppCompatActivity()
{
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val sharedPreferences = getSharedPreferences("SharedPreferences", MODE_PRIVATE)
        val btn_on_LAN = findViewById<Button>(R.id.btn_on_LAN)
        val ip_input_LAN = findViewById<EditText>(R.id.textLANIP)
        val btn_on_WAN = findViewById<Button>(R.id.btn_on_WAN)
        val ip_input_WAN = findViewById<EditText>(R.id.textWANIP)
        val port_input = findViewById<EditText>(R.id.textPort)
        var ip_address: String
        var port: Int

        ip_input_LAN.setText(sharedPreferences.getString("Saved_LAN_IP", ""))
        ip_input_WAN.setText(sharedPreferences.getString("Saved_WAN_IP", ""))
        port_input.setText(sharedPreferences.getString("Saved_Port", ""))

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.INTERNET), INTERNET_PERMISSION_CODE)

        btn_on_LAN.setOnClickListener {
            ip_address = ip_input_LAN.text.toString()
            port = if (port_input.text.toString().trim().isEmpty())
            {
                7776
            }
            else
            {
                port_input.text.toString().toInt()
            }
            Toast.makeText(this@MainActivity, "Turning on", Toast.LENGTH_SHORT).show()
            send_package(ip_address, port,"on")
        }

        btn_on_WAN.setOnClickListener {
            ip_address = ip_input_WAN.text.toString()
            port = if (port_input.text.toString().trim().isEmpty())
            {
                7776
            }
            else
            {
                port_input.text.toString().toInt()
            }
            Toast.makeText(this@MainActivity, "Turning on", Toast.LENGTH_SHORT).show()
            send_package(ip_address, port,"on")
        }
    }

    override fun onPause()
    {
        super.onPause()

        val ip_input_LAN = findViewById<EditText>(R.id.textLANIP)
        val ip_input_WAN = findViewById<EditText>(R.id.textWANIP)
        val port_input = findViewById<EditText>(R.id.textPort)

        val sharedPreferences = getSharedPreferences("SharedPreferences", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("Saved_LAN_IP", ip_input_LAN.text.toString())
        editor.putString("Saved_WAN_IP", ip_input_WAN.text.toString())
        editor.putString("Saved_Port", port_input.text.toString())
        editor.apply()
    }
}
