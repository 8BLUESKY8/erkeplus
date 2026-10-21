package gitee.leolin_me.erkepp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.data.AppPreferences
import gitee.leolin_me.erkepp.data.BlockRules

/**
 * 内容屏蔽：关键词与正则两套规则。
 *
 * 单独一页，是因为两个多行输入框放在设置首页会把其它项挤到屏幕外。
 */
@Composable
fun BlockRulesScreen(preferences: AppPreferences, onBack: () -> Unit) {
    val rules = remember(preferences.blockedKeywords, preferences.blockedRegexes) {
        BlockRules.parse(preferences.blockedKeywords, preferences.blockedRegexes)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("内容屏蔽", style = MaterialTheme.typography.titleMedium)
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "标题命中的活动不会出现在发现页。规则改完立即生效，不需要保存。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = blockRuleSummary(rules),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )

            OutlinedTextField(
                value = preferences.blockedKeywords,
                onValueChange = { preferences.blockedKeywords = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("关键词") },
                placeholder = { Text("例如：讲座、打卡") },
                supportingText = { Text("按子串匹配，忽略大小写。换行或逗号分隔。") },
                minLines = 4,
                shape = RoundedCornerShape(12.dp),
            )
            OutlinedTextField(
                value = preferences.blockedRegexes,
                onValueChange = { preferences.blockedRegexes = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("正则") },
                placeholder = { Text("例如：^\\d+天.*打卡$") },
                supportingText = { Text("一行一条。写错的正则会自动忽略，不会让页面崩溃。") },
                minLines = 4,
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 规则条数概览，设置首页和本页共用。 */
internal fun blockRuleSummary(rules: BlockRules): String = buildList {
    add("关键词 ${rules.keywordCount} 条")
    add("正则 ${rules.patternCount} 条")
    if (rules.invalidPatternCount > 0) add("${rules.invalidPatternCount} 条正则无效")
}.joinToString(" · ")
