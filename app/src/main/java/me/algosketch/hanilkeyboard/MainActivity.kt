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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.algosketch.hanilkeyboard.ime.HanilInputMethodService
import me.algosketch.hanilkeyboard.ime.KeyboardLayoutType
import me.algosketch.hanilkeyboard.ime.KeyboardSettings
import me.algosketch.hanilkeyboard.ui.theme.HanilKeyboardTheme

/** 키보드 활성화·선택 안내 및 테스트 화면. */
class MainActivity : ComponentActivity() {

    private var enabled by mutableStateOf(false)
    private var selected by mutableStateOf(false)
    private var layout by mutableStateOf(KeyboardLayoutType.HANGUL_QWERTY)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        layout = KeyboardSettings.layout(this)
        setContent {
            HanilKeyboardTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SetupScreen(
                        enabled = enabled,
                        selected = selected,
                        onOpenSettings = { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                        onPickKeyboard = { getSystemService(InputMethodManager::class.java).showInputMethodPicker() },
                        layout = layout,
                        onLayoutChange = {
                            layout = it
                            KeyboardSettings.setLayout(this, it)
                        },
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
    layout: KeyboardLayoutType,
    onLayoutChange: (KeyboardLayoutType) -> Unit,
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

        Text("3. 자판 고르기", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyboardLayoutType.entries.forEach { type ->
                LayoutOption(type, selected = type == layout, onSelect = { onLayoutChange(type) })
            }
        }

        Text("4. 입력해 보기", style = MaterialTheme.typography.titleMedium)
        Text(
            when (layout) {
                KeyboardLayoutType.HANGUL_QWERTY -> "예) 각코우 → がっこう / 学校, 와타시하 → わたしは / 私は\n"
                KeyboardLayoutType.KANA_PAD -> "예) か·う → く, さ(←) → ざ, か·や → きゃ, つ·小 → っ\n"
            } + "스페이스: 다음 후보, 엔터: 확정, 후보 탭: 바로 확정",
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

/** 자판 종류 선택 항목. 온보딩 설명을 함께 보여준다. */
@Composable
private fun LayoutOption(type: KeyboardLayoutType, selected: Boolean, onSelect: () -> Unit) {
    val (title, description) = when (type) {
        KeyboardLayoutType.HANGUL_QWERTY -> "한글 두벌식" to
            "익숙한 한글 자판으로 일본어 발음을 한글로 입력합니다. 각코우 → がっこう 처럼 " +
            "입력을 마치면 히라가나·가타카나·한자 후보가 나타납니다. 쌍자음·ㅒㅖ는 시프트 또는 키를 아래로 드래그해 입력합니다."
        KeyboardLayoutType.KANA_PAD -> "가나 (행·단 분리)" to
            "왼쪽에서 행(か さ た な は ま ら わ ん)을, 오른쪽에서 단(あ い う え お)을 골라 히라가나를 직접 입력합니다. " +
            "か 다음 い → き, か 다음 や → きゃ. 왼쪽 키를 왼쪽으로 드래그하면 탁음(が), 오른쪽은 반탁음(ぱ), " +
            "위로는 숫자입니다. 小 키는 직전 글자를 작은 글자(っ, ゃ …)로 바꿉니다."
    }
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}