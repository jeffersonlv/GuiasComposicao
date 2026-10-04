package com.jef.guiascomposicao

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Guias de Composição"
            textSize = 26f
        })
        root.addView(TextView(this).apply {
            text = "\nAtive o overlay, abra a câmera (nativa ou de qualquer app) " +
                "e escolha o guia no painel flutuante. Arraste pela alça ⠿ " +
                "para tirar o painel de cima do botão de disparo.\n"
            textSize = 16f
        })
        status = TextView(this).apply { textSize = 15f }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Ativar overlay"
            setOnClickListener { startOverlay() }
        })
        root.addView(Button(this).apply {
            text = "Desativar overlay"
            setOnClickListener {
                stopService(Intent(this@MainActivity, OverlayService::class.java))
                status.text = "Status: desativado"
            }
        })
        setContentView(ScrollView(this).apply { addView(root) })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        status.text = when {
            !Settings.canDrawOverlays(this) -> "Status: falta a permissão \"Exibir sobre outros apps\""
            OverlayService.isRunning -> "Status: ativo"
            else -> "Status: pronto"
        }
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Ative \"Exibir sobre outros apps\" e volte aqui", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        startForegroundService(Intent(this, OverlayService::class.java))
        moveTaskToBack(true) // sai da frente para você abrir a câmera
    }
}
