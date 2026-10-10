package me.algosketch.hanilkeyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.algosketch.hanilkeyboard.ime.HanilInputMethodService
import me.algosketch.hanilkeyboard.ui.theme.HanilKeyboardTheme

/** 키보드 활성화·선택 안내 및 테스트 화면. */
class MainActivity : ComponentActivity() {

    private var enabled by mutableStateOf(false)
    private var selected by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HanilKeyboardTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SetupScreen(
                        enabled = enabled,
                        selected = selected,
                        onOpenSettings = { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                        onPickKeyboard = { getSystemService(InputMethodManager::class.java).showInputMethodPicker() },
                        licenses = { assets.open("licenses/mozc.txt").bufferedReader().use { it.readText() } },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val imm = getSystemService(InputMethodManager::class.java)
        val ours = imm.enabledInputMethodList.firstOrNull {
            it.packageName == packageName && it.serviceName == HanilInputMethodService::class.java.name
        }
        enabled = ours != null
        selected = ours != null &&
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) == ours.id
    }
}

@Composable
private fun SetupScreen(
    enabled: Boolean,
    selected: Boolean,
    onOpenSettings: () -> Unit,
    onPickKeyboard: () -> Unit,
    licenses: () -> String,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    var licenseText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("한일 키보드", style = MaterialTheme.typography.headlineMedium)
        Text("한글 자판으로 일본어(히라가나·가타카나·한자)를 입력하는 키보드입니다. 입력 내용은 기기 안에서만 처리되며 외부로 전송되지 않습니다.")

        Text("1. 키보드 사용 설정 ${if (enabled) "✅" else ""}", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("시스템 설정에서 '한일 키보드' 켜기")
        }

        Text("2. 키보드 선택 ${if (selected) "✅" else ""}", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onPickKeyboard, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("입력 방법에서 '한일 키보드' 선택")
        }

        Text("3. 입력해 보기", style = MaterialTheme.typography.titleMedium)
        Text(
            "예) 각코우 → がっこう / 学校, 와타시하 → わたしは / 私は\n" +
                "스페이스: 다음 후보, 엔터: 확정, 후보 탭: 바로 확정",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            placeholder = { Text("여기에 입력") },
        )

        OutlinedButton(onClick = { licenseText = licenses() }, modifier = Modifier.fillMaxWidth()) {
            Text("오픈소스 라이선스")
        }
    }

    licenseText?.let { content ->
        AlertDialog(
            onDismissRequest = { licenseText = null },
            confirmButton = { TextButton(onClick = { licenseText = null }) { Text("닫기") } },
            title = { Text("오픈소스 라이선스") },
            text = {
                Text(
                    content,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            },
        )
    }
}
