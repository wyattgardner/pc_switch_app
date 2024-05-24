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
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.EditText
import java.net.InetSocketAddress

private const val INTERNET_PERMISSION_CODE = 1001

fun createJsonPacket(message : String ) : String
{
    val jsonData = JSONObject()
    jsonData.put("gpio", message)
    return jsonData.toString()
}

fun sendPackage(IP_address : String, port : Int, message : String)
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
    private var active_profile = 1

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val sharedPreferences = getSharedPreferences("SharedPreferences", MODE_PRIVATE)
        val btn_on_LAN = findViewById<Button>(R.id.btn_on_LAN)
        val ip_input_LAN = findViewById<EditText>(R.id.textLANIP)
        val btn_on_WAN = findViewById<Button>(R.id.btn_on_WAN)
        val ip_input_WAN = findViewById<EditText>(R.id.textWANIP)
        val btn_profile1 = findViewById<Button>(R.id.btn_profile1)
        val btn_profile2 = findViewById<Button>(R.id.btn_profile2)
        val btn_profile3 = findViewById<Button>(R.id.btn_profile3)
        val port_input = findViewById<EditText>(R.id.textPort)
        var ip_address: String
        var port: Int

        active_profile = sharedPreferences.getInt("Active_Profile", 1)
        loadData()

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
            sendPackage(ip_address, port,"on")
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
            sendPackage(ip_address, port,"on")
        }

        btn_profile1.setOnClickListener {
            saveData()
            active_profile = 1
            btn_profile1.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#673AB7"))
            btn_profile2.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#808080"))
            btn_profile3.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#808080"))
            loadData()
        }

        btn_profile2.setOnClickListener {
            saveData()
            active_profile = 2
            btn_profile1.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#808080"))
            btn_profile2.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#673AB7"))
            btn_profile3.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#808080"))
            loadData()
        }

        btn_profile3.setOnClickListener {
            saveData()
            active_profile = 3
            btn_profile1.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#808080"))
            btn_profile2.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#808080"))
            btn_profile3.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#673AB7"))
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

    private fun saveData()
    {
        val ip_input_LAN = findViewById<EditText>(R.id.textLANIP)
        val ip_input_WAN = findViewById<EditText>(R.id.textWANIP)
        val port_input = findViewById<EditText>(R.id.textPort)

        val sharedPreferences = getSharedPreferences("SharedPreferences", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt("Active_Profile", active_profile)
        editor.putString("Saved_LAN_IP_$active_profile", ip_input_LAN.text.toString())
        editor.putString("Saved_WAN_IP_$active_profile", ip_input_WAN.text.toString())
        editor.putString("Saved_Port_$active_profile", port_input.text.toString())
        editor.apply()
    }

    private fun loadData()
    {
        val ip_input_LAN = findViewById<EditText>(R.id.textLANIP)
        val ip_input_WAN = findViewById<EditText>(R.id.textWANIP)
        val port_input = findViewById<EditText>(R.id.textPort)

        val sharedPreferences = getSharedPreferences("SharedPreferences", MODE_PRIVATE)
        ip_input_LAN.setText(sharedPreferences.getString("Saved_LAN_IP_$active_profile", ""))
        ip_input_WAN.setText(sharedPreferences.getString("Saved_WAN_IP_$active_profile", ""))
        port_input.setText(sharedPreferences.getString("Saved_Port_$active_profile", ""))
    }
}
