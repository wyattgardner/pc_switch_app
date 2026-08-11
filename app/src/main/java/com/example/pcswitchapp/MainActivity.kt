package com.example.pcswitchapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import java.net.InetSocketAddress
import kotlin.math.max

private const val INTERNET_PERMISSION_CODE = 1001
private const val MAX_PROFILES = 99
// How long a toast stays up before a queued one may cut it short
private const val MIN_TOAST_MILLIS = 500L
// The toast window stays attached after cancel(). Adding the next one before that finishes
// throws BadTokenException inside ToastPresenter and the message is lost.
private const val TOAST_GAP_MILLIS = 500L

fun createJsonPacket(message : String ) : String
{
    val jsonData = JSONObject()
    jsonData.put("request", message)
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

            if (response == null)
            {
                // Firmware older than the status reply runs the command and closes without
                // answering, so a silent close still means the command landed
                println("No reply, closed cleanly (old firmware?)")
                onResult(true, "noack")
            }
            else
            {
                val status = JSONObject(response).optString("response")
                onResult(status == "ack", status)
            }
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
                // Old firmware holds the connection open for the whole relay time, which
                // outlasts this timeout on a force shutdown. Current firmware always answers.
                println("Connected but no reply (old firmware?): ${e.message}")
                onResult(true, "noack")
            }
            else
            {
                println("Timed out: ${e.message}")
                onResult(false, "timeout")
            }
        }
        catch (e: java.net.SocketException)
        {
            println("Connection reset: ${e.message}")
            onResult(false, "reset")
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
    private var profile_ids = mutableListOf<Int>()
    private var current_toast: Toast? = null
    private val toast_handler = Handler(Looper.getMainLooper())
    private var toast_shown_at = 0L

    private fun showToast(message: String)
    {
        // A newer message supersedes anything still waiting its turn
        toast_handler.removeCallbacksAndMessages(null)
        current_toast?.cancel()
        current_toast = Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT)
        current_toast?.show()
        toast_shown_at = SystemClock.uptimeMillis()
    }

    // Cuts the toast on screen short instead of replacing it outright, so a fast result does not
    // steal "Sending command..." before it is readable and get dropped along with it
    private fun queueToast(message: String)
    {
        val wait = (toast_shown_at + MIN_TOAST_MILLIS) - SystemClock.uptimeMillis()

        toast_handler.postDelayed({
            current_toast?.cancel()
            toast_handler.postDelayed({ showToast(message) }, TOAST_GAP_MILLIS)
        }, max(0L, wait))
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btn_on = findViewById<Button>(R.id.btn_on)
        val btn_fs = findViewById<Button>(R.id.btn_fs)
        val toggle_lan_wan = findViewById<ToggleButton>(R.id.toggle_lan_wan)
        val profile_grid = findViewById<GridLayout>(R.id.profile_grid)

        loadProfileList()
        active_profile = prefs().getInt("Active_Profile", profile_ids.first())
        if (!profile_ids.contains(active_profile))
        {
            active_profile = profile_ids.first()
        }
        loadData()

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.INTERNET), INTERNET_PERMISSION_CODE)

        btn_on.setOnClickListener {
            sendCommand("turn_pc_on", "Turning on")
        }

        btn_fs.setOnClickListener {
            sendCommand("force_shutdown_pc", "Shutting down")
        }

        toggle_lan_wan.setOnClickListener {
            // LAN if it's changing from LAN to WAN
            // WAN if it's changing from WAN to LAN
            val network_mode_change = if (toggle_lan_wan.isChecked) "LAN" else "WAN"
            saveData(network_mode_change)
            loadData()
        }

        // Column count depends on the width the grid is given, so wait for layout
        profile_grid.post {
            buildProfileTiles()
        }
    }

    override fun onPause()
    {
        super.onPause()

        saveData()
    }

    private fun prefs() = getSharedPreferences("SharedPreferences", MODE_PRIVATE)

    private fun sendCommand(request : String, success_message : String)
    {
        val ip_address = findViewById<EditText>(R.id.textIP).text.toString()
        val port_text = findViewById<EditText>(R.id.textPort).text.toString().trim()
        val port = if (port_text.isEmpty()) 7776 else port_text.toIntOrNull() ?: 7776

        showToast("Sending command...")
        sendPackage(ip_address, port, request) { acked, reason ->
            runOnUiThread {
                queueToast(when {
                    reason == "noack" -> "$success_message (no ack)"
                    acked -> success_message
                    reason == "refused" -> "Connection refused"
                    reason == "timeout" -> "Timed out"
                    reason == "full" -> "Busy, command dropped"
                    else -> "Error sending command"
                })
            }
        }
    }

    private fun loadProfileList()
    {
        val saved = prefs().getString("Profile_Ids", null)
        profile_ids = saved?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toMutableList() ?: mutableListOf()

        // Carries over the three profiles from before profiles were a list
        if (profile_ids.isEmpty())
        {
            profile_ids = mutableListOf(1, 2, 3)
            saveProfileList()
        }
    }

    private fun saveProfileList()
    {
        prefs().edit().putString("Profile_Ids", profile_ids.joinToString(",")).apply()
    }

    private fun defaultProfileName(id : Int) = "Profile " + (profile_ids.indexOf(id) + 1)

    private fun profileName(id : Int) : String
    {
        val saved = prefs().getString("Profile_Name_$id", null)
        return if (saved.isNullOrBlank()) defaultProfileName(id) else saved
    }

    private fun buildProfileTiles()
    {
        val grid = findViewById<GridLayout>(R.id.profile_grid)
        val tile_size = resources.getDimensionPixelSize(R.dimen.profile_tile_size)
        val tile_margin = resources.getDimensionPixelSize(R.dimen.profile_tile_margin)

        val parent = grid.parent as View
        var available = parent.width - parent.paddingLeft - parent.paddingRight
        if (available <= 0)
        {
            available = resources.displayMetrics.widthPixels
        }

        grid.removeAllViews()
        grid.columnCount = max(1, available / (tile_size + tile_margin * 2))

        for ((index, id) in profile_ids.withIndex())
        {
            val tile = makeTile((index + 1).toString(), id == active_profile)
            tile.setOnClickListener {
                switchProfile(id)
            }
            tile.setOnLongClickListener {
                showProfileOptions(id)
                true
            }
            grid.addView(tile)
        }

        val add_tile = makeTile("+", false)
        add_tile.setOnClickListener {
            addProfile()
        }
        grid.addView(add_tile)

        updateProfileName()
    }

    private fun makeTile(label : String, active : Boolean) : Button
    {
        val tile_size = resources.getDimensionPixelSize(R.dimen.profile_tile_size)
        val tile_margin = resources.getDimensionPixelSize(R.dimen.profile_tile_margin)

        return Button(this).apply {
            // Center alignment, otherwise GridLayout lines tiles up by text baseline
            layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, GridLayout.CENTER),
                GridLayout.spec(GridLayout.UNDEFINED, GridLayout.CENTER)
            ).apply {
                width = tile_size
                height = tile_size
                setMargins(tile_margin, tile_margin, tile_margin, tile_margin)
            }
            text = label
            setTextColor(Color.BLACK)
            // Shrinks two digit numbers to fit, enlarges the plus to match a digit optically
            val text_scale = when
            {
                label == "+" -> 1.35f
                label.length == 1 -> 1.0f
                else -> 0.75f
            }
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.profile_tile_text) * text_scale)
            minWidth = 0
            minHeight = 0
            setPadding(0, 0, 0, 0)
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, if (active) R.color.accent else R.color.profile_inactive))
        }
    }

    private fun updateProfileName()
    {
        findViewById<TextView>(R.id.text_profile_name).text = profileName(active_profile)
    }

    private fun switchProfile(id : Int)
    {
        saveData()
        active_profile = id
        prefs().edit().putInt("Active_Profile", id).apply()
        loadData()
        buildProfileTiles()
    }

    private fun addProfile()
    {
        if (profile_ids.size >= MAX_PROFILES)
        {
            showToast("Do you really need more?")
            return
        }

        saveData()

        val new_id = (profile_ids.maxOrNull() ?: 0) + 1
        profile_ids.add(new_id)
        saveProfileList()

        active_profile = new_id
        prefs().edit().putInt("Active_Profile", new_id).apply()
        loadData()
        buildProfileTiles()
    }

    private fun showProfileOptions(id : Int)
    {
        AlertDialog.Builder(this)
            .setTitle(profileName(id))
            .setItems(arrayOf("Rename", "Delete")) { _, which ->
                if (which == 0)
                {
                    showRenameDialog(id)
                }
                else
                {
                    confirmDelete(id)
                }
            }
            .show()
    }

    private fun showRenameDialog(id : Int)
    {
        val input = EditText(this).apply {
            setText(profileName(id))
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("Rename profile")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                prefs().edit().putString("Profile_Name_$id", input.text.toString().trim()).apply()
                updateProfileName()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(id : Int)
    {
        if (profile_ids.size <= 1)
        {
            showToast("Can't delete the only profile")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Delete " + profileName(id) + "?")
            .setPositiveButton("Delete") { _, _ ->
                deleteProfile(id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteProfile(id : Int)
    {
        profile_ids.remove(id)
        saveProfileList()

        prefs().edit()
            .remove("Saved_LAN_IP_$id")
            .remove("Saved_WAN_IP_$id")
            .remove("Saved_Port_$id")
            .remove("Saved_Network_Mode_$id")
            .remove("Profile_Name_$id")
            .apply()

        if (active_profile == id)
        {
            active_profile = profile_ids.first()
            prefs().edit().putInt("Active_Profile", active_profile).apply()
            loadData()
        }

        buildProfileTiles()
    }

    private fun saveData(network_mode_change : String = "none")
    {
        val ip_input = findViewById<EditText>(R.id.textIP).text.toString()
        val port_input = findViewById<EditText>(R.id.textPort).text.toString()
        // false = LAN, true = WAN
        val network_mode = findViewById<ToggleButton>(R.id.toggle_lan_wan).isChecked

        val editor = prefs().edit()

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

        // false = LAN, true = WAN
        val network_mode = prefs().getBoolean("Saved_Network_Mode_$active_profile", false)

        if (network_mode)
        {
            ip_input.setText(prefs().getString("Saved_WAN_IP_$active_profile", ""))
        }
        else
        {
            ip_input.setText(prefs().getString("Saved_LAN_IP_$active_profile", ""))
        }
        port_input.setText(prefs().getString("Saved_Port_$active_profile", ""))
        toggle_lan_wan.isChecked = network_mode
    }
}
