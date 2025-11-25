package org.css_apps_m3.password_manager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.css_apps_m3.password_manager.data.PasswordRepository

class DisclaimerActivity : ComponentActivity() {

    private lateinit var repo: PasswordRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = PasswordRepository(this)

        setContent {
            AppThemed {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Disclaimer",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            "We are not responsibly if your passwords are getting hacked! Use it at your own risk!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                                startActivity(Intent(this@DisclaimerActivity, MainActivity::class.java))
                                finish()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Accept",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.background)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}