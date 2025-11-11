package com.dcl.demo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton

@Composable
fun SaveEmbeddingDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var userId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar usuario") },
        text = {
            Column {
                OutlinedTextField(value = userId, onValueChange = { userId = it }, label = { Text("User ID") })
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") })
            }
        },
        confirmButton = {
            Button(onClick = { onSave(userId, name) }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
